import { test, expect } from '@playwright/test';
import { createPost, createUser, loginAs, uniq } from './helpers';

// 핵심 흐름: 신청 → 작성자 승인 → 팀이 자동으로 생기고 양쪽 "내 팀"에 보인다
test('신청을 승인하면 팀이 생겨 작성자와 신청자의 내 팀에 보인다', async ({ browser, request }) => {
  const leader = await createUser(request, 'lead');
  const member = await createUser(request, 'memb');
  const title = `E2E 팀 ${uniq('')}`;
  const postId = await createPost(request, leader, title);

  // 신청자: 글에서 신청 (성공 안내는 alert)
  const memberCtx = await browser.newContext();
  await loginAs(memberCtx, member);
  const memberPage = await memberCtx.newPage();
  memberPage.on('dialog', d => d.accept());
  await memberPage.goto(`/post/${postId}`);
  await memberPage.getByRole('button', { name: '포지션 신청하기' }).click();
  await expect(memberPage.getByRole('button', { name: '신청 완료' })).toBeDisabled();

  // 작성자: 마이페이지 → 모집 · 지원 → 신청자관리 → 승인
  const leaderCtx = await browser.newContext();
  await loginAs(leaderCtx, leader);
  const leaderPage = await leaderCtx.newPage();
  leaderPage.on('dialog', d => d.accept());
  await leaderPage.goto('/myPage');
  await leaderPage.getByRole('tab', { name: '모집 · 지원' }).click();
  await leaderPage.locator('#applyMngBtn').click();
  await expect(leaderPage.getByText(member.nickname)).toBeVisible();
  await leaderPage.getByRole('button', { name: '승인' }).click();

  // 양쪽 내 팀 탭(마이페이지 기본 탭)에 팀이 보인다
  await leaderPage.goto('/myPage?tab=team');
  await expect(leaderPage.getByText(title)).toBeVisible();
  await memberPage.goto('/myPage?tab=team');
  await expect(memberPage.getByText(title)).toBeVisible();
});
