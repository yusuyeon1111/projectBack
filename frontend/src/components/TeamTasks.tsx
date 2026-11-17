import { useEffect, useState } from 'react';
import axios from 'axios';
import { Box, Button, Stack, TextField, Typography } from '@mui/material';
import { errorMessage } from '../auth';
import { Task, TaskComment, TeamActivity, TeamDetail, activityText, taskStatusText } from '../types';

const statuses = Object.keys(taskStatusText) as Task['status'][];

// 팀 창 안의 업무 보드와 최근 활동. 완료·해산된 팀(open=false)은 읽기만 한다
export default function TeamTasks({ team, open }: { team: TeamDetail; open: boolean }) {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [activities, setActivities] = useState<TeamActivity[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [title, setTitle] = useState('');
  const [assignee, setAssignee] = useState('');
  const [editing, setEditing] = useState<{ taskId: number; title: string } | null>(null);
  const [commentsOf, setCommentsOf] = useState<number | null>(null);

  const api = (path: string) => `${process.env.REACT_APP_API_URL}/api/teams/${team.teamId}${path}`;

  const loadActivities = async (cursor?: number) => {
    const res = await axios.get<TeamActivity[]>(api(`/activities${cursor ? `?cursor=${cursor}` : ''}`));
    setActivities(prev => (cursor ? [...prev, ...res.data] : res.data));
    setHasMore(res.data.length === 30);
  };

  const load = async () => {
    const res = await axios.get<Task[]>(api('/tasks'));
    setTasks(res.data);
    await loadActivities();
  };

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

  const create = () => {
    if (!title.trim()) return;
    act(() => axios.post(api('/tasks'), { title, assigneeMemberId: assignee ? Number(assignee) : null }))
      .then(() => { setTitle(''); setAssignee(''); });
  };

  const assigneeSelect = (value: number | null, onChange: (v: string) => void, label = '담당자') => (
    <TextField select size="small" label={label} value={value ?? ''} disabled={!open}
      onChange={e => onChange(e.target.value)} SelectProps={{ native: true }} sx={{ minWidth: 100 }} InputLabelProps={{ shrink: true }}>
      <option value="">없음</option>
      {team.members.map(m => <option key={m.memberId} value={m.memberId}>{m.nickname ?? '(닉네임 없음)'}</option>)}
    </TextField>
  );

  return (
    <Box sx={{ mt: 2 }}>
      <Typography variant="subtitle1">업무</Typography>
      {open && (
        <Stack direction="row" spacing={1} sx={{ my: 1 }}>
          <TextField size="small" label="새 업무" value={title} onChange={e => setTitle(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter') create(); }} inputProps={{ maxLength: 100 }} sx={{ flex: 1 }} />
          {assigneeSelect(assignee ? Number(assignee) : null, setAssignee)}
          <Button variant="contained" size="small" onClick={create}>추가</Button>
        </Stack>
      )}
      {statuses.map(s => (
        <Box key={s} sx={{ mb: 1 }}>
          <Typography variant="body2" color="text.secondary">
            {taskStatusText[s]} {tasks.filter(t => t.status === s).length}
          </Typography>
          {tasks.filter(t => t.status === s).map(t => (
            <Box key={t.taskId}>
              <Stack direction="row" spacing={1} alignItems="center" sx={{ py: 0.5 }}>
                {editing?.taskId === t.taskId ? (
                  <>
                    <TextField size="small" value={editing.title} inputProps={{ maxLength: 100 }} sx={{ flex: 1 }}
                      onChange={e => setEditing({ taskId: t.taskId, title: e.target.value })} />
                    <Button size="small" onClick={() => update(t, { title: editing.title }).then(() => setEditing(null))}>저장</Button>
                    <Button size="small" onClick={() => setEditing(null)}>취소</Button>
                  </>
                ) : (
                  <>
                    <Typography sx={{ flex: 1 }}>{t.title}</Typography>
                    {assigneeSelect(t.assigneeMemberId, v => update(t, { assigneeMemberId: v ? Number(v) : null }))}
                    <TextField select size="small" label="상태" value={t.status} disabled={!open} SelectProps={{ native: true }}
                      onChange={e => act(() => axios.patch(api(`/tasks/${t.taskId}/status`), { from: t.status, to: e.target.value }))}>
                      {statuses.map(o => <option key={o} value={o}>{taskStatusText[o]}</option>)}
                    </TextField>
                    <Button size="small" onClick={() => setCommentsOf(commentsOf === t.taskId ? null : t.taskId)}>댓글</Button>
                    {open && <Button size="small" onClick={() => setEditing({ taskId: t.taskId, title: t.title })}>수정</Button>}
                    {open && t.deletable && (
                      <Button size="small" color="error" onClick={() => window.confirm(`"${t.title}" 업무를 지울까요?`)
                        && act(() => axios.delete(api(`/tasks/${t.taskId}`)))}>삭제</Button>
                    )}
                  </>
                )}
              </Stack>
              {commentsOf === t.taskId && (
                <TaskComments url={api(`/tasks/${t.taskId}/comments`)} open={open} onChange={() => loadActivities()} />
              )}
            </Box>
          ))}
        </Box>
      ))}

      <Typography variant="subtitle1" sx={{ mt: 2 }}>최근 활동</Typography>
      {activities.length === 0 && <Typography variant="body2" color="text.secondary">아직 활동이 없습니다.</Typography>}
      {activities.map(a => (
        <Typography key={a.id} variant="body2">
          {activityText(a)} <Typography component="span" variant="caption" color="text.secondary">{a.createdAt.slice(5, 16).replace('T', ' ')}</Typography>
        </Typography>
      ))}
      {hasMore && <Button size="small" onClick={() => loadActivities(activities[activities.length - 1].id)}>더 보기</Button>}
    </Box>
  );
}

// 업무 하나의 댓글. 쓰거나 지우면 활동도 다시 읽는다
function TaskComments({ url, open, onChange }: { url: string; open: boolean; onChange: () => void }) {
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
    onChange();
  };

  return (
    <Box sx={{ pl: 2, pb: 1, borderLeft: 2, borderColor: 'divider' }}>
      {comments.length === 0 && <Typography variant="body2" color="text.secondary">댓글이 없습니다.</Typography>}
      {comments.map(c => (
        <Stack key={c.commentId} direction="row" spacing={1} alignItems="center">
          <Typography variant="body2" sx={{ flex: 1, whiteSpace: 'pre-wrap' }}><b>{c.authorName}</b> {c.content}</Typography>
          {open && c.deletable && (
            <Button size="small" color="error" onClick={() => window.confirm('댓글을 지울까요?')
              && act(() => axios.delete(`${url}/${c.commentId}`))}>삭제</Button>
          )}
        </Stack>
      ))}
      {open && (
        <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
          <TextField size="small" label="댓글" value={content} onChange={e => setContent(e.target.value)}
            inputProps={{ maxLength: 2000 }} sx={{ flex: 1 }} />
          <Button size="small" variant="outlined" disabled={!content.trim()}
            onClick={() => act(() => axios.post(url, { content }))}>등록</Button>
        </Stack>
      )}
    </Box>
  );
}
