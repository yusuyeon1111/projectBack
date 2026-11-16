import { NotificationItem, notificationLink, notificationMessage } from './types';

const base: NotificationItem = {
  id: 1, type: 'APPLY', postId: 7, postTitle: '사이드 프로젝트', postCategory: 'project',
  role: 'backend', actor: '홍길동', read: false, createdAt: '2026-11-10T12:00:00',
};

test('알림 종류별 문구', () => {
  expect(notificationMessage(base)).toBe("'사이드 프로젝트' BACKEND 포지션에 홍길동님이 신청했습니다.");
  expect(notificationMessage({ ...base, type: 'ACCEPT', role: 'pm', actor: null }))
    .toBe("'사이드 프로젝트' 기획 포지션 신청이 승인되었습니다.");
  expect(notificationMessage({ ...base, type: 'REJECT', role: 'design', actor: null }))
    .toBe("'사이드 프로젝트' 디자인 포지션 신청이 거절되었습니다.");
});

test('알림 링크는 글 종류에 따라 상세 경로로', () => {
  expect(notificationLink(base)).toBe('/post/7');
  expect(notificationLink({ ...base, postCategory: 'study' })).toBe('/study/7');
});
