import { NotificationItem, notificationLink, notificationMessage } from './types';

const base: NotificationItem = {
  id: 1, type: 'APPLY', targetType: 'POST', targetId: 7, postId: 7, postTitle: '사이드 프로젝트', postCategory: 'project',
  role: 'backend', actor: '홍길동', read: false, createdAt: '2026-11-10T12:00:00',
};

test('알림 종류별 문구', () => {
  expect(notificationMessage(base)).toBe("'사이드 프로젝트' BACKEND 포지션에 홍길동님이 신청했습니다.");
  expect(notificationMessage({ ...base, type: 'ACCEPT', role: 'pm', actor: null }))
    .toBe("'사이드 프로젝트' 기획 포지션 신청이 승인되었습니다.");
  expect(notificationMessage({ ...base, type: 'REJECT', role: 'design', actor: null }))
    .toBe("'사이드 프로젝트' 디자인 포지션 신청이 거절되었습니다.");
  // 스터디는 포지션이 없으므로 글 단위로
  expect(notificationMessage({ ...base, postTitle: '자격증 스터디', postCategory: 'study', role: 'totalCount' }))
    .toBe("'자격증 스터디' 스터디에 홍길동님이 신청했습니다.");
  expect(notificationMessage({ ...base, type: 'ACCEPT', postTitle: '자격증 스터디', postCategory: 'study', role: 'totalCount', actor: null }))
    .toBe("'자격증 스터디' 스터디 신청이 승인되었습니다.");
});

test('알림 링크는 글 종류에 따라 상세 경로로', () => {
  expect(notificationLink(base)).toBe('/post/7');
  expect(notificationLink({ ...base, postCategory: 'study' })).toBe('/study/7');
});

test('팀 알림 문구와 링크', () => {
  const team: NotificationItem = { ...base, type: 'TEAM_STARTED', targetType: 'TEAM', targetId: 3, postTitle: '우리 팀', actor: null };
  expect(notificationMessage(team)).toBe("'우리 팀' 팀이 활동을 시작했습니다.");
  expect(notificationMessage({ ...team, type: 'TEAM_KICKED' })).toBe("'우리 팀' 팀에서 퇴출되었습니다.");
  expect(notificationMessage({ ...team, type: 'TEAM_LEFT', actor: '철수' })).toBe("'우리 팀' 팀에서 철수님이 탈퇴했습니다.");
  expect(notificationMessage({ ...team, type: 'TASK_ASSIGNED', actor: '철수' })).toBe("'우리 팀' 팀에서 철수님이 업무를 맡겼습니다.");
  expect(notificationMessage({ ...team, type: 'TASK_COMMENT', actor: '철수' })).toBe("'우리 팀' 팀 업무에 철수님이 댓글을 남겼습니다.");
  expect(notificationLink(team)).toBe('/myPage?tab=team');
});
