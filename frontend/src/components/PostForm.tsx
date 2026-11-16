import React, { useState, useRef, useEffect } from 'react';
import { TextField, Button, Box, MenuItem, Chip, Stack } from '@mui/material';
import { Editor } from '@toast-ui/react-editor';
import '@toast-ui/editor/dist/toastui-editor.css';
import '../css/PostForm.css'
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { regions } from "../types";

interface PositionInput {
  id: number;
  role: string;
  count: number;
  status?: string;
}
const initialContents = [
  "## 1. 프로젝트 개요 \n",
  " ### 1-1. 프로젝트 소개 ",
  " - 간단하게 우리 프로젝트가 무엇인지 설명해 주세요.",
  " - 예) “온라인 스터디 매칭 플랫폼을 개발하는 프로젝트입니다. \n",
  " ### 1-2. 주제 ",
  " - 프로젝트의 핵심 주제를 한 문장으로 적어 주세요.",
  " - 예) “효율적인 스터디 그룹을 연결해주는 서비스” \n",
  " ### 1-3. 목표 ",
  " - 이번 프로젝트를 통해 이루고 싶은 목표를 적어 주세요.",
  " - 예) “포트폴리오용 MVP 완성” \n",
  "## 2. 진행 방식 및 일정",
  " - 프로젝트 진행 방법과 주요 일정을 알려 주세요.",
  " - 예) “주 1회 온라인 미팅, 8월 10일부터 10월 말까지 진행” \n",
  "## 3. 지원 자격",
  " - 함께 일할 팀원에게 기대하는 조건이나 필요한 기술을 적어 주세요. ",
  " - 예) “Git 사용 가능자, 소통 잘하는 분, 협업 경험자 우대” \n",
  "## 4. 기대할 수 있는 점",
  " - 이 프로젝트에 참여하면 어떤 경험과 이점이 있을지 알려 주세요.",
  " - 예) “실전 협업 경험, 배포 가능한 포트폴리오, 기술면접 대비” \n",
].join('\n');

const refreshContents = [
  "## 1. 프로젝트 개요",
  " ### 1-1. 프로젝트 소개 ",
 " - ",
  " ### 1-2. 주제 ",
  " - ",
  " ### 1-3. 목표 ",
  " - ",
  "## 2. 진행 방식 및 일정",
  " - ",
  "## 3. 지원 자격",
  " - ",
  "## 4. 기대할 수 있는 점",
  " - ",
].join('\n');

function PostForm() {
  const navigate = useNavigate();
  const [title, setTitle] = useState('');
  const editorRef = useRef<Editor>(null);
  const [stackInput, setStackInput] = useState("");
  const [stackList, setStackList] = useState<string[]>([]);
  const [type, setType] = useState('online');
  const [region, setRegion] = useState('nothing');
  const [showEditor, setShowEditor] = useState(false);
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const today = new Date().toISOString().split('T')[0];
  const [positions, setPositions] = useState<PositionInput[]>([
    { id: Date.now(), role: "backend", count: 1 , status:"ING"},
  ]);

  useEffect(() => {
    setTimeout(() => setShowEditor(true), 0);
  }, []);

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!startDate || !endDate) {
      alert('시작일과 마감일을 모두 선택해주세요.');
      return;
    }

    if (startDate < today) {
    alert("시작일은 오늘 이후로 설정해주세요.");
    return;
  }
  if (endDate && endDate < startDate) {
    alert("마감일은 시작일 이후로 설정해주세요.");
    return;
  }
    const markdown = editorRef.current!.getInstance().getMarkdown();

    let postStatus = '';
    if(startDate == today) {
      postStatus = "IN_PROGRESS"
    } else {
      postStatus = "SCHEDULED"
    }

    const postData = {
      title,
      content: markdown,
      positions: positions,
      techStacks : stackList,
      region,
      projectType:type,
      category:'project',
      status:'ING',
      startDate,
      endDate,
      postStatus:postStatus
    };
    try {
    const response = await axios.post(`${process.env.REACT_APP_API_URL}/api/post/create`, postData);

    if (response.status === 200 || response.status === 201) {
      const postId = response.data;
      alert('게시글이 등록되었습니다.');
      navigate(`/post/${postId}`);
    } else {
      alert('등록에 실패했습니다.');
    }
  } catch (error) {
    console.error('등록 오류:', error);
    alert('오류가 발생했습니다. 다시 시도해 주세요.');
  }
  };

  const handleRefresh = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    editorRef.current!.getInstance().setMarkdown(refreshContents);
  };

  const handleAddStack = () => {
    const trimmed = stackInput.trim();
    if (trimmed && !stackList.includes(trimmed)) {
      setStackList([...stackList, trimmed]);
      setStackInput("");
    }
  };

  const handleDeleteStack = (stackToDelete: string) => {
    setStackList(stackList.filter((stack) => stack !== stackToDelete));
  };

   const handleAdd = () => {
    setPositions([
      ...positions,
      { id: Date.now(), role: "frontend", count: 1 , status:"ING"},
    ]);
  };

  const handleRemove = (id: number) => {
    setPositions(positions.filter((pos) => pos.id !== id));
  };

  const handleChange = (id: number, field: "role" | "count", value: string | number) => {
    setPositions(
      positions.map((pos) =>
        pos.id === id ? { ...pos, [field]: value } : pos
      )
    );
  };

  return (
   <div className="form-container">
      <form className="form" onSubmit={handleSubmit}>
        <div className='form-top'>
        <label>
          <span className="label">프로젝트명</span>
          <input name="title" className="input" value={title}  onChange={(e) => setTitle(e.target.value)}/>
        </label>
        <div className="date-section">
          <label>
            <span className="label">시작일</span>
            <TextField
              type="date"
              size="small"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              className="input"
              InputLabelProps={{ shrink: true }}
              inputProps={{ min: today }} // 오늘 이전 선택 불가
            />
          </label>
          <label style={{ marginLeft: '10px' }}>
            <span className="label">마감일</span>
            <TextField
              type="date"
              size="small"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
              className="input"
              InputLabelProps={{ shrink: true }}
              inputProps={{ min: startDate || today }} // 시작일 이전 선택 불가
            />
          </label>
        </div>

        <div>
          <span className="label">프로젝트 방식</span>
            <div className="project-type-row">
              <select
                name="projectType"
                value={type}
                onChange={(e) => setType(e.target.value)}
                className="select"
              >
                <option value="online">온라인</option>
                <option value="offline">오프라인</option>
                <option value="both">온라인/오프라인</option>
              </select>
              <select
                name="region"
                value={region}
                onChange={(e) => setRegion(e.target.value)}
                className="select"
              >
                <option value="nothing">지역무관</option>
                {type !== 'online' && regions.map((r) => <option key={r}>{r}</option>)}
              </select>
            </div>
          </div>
        <div className='memberLabel'>
        <span className="label">모집인원</span>
         {positions.map((pos) => (
            <label className="row" key={pos.id}>
              <select
                name="position"
                className="select"
                value={pos.role}
                onChange={(e) => handleChange(pos.id, "role", e.target.value)}
              >
                <option value="backend">BACKEND</option>
                <option value="frontend">FRONTEND</option>
                <option value="pm">기획</option>
                <option value="design">디자인</option>
                <option value="etc">기타</option>
              </select>
              <input
                id='count'
                type="number"
                className="input small"
                min="1"
                value={pos.count}
                onChange={(e) => handleChange(pos.id, "count", Number(e.target.value))}
              />
              <button type="button" onClick={handleAdd} id='graBtn'>
                추가
              </button>
              {positions.length > 1 && (
                <button type="button" onClick={() => handleRemove(pos.id)} id='graBtn'>
                  삭제
                </button>
              )}
            </label>
          ))}
        </div>
        <div className="stack-section">
          <span className="label">사용 스택</span>
          <span className="sub">- 프론트엔드, 백엔드, 협업 도구 등 어떤 기술과 도구를 사용할 계획인지 적어 주세요.</span>
          <div className="stack-input">
            <TextField
              variant="outlined"
              size="small"
              value={stackInput}
              onChange={(e) => setStackInput(e.target.value)}
              onKeyPress={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  handleAddStack();
                }
              }}
              sx={{ flex: 1 }}
            />
            <button type="button" onClick={handleAddStack} style={{fontSize:'25px'}} id='graBtn'>+</button>
          </div>
          <Stack direction="row" spacing={1} flexWrap="wrap" mt={2}>
            {stackList.map((stack, i) => (
              <Chip key={i} label={stack} onDelete={() => handleDeleteStack(stack)} color="primary" />
            ))}
          </Stack>
        </div>
        </div>
        <span className="label" style={{marginTop:'20px'}}>설명</span>
        <span className="sub"> - 자유롭게 프로젝트에 대한 설명을 작성해주세요.</span>
        <div className="editor-wrapper">
          <Editor
            ref={editorRef}
            height="400px"
            initialEditType="wysiwyg"
            previewStyle="vertical"
             initialValue={initialContents}
          />
          <div className="editor-buttons">
            <button type="button" onClick={handleRefresh} id='submitBtn'>초기화</button>
          </div>
        </div> 
        <div className='btmBtn-container'>
            <button type='submit' id='submitBtn'>등록</button>
            <button type='button' onClick={() => navigate("/")} id='cancleBtn'>취소</button>
        </div> 
      </form>
    </div>
  );
}

export default PostForm;
