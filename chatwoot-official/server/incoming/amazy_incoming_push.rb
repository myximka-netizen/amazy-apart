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
