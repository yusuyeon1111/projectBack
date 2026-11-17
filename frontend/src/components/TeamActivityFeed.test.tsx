import { render, screen } from '@testing-library/react';
import axios from 'axios';
import TeamActivityFeed from './TeamActivityFeed';

jest.mock('axios', () => ({
  __esModule: true,
  default: { get: jest.fn(), interceptors: { request: { use: jest.fn() }, response: { use: jest.fn() } } },
}));
const api = axios as jest.Mocked<typeof axios>;

const activity = {
  id: 9, actorMemberId: 2, actorName: '철수', type: 'TASK_STATUS', targetType: 'TASK', targetId: 3,
  detail: { title: 'API 명세', from: 'TODO', to: 'IN_PROGRESS' }, createdAt: '2026-11-17T10:30:00',
};

test('탭이 보일 때 활동을 읽어 문장으로 보여 준다', async () => {
  api.get.mockResolvedValue({ data: [activity] });
  const { rerender } = render(<TeamActivityFeed teamId={7} visible={false} />);
  expect(api.get).not.toHaveBeenCalled();

  rerender(<TeamActivityFeed teamId={7} visible />);
  expect(await screen.findByText(/철수님이 "API 명세"을\(를\) 할 일 → 진행 중/)).toBeInTheDocument();
  expect(api.get).toHaveBeenCalledWith(expect.stringContaining('/api/teams/7/activities'));
});

test('탭을 연 채로 팀 상태가 바뀌면(reloadKey) 다시 읽는다', async () => {
  api.get.mockReset();
  api.get.mockResolvedValue({ data: [activity] });
  const { rerender } = render(<TeamActivityFeed teamId={7} visible reloadKey={{ status: 'FORMING' }} />);
  await screen.findByText(/철수님이/);
  expect(api.get).toHaveBeenCalledTimes(1);

  api.get.mockResolvedValue({ data: [{ ...activity, id: 10, type: 'TEAM_STARTED', targetType: 'TEAM', detail: null }, activity] });
  rerender(<TeamActivityFeed teamId={7} visible reloadKey={{ status: 'ACTIVE' }} />);
  await screen.findAllByText(/철수님이/);
  expect(api.get).toHaveBeenCalledTimes(2);
});
