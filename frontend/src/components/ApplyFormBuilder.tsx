import { useState } from 'react';
import '../css/ApplyForm.css';
import { Dialog, FormControlLabel, Switch } from '@mui/material';
import { IconType } from 'react-icons';
import {
  FiAlignLeft, FiArrowDown, FiArrowUp, FiCheckSquare, FiChevronDown, FiCircle, FiCopy, FiDisc, FiEye, FiLayout,
  FiLink, FiLogOut, FiPaperclip, FiPlusCircle, FiSave, FiSettings, FiSquare, FiTrash2, FiType, FiX,
} from 'react-icons/fi';
import { APPLY_FILE_LIMIT, APPLY_TEXT_MAX, ApplyQuestion, ApplyQuestionType, applyQuestionTypeText, hasOptions } from '../types';
import ApplyFormRenderer, { AnswerState, missingRequired } from './ApplyFormRenderer';
import { toast } from './Toast';

interface Props {
  value: ApplyQuestion[];
  onChange: (questions: ApplyQuestion[]) => void;
}

const MAX_QUESTIONS = 20;

const typeIcon: Record<ApplyQuestionType, IconType> = {
  TEXT: FiType, TEXTAREA: FiAlignLeft, URL: FiLink,
  SELECT: FiChevronDown, RADIO: FiDisc, CHECKBOX: FiCheckSquare, FILE: FiPaperclip,
};

const palette: { title: string; types: ApplyQuestionType[] }[] = [
  { title: '기본 항목', types: ['TEXT', 'TEXTAREA', 'URL'] },
  { title: '선택 항목', types: ['SELECT', 'RADIO', 'CHECKBOX'] },
  { title: '특수 항목', types: ['FILE'] },
];

// 새 질문의 key는 브라우저가 만든다. 서버는 [A-Za-z0-9-]{1,36}만 받는다.
const newKey = () =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;

/**
 * 글 폼에는 신청서 요약만 보이고, 편집은 전체 화면 빌더에서 한다.
 * 빌더는 사본(draft)을 고치고 "저장하기"를 눌러야 글 폼에 반영된다.
 */
function ApplyFormBuilder({ value, onChange }: Props) {
  const [draft, setDraft] = useState<ApplyQuestion[] | null>(null); // null이면 빌더 닫힘

  const open = () => setDraft(value.map(q => ({ ...q, options: [...q.options] })));

  const save = () => {
    const error = applyQuestionsError(draft!);
    if (error) {
      alert(error);
      return;
    }
    onChange(draft!);
    setDraft(null);
  };

  const exit = () => {
    if (JSON.stringify(draft) !== JSON.stringify(value) && !window.confirm('저장하지 않은 변경 내용이 사라집니다. 나갈까요?')) return;
    setDraft(null);
  };

  return (
    <>
      <div className="apply-summary">
        {value.length === 0 ? (
          <p className="apply-summary-empty">질문이 없습니다. 지원자는 신청 버튼을 누르면 바로 신청됩니다.</p>
        ) : (
          <ol className="apply-summary-list">
            {value.map(q => {
              const Icon = typeIcon[q.type];
              return (
                <li key={q.key}>
                  <Icon className="apply-summary-icon" />
                  <span>{q.label}</span>
                  {q.required && <span className="apply-required">*</span>}
                </li>
              );
            })}
          </ol>
        )}
        <button type="button" className="apply-summary-btn" onClick={open}>
          <FiLayout /> {value.length === 0 ? '신청서 만들기' : '신청서 편집'}
        </button>
      </div>

      <Dialog fullScreen open={draft !== null} onClose={exit}>
        {draft && (
          <BuilderScreen draft={draft} setDraft={setDraft} onSave={save} onExit={exit} />
        )}
      </Dialog>
    </>
  );
}

interface ScreenProps {
  draft: ApplyQuestion[];
  setDraft: (q: ApplyQuestion[]) => void;
  onSave: () => void;
  onExit: () => void;
}

// 빌더가 닫히면 언마운트되므로 아래 상태는 열 때마다 새로 시작한다
function BuilderScreen({ draft, setDraft, onSave, onExit }: ScreenProps) {
  const [mode, setMode] = useState<'build' | 'preview'>('build');
  const [optionInputs, setOptionInputs] = useState<Record<string, string>>({});
  const [previewAnswers, setPreviewAnswers] = useState<AnswerState>({});
  const [focusKey, setFocusKey] = useState<string | null>(null);

  const update = (index: number, patch: Partial<ApplyQuestion>) =>
    setDraft(draft.map((q, i) => (i === index ? { ...q, ...patch } : q)));

  const move = (index: number, delta: number) => {
    const next = [...draft];
    [next[index], next[index + delta]] = [next[index + delta], next[index]];
    setDraft(next);
  };

  const addOption = (index: number) => {
    const q = draft[index];
    const option = (optionInputs[q.key] ?? '').trim();
    if (option && !q.options.includes(option)) update(index, { options: [...q.options, option] });
    setOptionInputs({ ...optionInputs, [q.key]: '' });
  };

  const addQuestion = (type: ApplyQuestionType) => {
    if (draft.length >= MAX_QUESTIONS) {
      alert(`질문은 ${MAX_QUESTIONS}개까지 만들 수 있습니다.`);
      return;
    }
    const key = newKey();
    setDraft([...draft, { key, type, label: '', description: '', required: false, options: [] }]);
    setFocusKey(key);
    setMode('build');
  };

  // 바로 아래에 같은 내용의 질문을 하나 더 만든다(key만 새로)
  const copy = (index: number) => {
    if (draft.length >= MAX_QUESTIONS) {
      alert(`질문은 ${MAX_QUESTIONS}개까지 만들 수 있습니다.`);
      return;
    }
    const key = newKey();
    const next = [...draft];
    next.splice(index + 1, 0, { ...draft[index], key, options: [...draft[index].options] });
    setDraft(next);
    setFocusKey(key);
  };

  const labeled = draft.filter(q => q.label.trim());

  return (
    <div className="fb">
      <header className="fb-top">
        <h2 className="fb-title"><FiLayout /> 신청서 빌더</h2>
        <div className="fb-mode">
          <button type="button" className={mode === 'build' ? 'on' : ''} onClick={() => setMode('build')}>
            <FiSettings /> 빌더 모드
          </button>
          <button type="button" className={mode === 'preview' ? 'on' : ''} onClick={() => setMode('preview')}>
            <FiEye /> 미리보기
          </button>
        </div>
        <div className="fb-actions">
          <button type="button" className="fb-btn primary" onClick={onSave}><FiSave /> 저장하기</button>
          <button type="button" className="fb-btn" onClick={onExit}><FiLogOut /> 나가기</button>
        </div>
      </header>

      <div className="fb-body">
        <main className="fb-canvas">
          <div className="fb-canvas-inner">
            <div className="fb-count">
              <span>총 {draft.length}개</span>
              {mode === 'build' && draft.length > 0 && (
                <button type="button" className="fb-clear" onClick={() => window.confirm('질문을 모두 지울까요?') && setDraft([])}>
                  <FiTrash2 /> 전체 삭제
                </button>
              )}
            </div>

            {mode === 'preview' ? (
              <div className="fb-card fb-preview">
                {/* 지원자가 신청 버튼을 눌렀을 때 뜨는 창(ApplyModal)과 같은 모양으로 보여 준다 */}
                <div className="fb-preview-head">
                  <h3>신청서 작성</h3>
                  <p>질문 {labeled.length}개 · 필수 {labeled.filter(q => q.required).length}개</p>
                </div>
                {labeled.length > 0
                  ? <ApplyFormRenderer questions={labeled} value={previewAnswers} onChange={setPreviewAnswers} preview />
                  : <p className="fb-empty-text">질문 내용을 입력하면 지원자에게 보이는 모습이 여기에 나옵니다.</p>}
                {labeled.length > 0 && (
                  <div className="fb-preview-foot">
                    <button type="button" className="fb-btn" onClick={() => setPreviewAnswers({})}>입력 지우기</button>
                    {/* 미리보기라 보내지 않고 필수 항목 확인만 해 본다 */}
                    <button type="button" className="fb-btn primary"
                            onClick={() => toast(missingRequired(labeled, previewAnswers) ?? '필수 항목을 모두 채웠습니다. 미리보기라 제출되지는 않습니다.')}>
                      신청하기
                    </button>
                  </div>
                )}
              </div>
            ) : draft.length === 0 ? (
              <div className="fb-card fb-empty">
                <FiPlusCircle className="fb-empty-icon" />
                <p className="fb-empty-text">항목 추가에서 원하는 항목을 눌러 질문을 만드세요</p>
              </div>
            ) : (
              draft.map((q, i) => (
                <QuestionCard
                  key={q.key} q={q} first={i === 0} last={i === draft.length - 1} autoFocus={q.key === focusKey}
                  optionInput={optionInputs[q.key] ?? ''}
                  setOptionInput={v => setOptionInputs({ ...optionInputs, [q.key]: v })}
                  onAddOption={() => addOption(i)}
                  onChange={patch => update(i, patch)}
                  onMove={delta => move(i, delta)}
                  onCopy={() => copy(i)}
                  onDelete={() => setDraft(draft.filter((_, j) => j !== i))}
                />
              ))
            )}
          </div>
        </main>

        <aside className="fb-panel">
          <h3>항목 추가</h3>
          {palette.map(group => (
            <section key={group.title} className="fb-group">
              <p className="fb-group-title">{group.title}</p>
              {group.types.map(type => {
                const Icon = typeIcon[type];
                return (
                  <button key={type} type="button" className="fb-palette-item" onClick={() => addQuestion(type)}>
                    <Icon /> {applyQuestionTypeText[type]}
                  </button>
                );
              })}
            </section>
          ))}
        </aside>
      </div>
    </div>
  );
}

interface CardProps {
  q: ApplyQuestion;
  first: boolean;
  last: boolean;
  autoFocus: boolean;
  optionInput: string;
  setOptionInput: (v: string) => void;
  onAddOption: () => void;
  onChange: (patch: Partial<ApplyQuestion>) => void;
  onMove: (delta: number) => void;
  onCopy: () => void;
  onDelete: () => void;
}

/** 질문 카드: 제목·설명을 바로 입력하고, 아래에 지원자가 답할 칸의 모양을 보여준다 */
function QuestionCard({ q, first, last, autoFocus, optionInput, setOptionInput, onAddOption, onChange, onMove, onCopy, onDelete }: CardProps) {
  const Icon = typeIcon[q.type];
  const textMax = APPLY_TEXT_MAX[q.type];
  const OptionIcon = q.type === 'CHECKBOX' ? FiSquare : FiCircle;

  return (
    <div className="fb-card fb-item">
      <div className="fb-item-head">
        <div className="fb-item-titles">
          <div className="fb-q-title-row">
            {q.required && <span className="apply-required">*</span>}
            <input
              className="fb-q-title" placeholder="제목을 입력해 주세요." value={q.label} maxLength={100}
              autoFocus={autoFocus} onChange={e => onChange({ label: e.target.value })}
            />
          </div>
          <input
            className="fb-q-desc" placeholder="설명을 입력해 주세요. (선택)" value={q.description ?? ''} maxLength={300}
            onChange={e => onChange({ description: e.target.value })}
          />
        </div>
        <div className="fb-item-tools">
          <span className="fb-badge"><Icon /> {applyQuestionTypeText[q.type]}</span>
          <FormControlLabel
            className="fb-required"
            control={<Switch size="small" checked={q.required} onChange={e => onChange({ required: e.target.checked })} />}
            label="답변 필수"
          />
          <button type="button" className="fb-icon-btn" aria-label="위로" disabled={first} onClick={() => onMove(-1)}><FiArrowUp /></button>
          <button type="button" className="fb-icon-btn" aria-label="아래로" disabled={last} onClick={() => onMove(1)}><FiArrowDown /></button>
          <button type="button" className="fb-icon-btn" aria-label="질문 복사" onClick={onCopy}><FiCopy /></button>
          <button type="button" className="fb-icon-btn" aria-label="질문 삭제" onClick={onDelete}><FiTrash2 /></button>
        </div>
      </div>

      {textMax !== undefined && (
        <>
          <div className={`fb-answer ${q.type === 'TEXTAREA' ? 'tall' : ''}`}>
            {q.type === 'URL' ? 'https://' : q.type === 'TEXTAREA' ? '긴 답변' : '짧은 답변'}
          </div>
          <p className="fb-answer-limit">최대 {textMax.toLocaleString()}자</p>
        </>
      )}

      {q.type === 'FILE' && (
        <div className="fb-answer file">
          <FiPaperclip /> {APPLY_FILE_LIMIT.extensions.filter(e => e !== 'jpeg').join(', ')} · 파일당 {APPLY_FILE_LIMIT.maxSize / 1024 / 1024}MB · 최대 {APPLY_FILE_LIMIT.maxCount}개
        </div>
      )}

      {hasOptions(q.type) && (
        <div className="fb-options">
          {q.options.map((option, n) => (
            <div key={option} className="fb-option">
              {q.type === 'SELECT' ? <span className="fb-option-no">{n + 1}.</span> : <OptionIcon className="fb-option-icon" />}
              <span className="fb-option-text">{option}</span>
              <button type="button" className="fb-icon-btn small" aria-label={`${option} 삭제`}
                      onClick={() => onChange({ options: q.options.filter(o => o !== option) })}><FiX /></button>
            </div>
          ))}
          <div className="fb-option add">
            {q.type === 'SELECT' ? <span className="fb-option-no">{q.options.length + 1}.</span> : <OptionIcon className="fb-option-icon" />}
            <input
              placeholder="선택지 입력 후 Enter" value={optionInput} maxLength={100}
              onChange={e => setOptionInput(e.target.value)}
              onKeyDown={e => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  // 한글 조합 중 Enter는 조합 확정용으로 한 번 더 들어와 선택지가 두 번 추가된다
                  if (e.nativeEvent.isComposing) return;
                  onAddOption();
                }
              }}
            />
            <button type="button" className="fb-option-add" onClick={onAddOption}>추가</button>
          </div>
        </div>
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
