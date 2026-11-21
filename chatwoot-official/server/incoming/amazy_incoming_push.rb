# Personal incoming-message notifications, using Chatwoot 4.19.1's regular
# permission checks, notification record, Sidekiq delivery job and FCM payload.
module AmazyIncomingPush
  ACCOUNT_ID = 2
  OPERATOR_ID = 2

  def perform
    return super unless message.account_id == ACCOUNT_ID && message.incoming? && !message.private?

    # A redelivery of the same event must not race its original delivery.
    message.with_lock do
      super
      recipient = account.users.find_by(id: OPERATOR_ID)
      next if recipient.nil? || already_notified?(recipient)
      NotificationBuilder.new(notification_type: 'participating_conversation_new_message',
                              user: recipient, account: account, primary_actor: conversation,
                              secondary_actor: message).perform
    end
  end
end

Rails.application.config.to_prepare do
  service = Messages::NewMessageNotificationService
  service.prepend(AmazyIncomingPush) unless service < AmazyIncomingPush
end

# Upstream removes all but the newest notification in a conversation. Its
# delivery job takes a GlobalID, so deleting an earlier row can lose the push
# when two messages arrive before the default queue drains.
module AmazyIncomingPushRetention
  def perform(notification)
    return super unless notification.is_a?(Notification) && notification.account_id == AmazyIncomingPush::ACCOUNT_ID &&
                        notification.user_id == AmazyIncomingPush::OPERATOR_ID && notification.primary_actor_type == 'Conversation'

    group = Notification.where(account_id: notification.account_id, user_id: notification.user_id,
                               primary_actor_type: 'Conversation', primary_actor_id: notification.primary_actor_id)
    latest = group.order(created_at: :desc, id: :desc).pick(:id)
    guests = Message.where(account_id: notification.account_id, conversation_id: notification.primary_actor_id,
                           message_type: :incoming, private: false)
    # Keep recent guest alerts through Sidekiq delivery/retries. On the next
    # event older alerts are coalesced as usual. Never delete another account's rows.
    protected_rows = group.where(secondary_actor_type: 'Message', secondary_actor_id: guests.select(:id))
                          .where(created_at: 7.days.ago..)
    group.where.not(id: latest).where.not(id: protected_rows.select(:id)).find_each(&:destroy!)
  end
end

Rails.application.config.to_prepare do
  job = Notification::RemoveDuplicateNotificationJob
  job.prepend(AmazyIncomingPushRetention) unless job < AmazyIncomingPushRetention
end
