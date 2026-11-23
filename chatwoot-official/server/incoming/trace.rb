# Read-only by default. An explicit Android subscription ID sends one neutral
# diagnostic banner using Chatwoot's native FCM client, without replaying an event.
require 'sidekiq/api'
require 'securerandom'

def trace_jobs(messages, notices)
  event_job = 'EventDispatcherJob'
  push_job = 'Notification::PushNotificationJob'
  message_gids = messages.map { |m| m.to_global_id.to_s }
  notice_gids = notices.map { |n| n.to_global_id.to_s }
  workers = Sidekiq::ProcessSet.new(false).reject { |p| p['quiet'] }
  [EventDispatcherJob.queue_name, Notification::PushNotificationJob.queue_name].uniq.each do |name|
    queue = Sidekiq::Queue.new(name)
    puts "CRM queue=#{name} workers=#{workers.count { |p| Array(p['queues']).include?(name) }} waiting=#{queue.size} oldest_seconds=#{queue.latency.round}"
  end
  { retry: Sidekiq::RetrySet.new, dead: Sidekiq::DeadSet.new }.each do |state, jobs|
    # Bound Redis reads even on a busy shared installation. Never print job args.
    jobs.take(150).each do |job|
      wrapper = job.args.first
      next unless wrapper.is_a?(Hash)
      klass = wrapper['job_class']
      args = wrapper['arguments']
      next unless args.is_a?(Array)
      gid =
        case klass
        when event_job
          next unless args.first == 'message.created' && args[2].is_a?(Hash)
          args[2].dig('message', '_aj_globalid')
        when push_job
          args.first['_aj_globalid'] if args.first.is_a?(Hash)
        end
      next unless (klass == event_job ? message_gids : notice_gids).include?(gid)
      error = job.item['error_class'].to_s
      error = 'unknown' unless error.match?(/\A[A-Za-z][A-Za-z0-9_:]{0,150}\z/)
      puts "CRM job=#{klass} id=#{gid.split('/').last} state=#{state} error=#{error}"
    end
  end
end

def trace_native_probe(notice, sub_id)
  sub = NotificationSubscription.fcm.find_by(user_id: notice.user_id, id: sub_id)
  attrs = sub&.subscription_attributes || {}
  abort 'CRM probe=android_subscription_unavailable' unless attrs['devicePlatform'].to_s.casecmp?('Android') && attrs['push_token'].present?
  project = GlobalConfigService.load('FIREBASE_PROJECT_ID', nil)
  credentials = GlobalConfigService.load('FIREBASE_CREDENTIALS', nil)
  abort 'CRM probe=firebase_settings_missing' if project.blank? || credentials.blank?
  puts "CRM native_project_matches=#{project == 'volshebno-tut-crm'}"
  label = SecureRandom.hex(3)
  service = Notification::PushNotificationService.new(notification: notice)
  options = service.send(:fcm_options, sub)
  options[:notification] = { title: "CRM #{label}", body: "Push test - conversation #{notice.conversation.display_id}" }
  client = Notification::FcmService.new(project, credentials).fcm_client
  puts 'CRM native_client_ready=true'
  response = client.send_v1(options)
  status = Integer(response.fetch(:status_code))
  data = begin
    JSON.parse(response.fetch(:body))
  rescue JSON::ParserError
    {}
  end
  accepted = status.between?(200, 299) && data.is_a?(Hash) && data['name'].present?
  reason = data.is_a?(Hash) && data['error'].is_a?(Hash) ? data['error'] : {}
  codes = [reason['status'], *Array(reason['details']).grep(Hash).flat_map { |d| d.values_at('errorCode', 'reason') }]
  codes = codes.grep(/\A[A-Z][A-Z0-9_]{1,60}\z/).uniq
  puts "CRM native_probe=#{label} subscription=#{sub.id} http=#{status} accepted=#{accepted} codes=#{codes.join(',')}"
end

begin
  account = Account.find(2)
  member = account.account_users.find_by!(user_id: 2)
  chat = account.conversations.find_by(display_id: Integer(ENV.fetch('CRM_CONVERSATION_ID'), 10))
  abort 'CRM conversation=not_found_in_account_2' unless chat
  settings = NotificationSetting.find_by(account: account, user: member.user)
  visible = ConversationPolicy.new({ user: member.user, account: account, account_user: member }, chat).show?
  blocked = chat.contact.blocked?
  puts "CRM conversation=#{chat.display_id} inbox=#{chat.inbox.channel_type} assignee=#{chat.assignee_id || 'none'} access=#{!!visible} blocked=#{blocked}"
  installed = defined?(AmazyIncomingPush) && Messages::NewMessageNotificationService < AmazyIncomingPush
  puts "CRM rule_in_runner=#{!!installed} assigned_push=#{!!settings&.push_assigned_conversation_new_message?} participant_push=#{!!settings&.push_participating_conversation_new_message?}"
  NotificationSubscription.fcm.where(user_id: member.user_id).each do |sub|
    attrs = sub.subscription_attributes || {}
    puts "CRM subscription=#{sub.id} android=#{attrs['devicePlatform'].to_s.casecmp?('Android')} token_present=#{attrs['push_token'].present?}"
  end
  recent = chat.messages.reorder(id: :desc).limit(5).to_a
  incoming = chat.messages.incoming.where(private: false).reorder(id: :desc).first
  messages = (recent + [incoming]).compact.uniq
  notices = chat.notifications.where(account: account, user: member.user, secondary_actor: messages).order(:id).to_a
  messages.each do |msg|
    ids = notices.select { |n| n.secondary_actor_id == msg.id }.map { |n| "#{n.id}/#{n.notification_type}" }
    puts "CRM msg=#{msg.id} msk=#{msg.created_at.in_time_zone('Europe/Moscow').strftime('%F_%T')} type=#{msg.message_type} private=#{msg.private?} notices=#{ids.presence&.join(',') || 'none'}"
  end
  stage = 'queues'
  trace_jobs(messages, notices)
  if ENV['CRM_PROBE_SUBSCRIPTION'].present?
    stage = 'native_probe'
    abort 'CRM probe=conversation_access_or_block' unless visible && !blocked
    notice = notices.reverse.find { |n| n.secondary_actor_id == incoming&.id }
    abort 'CRM probe=no_notification_for_latest_incoming' unless notice
    trace_native_probe(notice, Integer(ENV.fetch('CRM_PROBE_SUBSCRIPTION'), 10))
  end
rescue StandardError => error
  # Exception messages may contain authorization headers, customer data or tokens.
  abort "CRM stage=#{stage || 'records'} exception=#{error.class.name}"
end
