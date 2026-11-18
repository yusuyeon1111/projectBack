import { test, expect } from '@playwright/test';
import { createPost, createUser, uniq } from './helpers';

test('비로그인으로 목록에서 글을 열 수 있고, 신청하면 로그인 안내가 뜬다', async ({ page, request }) => {
  const author = await createUser(request, 'anon');
  const title = `E2E 비로그인 ${uniq('')}`;
  await createPost(request, author, title);

  await page.goto('/');
  await page.getByText(title).click();   // 최신순 첫 페이지에 방금 만든 글이 있다

  await expect(page).toHaveURL(/\/post\/\d+$/);
  await page.getByRole('button', { name: '포지션 신청하기' }).click();
  await expect(page.getByText('로그인이 필요한 기능입니다')).toBeVisible();
});
