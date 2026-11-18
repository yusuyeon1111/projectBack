import { test, expect } from '@playwright/test';
import { createUser, PASSWORD } from './helpers';

test('아이디로 로그인하면 메인으로 가고 메뉴가 로그인 상태로 바뀐다', async ({ page, request }) => {
  const user = await createUser(request, 'login');

  await page.goto('/signin');
  await page.locator('#username').fill(user.username);
  await page.locator('#password').fill(PASSWORD);
  await page.getByRole('button', { name: '로그인', exact: true }).click();

  await expect(page.getByText('로그인이 완료되었습니다!')).toBeVisible();
  await expect(page).toHaveURL(/\/$/);
  await page.locator('#userIcon').click();
  await expect(page.getByText('마이페이지')).toBeVisible();
  await expect(page.getByText('로그아웃')).toBeVisible();
});
