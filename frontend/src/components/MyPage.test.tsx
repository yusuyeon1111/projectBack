import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import userEvent from '@testing-library/user-event';
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

test('그냥 와도 내 팀 탭이 먼저 열리고, 탭은 용도별 세 개다', async () => {
  renderAt('/myPage');
  expect(await screen.findByRole('tab', { name: '내 팀', selected: true })).toBeInTheDocument();
  expect(screen.getAllByRole('tab').map(t => t.textContent)).toEqual(['내 팀', '모집 · 지원', '찜한 글']);
});

test('모집 · 지원 탭에서 내가 모집한 글과 지원한 글을 오간다', async () => {
  api.get.mockImplementation((url: string) => Promise.resolve({
    data: url.endsWith('/api/teams/my') ? []
      : { member: null, posts: [], acceptPosts: [], likePosts: [], applyPosts: [
          { postId: 1, applyId: 3, postTitle: '지원한 글', category: 'study', applyStatus: 'APPLY', appliedAt: '2026-11-17', positionName: 'backend' },
        ] },
  }) as any);
  renderAt('/myPage');
  userEvent.click(await screen.findByRole('tab', { name: '모집 · 지원' }));
  expect(await screen.findByText('작성한 모집글이 없습니다.')).toBeInTheDocument();

  userEvent.click(screen.getByRole('button', { name: '내가 지원한 글 1' }));
  expect(screen.getByText('지원한 글')).toBeInTheDocument();
  expect(screen.getByText('대기')).toBeInTheDocument();
});
