import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axios from 'axios';
import TeamChat from './TeamChat';
import { ChatMessage, TeamDetail } from '../types';

jest.mock('axios', () => ({
  __esModule: true,
  default: { get: jest.fn(), post: jest.fn(), interceptors: { request: { use: jest.fn() }, response: { use: jest.fn() } } },
}));
const api = axios as jest.Mocked<typeof axios>;

const team: TeamDetail = { teamId: 7, postId: 1, name: '팀', status: 'ACTIVE', createdAt: '', members: [] };
const msg = (id: number, over: Partial<ChatMessage> = {}): ChatMessage => ({
  id, teamId: 7, senderMemberId: 1, senderName: '철수', content: `메시지${id}`, createdAt: '2026-11-17T10:00:00', ...over,
});
const chat = (m: ChatMessage) => act(() => { window.dispatchEvent(new CustomEvent('team-chat', { detail: m })); });

beforeEach(() => {
  jest.resetAllMocks();
  window.alert = jest.fn();
});

test('최신 대화를 오름차순으로 보이고, 실시간 메시지는 이 팀 것만 겹치지 않게 붙인다', async () => {
  api.get.mockResolvedValue({ data: [msg(2), msg(1)] }); // 서버는 최신순으로 준다
  render(<TeamChat team={team} open />);
  await screen.findByText('메시지2');
  expect(screen.getByText('메시지1').compareDocumentPosition(screen.getByText('메시지2')))
    .toBe(Node.DOCUMENT_POSITION_FOLLOWING);

  chat(msg(3));
  chat(msg(3)); // 같은 메시지가 SSE와 조회로 두 번 와도 하나
  chat(msg(4, { teamId: 8, content: '다른 팀' }));
  expect(screen.getAllByText('메시지3')).toHaveLength(1);
  expect(screen.queryByText('다른 팀')).not.toBeInTheDocument();
});

test('SSE가 다시 연결되면 마지막 id 이후를 받고, 꽉 차면 이어서 받는다', async () => {
  api.get.mockResolvedValueOnce({ data: [msg(5)] })
    .mockResolvedValueOnce({ data: Array.from({ length: 100 }, (_, i) => msg(6 + i)) })
    .mockResolvedValueOnce({ data: [msg(106)] });
  render(<TeamChat team={team} open />);
  await screen.findByText('메시지5');

  act(() => { window.dispatchEvent(new Event('sse-connected')); });
  await screen.findByText('메시지106');
  expect(api.get).toHaveBeenNthCalledWith(2, expect.stringContaining('/api/teams/7/messages?after=5&limit=100'));
  expect(api.get).toHaveBeenNthCalledWith(3, expect.stringContaining('?after=105&limit=100'));
});

test('보낸 메시지를 바로 보이고, 끝난 팀은 입력창이 없다', async () => {
  api.get.mockResolvedValue({ data: [] });
  api.post.mockResolvedValue({ data: msg(1, { content: '안녕하세요' }) });
  const { unmount } = render(<TeamChat team={team} open />);
  await screen.findByText('아직 대화가 없습니다.');

  userEvent.type(screen.getByLabelText('메시지'), '안녕하세요');
  userEvent.click(screen.getByText('보내기'));
  expect(await screen.findByText('안녕하세요')).toBeInTheDocument();
  expect(api.post).toHaveBeenCalledWith(expect.stringContaining('/api/teams/7/messages'), { content: '안녕하세요' });
  unmount();

  render(<TeamChat team={{ ...team, status: 'COMPLETED' }} open={false} />);
  await waitFor(() => expect(screen.queryByLabelText('메시지')).not.toBeInTheDocument());
});
