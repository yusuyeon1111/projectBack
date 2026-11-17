import { useEffect, useState } from 'react';
import axios from 'axios';
import { Modal, Box, Typography, Button, Stack, Chip, IconButton, Menu, MenuItem, Tabs, Tab, AvatarGroup } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import StarIcon from '@mui/icons-material/Star';
import { errorMessage } from '../auth';
import { TeamDetail, TeamMember, TeamSummary, roleTextMap, teamStatusColor, teamStatusText } from '../types';
import TeamTasks from './TeamTasks';
import TeamChat from './TeamChat';
import TeamActivityFeed from './TeamActivityFeed';
import TeamAvatar from './TeamAvatar';

const modalStyle = {
  position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)',
  width: 960, maxWidth: 'calc(100vw - 32px)', height: 720, maxHeight: 'calc(100vh - 32px)',
  bgcolor: 'background.paper', borderRadius: '12px', boxShadow: 24, overflow: 'hidden', outline: 'none',
  display: 'flex', flexDirection: 'column',
} as const;

const tabs = ['채팅', '업무', '활동'] as const;

// 마이페이지 "내 팀" 탭. 첫 승인 때 생긴 팀 목록과 팀 창(팀원·채팅·업무·활동)
export default function MyTeams() {
  const [teams, setTeams] = useState<TeamSummary[] | null>(null);
  const [error, setError] = useState('');
  const [detail, setDetail] = useState<TeamDetail | null>(null);
  const [tab, setTab] = useState(0);
  const [menu, setMenu] = useState<{ anchor: HTMLElement; member: TeamMember } | null>(null);

  const loadTeams = () =>
    axios.get<TeamSummary[]>(`${process.env.REACT_APP_API_URL}/api/teams/my`)
      .then(res => setTeams(res.data))
      .catch(e => setError(errorMessage(e, '팀 목록을 불러오지 못했습니다.')));

  useEffect(() => { loadTeams(); }, []);

  const openTeam = async (teamId: number) => {
    try {
      const res = await axios.get<TeamDetail>(`${process.env.REACT_APP_API_URL}/api/teams/${teamId}`);
      if (res.data.teamId !== detail?.teamId) setTab(0);
      setDetail(res.data);
    } catch (e) {
      alert(errorMessage(e, '팀 정보를 불러오지 못했습니다.'));
    }
  };

  // 팀장 위임·탈퇴 뒤에는 내 역할이 바뀌므로 목록과 팀을 다시 읽는다. 탈퇴했으면 팀을 더 볼 수 없어 창을 닫는다
  const act = async (confirmText: string | null, request: () => Promise<unknown>, left = false) => {
    setMenu(null);
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
              {/* 머리: 팀 이름, 상태, 팀원 얼굴 */}
              <Box sx={{ px: 3, py: 2.5, color: '#fff', background: 'linear-gradient(135deg, #007bff 0%, #4f46e5 100%)' }}>
                <Stack direction="row" alignItems="flex-start" spacing={2}>
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Typography variant="h6" fontWeight={700} noWrap>{detail.name}</Typography>
                    <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 1 }}>
                      <Chip size="small" label={teamStatusText[detail.status] ?? detail.status}
                        sx={{ bgcolor: 'rgba(255,255,255,0.22)', color: '#fff', fontWeight: 600 }} />
                      <Typography variant="body2" sx={{ opacity: 0.9 }}>팀원 {detail.members.length}명</Typography>
                    </Stack>
                  </Box>
                  <AvatarGroup max={5} sx={{ '& .MuiAvatar-root': { borderColor: '#fff' }, display: { xs: 'none', sm: 'flex' } }}>
                    {detail.members.map(m => <TeamAvatar key={m.memberId} memberId={m.memberId} name={m.nickname} size={34} />)}
                  </AvatarGroup>
                  <IconButton aria-label="닫기" onClick={() => setDetail(null)} sx={{ color: '#fff', mt: -0.5 }}><CloseIcon /></IconButton>
                </Stack>
              </Box>

              <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: { xs: 'column', md: 'row' } }}>
                {/* 왼쪽: 팀원과 팀 관리 */}
                <Box sx={{ width: { md: 240 }, flexShrink: 0, borderRight: { md: 1 }, borderBottom: { xs: 1, md: 0 }, borderColor: 'divider',
                  bgcolor: '#F9FAFB', p: 2, display: 'flex', flexDirection: 'column', overflowY: 'auto', maxHeight: { xs: 220, md: 'none' } }}>
                  <Typography variant="overline" color="text.secondary">팀원</Typography>
                  <Stack spacing={1} sx={{ mt: 0.5 }}>
                    {detail.members.map(m => (
                      <Stack key={m.memberId} direction="row" spacing={1.25} alignItems="center">
                        <TeamAvatar memberId={m.memberId} name={m.nickname} size={32} />
                        <Box sx={{ flex: 1, minWidth: 0 }}>
                          <Typography variant="body2" fontWeight={600} noWrap>
                            {m.nickname ?? '(닉네임 없음)'}{m.memberId === detail.myMemberId && <Typography component="span" variant="caption" color="text.secondary"> (나)</Typography>}
                          </Typography>
                          {m.leader
                            ? <Chip size="small" icon={<StarIcon />} label="팀장" color="warning" variant="outlined" sx={{ height: 20, fontSize: 11 }} />
                            : <Typography variant="caption" color="text.secondary">{roleTextMap[m.positionRole ?? ''] ?? m.positionRole ?? '팀원'}</Typography>}
                        </Box>
                        {amLeader && open && !m.leader && (
                          <IconButton size="small" aria-label={`${m.nickname} 관리`} onClick={e => setMenu({ anchor: e.currentTarget, member: m })}>
                            <MoreVertIcon fontSize="small" />
                          </IconButton>
                        )}
                      </Stack>
                    ))}
                  </Stack>
                  <Menu anchorEl={menu?.anchor} open={menu !== null} onClose={() => setMenu(null)}>
                    <MenuItem onClick={() => menu && act(`${menu.member.nickname}님에게 팀장을 넘길까요?`,
                      () => axios.put(api('/leader'), { memberId: menu.member.memberId }))}>팀장 위임</MenuItem>
                    <MenuItem sx={{ color: 'error.main' }} onClick={() => menu && act(`${menu.member.nickname}님을 퇴출할까요? 퇴출된 사람은 이 글에 다시 신청할 수 없습니다.`,
                      () => axios.delete(api(`/members/${menu.member.memberId}`)))}>퇴출</MenuItem>
                  </Menu>

                  {open && (
                    <Stack spacing={1} sx={{ mt: 'auto', pt: 2 }}>
                      {amLeader && detail.status === 'FORMING' && (
                        <Button variant="contained" disableElevation onClick={() => act(null, () => axios.post(api('/start')))}>팀 시작</Button>
                      )}
                      {amLeader && detail.status === 'ACTIVE' && (
                        <Button variant="contained" color="success" disableElevation
                          onClick={() => act('팀을 완료할까요? 완료하면 더 바꿀 수 없습니다.', () => axios.post(api('/complete')))}>팀 완료</Button>
                      )}
                      {amLeader && (
                        <Button color="error" size="small" onClick={() => act('팀을 해산할까요? 되돌릴 수 없습니다.', () => axios.post(api('/disband')))}>해산</Button>
                      )}
                      {!amLeader && (
                        <Button color="error" size="small" onClick={() => act('팀에서 탈퇴할까요?', () => axios.delete(api('/members/me')), true)}>탈퇴</Button>
                      )}
                    </Stack>
                  )}
                  {!open && (
                    <Chip sx={{ mt: 'auto' }} color={teamStatusColor[detail.status] ?? 'default'} variant="outlined"
                      label="끝난 팀이라 읽기만 할 수 있습니다" />
                  )}
                </Box>

                {/* 오른쪽: 채팅·업무·활동. 탭을 바꿔도 채팅이 끊기지 않게 모두 띄워 두고 가린다 */}
                <Box sx={{ flex: 1, minWidth: 0, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
                  <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ px: 2, borderBottom: 1, borderColor: 'divider', minHeight: 44,
                    '& .MuiTab-root': { minHeight: 44, fontWeight: 600 } }}>
                    {tabs.map(t => <Tab key={t} label={t} />)}
                  </Tabs>
                  <Box sx={{ flex: 1, minHeight: 0, display: tab === 0 ? 'flex' : 'none', flexDirection: 'column' }}>
                    <TeamChat team={detail} open={open} />
                  </Box>
                  <Box sx={{ flex: 1, minHeight: 0, overflowY: 'auto', display: tab === 1 ? 'block' : 'none' }}>
                    <TeamTasks team={detail} open={open} />
                  </Box>
                  <Box sx={{ flex: 1, minHeight: 0, overflowY: 'auto', display: tab === 2 ? 'block' : 'none' }}>
                    <TeamActivityFeed teamId={detail.teamId} visible={tab === 2} reloadKey={detail} />
                  </Box>
                </Box>
              </Box>
            </>
          )}
        </Box>
      </Modal>
    </>
  );
}
