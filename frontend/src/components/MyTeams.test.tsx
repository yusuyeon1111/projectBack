import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axios from 'axios';
import MyTeams from './MyTeams';

jest.mock('axios', () => ({
  __esModule: true,
  default: { get: jest.fn(), post: jest.fn(), put: jest.fn(), patch: jest.fn(), delete: jest.fn(), interceptors: { request: { use: jest.fn() }, response: { use: jest.fn() } } },
}));
const api = axios as jest.Mocked<typeof axios>;

beforeEach(() => {
  api.get.mockReset();
  window.confirm = jest.fn(() => true);
});

const team = (leader: boolean) => (url: string) => Promise.resolve({
  data: url.includes('/tasks') || url.includes('/activities') || url.includes('/messages') ? []
  : url.endsWith('/api/teams/my')
    ? [{ teamId: 7, postId: 1, name: '사이드 프로젝트', status: 'FORMING', leader }]
    : { teamId: 7, postId: 1, name: '사이드 프로젝트', status: 'FORMING', createdAt: '2026-11-17T10:00:00', members: [
        { memberId: 1, nickname: '나', positionRole: null, leader: true, joinedAt: '2026-11-17T10:00:00' },
        { memberId: 2, nickname: '철수', positionRole: 'backend', leader: false, joinedAt: '2026-11-17T10:00:00' },
      ] },
}) as any;

test('내 팀 목록을 보여 주고 누르면 팀원을 연다', async () => {
  api.get.mockImplementation(team(true));

  render(<MyTeams />);
  expect(await screen.findByText('팀장')).toBeInTheDocument();
  expect(screen.getByText('팀 구성 중')).toBeInTheDocument();

  userEvent.click(screen.getByText('사이드 프로젝트'));
  expect(await screen.findByText(/팀원 2명/)).toBeInTheDocument();
  expect(screen.getByText(/철수 ·/)).toBeInTheDocument(); // 담당자 선택지에도 이름이 있어 팀원 줄로 찾는다
  expect(api.get).toHaveBeenLastCalledWith(expect.stringContaining('/api/teams/7'));
});

test('팀이 없으면 안내 문구를 보여 준다', async () => {
  api.get.mockResolvedValue({ data: [] });
  render(<MyTeams />);
  expect(await screen.findByText(/참여 중인 팀이 없습니다/)).toBeInTheDocument();
});

test('팀장은 팀원을 퇴출하고 팀을 시작할 수 있고 탈퇴 버튼은 없다', async () => {
  api.get.mockImplementation(team(true));
  api.delete.mockResolvedValue({});
  api.post.mockResolvedValue({});
  render(<MyTeams />);
  userEvent.click(await screen.findByText('사이드 프로젝트'));

  userEvent.click(await screen.findByText('퇴출'));
  expect(await screen.findByText('팀 시작')).toBeInTheDocument();
  expect(api.delete).toHaveBeenCalledWith(expect.stringContaining('/api/teams/7/members/2'));
  expect(screen.queryByText('탈퇴')).not.toBeInTheDocument();

  userEvent.click(screen.getByText('팀 시작'));
  expect(api.post).toHaveBeenCalledWith(expect.stringContaining('/api/teams/7/start'));
});

test('팀원은 탈퇴만 할 수 있고 탈퇴하면 창이 닫힌다', async () => {
  api.get.mockImplementation(team(false));
  api.delete.mockResolvedValue({});
  render(<MyTeams />);
  userEvent.click(await screen.findByText('사이드 프로젝트'));

  expect(await screen.findByText('탈퇴')).toBeInTheDocument();
  expect(screen.queryByText('퇴출')).not.toBeInTheDocument();
  userEvent.click(screen.getByText('탈퇴'));
  expect(api.delete).toHaveBeenCalledWith(expect.stringContaining('/api/teams/7/members/me'));
  await screen.findByText('사이드 프로젝트');
  await waitFor(() => expect(screen.queryByText('탈퇴')).not.toBeInTheDocument());
});
