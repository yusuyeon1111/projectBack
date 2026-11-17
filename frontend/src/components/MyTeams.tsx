import { useEffect, useState } from 'react';
import axios from 'axios';
import { Modal, Box, Typography, List, ListItem, Button, Stack } from '@mui/material';
import { errorMessage } from '../auth';
import { TeamDetail, TeamSummary, roleTextMap, teamStatusText } from '../types';
import TeamTasks from './TeamTasks';

const modalStyle = {
  position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)',
  width: 640, maxWidth: 'calc(100vw - 32px)', maxHeight: 'calc(100vh - 32px)', overflowY: 'auto', bgcolor: 'background.paper', borderRadius: '8px', boxShadow: 24, p: 4,
} as const;

// 마이페이지 "내 팀" 탭. 첫 승인 때 생긴 팀 목록과 팀원 보기
export default function MyTeams() {
  const [teams, setTeams] = useState<TeamSummary[] | null>(null);
  const [error, setError] = useState('');
  const [detail, setDetail] = useState<TeamDetail | null>(null);

  const loadTeams = () =>
    axios.get<TeamSummary[]>(`${process.env.REACT_APP_API_URL}/api/teams/my`)
      .then(res => setTeams(res.data))
      .catch(e => setError(errorMessage(e, '팀 목록을 불러오지 못했습니다.')));

  useEffect(() => { loadTeams(); }, []);

  const openTeam = async (teamId: number) => {
    try {
      const res = await axios.get<TeamDetail>(`${process.env.REACT_APP_API_URL}/api/teams/${teamId}`);
      setDetail(res.data);
    } catch (e) {
      alert(errorMessage(e, '팀 정보를 불러오지 못했습니다.'));
    }
  };

  // 팀장 위임·탈퇴 뒤에는 내 역할이 바뀌므로 목록과 팀을 다시 읽는다. 탈퇴했으면 팀을 더 볼 수 없어 창을 닫는다
  const act = async (confirmText: string | null, request: () => Promise<unknown>, left = false) => {
    if (!detail || (confirmText && !window.confirm(confirmText))) return;
    try {
      await request();
      await loadTeams();
      if (left) setDetail(null);
      else await openTeam(detail.teamId);
    } catch (e) {
      alert(errorMessage(e, '처리하지 못했습니다.'));
    }
  };

  const api = (path: string) => `${process.env.REACT_APP_API_URL}/api/teams/${detail?.teamId}${path}`;
  const amLeader = teams?.find(t => t.teamId === detail?.teamId)?.leader ?? false;
  const open = detail?.status === 'FORMING' || detail?.status === 'ACTIVE';

  if (error) return <div><p>{error}</p></div>;
  if (!teams) return <div><p>불러오는 중...</p></div>;
  if (teams.length === 0) return <div><p>참여 중인 팀이 없습니다. 내 글의 신청을 승인하거나 신청이 승인되면 팀이 생깁니다.</p></div>;

  return (
    <>
      {teams.map(team => (
        <div key={team.teamId} className='postItem'>
          {team.leader ? <span id='projectlabel'>팀장</span> : <span id='studylabel'>팀원</span>}
          <span className='myTitle' onClick={() => openTeam(team.teamId)}>{team.name}</span>
          <span className='status' data-type={team.status === 'FORMING' || team.status === 'ACTIVE' ? 'active' : 'end'}>
            {teamStatusText[team.status] ?? team.status}
          </span>
        </div>
      ))}

      <Modal open={detail !== null} onClose={() => setDetail(null)}>
        <Box sx={modalStyle}>
          {detail && (
            <>
              <Typography variant="h6">{detail.name}</Typography>
              <Typography variant="body2" color="text.secondary">
                {teamStatusText[detail.status] ?? detail.status} · 팀원 {detail.members.length}명
              </Typography>
              <List>
                {detail.members.map(m => (
                  <ListItem key={m.memberId} disableGutters>
                    {m.nickname ?? '(닉네임 없음)'}
                    {m.leader ? ' · 팀장' : m.positionRole ? ` · ${roleTextMap[m.positionRole] ?? m.positionRole}` : ''}
                    {amLeader && open && !m.leader && (
                      <Stack direction="row" spacing={0.5} sx={{ ml: 'auto' }}>
                        <Button size="small" onClick={() => act(`${m.nickname}님에게 팀장을 넘길까요?`,
                          () => axios.put(api('/leader'), { memberId: m.memberId }))}>팀장 위임</Button>
                        <Button size="small" color="error" onClick={() => act(`${m.nickname}님을 퇴출할까요? 퇴출된 사람은 이 글에 다시 신청할 수 없습니다.`,
                          () => axios.delete(api(`/members/${m.memberId}`)))}>퇴출</Button>
                      </Stack>
                    )}
                  </ListItem>
                ))}
              </List>
              {open && (
                <Stack direction="row" spacing={1} justifyContent="flex-end">
                  {amLeader && detail.status === 'FORMING' && (
                    <Button variant="contained" size="small" onClick={() => act(null, () => axios.post(api('/start')))}>팀 시작</Button>
                  )}
                  {amLeader && detail.status === 'ACTIVE' && (
                    <Button variant="contained" size="small" onClick={() => act('팀을 완료할까요? 완료하면 더 바꿀 수 없습니다.', () => axios.post(api('/complete')))}>팀 완료</Button>
                  )}
                  {amLeader && (
                    <Button color="error" size="small" onClick={() => act('팀을 해산할까요? 되돌릴 수 없습니다.', () => axios.post(api('/disband')))}>해산</Button>
                  )}
                  {!amLeader && (
                    <Button color="error" size="small" onClick={() => act('팀에서 탈퇴할까요?', () => axios.delete(api('/members/me')), true)}>탈퇴</Button>
                  )}
                </Stack>
              )}
              <TeamTasks team={detail} open={open} />
            </>
          )}
        </Box>
      </Modal>
    </>
  );
}
