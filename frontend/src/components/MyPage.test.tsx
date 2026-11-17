import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import axios from 'axios';
import MyPage from './MyPage';

jest.mock('axios', () => ({
  __esModule: true,
  default: { get: jest.fn(), post: jest.fn(), isAxiosError: jest.fn(), interceptors: { request: { use: jest.fn() }, response: { use: jest.fn() } } },
}));
const api = axios as jest.Mocked<typeof axios>;

beforeEach(() => {
  api.get.mockImplementation((url: string) => Promise.resolve({
    data: url.endsWith('/api/teams/my') ? []
      : { member: null, posts: [], applyPosts: [], acceptPosts: [], likePosts: [] },
  }) as any);
});

const renderAt = (path: string) => render(<MemoryRouter initialEntries={[path]}><MyPage /></MemoryRouter>);

test('팀 알림 링크(?tab=team)로 오면 내 팀 탭이 열린다', async () => {
  renderAt('/myPage?tab=team');
  expect(await screen.findByRole('tab', { name: '내 팀', selected: true })).toBeInTheDocument();
});

test('그냥 오면 작성글 탭이 열린다', async () => {
  renderAt('/myPage');
  expect(await screen.findByRole('tab', { name: '작성글', selected: true })).toBeInTheDocument();
});
