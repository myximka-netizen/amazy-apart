# Provide the server's service-account JSON on stdin. Nothing is echoed.
require 'json'
require 'openssl'
expected = 'volshebno-tut-crm'
begin
  key = JSON.parse(STDIN.read)
  abort 'Wrong Firebase project or key type' unless key['type'] == 'service_account' && key['project_id'] == expected &&
                                                    key['client_email'].present? && OpenSSL::PKey.read(key.fetch('private_key')).private?
  ActiveRecord::Base.logger.silence do
    InstallationConfig.transaction do
      { 'FIREBASE_PROJECT_ID' => expected, 'FIREBASE_CREDENTIALS' => JSON.generate(key) }.each do |name, value|
        setting = InstallationConfig.find_or_initialize_by(name: name)
        setting.locked = false if setting.new_record?
        setting.value = value
        setting.save! if setting.changed?
      end
    end
  end
  puts 'Firebase settings saved.'
rescue StandardError => e
  abort "Settings were not saved (#{e.class.name}). Verify the service-account file and Chatwoot version."
end
