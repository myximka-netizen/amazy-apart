# Only seed the empty, isolated CI instance.
abort 'Disposable database required' unless ENV['POSTGRES_DATABASE'] == 'crm_compat' && Account.none?
Account.create!(name: 'Unused test account')
account = Account.create!(name: 'Mobile compatibility test')
raise 'Expected account 2' unless account.id == 2
agent = User.new(name: 'Test operator', email: 'operator@example.org', password: ENV.fetch('CRM_TEST_PASSWORD'))
agent.skip_confirmation!
agent.save!
AccountUser.create!(account: account, user: agent, role: :administrator)
watcher = User.new(name: 'Personal alerts', email: 'alerts@example.org', password: ENV.fetch('CRM_TEST_PASSWORD'))
watcher.skip_confirmation!
watcher.save!
raise 'Expected personal operator 2' unless watcher.id == 2
AccountUser.create!(account: account, user: watcher, role: :administrator)
channel = Channel::Api.create!(account: account)
inbox = account.inboxes.create!(name: 'Test API inbox', channel: channel)
inbox.inbox_members.create!(user: agent)
team = account.teams.create!(name: 'Mobile QA')
team.team_members.create!(user: agent)
account.labels.create!(title: 'mobile-qa', color: '#206ee8')
account.canned_responses.create!(short_code: 'arrival', content: 'QA arrival instructions')
contact = account.contacts.create!(name: 'Mobile QA contact', email: 'guest@example.org')
link = ContactInbox.create!(contact: contact, inbox: inbox, source_id: SecureRandom.uuid)
conversation = account.conversations.create!(contact: contact, contact_inbox: link, inbox: inbox, assignee: agent)
result = { agent: agent.id, team: team.id, contact: contact.id, inbox: inbox.id, conversation: conversation.reload.display_id,
           channel: channel.identifier, source: link.source_id }
File.write('/tmp/crm-fixtures.json', result.to_json, mode: 'w', perm: 0600)
