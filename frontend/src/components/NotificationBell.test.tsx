import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import axios from 'axios';
import Header from './Header';
import { NotificationItem } from '../types';

jest.mock('axios', () => ({
  __esModule: true,
  default: {
    get: jest.fn(), post: jest.fn(), patch: jest.fn(),
    interceptors: { request: { use: jest.fn() }, response: { use: jest.fn() } },
  },
}));
const api = axios as jest.Mocked<typeof axios>;

class FakeEventSource {
  static instances: FakeEventSource[] = [];
  listeners: Record<string, ((e: any) => void)[]> = {};
  closed = false;
  constructor(public url: string) { FakeEventSource.instances.push(this); }
  addEventListener(type: string, fn: (e: any) => void) { (this.listeners[type] ??= []).push(fn); }
  close() { this.closed = true; }
  emit(type: string, data?: string) { act(() => this.listeners[type]?.forEach(fn => fn({ data }))); }
}

const item = (id: number, over: Partial<NotificationItem> = {}): NotificationItem => ({
  id, type: 'APPLY', postId: 10, postTitle: `글${id}`, postCategory: 'project',
  role: 'backend', actor: '철수', read: false, createdAt: '2026-11-10T12:00:00', ...over,
});

let list: NotificationItem[];
let count: number;
let ticketNo: number;

beforeEach(() => {
  (global as any).EventSource = FakeEventSource;
  FakeEventSource.instances = [];
  localStorage.setItem('accessToken', 'token');
  list = [item(5), item(4, { type: 'ACCEPT', read: true, postCategory: 'study', postId: 20 })];
  count = 1;
  ticketNo = 0;
  api.get.mockImplementation((url: string) =>
    Promise.resolve({ data: url.endsWith('/unread-count') ? { count } : list }));
  api.post.mockImplementation(() => Promise.resolve({ data: { ticket: `t${++ticketNo}` } }));
  api.patch.mockResolvedValue({});
});

afterEach(() => {
  localStorage.clear();
  jest.useRealTimers();
});

const renderHeader = () => render(
  <MemoryRouter initialEntries={['/']}>
    <Routes>
      <Route path="/" element={<Header />} />
      <Route path="/post/:postId" element={<p>프로젝트 상세</p>} />
    </Routes>
  </MemoryRouter>
);

const connected = async (n: number) => {
  await waitFor(() => expect(FakeEventSource.instances).toHaveLength(n));
  return FakeEventSource.instances[n - 1];
};

test('로그아웃 상태면 종도 구독도 없다', async () => {
  localStorage.clear();
  renderHeader();
  await act(async () => {});
  expect(screen.queryByLabelText('알림')).not.toBeInTheDocument();
  expect(api.get).not.toHaveBeenCalled();
  expect(FakeEventSource.instances).toHaveLength(0);
});

test('안 읽은 개수 배지와 목록을 보여준다', async () => {
  renderHeader();
  expect(await screen.findByText('1')).toHaveClass('notification-badge');
  userEvent.click(screen.getByLabelText('알림'));
  expect(screen.getByText("'글5' BACKEND 포지션에 철수님이 신청했습니다.").closest('li')).toHaveClass('unread');
  expect(screen.getByText("'글4' BACKEND 포지션 신청이 승인되었습니다.").closest('li')).not.toHaveClass('unread');
});

test('알림을 누르면 읽음 처리하고 글로 이동한다', async () => {
  renderHeader();
  await screen.findByText('1');
  userEvent.click(screen.getByLabelText('알림'));
  userEvent.click(screen.getByText(/글5/));
  expect(await screen.findByText('프로젝트 상세')).toBeInTheDocument();
  expect(api.patch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/notifications\/5\/read$/));
});

test('모두 읽음', async () => {
  renderHeader();
  await screen.findByText('1');
  userEvent.click(screen.getByLabelText('알림'));
  userEvent.click(screen.getByText('모두 읽음'));
  await waitFor(() => expect(screen.queryByText('1')).not.toBeInTheDocument());
  expect(api.patch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/notifications\/read-all$/));
  expect(document.querySelector('li.unread')).toBeNull();
});

test('알림이 없으면 안내 문구', async () => {
  list = [];
  count = 0;
  renderHeader();
  await connected(1);
  userEvent.click(screen.getByLabelText('알림'));
  expect(screen.getByText('알림이 없습니다.')).toBeInTheDocument();
  expect(document.querySelector('.notification-badge')).toBeNull();
});

test('실시간 알림을 맨 앞에 추가하고 같은 id는 무시한다', async () => {
  renderHeader();
  const es = await connected(1);
  expect(es.url).toMatch(/\/api\/notifications\/subscribe\?ticket=t1&lastEventId=5$/);

  es.emit('notification', JSON.stringify(item(6)));
  es.emit('notification', JSON.stringify(item(6)));
  expect(screen.getByText('2')).toHaveClass('notification-badge');
  userEvent.click(screen.getByLabelText('알림'));
  expect(screen.getAllByRole('listitem').map(li => li.textContent)).toEqual([
    expect.stringContaining('글6'), expect.stringContaining('글5'), expect.stringContaining('글4'),
  ]);
});

test('끊기면 닫고 새 티켓으로 마지막 id부터 재연결, 대기는 두 배씩', async () => {
  jest.useFakeTimers();
  renderHeader();
  const first = await connected(1);
  first.emit('notification', JSON.stringify(item(6)));

  first.emit('error');
  expect(first.closed).toBe(true);
  act(() => { jest.advanceTimersByTime(999); });
  expect(FakeEventSource.instances).toHaveLength(1);
  act(() => { jest.advanceTimersByTime(1); });
  const second = await connected(2);
  expect(second.url).toMatch(/ticket=t2&lastEventId=6$/);

  second.emit('error');
  act(() => { jest.advanceTimersByTime(1999); });
  await act(async () => {});
  expect(FakeEventSource.instances).toHaveLength(2);
  act(() => { jest.advanceTimersByTime(1); });
  expect((await connected(3)).url).toMatch(/ticket=t3&lastEventId=6$/);
});

test('언마운트하면 연결을 닫는다', async () => {
  const { unmount } = renderHeader();
  const es = await connected(1);
  unmount();
  expect(es.closed).toBe(true);
});

test('재연결 대기 중 언마운트하면 다시 붙지 않는다', async () => {
  jest.useFakeTimers();
  const { unmount } = renderHeader();
  const es = await connected(1);
  es.emit('error');
  unmount();
  act(() => { jest.advanceTimersByTime(60000); });
  await act(async () => {});
  expect(FakeEventSource.instances).toHaveLength(1);
  expect(api.post).toHaveBeenCalledTimes(1);
});
