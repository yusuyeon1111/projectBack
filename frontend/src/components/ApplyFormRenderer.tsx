import { useState } from 'react';
import axios from 'axios';
import '../css/ApplyForm.css';
import {
  Box, Checkbox, Chip, FormControlLabel, FormGroup, MenuItem, Radio, RadioGroup, Stack, TextField, Typography,
} from '@mui/material';
import { APPLY_FILE_LIMIT, APPLY_TEXT_MAX, ApplyAnswerInput, ApplyQuestion } from '../types';
import { errorMessage } from '../auth';
import { FiUploadCloud } from 'react-icons/fi';

export type AnswerState = Record<string, ApplyAnswerInput>;

interface Props {
  questions: ApplyQuestion[];
  value: AnswerState;
  onChange: (value: AnswerState) => void;
  preview?: boolean; // 작성자 미리보기: 입력은 되지만 파일은 실제로 올리지 않는다
}

const empty: ApplyAnswerInput = { values: [], files: [] };

const formatSize = (bytes: number) =>
  bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)}MB` : `${Math.ceil(bytes / 1024)}KB`;

/**
 * 첨부 파일을 S3에 직접 올린다. 서버가 키와 presigned URL을 주고, 브라우저가 그 URL로 PUT한다.
 * axios 대신 fetch를 쓰는 이유: axios 전역 인터셉터가 Authorization 헤더를 붙이면
 * S3가 "인증 방식이 둘"이라며 거절한다.
 */
const uploadFile = async (file: File) => {
  const { data } = await axios.get<{ url: string; key: string }>(
    `${process.env.REACT_APP_API_URL}/api/post/apply-file/upload-url`, { params: { filename: file.name } });
  const res = await fetch(data.url, { method: 'PUT', body: file });
  if (!res.ok) throw new Error('upload failed');
  return { key: data.key, name: file.name, size: file.size };
};

/** 신청서 질문을 입력 폼으로 그린다. 신청 모달과 작성자 미리보기에서 같이 쓴다. */
function ApplyFormRenderer({ questions, value, onChange, preview }: Props) {
  const [uploading, setUploading] = useState<string | null>(null);
  const answerOf = (key: string) => value[key] ?? empty;
  const set = (key: string, patch: Partial<ApplyAnswerInput>) =>
    onChange({ ...value, [key]: { ...answerOf(key), ...patch } });

  const handleFiles = async (q: ApplyQuestion, list: FileList | null) => {
    const current = answerOf(q.key).files;
    const files = Array.from(list ?? []);
    if (current.length + files.length > APPLY_FILE_LIMIT.maxCount) {
      return alert(`파일은 ${APPLY_FILE_LIMIT.maxCount}개까지 올릴 수 있습니다.`);
    }
    for (const f of files) {
      const ext = f.name.split('.').pop()?.toLowerCase() ?? '';
      if (!APPLY_FILE_LIMIT.extensions.includes(ext)) return alert('pdf, jpg, png, zip 파일만 올릴 수 있습니다.');
      if (f.size > APPLY_FILE_LIMIT.maxSize) return alert(`'${f.name}' 파일이 10MB를 넘습니다.`);
    }
    if (preview) {
      return set(q.key, { files: [...current, ...files.map(f => ({ key: f.name, name: f.name, size: f.size }))] });
    }
    setUploading(q.key);
    try {
      const uploaded = [];
      for (const f of files) uploaded.push(await uploadFile(f));
      set(q.key, { files: [...current, ...uploaded] });
    } catch (error) {
      alert(errorMessage(error, '파일을 올리지 못했습니다. 잠시 후 다시 시도해주세요.'));
    } finally {
      setUploading(null);
    }
  };

  // 고른 선택지는 파란 테두리 칸으로 보인다(라디오·체크박스)
  const optionSx = (checked: boolean) => ({
    m: 0, mb: 1, px: 1.5, py: 0.25, width: '100%', boxSizing: 'border-box', borderRadius: '8px', border: '1px solid',
    '&:last-of-type': { mb: 0 },
    borderColor: checked ? '#007bff' : '#e5e7eb', bgcolor: checked ? '#f0f7ff' : '#fff',
    transition: 'all .15s', '&:hover': { borderColor: checked ? '#007bff' : '#9ec5fe' },
  });

  return (
    <Stack spacing={2}>
      {questions.map((q, i) => {
        const answer = answerOf(q.key);
        const single = answer.values[0] ?? '';
        const max = APPLY_TEXT_MAX[q.type];
        return (
          <Box key={q.key} className="apply-q">
            <Stack direction="row" spacing={1} alignItems="flex-start" mb={q.description ? 0.5 : 1.25}>
              <span className="apply-q-no">Q{i + 1}</span>
              <Typography fontWeight={700} sx={{ flex: 1, pt: '1px', wordBreak: 'break-word' }}>{q.label}</Typography>
              {q.type === 'CHECKBOX' && <span className="apply-q-tag multi">복수 선택</span>}
              {q.required && <span className="apply-q-tag required">필수</span>}
            </Stack>
            {q.description && (
              <Typography variant="body2" color="text.secondary" whiteSpace="pre-line" mb={1.25}>{q.description}</Typography>
            )}
            {(q.type === 'TEXT' || q.type === 'URL') && (
              <TextField size="small" fullWidth value={single}
                         placeholder={q.type === 'URL' ? 'https://' : '답변을 입력하세요'}
                         inputProps={{ maxLength: max }}
                         onChange={e => set(q.key, { values: [e.target.value] })} />
            )}
            {q.type === 'TEXTAREA' && (
              <TextField size="small" fullWidth multiline minRows={4} value={single} placeholder="답변을 입력하세요"
                         inputProps={{ maxLength: max }}
                         onChange={e => set(q.key, { values: [e.target.value] })} />
            )}
            {max && <p className="apply-q-count">{single.length} / {max}</p>}
            {q.type === 'SELECT' && (
              <TextField select size="small" fullWidth value={single}
                         SelectProps={{ displayEmpty: true }}
                         onChange={e => set(q.key, { values: e.target.value ? [e.target.value] : [] })}>
                <MenuItem value=""><span style={{ color: '#9ca3af' }}>선택하세요</span></MenuItem>
                {q.options.map(o => <MenuItem key={o} value={o}>{o}</MenuItem>)}
              </TextField>
            )}
            {q.type === 'RADIO' && (
              <RadioGroup value={single} onChange={e => set(q.key, { values: [e.target.value] })}>
                {q.options.map(o => (
                  <FormControlLabel key={o} value={o} control={<Radio size="small" />} label={o} sx={optionSx(single === o)} />
                ))}
              </RadioGroup>
            )}
            {q.type === 'CHECKBOX' && (
              <FormGroup>
                {q.options.map(o => (
                  <FormControlLabel key={o} label={o} sx={optionSx(answer.values.includes(o))} control={
                    <Checkbox size="small" checked={answer.values.includes(o)}
                              onChange={e => set(q.key, {
                                values: e.target.checked ? [...answer.values, o] : answer.values.filter(v => v !== o),
                              })} />
                  } />
                ))}
              </FormGroup>
            )}
            {q.type === 'FILE' && (
              <Box>
                {answer.files.length < APPLY_FILE_LIMIT.maxCount && (
                  <label className="apply-file-drop">
                    <FiUploadCloud className="apply-file-icon" />
                    <span className="apply-file-main">{uploading === q.key ? '올리는 중...' : '파일 선택'}</span>
                    <span className="apply-file-hint">pdf, jpg, png, zip · 파일당 10MB · 최대 {APPLY_FILE_LIMIT.maxCount}개</span>
                    <input type="file" hidden multiple disabled={uploading !== null}
                           accept={APPLY_FILE_LIMIT.extensions.map(e => `.${e}`).join(',')}
                           onChange={e => { handleFiles(q, e.target.files); e.target.value = ''; }} />
                  </label>
                )}
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap mt={1}>
                  {answer.files.map(f => (
                    <Chip key={f.key} size="small" variant="outlined" label={`${f.name}${f.size ? ` (${formatSize(f.size)})` : ''}`}
                          onDelete={() => set(q.key, { files: answer.files.filter(x => x.key !== f.key) })} />
                  ))}
                </Stack>
              </Box>
            )}
          </Box>
        );
      })}
    </Stack>
  );
}

/** 제출 전에 브라우저에서 필수 항목만 먼저 확인한다. 나머지 규칙은 서버가 검사해 메시지를 돌려준다. */
export const missingRequired = (questions: ApplyQuestion[], value: AnswerState): string | null => {
  const q = questions.find(q => {
    const a = value[q.key] ?? empty;
    return q.required && a.values.every(v => !v.trim()) && a.files.length === 0;
  });
  return q ? `'${q.label}'은(는) 필수 항목입니다.` : null;
};

export const toAnswerRequest = (questions: ApplyQuestion[], value: AnswerState) =>
  questions.map(q => ({
    questionKey: q.key,
    values: (value[q.key]?.values ?? []).filter(v => v.trim()),
    files: (value[q.key]?.files ?? []).map(f => ({ key: f.key, name: f.name })),
  }));

export default ApplyFormRenderer;
