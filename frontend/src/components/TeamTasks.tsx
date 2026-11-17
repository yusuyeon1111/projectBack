import { useEffect, useState } from 'react';
import axios from 'axios';
import { Box, Button, IconButton, Paper, Stack, TextField, Tooltip, Typography } from '@mui/material';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutline';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import AddIcon from '@mui/icons-material/Add';
import { errorMessage } from '../auth';
import { Task, TaskComment, TeamDetail, taskStatusText } from '../types';
import TeamAvatar from './TeamAvatar';

const statuses = Object.keys(taskStatusText) as Task['status'][];
const statusColor: Record<Task['status'], string> = { TODO: '#9ca3af', IN_PROGRESS: '#007bff', DONE: '#16a34a' };

// 팀 창의 업무 보드(할 일 · 진행 중 · 완료). 완료·해산된 팀(open=false)은 읽기만 한다
export default function TeamTasks({ team, open }: { team: TeamDetail; open: boolean }) {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [title, setTitle] = useState('');
  const [assignee, setAssignee] = useState('');
  const [editing, setEditing] = useState<{ taskId: number; title: string; assignee: string } | null>(null);
  const [commentsOf, setCommentsOf] = useState<number | null>(null);

  const api = (path: string) => `${process.env.REACT_APP_API_URL}/api/teams/${team.teamId}${path}`;
  const member = (id: number | null) => team.members.find(m => m.memberId === id);

  const load = () => axios.get<Task[]>(api('/tasks')).then(res => setTasks(res.data));

  useEffect(() => {
    load().catch(e => alert(errorMessage(e, '업무를 불러오지 못했습니다.')));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [team.teamId]);

  // 충돌(409)이면 다른 팀원이 먼저 바꾼 것이라 새로 읽어 최신 상태를 보여 준다
  const act = async (request: () => Promise<unknown>) => {
    try {
      await request();
    } catch (e) {
      alert(errorMessage(e, '처리하지 못했습니다.'));
    }
    await load().catch(() => {});
  };

  // 수정은 칸 전체를 바꾸므로 고치지 않는 값도 그대로 보낸다
  const update = (t: Task, change: Partial<Pick<Task, 'title' | 'assigneeMemberId'>>) =>
    act(() => axios.patch(api(`/tasks/${t.taskId}`), {
      title: t.title, description: t.description, assigneeMemberId: t.assigneeMemberId, dueDate: t.dueDate,
      ...change, version: t.version,
    }));

  const move = (t: Task, to: Task['status']) => act(() => axios.patch(api(`/tasks/${t.taskId}/status`), { from: t.status, to }));

  const create = () => {
    if (!title.trim()) return;
    act(() => axios.post(api('/tasks'), { title, assigneeMemberId: assignee ? Number(assignee) : null }))
      .then(() => { setTitle(''); setAssignee(''); });
  };

  const assigneeSelect = (value: string, onChange: (v: string) => void) => (
    <TextField select size="small" label="담당자" value={value} onChange={e => onChange(e.target.value)}
      SelectProps={{ native: true }} sx={{ minWidth: 110 }} InputLabelProps={{ shrink: true }}>
      <option value="">없음</option>
      {team.members.map(m => <option key={m.memberId} value={m.memberId}>{m.nickname ?? '(닉네임 없음)'}</option>)}
    </TextField>
  );

  const card = (t: Task) => {
    const i = statuses.indexOf(t.status);
    const who = member(t.assigneeMemberId);
    if (editing?.taskId === t.taskId) {
      return (
        <Paper key={t.taskId} variant="outlined" sx={{ p: 1.5, borderRadius: 2 }}>
          <Stack spacing={1}>
            <TextField size="small" value={editing.title} inputProps={{ maxLength: 100 }} autoFocus
              onChange={e => setEditing({ ...editing, title: e.target.value })} />
            {assigneeSelect(editing.assignee, v => setEditing({ ...editing, assignee: v }))}
            <Stack direction="row" spacing={0.5} justifyContent="flex-end">
              <Button size="small" onClick={() => setEditing(null)}>취소</Button>
              <Button size="small" variant="contained" disableElevation disabled={!editing.title.trim()}
                onClick={() => update(t, { title: editing.title, assigneeMemberId: editing.assignee ? Number(editing.assignee) : null })
                  .then(() => setEditing(null))}>저장</Button>
            </Stack>
          </Stack>
        </Paper>
      );
    }
    return (
      <Paper key={t.taskId} variant="outlined" sx={{ p: 1.5, borderRadius: 2, borderLeft: 3, borderLeftColor: statusColor[t.status],
        transition: 'box-shadow .15s', '&:hover': { boxShadow: '0 2px 8px rgba(0,0,0,0.08)' } }}>
        <Typography variant="body2" fontWeight={600} sx={{ wordBreak: 'break-word',
          ...(t.status === 'DONE' && { color: 'text.secondary', textDecoration: 'line-through' }) }}>{t.title}</Typography>
        <Stack direction="row" alignItems="center" spacing={0.75} sx={{ mt: 1 }}>
          {who
            ? <><TeamAvatar memberId={who.memberId} name={who.nickname} size={20} /><Typography variant="caption">{who.nickname}</Typography></>
            : <Typography variant="caption" color="text.disabled">담당 없음</Typography>}
          {t.dueDate && <Typography variant="caption" color="text.secondary">· ~{t.dueDate.slice(5).replace('-', '/')}</Typography>}
        </Stack>
        <Stack direction="row" alignItems="center" sx={{ mt: 0.5, mx: -0.75, mb: -0.75 }}>
          {open && i > 0 && (
            <Tooltip title={`${taskStatusText[statuses[i - 1]]}(으)로`}>
              <IconButton size="small" aria-label={`${taskStatusText[statuses[i - 1]]}(으)로`} onClick={() => move(t, statuses[i - 1])}>
                <ChevronLeftIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
          {open && i < statuses.length - 1 && (
            <Tooltip title={`${taskStatusText[statuses[i + 1]]}(으)로`}>
              <IconButton size="small" aria-label={`${taskStatusText[statuses[i + 1]]}(으)로`} onClick={() => move(t, statuses[i + 1])}>
                <ChevronRightIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
          <Box sx={{ flex: 1 }} />
          <IconButton size="small" aria-label="댓글" color={commentsOf === t.taskId ? 'primary' : 'default'}
            onClick={() => setCommentsOf(commentsOf === t.taskId ? null : t.taskId)}><ChatBubbleOutlineIcon sx={{ fontSize: 16 }} /></IconButton>
          {open && (
            <IconButton size="small" aria-label="수정"
              onClick={() => setEditing({ taskId: t.taskId, title: t.title, assignee: t.assigneeMemberId ? String(t.assigneeMemberId) : '' })}>
              <EditOutlinedIcon sx={{ fontSize: 16 }} />
            </IconButton>
          )}
          {open && t.deletable && (
            <IconButton size="small" aria-label="업무 삭제" onClick={() => window.confirm(`"${t.title}" 업무를 지울까요?`)
              && act(() => axios.delete(api(`/tasks/${t.taskId}`)))}><DeleteOutlineIcon sx={{ fontSize: 16 }} /></IconButton>
          )}
        </Stack>
        {commentsOf === t.taskId && <TaskComments url={api(`/tasks/${t.taskId}/comments`)} open={open} />}
      </Paper>
    );
  };

  return (
    <Box sx={{ p: 2 }}>
      {open && (
        <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
          <TextField size="small" label="새 업무" placeholder="할 일을 적고 Enter" value={title} onChange={e => setTitle(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter' && !e.nativeEvent.isComposing) create(); }} inputProps={{ maxLength: 100 }} sx={{ flex: 1 }} />
          {assigneeSelect(assignee, setAssignee)}
          <Button variant="contained" disableElevation startIcon={<AddIcon />} onClick={create} disabled={!title.trim()}>추가</Button>
        </Stack>
      )}
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' }, gap: 1.5, alignItems: 'start' }}>
        {statuses.map(s => {
          const list = tasks.filter(t => t.status === s);
          return (
            <Box key={s} sx={{ bgcolor: '#F3F4F6', borderRadius: 2, p: 1.25, minHeight: 120 }}>
              <Stack direction="row" alignItems="center" spacing={1} sx={{ px: 0.5, mb: 1 }}>
                <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: statusColor[s] }} />
                <Typography variant="body2" fontWeight={700}>{taskStatusText[s]}</Typography>
                <Typography variant="caption" sx={{ bgcolor: '#e5e7eb', borderRadius: 5, px: 0.9, fontWeight: 600 }}>{list.length}</Typography>
              </Stack>
              <Stack spacing={1}>
                {list.map(card)}
                {list.length === 0 && <Typography variant="caption" color="text.disabled" sx={{ textAlign: 'center', py: 2 }}>비어 있음</Typography>}
              </Stack>
            </Box>
          );
        })}
      </Box>
    </Box>
  );
}

// 업무 하나의 댓글
function TaskComments({ url, open }: { url: string; open: boolean }) {
  const [comments, setComments] = useState<TaskComment[]>([]);
  const [content, setContent] = useState('');

  const load = () => axios.get<TaskComment[]>(url).then(res => setComments(res.data));
  useEffect(() => {
    load().catch(e => alert(errorMessage(e, '댓글을 불러오지 못했습니다.')));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [url]);

  const act = async (request: () => Promise<unknown>) => {
    try {
      await request();
      setContent('');
    } catch (e) {
      alert(errorMessage(e, '처리하지 못했습니다.'));
    }
    await load().catch(() => {});
  };

  return (
    <Box sx={{ mt: 1.25, pt: 1.25, borderTop: 1, borderColor: 'divider' }}>
      {comments.length === 0 && <Typography variant="caption" color="text.secondary">댓글이 없습니다.</Typography>}
      <Stack spacing={1}>
        {comments.map(c => (
          <Stack key={c.commentId} direction="row" spacing={1} alignItems="flex-start">
            <TeamAvatar memberId={c.authorMemberId} name={c.authorName} size={20} />
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <Typography variant="caption" fontWeight={600}>{c.authorName}</Typography>
              <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', fontSize: 13 }}>{c.content}</Typography>
            </Box>
            {open && c.deletable && (
              <Button size="small" color="error" sx={{ minWidth: 0, fontSize: 11 }} onClick={() => window.confirm('댓글을 지울까요?')
                && act(() => axios.delete(`${url}/${c.commentId}`))}>삭제</Button>
            )}
          </Stack>
        ))}
      </Stack>
      {open && (
        <Stack direction="row" spacing={0.5} sx={{ mt: 1 }}>
          <TextField size="small" label="댓글" value={content} onChange={e => setContent(e.target.value)}
            inputProps={{ maxLength: 2000 }} sx={{ flex: 1, '& input': { fontSize: 13 } }} />
          <Button size="small" variant="outlined" disabled={!content.trim()}
            onClick={() => act(() => axios.post(url, { content }))}>등록</Button>
        </Stack>
      )}
    </Box>
  );
}
