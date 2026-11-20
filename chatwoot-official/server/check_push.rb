# Run in the Chatwoot sidekiq container with bundle exec rails runner - .
# The default mode reads configuration. "probe" sends a device-only test.
# "send" also tests opening an assigned conversation. Neither writes a chat message.
require 'json'
require 'net/http'
require 'securerandom'
require 'stringio'

account_id = 2
expected_project = 'volshebno-tut-crm'
begin
  project = GlobalConfigService.load('FIREBASE_PROJECT_ID', nil)
  credentials = GlobalConfigService.load('FIREBASE_CREDENTIALS', nil)
  key = credentials.is_a?(String) && !credentials.empty? ? JSON.parse(credentials) : {}
  correct_key = project == expected_project && key['project_id'] == project &&
                key['type'] == 'service_account' && key['private_key'].present? && key['client_email'].present?
  puts "Chatwoot=#{Chatwoot.config[:version]} account=#{account_id} account_exists=#{Account.exists?(account_id)}"
  puts "firebase_project_matches=#{project == expected_project} service_account_matches=#{correct_key}"
  # Match against the running Rails router without executing a controller or modifying CRM data.
  base = "/api/v1/accounts/#{account_id}"
  routes = {
    get: ["/api", "/api/v1/profile", "#{base}/conversations", "#{base}/conversations/1/messages",
          "#{base}/notifications", "#{base}/search/contacts", "#{base}/search/messages", "#{base}/search/conversations",
          "#{base}/canned_responses", "#{base}/contacts/1", "#{base}/contacts/1/conversations",
          "#{base}/teams", "#{base}/labels"],
    post: ["/auth/sign_in", "/api/v1/notification_subscriptions", "#{base}/conversations/1/messages",
           "#{base}/conversations/1/assignments", "#{base}/conversations/1/labels",
           "#{base}/conversations/1/update_last_seen", "#{base}/conversations/1/toggle_status", "#{base}/conversations/1/toggle_priority"]
  }
  missing = []
  routes.each do |method, paths|
    paths.each do |path|
      begin
        Rails.application.routes.recognize_path(path, method: method)
      rescue ActionController::RoutingError
        missing << "#{method.to_s.upcase} #{path}"
      end
    end
  end
  puts "mobile_routes=#{routes.values.sum(&:size) - missing.size}/#{routes.values.sum(&:size)}"
  missing.each { |route| puts "missing_route=#{route}" }
  members = AccountUser.where(account_id: account_id)
  subscriptions = NotificationSubscription.fcm.where(user_id: members.select(:user_id))
  agent_id = ENV['CRM_AGENT_ID'].presence&.then { |value| Integer(value) }
  if agent_id
    abort 'Operator is not a member of account 2' unless members.exists?(user_id: agent_id)
    subscriptions = subscriptions.where(user_id: agent_id)
    settings = NotificationSetting.find_by(account_id: account_id, user_id: agent_id)
    puts "push_new_conversation_enabled=#{!!settings&.push_conversation_creation?}"
    puts "push_assigned_message_enabled=#{!!settings&.push_assigned_conversation_new_message?}"
    puts "push_participating_message_enabled=#{!!settings&.push_participating_conversation_new_message?}"
    recent = Notification.where(account_id: account_id, user_id: agent_id).order(id: :desc).limit(4)
    recent.pluck(:id, :notification_type, :created_at).each do |id, kind, created|
      puts "notification_id=#{id} kind=#{kind} created=#{created.utc.iso8601}"
    end
  end
  puts "registered_devices=#{subscriptions.count}"
  subscriptions.order(id: :desc).limit(20).each do |sub|
    attrs = sub.subscription_attributes || {}
    puts "subscription_id=#{sub.id} agent_id=#{sub.user_id} android=#{attrs['devicePlatform'].to_s.casecmp('Android').zero?} has_token=#{attrs['push_token'].present?} updated=#{sub.updated_at.utc.iso8601}"
  end
  mode = ENV.fetch('CRM_PUSH_MODE', 'check')
  exit if mode == 'check'
  abort 'Unknown test mode' unless %w[probe send].include?(mode)
  abort 'Firebase settings are incomplete' unless correct_key
  abort 'Set CRM_AGENT_ID for the test operator' unless agent_id
  target = subscriptions.find(Integer(ENV.fetch('CRM_SUBSCRIPTION_ID')))
  attrs = target.subscription_attributes
  abort 'Select an Android subscription with a token' unless attrs['devicePlatform'].to_s.casecmp('Android').zero? && attrs['push_token'].present?
  test_id = SecureRandom.hex(3)
  message = { token: attrs.fetch('push_token'), android: { priority: 'high' },
              notification: { title: "CRM - #{test_id}", body: "FCM test #{test_id}" } }
  if mode == 'send'
    display_id = Integer(ENV.fetch('CRM_CONVERSATION_ID'))
    chat = Account.find(account_id).conversations.find_by!(display_id: display_id, assignee_id: agent_id)
    event = { account_id: account_id, notification_type: 'conversation_creation',
              primary_actor_type: 'Conversation', primary_actor_id: chat.id, primary_actor: { id: chat.display_id } }
    message[:notification] = { title: "Волшебно тут CRM · #{test_id}", body: "Проверка перехода в тестовый диалог №#{chat.display_id}" }
    message[:data] = { payload: { data: { notification: event } }.to_json }
  end
  stage = 'Google OAuth'
  require 'googleauth'
  auth = Google::Auth::ServiceAccountCredentials.make_creds(
    json_key_io: StringIO.new(credentials), scope: 'https://www.googleapis.com/auth/firebase.messaging'
  )
  oauth = auth.fetch_access_token!.fetch('access_token')
  stage = 'FCM HTTP v1'
  uri = URI("https://fcm.googleapis.com/v1/projects/#{project}/messages:send")
  request = Net::HTTP::Post.new(uri, 'Authorization' => "Bearer #{oauth}", 'Content-Type' => 'application/json')
  request.body = JSON.generate(message: message)
  response = Net::HTTP.start(uri.host, uri.port, use_ssl: true, open_timeout: 10, read_timeout: 20) { |http| http.request(request) }
  result = begin
    JSON.parse(response.body)
  rescue JSON::ParserError
    {}
  end
  unless response.is_a?(Net::HTTPSuccess) && result['name'].present?
    problem = result.fetch('error', {})
    codes = [problem['status'], *Array(problem['details']).flat_map { |row| row.values_at('errorCode', 'reason') }]
    labels = codes.compact.grep(/\A[A-Z][A-Z0-9_]{1,64}\z/).uniq.join(',')
    abort "FCM HTTP #{response.code} status=#{labels.presence || 'unknown'}"
  end
  puts "FCM accepted test=#{test_id}#{" conversation=#{display_id}" if mode == 'send'} at=#{Time.now.utc.iso8601}"
rescue StandardError => error
  # Some libraries include signed requests or the whole response in exception messages.
  abort "Diagnostic stopped at #{stage || 'configuration'} (#{error.class.name}). Check the selected IDs, server version, Firebase settings and network."
end
