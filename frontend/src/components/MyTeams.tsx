import { useEffect, useState } from 'react';
import axios from 'axios';
import { Modal, Box, Typography, List, ListItem } from '@mui/material';
import { errorMessage } from '../auth';
import { TeamDetail, TeamSummary, roleTextMap, teamStatusText } from '../types';

const modalStyle = {
  position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)',
  width: 400, maxWidth: 'calc(100vw - 32px)', bgcolor: 'background.paper', borderRadius: '8px', boxShadow: 24, p: 4,
} as const;

// 마이페이지 "내 팀" 탭. 첫 승인 때 생긴 팀 목록과 팀원 보기
export default function MyTeams() {
  const [teams, setTeams] = useState<TeamSummary[] | null>(null);
  const [error, setError] = useState('');
  const [detail, setDetail] = useState<TeamDetail | null>(null);

  useEffect(() => {
    axios.get<TeamSummary[]>(`${process.env.REACT_APP_API_URL}/api/teams/my`)
      .then(res => setTeams(res.data))
      .catch(e => setError(errorMessage(e, '팀 목록을 불러오지 못했습니다.')));
  }, []);

  const openTeam = async (teamId: number) => {
    try {
      const res = await axios.get<TeamDetail>(`${process.env.REACT_APP_API_URL}/api/teams/${teamId}`);
      setDetail(res.data);
    } catch (e) {
      alert(errorMessage(e, '팀 정보를 불러오지 못했습니다.'));
    }
  };

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
                  </ListItem>
                ))}
              </List>
            </>
          )}
        </Box>
      </Modal>
    </>
  );
}
