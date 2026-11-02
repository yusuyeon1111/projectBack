import React, { useState, useRef, useEffect } from 'react';
import { TextField, Button, Box, MenuItem, Chip, Stack } from '@mui/material';
import { Editor } from '@toast-ui/react-editor';
import '@toast-ui/editor/dist/toastui-editor.css';
import '../css/PostForm.css'
import { Category } from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
const regions = [
  '서울특별시','부산광역시','대구광역시','인천광역시','광주광역시','대전광역시','울산광역시','세종특별자치시','경기도',
  '강원도','충청북도','충청남도','전라북도','전라남도','경상북도','경상남도','제주특별자치도',
];
const initialContents = [
  "### 1. 스터디 주제/목표",
  " - 자격증 이름이나 공부할 기술을 간단히 적어주세요.",
  " - 예) “정보처리기사 자격증 대비 스터디입니다. \n",
  " - 이번 스터디 목표는 자격증 합격입니다.”",
  "## 2. 진행 방식 및 일정",
  " - 모임 방식과 빈도, 기간을 적어주세요. ",
  " - 예) “매주 토요일 오후 3시~5시, 온라인 줌 미팅, 8월부터 3개월간 진행 예정”",
  "## 3. 모집 대상 및 수준",
  " - 참가자의 기본 조건이나 수준을 적어주세요. ",
  " - 예) “초보자도 환영, 성실하게 참여할 분, 자격증 공부에 의지가 있으신 분” \n",
  "## 4. 참여 혜택 및 규칙",
  " - 스터디 참여 시 기대할 수 있는 효과를 적어주세요.",
  " - 예) “체계적인 시험 대비, 모르는 부분 함께 공부, 출석과 과제 참여 필수”",
].join('\n');

const refreshContents = [
  " ### 1. 스터디 주제/목표 ",
 " - ",
  " ### 2. 진행 방식 및 일정 ",
  " - ",
  " ### 1-3. 목표 ",
  " - ",
  "## 3. 모집 대상 및 수준",
  " - ",
  "## 4. 참여 혜택 및 규칙",
  " - ",
].join('\n');

function PostForm() {
  const navigate = useNavigate();
  const [title, setTitle] = useState('');
  const editorRef = useRef();
  const [type, setType] = useState('online');
  const [region, setRegion] = useState('nothing');
  const username = localStorage.getItem("username")
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const today = new Date().toISOString().split('T')[0];
  const [positions, setPositions] = useState([
      { id: Date.now(), role: "totalCount", count: 1 },
    ]);
  const handleSubmit = async (e) => {
    let postStatus = "";
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
    if(startDate == today) {
      postStatus = "IN_PROGRESS"
    } else {
      postStatus = "SCHEDULED"
    }
    const markdown = editorRef.current.getInstance().getMarkdown();

    const postData = {
      title,
      content: markdown,
      region,
      projectType:type,
      category:'study',
      author:username,
      positions: positions,
      startDate,
      endDate,
      postStatus:postStatus,
      status:'ING'
    };
    try {
    const response = await axios.post(`${process.env.REACT_APP_API_URL}/api/post/create`, postData);

    if (response.status === 200 || response.status === 201) {
      const postId = response.data;
      alert('게시글이 등록되었습니다.');
      navigate(`/study/${postId}`);
    } else {
      alert('등록에 실패했습니다.');
    }
  } catch (error) {
    console.error('등록 오류:', error);
    alert('오류가 발생했습니다. 다시 시도해 주세요.');
  }
  };

  const handleRefresh = (e) => {
    e.preventDefault();
    editorRef.current.getInstance().setMarkdown(refreshContents);
  };

  const handleChange = (id, field, value) => {
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
          <span className="label">스터디명</span>
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
          <span className="label">스터디 방식</span>
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
        </div>
        <div className='memberLabel'>
        <span className="label" style={{marginTop:'30px'}}>모집인원</span>
         {positions.map((pos) => (
            <label className="row" key={pos.id}>
              <select
                name="position"
                className="select"
                value={pos.role}
                onChange={(e) => handleChange(pos.id, "role", e.target.value)}
              >
                <option value="totalCount">총 인원수</option>
              </select>
              <input
                id='count'
                type="number"
                className="input small"
                min="1"
                value={pos.count}
                onChange={(e) => handleChange(pos.id, "count", e.target.value)}
              />
            </label>
          ))}
        </div>
        <h2 className="label" style={{marginTop:'30px'}}>설명</h2>
        <span className="sub"> - 자유롭게 스터디에 대한 설명을 작성해주세요.</span>
        <div className="editor-wrapper">
          <Editor
            ref={editorRef}
            height="400px"
            initialEditType="wysiwyg"
            previewStyle="vertical"
             initialValue={initialContents}
          />
          <div className="editor-buttons">
            <button type="button" onClick={handleRefresh}>초기화</button>
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
