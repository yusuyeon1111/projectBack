import { useEffect, useState } from 'react';
import axios from 'axios';
import { Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Stack, Typography } from '@mui/material';
import { ApplyAnswer, ApplyFile } from '../types';
import { errorMessage } from '../auth';

interface Props {
  applicationId: number | null; // 신청 id(post_position_member.id). null이면 닫힘
  title?: string;
  onClose: () => void;
}

/** 제출된 신청서를 읽기 전용으로 보여준다. 글 작성자와 신청자 본인만 열 수 있다(서버가 확인). */
function ApplicationAnswersDialog({ applicationId, title, onClose }: Props) {
  const [answers, setAnswers] = useState<ApplyAnswer[] | null>(null);

  useEffect(() => {
    if (applicationId == null) return;
    setAnswers(null);
    axios.get<ApplyAnswer[]>(`${process.env.REACT_APP_API_URL}/api/post/application/${applicationId}`)
      .then(res => setAnswers(res.data))
      .catch(error => {
        alert(errorMessage(error, '신청서를 불러오지 못했습니다.'));
        onClose();
      });
  }, [applicationId, onClose]);

  // 받을 때마다 서버에서 몇 분짜리 URL을 새로 받는다
  const download = async (file: ApplyFile) => {
    try {
      const { data } = await axios.get<{ url: string }>(
        `${process.env.REACT_APP_API_URL}/api/post/application/${applicationId}/file`, { params: { key: file.key } });
      window.location.href = data.url;
    } catch (error) {
      alert(errorMessage(error, '파일을 받지 못했습니다.'));
    }
  };

  return (
    <Dialog open={applicationId != null} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle fontWeight={700}>{title ?? '신청서'}</DialogTitle>
      <DialogContent dividers>
        {answers === null && <Typography color="text.secondary">불러오는 중...</Typography>}
        {answers?.length === 0 && <Typography color="text.secondary">신청서 없이 신청했습니다.</Typography>}
        <Stack spacing={2.5}>
          {answers?.map(a => (
            <Box key={a.questionKey}>
              <Typography fontWeight={600} mb={0.5}>{a.label}</Typography>
              {a.type === 'FILE' ? (
                a.files.length > 0 ? (
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    {a.files.map(f => (
                      <Chip key={f.key} label={f.name} onClick={() => download(f)} clickable variant="outlined" />
                    ))}
                  </Stack>
                ) : <Typography color="text.secondary">첨부 없음</Typography>
              ) : a.values.length > 0 ? (
                a.type === 'URL'
                  ? <a href={a.values[0]} target="_blank" rel="noopener noreferrer">{a.values[0]}</a>
                  : <Typography whiteSpace="pre-wrap">{a.values.join(', ')}</Typography>
              ) : <Typography color="text.secondary">응답 없음</Typography>}
            </Box>
          ))}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>닫기</Button>
      </DialogActions>
    </Dialog>
  );
}

export default ApplicationAnswersDialog;
