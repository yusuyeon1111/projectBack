import { useState } from 'react';
import axios from 'axios';
import { Button, Dialog, DialogActions, DialogContent, DialogTitle } from '@mui/material';
import { ApplyQuestion } from '../types';
import { errorMessage } from '../auth';
import ApplyFormRenderer, { AnswerState, missingRequired, toAnswerRequest } from './ApplyFormRenderer';

interface Props {
  open: boolean;
  positionId: number | null;
  questions: ApplyQuestion[];
  onClose: () => void;
  onApplied: (positionId: number) => void;
}

/** 신청서가 있는 글에서 신청 버튼을 누르면 뜨는 작성 창 */
function ApplyModal({ open, positionId, questions, onClose, onApplied }: Props) {
  const [answers, setAnswers] = useState<AnswerState>({});
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    const missing = missingRequired(questions, answers);
    if (missing) return alert(missing);
    if (positionId == null) return;
    setSubmitting(true);
    try {
      const response = await axios.post<string>(`${process.env.REACT_APP_API_URL}/api/post/apply`, {
        positionId,
        answers: toAnswerRequest(questions, answers),
      });
      alert(response.data);
      setAnswers({});
      onApplied(positionId);
    } catch (error) {
      alert(errorMessage(error, '신청 실패'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} fullWidth maxWidth="sm">
      <DialogTitle fontWeight={700}>신청서 작성</DialogTitle>
      <DialogContent dividers>
        <ApplyFormRenderer questions={questions} value={answers} onChange={setAnswers} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={submitting}>취소</Button>
        <Button variant="contained" onClick={submit} disabled={submitting}>
          {submitting ? '제출 중...' : '신청하기'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default ApplyModal;
