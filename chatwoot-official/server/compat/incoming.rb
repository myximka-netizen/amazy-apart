abort 'Disposable database required' unless ENV['POSTGRES_DATABASE'] == 'crm_compat'
account = Account.find(2)
inbox = account.inboxes.first!
operator = account.users.find(2)
other = account.users.where.not(id: operator.id).first!
abort 'Native push flags missing' unless operator.notification_settings.find_by!(account: account).then { |s| s.push_participating_conversation_new_message? && s.push_assigned_conversation_new_message? }
abort 'Expected optional disablement of auto-assignment' if inbox.reload.enable_auto_assignment?
abort 'Existing assignments lost' unless account.conversations.where(assignee: other).exists?

def observe(inbox, assignee, operator, visibility: true)
  chat = inbox.conversations.first!
  conversation = inbox.conversations.create!(account: inbox.account, contact: chat.contact,
                                           contact_inbox: chat.contact_inbox, assignee: assignee)
  raise 'Unexpected automatic assignment' unless conversation.reload.assignee_id == assignee&.id
  create_message = ->(type, private_note) do
    conversation.messages.create!(account: inbox.account, inbox: inbox, sender: type == :incoming ? chat.contact : assignee,
                                  content: 'Isolated QA', message_type: type, private: private_note)
  end
  messages = [create_message.call(:incoming, false), create_message.call(:incoming, false)]
  # Verify actual async dispatch in the separate worker before replaying an event.
  wanted = conversation.notifications.where(user: operator, secondary_actor: messages)
  deadline = Process.clock_gettime(Process::CLOCK_MONOTONIC) + 30
  if visibility
    until wanted.distinct.count(:secondary_actor_id) == 2
      raise 'Worker did not route both incoming messages' if Process.clock_gettime(Process::CLOCK_MONOTONIC) > deadline
      sleep 0.25
    end
  end
  messages.each { |m| Messages::NewMessageNotificationService.new(message: m).perform }
  expected = visibility ? 2 : 0
  raise 'Missing, duplicate or unauthorized notifications' unless wanted.count == expected
  raise 'Conversation was reassigned or operator was enrolled as a participant' unless conversation.reload.assignee_id == assignee&.id && conversation.conversation_participants.where(user: operator).none?
  messages
end

inbox.inbox_members.find_or_create_by!(user: operator)
observe(inbox, nil, operator)
puts 'PASS unassigned: first and repeat incoming, restart-safe event replay'
observe(inbox, other, operator)
puts 'PASS assigned to another operator: no transfer, no participant change'
observe(inbox, operator, operator)
puts 'PASS already assigned to recipient: no duplicate message notification'
base = inbox.conversations.reorder(:id).first!
[:outgoing, :activity].each do |kind|
  msg = base.messages.create!(account: account, inbox: inbox, sender: other, content: 'Isolated QA', message_type: kind)
  Messages::NewMessageNotificationService.new(message: msg).perform
  raise 'Unexpected extra notification' if operator.notifications.where(secondary_actor: msg).exists?
end
note = base.messages.create!(account: account, inbox: inbox, sender: other, content: 'Isolated QA note', message_type: :outgoing, private: true)
Messages::NewMessageNotificationService.new(message: note).perform
raise 'Unexpected note alert' if operator.notifications.where(secondary_actor: note).exists?
puts 'PASS outgoing messages, notes and service events excluded from additional delivery'

account.account_users.find_by!(user: operator).update!(role: :agent)
inbox.inbox_members.where(user: operator).destroy_all
observe(inbox, nil, operator, visibility: false)
puts 'PASS access policy for an inbox outside the operator permissions'

foreign = Account.find(1)
foreign.account_users.create!(user: operator, role: :administrator)
channel = Channel::Api.create!(account: foreign)
foreign_inbox = foreign.inboxes.create!(name: 'Other account', channel: channel, enable_auto_assignment: false)
contact = foreign.contacts.create!(name: 'Independent guest')
link = ContactInbox.create!(contact: contact, inbox: foreign_inbox, source_id: SecureRandom.uuid)
foreign_inbox.conversations.create!(account: foreign, contact: contact, contact_inbox: link)
observe(foreign_inbox, nil, operator, visibility: false)
puts 'PASS foreign account excluded even with administrator access'
