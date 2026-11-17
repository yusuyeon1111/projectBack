import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axios from 'axios';
import MyTeams from './MyTeams';

jest.mock('axios', () => ({
  __esModule: true,
  default: { get: jest.fn(), interceptors: { request: { use: jest.fn() }, response: { use: jest.fn() } } },
}));
const api = axios as jest.Mocked<typeof axios>;

beforeEach(() => api.get.mockReset());

test('내 팀 목록을 보여 주고 누르면 팀원을 연다', async () => {
  api.get.mockImplementation((url: string) => Promise.resolve({
    data: url.endsWith('/api/teams/my')
      ? [{ teamId: 7, postId: 1, name: '사이드 프로젝트', status: 'FORMING', leader: true }]
      : { teamId: 7, postId: 1, name: '사이드 프로젝트', status: 'FORMING', createdAt: '2026-11-17T10:00:00', members: [
          { memberId: 1, nickname: '나', positionRole: null, leader: true, joinedAt: '2026-11-17T10:00:00' },
          { memberId: 2, nickname: '철수', positionRole: 'backend', leader: false, joinedAt: '2026-11-17T10:00:00' },
        ] },
  }) as any);

  render(<MyTeams />);
  expect(await screen.findByText('팀장')).toBeInTheDocument();
  expect(screen.getByText('팀 구성 중')).toBeInTheDocument();

  userEvent.click(screen.getByText('사이드 프로젝트'));
  expect(await screen.findByText(/팀원 2명/)).toBeInTheDocument();
  expect(screen.getByText(/철수/)).toBeInTheDocument();
  expect(api.get).toHaveBeenLastCalledWith(expect.stringContaining('/api/teams/7'));
});

test('팀이 없으면 안내 문구를 보여 준다', async () => {
  api.get.mockResolvedValue({ data: [] });
  render(<MyTeams />);
  expect(await screen.findByText(/참여 중인 팀이 없습니다/)).toBeInTheDocument();
});
