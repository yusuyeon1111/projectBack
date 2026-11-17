import { useState } from 'react';
import '../css/ApplyForm.css';
import { Box, Button, Checkbox, Chip, FormControlLabel, IconButton, MenuItem, Stack, TextField } from '@mui/material';
import { FiArrowDown, FiArrowUp, FiTrash2 } from 'react-icons/fi';
import { ApplyQuestion, ApplyQuestionType, applyQuestionTypeText, hasOptions } from '../types';
import ApplyFormRenderer, { AnswerState } from './ApplyFormRenderer';

interface Props {
  value: ApplyQuestion[];
  onChange: (questions: ApplyQuestion[]) => void;
}

const MAX_QUESTIONS = 20;

// 새 질문의 key는 브라우저가 만든다. 서버는 [A-Za-z0-9-]{1,36}만 받는다.
const newKey = () =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;

/** 글 작성자가 신청서 질문을 만드는 편집기. 순서는 위/아래 버튼으로 바꾼다. */
function ApplyFormBuilder({ value, onChange }: Props) {
  const [optionInputs, setOptionInputs] = useState<Record<string, string>>({});
  const [preview, setPreview] = useState(false);
  const [previewAnswers, setPreviewAnswers] = useState<AnswerState>({});

  const update = (index: number, patch: Partial<ApplyQuestion>) =>
    onChange(value.map((q, i) => (i === index ? { ...q, ...patch } : q)));

  const move = (index: number, delta: number) => {
    const next = [...value];
    [next[index], next[index + delta]] = [next[index + delta], next[index]];
    onChange(next);
  };

  const addOption = (index: number) => {
    const q = value[index];
    const option = (optionInputs[q.key] ?? '').trim();
    if (option && !q.options.includes(option)) {
      update(index, { options: [...q.options, option] });
    }
    setOptionInputs({ ...optionInputs, [q.key]: '' });
  };

  const addQuestion = () =>
    onChange([...value, { key: newKey(), type: 'TEXT', label: '', required: false, options: [] }]);

  return (
    <div className="apply-builder">
      {value.length === 0 && (
        <p className="sub">질문을 추가하지 않으면 신청 버튼을 누르는 즉시 신청됩니다.</p>
      )}
      {value.map((q, i) => (
        <Box key={q.key} className="apply-builder-item">
          <Stack direction="row" spacing={1} alignItems="center">
            <TextField
              select size="small" value={q.type} sx={{ minWidth: 130 }}
              onChange={e => {
                const type = e.target.value as ApplyQuestionType;
                update(i, { type, options: hasOptions(type) ? q.options : [] });
              }}
            >
              {Object.entries(applyQuestionTypeText).map(([type, text]) => (
                <MenuItem key={type} value={type}>{text}</MenuItem>
              ))}
            </TextField>
            <TextField
              size="small" fullWidth placeholder="질문 내용" value={q.label}
              inputProps={{ maxLength: 100 }}
              onChange={e => update(i, { label: e.target.value })}
            />
          </Stack>

          {hasOptions(q.type) && (
            <Box mt={1}>
              <Stack direction="row" spacing={1}>
                <TextField
                  size="small" fullWidth placeholder="선택지 입력 후 Enter"
                  value={optionInputs[q.key] ?? ''} inputProps={{ maxLength: 100 }}
                  onChange={e => setOptionInputs({ ...optionInputs, [q.key]: e.target.value })}
                  onKeyDown={e => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      addOption(i);
                    }
                  }}
                />
                <button type="button" id="graBtn" onClick={() => addOption(i)}>추가</button>
              </Stack>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap mt={1}>
                {q.options.map(option => (
                  <Chip key={option} label={option} size="small"
                        onDelete={() => update(i, { options: q.options.filter(o => o !== option) })} />
                ))}
              </Stack>
            </Box>
          )}

          <Stack direction="row" alignItems="center" justifyContent="space-between" mt={0.5}>
            <FormControlLabel
              control={<Checkbox size="small" checked={q.required} onChange={e => update(i, { required: e.target.checked })} />}
              label="필수"
            />
            <Stack direction="row">
              <IconButton size="small" aria-label="위로" disabled={i === 0} onClick={() => move(i, -1)}><FiArrowUp /></IconButton>
              <IconButton size="small" aria-label="아래로" disabled={i === value.length - 1} onClick={() => move(i, 1)}><FiArrowDown /></IconButton>
              <IconButton size="small" aria-label="질문 삭제" onClick={() => onChange(value.filter((_, j) => j !== i))}><FiTrash2 /></IconButton>
            </Stack>
          </Stack>
        </Box>
      ))}

      <Stack direction="row" spacing={1}>
        <Button variant="outlined" size="small" onClick={addQuestion} disabled={value.length >= MAX_QUESTIONS}>
          + 질문 추가
        </Button>
        {value.length > 0 && (
          <Button size="small" onClick={() => setPreview(!preview)}>
            {preview ? '미리보기 닫기' : '미리보기'}
          </Button>
        )}
      </Stack>

      {preview && value.length > 0 && (
        <Box className="apply-builder-preview">
          <ApplyFormRenderer questions={value} value={previewAnswers} onChange={setPreviewAnswers} preview />
        </Box>
      )}
    </div>
  );
}

/** 저장 전에 브라우저에서 먼저 막을 것. 서버도 같은 규칙으로 다시 검사한다. */
export const applyQuestionsError = (questions: ApplyQuestion[]): string | null => {
  for (const q of questions) {
    if (!q.label.trim()) return '신청서 질문 내용을 입력해주세요.';
    if (hasOptions(q.type) && q.options.length === 0) return `'${q.label}' 질문에 선택지를 하나 이상 추가해주세요.`;
  }
  return null;
};

export default ApplyFormBuilder;
