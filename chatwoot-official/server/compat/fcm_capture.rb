# Only the isolated CI worker mounts this file. Keep the real queue, subscription
# lookup, preferences and payload builder, replacing the external FCM call.
abort 'Disposable database required' unless ENV['POSTGRES_DATABASE'] == 'crm_compat' && ENV['CRM_CAPTURE_FCM'] == 'test'
module CaptureSyntheticFcm
  def fcm_client = self

  def send_v1(payload)
    if payload.fetch(:token) == 'qa-personal-device'
      event = JSON.parse(payload.fetch(:data).fetch(:payload)).fetch('data').fetch('notification')
      File.open('/tmp/crm-qa-personal-fcm.jsonl', 'a') do |log|
        log.flock(File::LOCK_EX)
        log.puts(event.to_json)
      end
    end
    { status_code: 200, body: '{"name":"projects/local-capture/messages/test"}' }
  end
end

Rails.application.config.to_prepare do
  service = Notification::FcmService
  service.prepend(CaptureSyntheticFcm) unless service < CaptureSyntheticFcm
end
