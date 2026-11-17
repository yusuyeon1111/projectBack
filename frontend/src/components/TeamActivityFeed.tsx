import { useEffect, useState } from 'react';
import axios from 'axios';
import { Box, Button, Stack, Typography } from '@mui/material';
import HistoryIcon from '@mui/icons-material/History';
import { errorMessage } from '../auth';
import { TeamActivity, activityText } from '../types';
import TeamAvatar from './TeamAvatar';

const PAGE = 30;

// 팀 활동 타임라인(최신순). 업무 탭에서 바뀐 내용이 보이도록 탭을 열 때마다 다시 읽는다.
// 탭을 연 채로 팀 시작·위임 등을 하면 부모가 팀을 다시 읽어 reloadKey가 바뀌므로 그때도 다시 읽는다
export default function TeamActivityFeed({ teamId, visible, reloadKey }: { teamId: number; visible: boolean; reloadKey?: unknown }) {
  const [activities, setActivities] = useState<TeamActivity[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const url = `${process.env.REACT_APP_API_URL}/api/teams/${teamId}/activities`;

  const load = async (cursor?: number) => {
    const res = await axios.get<TeamActivity[]>(cursor ? `${url}?cursor=${cursor}` : url);
    setActivities(prev => (cursor ? [...prev, ...res.data] : res.data));
    setHasMore(res.data.length === PAGE);
  };

  useEffect(() => {
    if (visible) load().catch(e => alert(errorMessage(e, '활동을 불러오지 못했습니다.')));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [teamId, visible, reloadKey]);

  if (activities.length === 0) {
    return (
      <Stack alignItems="center" spacing={1} sx={{ py: 8, color: 'text.secondary' }}>
        <HistoryIcon sx={{ fontSize: 40, opacity: 0.4 }} />
        <Typography variant="body2">아직 활동이 없습니다.</Typography>
      </Stack>
    );
  }

  return (
    <Box sx={{ px: 3, py: 2 }}>
      {activities.map((a, i) => (
        <Stack key={a.id} direction="row" spacing={1.5}>
          {/* 얼굴 아래로 이어지는 세로선이 타임라인이 된다 */}
          <Stack alignItems="center">
            <TeamAvatar memberId={a.actorMemberId} name={a.actorName} size={28} />
            {i < activities.length - 1 && <Box sx={{ flex: 1, width: 2, bgcolor: 'divider', my: 0.5 }} />}
          </Stack>
          <Box sx={{ pb: 2, pt: 0.25 }}>
            <Typography variant="body2">{activityText(a)}</Typography>
            <Typography variant="caption" color="text.secondary">{a.createdAt.slice(5, 16).replace('T', ' ').replace('-', '/')}</Typography>
          </Box>
        </Stack>
      ))}
      {hasMore && <Button size="small" onClick={() => load(activities[activities.length - 1].id)}>더 보기</Button>}
    </Box>
  );
}
