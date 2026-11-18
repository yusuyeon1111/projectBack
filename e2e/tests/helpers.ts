import { APIRequestContext, BrowserContext, expect } from '@playwright/test';

export const PASSWORD = 'e2e-pass-1234';

/** 실행마다 겹치지 않는 아이디. 같은 DB에서 여러 번 돌려도 된다 */
export const uniq = (prefix: string) => `${prefix}${Date.now().toString(36)}${Math.floor(Math.random() * 1e4)}`;

async function call<T>(api: APIRequestContext, method: 'GET' | 'POST', path: string, data?: unknown, token?: string): Promise<T> {
  const res = await api.fetch(`/api${path}`, {
    method, data, headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  expect(res.ok(), `${method} ${path} → ${res.status()} ${await res.text()}`).toBeTruthy();
  const text = await res.text();
  try { return JSON.parse(text) as T; } catch { return text as unknown as T; }
}

export type User = { username: string; nickname: string; token: string; refreshToken: string };

/** API로 가입·로그인한다. 화면 로그인은 login.spec.ts에서 따로 검증한다 */
export async function createUser(api: APIRequestContext, prefix: string): Promise<User> {
  const username = uniq(prefix);
  await call(api, 'POST', '/member/signup', {
    username, password: PASSWORD, nickname: username, name: username, email: `${username}@e2e.test`,
  });
  const res = await call<{ accessToken: string; refreshToken: string }>(api, 'POST', '/member/signin', { username, password: PASSWORD });
  return { username, nickname: username, token: res.accessToken, refreshToken: res.refreshToken };
}

/** 신청서 없는 프로젝트 모집글(백엔드 1명)을 만들고 글 id를 돌려준다 */
export async function createPost(api: APIRequestContext, author: User, title: string): Promise<number> {
  const start = new Date(Date.now() + 86_400_000).toISOString().slice(0, 10);
  const end = new Date(Date.now() + 40 * 86_400_000).toISOString().slice(0, 10);
  const created = await call<number | { id: number }>(api, 'POST', '/post/create', {
    title, category: 'project', content: 'E2E 테스트 글', projectType: 'online', region: '서울특별시',
    techStacks: ['Spring'], startDate: start, endDate: end, positions: [{ role: 'backend', count: 1 }],
  }, author.token);
  return typeof created === 'number' ? created : created.id;
}

/** 화면을 열기 전에 로그인 상태(localStorage)를 넣는다. SignIn.tsx가 저장하는 값과 같다 */
export async function loginAs(context: BrowserContext, user: User) {
  await context.addInitScript(u => {
    localStorage.setItem('accessToken', u.token);
    localStorage.setItem('refreshToken', u.refreshToken);
    localStorage.setItem('username', u.username);
    localStorage.setItem('role', 'ROLE_USER');
  }, user);
}
