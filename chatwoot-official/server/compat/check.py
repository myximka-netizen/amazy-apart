"""Exercise the release client's API contracts on a disposable Chatwoot 4.19.1."""
import io
import json
import os
import time
from pathlib import Path

import requests
import websocket

host = 'http://127.0.0.1:3000'
fixtures = json.loads(Path(os.environ['CRM_FIXTURES']).read_text())
session = requests.Session()
root = '/api/v1/accounts/2'
chat = f"{root}/conversations/{fixtures['conversation']}"
visitor_chat = (f"/public/api/v1/inboxes/{fixtures['channel']}/contacts/{fixtures['source']}"
                f"/conversations/{fixtures['conversation']}/messages")


def call(method, path, *, visitor=False, **kwargs):
    client = requests if visitor else session
    response = client.request(method, host + path, timeout=20, **kwargs)
    if not response.ok:
        # Never print tokens, request headers, or response bodies.
        raise RuntimeError(f"{method} {path.replace(fixtures['source'], 'test-contact')} returned {response.status_code}")
    return response


def subscribe(pubsub):
    ws = websocket.create_connection('ws://127.0.0.1:3000/cable', origin=host,
                                     subprotocols=['actioncable-v1-json'], timeout=15)
    identifier = json.dumps({'channel': 'RoomChannel', 'pubsub_token': pubsub,
                             'account_id': 2, 'user_id': fixtures['agent']})
    ws.send(json.dumps({'command': 'subscribe', 'identifier': identifier}))
    until(ws, lambda event: event.get('type') == 'confirm_subscription')
    return ws


def until(ws, predicate):
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        ws.settimeout(max(0.1, deadline - time.monotonic()))
        event = json.loads(ws.recv())
        if predicate(event):
            return
    raise RuntimeError('Action Cable event timeout')


def create_incoming(text):
    data = call('POST', visitor_chat, visitor=True, json={'content': text}).json()
    assert data['content'] == text
    return data['id']


def received_id(event, message_id):
    data = event.get('message')
    return isinstance(data, dict) and data.get('event') == 'message.created' and data['data']['id'] == message_id


def records(data):
    if isinstance(data, dict):
        yield data
        for value in data.values():
            yield from records(value)
    elif isinstance(data, list):
        for value in data:
            yield from records(value)


def run():
    assert call('GET', '/api').json()['version'] == '4.19.1'
    login = call('POST', '/auth/sign_in', json={
        'email': 'operator@example.org', 'password': os.environ['CRM_TEST_PASSWORD']})
    session.headers.update({k: login.headers[k] for k in ['access-token', 'client', 'uid']})
    profile = call('GET', '/api/v1/profile').json()
    assert any(a['id'] == 2 for a in profile['accounts'])
    assert call('GET', chat).json()['id'] == fixtures['conversation']
    print('PASS session, account 2 and conversation', flush=True)

    ws = subscribe(profile['pubsub_token'])
    incoming = create_incoming('Mobile QA arrival question')
    until(ws, lambda event: received_id(event, incoming))
    ws.close()
    missed = create_incoming('Mobile QA while disconnected')
    ws = subscribe(profile['pubsub_token'])
    latest = create_incoming('Mobile QA after reconnect')
    until(ws, lambda event: received_id(event, latest))
    ws.close()
    history = call('GET', chat + '/messages', params={'after': incoming}).json()['payload']
    assert {missed, latest} <= {m['id'] for m in history}
    print('PASS incoming messages, WebSocket, reconnect and missed history', flush=True)

    answer = call('POST', chat + '/messages', json={
        'content': 'QA quoted reply', 'private': False, 'content_attributes': {'in_reply_to': incoming}}).json()
    assert answer['content_attributes']['in_reply_to'] == incoming
    note = call('POST', chat + '/messages', json={'content': 'QA internal note', 'private': True}).json()
    assert note['private'] is True
    visible = list(records(call('GET', visitor_chat, visitor=True).json()))
    assert any(m.get('content') == 'QA quoted reply' for m in visible)
    assert all(m.get('content') != 'QA internal note' for m in visible)
    print('PASS quoted reply and private-note visibility', flush=True)

    samples = [('photo.jpg', 'image/jpeg', 'image'), ('video.mp4', 'video/mp4', 'video'),
               ('guide.pdf', 'application/pdf', 'file'), ('recording.m4a', 'audio/mp4', 'audio')]
    for name, mime, kind in samples:
        raw = Path(os.environ['CRM_MEDIA'], name).read_bytes()
        result = call('POST', chat + '/messages', files={'attachments[]': (name, io.BytesIO(raw), mime)},
                      data={'private': 'false', 'echo_id': f'qa-{name}'}).json()
        attachment = result['attachments'][0]
        assert attachment['file_type'] == kind, (name, attachment['file_type'])
        # This is an isolated server with only local blob storage.
        assert attachment['data_url'].startswith(host + '/')
        preview = requests.get(attachment['data_url'], timeout=20)
        preview.raise_for_status()
        assert preview.content == raw, name
    print('PASS photo, video, PDF and M4A multipart uploads and attachment downloads', flush=True)

    assert any(r.get('id') == fixtures['contact'] for r in records(call('GET', root + '/search/contacts', params={'q': 'Mobile QA'}).json()))
    assert any(r.get('id') == incoming for r in records(call('GET', root + '/search/messages', params={'q': 'arrival question'}).json()))
    assert call('GET', root + f"/contacts/{fixtures['contact']}").json()['payload']['id'] == fixtures['contact']
    canned = call('GET', root + '/canned_responses', params={'search': 'arrival'}).json()
    assert any(r.get('short_code') == 'arrival' for r in records(canned))
    filtered = call('GET', root + '/conversations', params={
        'status': 'open', 'assignee_type': 'mine', 'sort_by': 'latest', 'inbox_id': fixtures['inbox']}).json()
    assert any(c['id'] == fixtures['conversation'] for c in filtered['data']['payload'])
    print('PASS contacts, search, saved replies, conversation filters and sort request', flush=True)

    call('POST', chat + '/assignments', json={'assignee_id': fixtures['agent']})
    call('POST', chat + '/assignments', params={'team_id': fixtures['team']})
    call('POST', chat + '/labels', json={'labels': ['mobile-qa']})
    call('POST', chat + '/toggle_priority', json={'priority': 'high'})
    call('POST', chat + '/toggle_status', json={'status': 'resolved'})
    updated = call('GET', chat).json()
    assert updated['meta']['assignee']['id'] == fixtures['agent'] and updated['meta']['team']['id'] == fixtures['team']
    assert 'mobile-qa' in updated['labels'] and updated['priority'] == 'high' and updated['status'] == 'resolved'
    call('POST', chat + '/toggle_status', json={'status': 'open'})
    call('POST', chat + '/unread')
    assert call('GET', chat).json()['unread_count'] > 0
    call('POST', chat + '/update_last_seen')
    assert call('GET', chat).json()['unread_count'] == 0
    call('GET', root + '/notifications')
    print('PASS assignment, team, labels, priority, status and unread counters', flush=True)

    device = {'device_id': 'isolated-ci-device', 'devicePlatform': 'Android', 'push_token': 'synthetic-registration'}
    path = '/api/v1/notification_subscriptions'
    def register():
        return call('POST', path, json={'notification_subscription': {
            'subscription_type': 'fcm', 'subscription_attributes': device}}).json()
    original = register()
    device['push_token'] = 'synthetic-rotation'
    rotated = register()
    assert original['id'] == rotated['id']
    assert rotated['subscription_attributes']['push_token'] == device['push_token']
    call('DELETE', path, json={'push_token': device['push_token']})
    print('PASS FCM subscription registration, rotation and logout; device delivery requires Honor', flush=True)


if __name__ == '__main__':
    run()
