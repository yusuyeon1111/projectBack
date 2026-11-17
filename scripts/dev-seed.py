#!/usr/bin/env python3
"""로컬 개발용 테스트 데이터를 실행 중인 서버에 API로 만든다.

    python3 scripts/dev-seed.py                        # http://localhost:8080
    python3 scripts/dev-seed.py http://localhost:8090  # 다른 주소

만드는 것:
  - 계정 3개(비밀번호는 모두 PASSWORD): leader(팀장), alice(앨리스), bob(밥)
  - leader의 프로젝트 모집글 1개(backend 2명)
  - alice 신청 → 승인(첫 승인이라 팀이 생긴다), bob 신청은 대기로 둔다(승인 화면 확인용)

이미 만든 적이 있으면(leader에게 팀이 있으면) 아무것도 하지 않는다.
운영 서버에는 쓰지 않는다. 테스트 계정이 생긴다.
"""
import json
import sys
import urllib.error
import urllib.request
from datetime import date, timedelta

PASSWORD = 'test1234!'
USERS = {'leader': '팀장', 'alice': '앨리스', 'bob': '밥'}
BASE = (sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:8080').rstrip('/') + '/api'


def call(method, path, body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request) as response:
            raw = response.read().decode()
    except urllib.error.HTTPError as e:
        raise SystemExit(f'{method} {path} 실패: {e.code} {e.read().decode()}')
    try:
        return json.loads(raw)
    except ValueError:
        return raw


def sign_in(username):
    return call('POST', '/member/signin', {'username': username, 'password': PASSWORD})['accessToken']


def main():
    tokens = {}
    for username, nickname in USERS.items():
        try:
            tokens[username] = sign_in(username)  # 이미 있는 계정
            continue
        except SystemExit:
            pass
        call('POST', '/member/signup', {'username': username, 'password': PASSWORD, 'nickname': nickname,
                                        'email': f'{username}@dev.test', 'name': nickname})
        tokens[username] = sign_in(username)

    if call('GET', '/teams/my', token=tokens['leader']):
        print('이미 시드가 있습니다. 계정만 확인했습니다.')
        return

    start = date.today() + timedelta(days=1)
    created = call('POST', '/post/create', {
        'title': '[개발용] 사이드 프로젝트 팀원 모집', 'category': 'project', 'content': '개발용 시드 데이터입니다.',
        'projectType': 'online', 'region': '서울특별시', 'techStacks': ['Spring', 'React'],
        'startDate': start.isoformat(), 'endDate': (start + timedelta(days=40)).isoformat(),
        'positions': [{'role': 'backend', 'count': 2}],
    }, tokens['leader'])
    post_id = created if isinstance(created, int) else created['id']
    position_id = call('GET', f'/post/view/{post_id}', token=tokens['leader'])['positions'][0]['id']

    for username in ('alice', 'bob'):
        call('POST', '/post/apply', {'positionId': position_id}, tokens[username])
    applications = call('GET', f'/post/applyList/{post_id}', token=tokens['leader'])
    alice = next(a for a in applications if a['memberName'] == USERS['alice'])
    call('POST', '/post/accept', {'postPositionId': alice['applyId']}, tokens['leader'])

    print(f'글 {post_id}번을 만들고 alice를 승인해 팀을 만들었습니다. bob의 신청은 대기 중입니다.')
    print(f'계정: {", ".join(USERS)} / 비밀번호: {PASSWORD}')


if __name__ == '__main__':
    main()
