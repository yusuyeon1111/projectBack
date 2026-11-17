import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axios from 'axios';
import TeamTasks from './TeamTasks';
import { TeamDetail } from '../types';

jest.mock('axios', () => ({
  __esModule: true,
  default: { get: jest.fn(), post: jest.fn(), patch: jest.fn(), delete: jest.fn(), interceptors: { request: { use: jest.fn() }, response: { use: jest.fn() } } },
}));
const api = axios as jest.Mocked<typeof axios>;

const team: TeamDetail = {
  teamId: 7, postId: 1, name: '팀', status: 'ACTIVE', createdAt: '2026-11-17T10:00:00', members: [
    { memberId: 1, nickname: '나', positionRole: null, leader: true, joinedAt: '2026-11-17T10:00:00' },
    { memberId: 2, nickname: '철수', positionRole: 'backend', leader: false, joinedAt: '2026-11-17T10:00:00' },
  ], myMemberId: 1,
};
const task = {
  taskId: 3, title: 'API 명세', description: '설명', status: 'TODO', assigneeMemberId: 2, creatorMemberId: 1,
  dueDate: null, version: 4, createdAt: '2026-11-17T10:00:00', updatedAt: '2026-11-17T10:00:00', deletable: false,
};
const comment = { commentId: 5, authorMemberId: 2, authorName: '철수', content: '확인했어요', createdAt: '2026-11-17T11:00:00', deletable: true };

beforeEach(() => {
  jest.resetAllMocks();
  api.get.mockImplementation(((url: string) => Promise.resolve({
    data: url.includes('/comments') ? [comment] : [task],
  })) as any);
  window.alert = jest.fn();
});

test('업무를 상태 칸에 담당자와 함께 보여 주고, 지울 권한이 없으면 삭제 버튼이 없다', async () => {
  render(<TeamTasks team={team} open />);
  expect(await screen.findByText('API 명세')).toBeInTheDocument();
  expect(screen.getByText('철수', { selector: 'span' })).toBeInTheDocument(); // 담당자 선택지(option)가 아닌 카드의 이름
  expect(screen.queryByLabelText('업무 삭제')).not.toBeInTheDocument();
});

test('상태를 바꾸면 지금 상태를 from으로 보내고, 수정은 version과 나머지 칸을 함께 보낸다', async () => {
  api.patch.mockResolvedValue({});
  render(<TeamTasks team={team} open />);
  await screen.findByText('API 명세');

  userEvent.click(screen.getByLabelText('진행 중(으)로'));
  await waitFor(() => expect(api.patch).toHaveBeenCalledWith(expect.stringContaining('/api/teams/7/tasks/3/status'), { from: 'TODO', to: 'IN_PROGRESS' }));

  userEvent.click(screen.getByLabelText('수정'));
  const input = screen.getByDisplayValue('API 명세');
  userEvent.clear(input);
  userEvent.type(input, '새 제목');
  userEvent.click(screen.getByText('저장'));
  await waitFor(() => expect(api.patch).toHaveBeenLastCalledWith(expect.stringContaining('/api/teams/7/tasks/3'), {
    title: '새 제목', description: '설명', assigneeMemberId: 2, dueDate: null, version: 4,
  }));
});

test('충돌(409)이면 알리고 다시 읽는다', async () => {
  api.patch.mockRejectedValue({ response: { status: 409, data: { error: '다른 팀원이 먼저 업무를 바꿨습니다.' } } });
  render(<TeamTasks team={team} open />);
  await screen.findByText('API 명세');
  const loads = api.get.mock.calls.length;

  userEvent.click(screen.getByLabelText('진행 중(으)로'));
  await waitFor(() => expect(window.alert).toHaveBeenCalledWith(expect.stringContaining('먼저 업무를 바꿨습니다')));
  await waitFor(() => expect(api.get.mock.calls.length).toBeGreaterThan(loads));
});

test('끝난 팀에서는 업무를 추가하거나 고칠 수 없다', async () => {
  render(<TeamTasks team={team} open={false} />);
  await screen.findByText('API 명세');
  expect(screen.queryByText('추가')).not.toBeInTheDocument();
  expect(screen.queryByLabelText('수정')).not.toBeInTheDocument();
  expect(screen.queryByLabelText('진행 중(으)로')).not.toBeInTheDocument();
});

test('댓글을 열어 읽고 쓰며, 지울 수 있는 댓글에만 삭제 버튼이 있다', async () => {
  api.post.mockResolvedValue({});
  render(<TeamTasks team={team} open />);
  await screen.findByText('API 명세');

  userEvent.click(screen.getByLabelText('댓글'));
  expect(await screen.findByText('확인했어요')).toBeInTheDocument();
  expect(screen.getByText('삭제')).toBeInTheDocument(); // 업무는 못 지우고 댓글은 지울 수 있다

  userEvent.type(screen.getByRole('textbox', { name: '댓글' }), '좋아요');
  userEvent.click(screen.getByText('등록'));
  await waitFor(() => expect(api.post).toHaveBeenCalledWith(expect.stringContaining('/api/teams/7/tasks/3/comments'), { content: '좋아요' }));
});
