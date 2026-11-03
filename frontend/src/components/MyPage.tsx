import React, { useEffect, useState } from 'react';
import '../css/Mypage.css';
import axios from 'axios';
import { MdEdit } from "react-icons/md";
import {Tabs, Tab, Modal,Typography, Box, TextField, Button, Stack, Chip, List, ListItem } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { IoIosSettings } from "react-icons/io";
import { AcceptPost, ApplyMember, ApplyPost, Member, MyPageResponse, Post, roleTextMap } from '../types';

interface TabPanelProps {
  children?: React.ReactNode;
  value: number;
  index: number;
}

function CustomTabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;

  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`simple-tabpanel-${index}`}
      aria-labelledby={`simple-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ p: 3 }}>{children}</Box>}
    </div>
  );
}

function a11yProps(index: number) {
  return {
    id: `simple-tab-${index}`,
    'aria-controls': `simple-tabpanel-${index}`,
  };
}

function MyPage() {
  const [open, setOpen] = useState(false);
  const [nickname, setNickname] = useState("");
  const [introduce, setIntroduce] = useState("");
  const [memberData, setMemberData] = useState<Member | null>(null);
  const [postData, setPostData] = useState<Post[]>([]);
  const [applyData, setApplyData] = useState<ApplyPost[]>([]);
  const [acceptData, setAcceptData] = useState<AcceptPost[]>([]);
  const [likeData, setLikeData] = useState<Post[]>([]);
  const [stackInput, setStackInput] = useState("");
  const [stackList, setStackList] = useState<string[]>([]);
  const username = localStorage.getItem("username")
  const navigate = useNavigate();
  const [openModal, setOpenModal] = useState(false);
  // /api/post/postMember 응답: 선택한 글의 신청자 목록
  const [selectedPost, setSelectedPost] = useState<ApplyMember[] | null>(null);
  const [openApplicantModal, setOpenApplicantModal] = useState(false);
  const [selectedApplicant, setSelectedApplicant] = useState<Member | null>(null);
  const modalStyle = {
    position: 'absolute',
    top: '50%',
    left: '50%',
    transform: 'translate(-50%, -50%)',
    width: 400,
    bgcolor: 'background.paper',
    borderRadius: '8px',
    boxShadow: 24,
    p: 4,
  } as const;

   useEffect(() => {
      const fetchData = async () => {
        try {
          const response = await axios.get<MyPageResponse>(`${process.env.REACT_APP_API_URL}/api/member/myPage`, {
            params: { username }
          });
          setMemberData(response.data.member);
          setAcceptData(response.data.acceptPosts)
          setApplyData(response.data.applyPosts)
          setLikeData(response.data.likePosts)
          setPostData(response.data.posts)
        } catch (error) {
          if (axios.isAxiosError(error) && error.response) {
            alert(error.response.data);
          } else {
            alert("네트워크 에러");
          }
        }
      };

      fetchData();  // 호출
    }, [username]);

  const [value, setValue] = React.useState(0);

  const handleChange = (event: React.SyntheticEvent, newValue: number) => {
    setValue(newValue);
  };

  const handleOpen = () => {
    if (memberData) {
      setNickname(memberData.nickname);
      setIntroduce(memberData.introduce || "");
      setStackList(memberData.preferredTechStacks || []);
    }
    setOpen(true);
  };

  const handleClose = () => setOpen(false);

  const handleSave = async () => {
    try {
      const updatedData = {
        username: localStorage.getItem("username"),
        nickname,
        introduce,
        preferredTechStacks: stackList,
      };

      await axios.post(`${process.env.REACT_APP_API_URL}/api/member/update`, updatedData);

      alert("수정 완료");

      // 상태 업데이트로 화면 갱신
      setMemberData(prev => ({
        ...prev!,
        nickname: updatedData.nickname,
        introduce: updatedData.introduce,
        preferredTechStacks: updatedData.preferredTechStacks,
      }));

      handleClose();
    } catch (err) {
      alert("수정 실패");
    }
  };

      // 스택 추가
    const handleAddStack = () => {
      const trimmed = stackInput.trim();
      if (trimmed && !stackList.includes(trimmed)) {
        setStackList((prev) => [...prev, trimmed]);
        setStackInput("");
      }
    };

    // 스택 삭제
    const handleDeleteStack = (stackToDelete: string) => {
      setStackList((prev) => prev.filter((stack) => stack !== stackToDelete));
    };

    // 모달 열기
     const handleOpenModal = async (postId: number) => {
      try{
        const response = await axios.get<ApplyMember[]>(`${process.env.REACT_APP_API_URL}/api/post/postMember/${postId}`)
        setSelectedPost(response.data);
        } catch (error) {
          alert("정보를 불러오는 데 실패했습니다. 다시 시도해주세요.")
        }
      setOpenModal(true);
    };

    const handleCloseModal = () => {
      setOpenModal(false);
      setSelectedPost(null);
    };

   const handleAccept = async (postId: number, positionId: number, postPositionId: number) => {
      try {
        const response = await axios.post<ApplyMember[]>(`${process.env.REACT_APP_API_URL}/api/post/accept`, {
          postId,
          positionId,
          postPositionId,
          username,
        });

        // 서버에서 최신 신청자 목록 받아서 바로 상태 업데이트
        setSelectedPost(response.data);
        alert("수락되었습니다.");
      } catch (error: any) {
        alert(error.response?.data || "에러가 발생했습니다.");
      }
    };

    const handleReject = async (postId: number, positionId: number, postPositionId: number) => {
      try {
        const response = await axios.post<ApplyMember[]>(`${process.env.REACT_APP_API_URL}/api/post/reject`, {
          postId,
          positionId,
          postPositionId,
          username,
        });

        setSelectedPost(response.data);
        alert("거절되었습니다.");
      } catch (error: any) {
        alert(error.response?.data || "에러가 발생했습니다.");
      }
    };



      // 신청자 클릭 시 상세 정보 모달 열기
      const handleOpenApplicantModal = async (memberId: number) => {
        try{
          const response = await axios.get<Member>(`${process.env.REACT_APP_API_URL}/api/member/getUserInfo/${memberId}`)
          setSelectedApplicant(response.data);
        setOpenApplicantModal(true);
        setOpenModal(false);
        } catch(error) {
          alert("사용자 조회에 실패했습니다.")
          setOpenApplicantModal(false);
        }
      };

      const handleCloseApplicantModal = () => {
        setOpenModal(true);
        setOpenApplicantModal(false);
        setSelectedApplicant(null);
      };

  return (
    <div className="mypage-container">
      <div className='mypage-inner'>
      <div className="myInfo-container">
        {memberData ? (
          <>
           <div className='myInfo-inner'>
            <div id="editIcon"><span>프로필 수정</span><MdEdit size={22} onClick={handleOpen} style={{ cursor: "pointer" }} /><IoIosSettings size={22} style={{marginLeft:'10px', cursor: "pointer" }}/></div>

            <div className="profile-header">
              <span className="nickname">{memberData.nickname}</span>
              {memberData?.preferredPosition && (
                <span className="role-badge">{memberData.preferredPosition}</span>
              )}
               <>
                  {/* 모달 */}
                  <Modal open={open} onClose={handleClose}>
                    <Box sx={modalStyle}>
                      <h3>프로필 수정</h3>
                      <TextField
                        fullWidth
                        margin="normal"
                        label="닉네임"
                        value={nickname}
                        onChange={(e) => setNickname(e.target.value)}
                      />
                      <TextField
                        fullWidth
                        margin="normal"
                        label="소개"
                        multiline
                        rows={4}
                        value={introduce}
                        onChange={(e) => setIntroduce(e.target.value)}
                      />
                      <div className='modal-stack'>
                            <TextField
                              variant="outlined"
                              label="기술스택"
                              size="small"
                              value={stackInput}
                              onChange={(e) => setStackInput(e.target.value)}
                              onKeyPress={(e) => {
                                if (e.key === "Enter") {
                                  e.preventDefault();
                                  handleAddStack();
                                }
                              }}
                              sx={{
                                flex: 1,
                                minWidth: 0,
                                '& .MuiOutlinedInput-root': {
                                  padding: 0,
                                  fontSize: '14px',
                                },
                                '& .MuiOutlinedInput-input': {
                                  padding: '12px',
                                },
                              }}
                            />
                            <button type="button" onClick={handleAddStack}>+</button>
                          <Stack direction="row" spacing={1} flexWrap="wrap" mt={2}>
                            {stackList.map((stack, index) => (
                              <Chip
                                key={index}
                                label={stack}
                                onDelete={() => handleDeleteStack(stack)}
                                color="primary"
                                sx={{ mb: 1 }}
                              />
                            ))}
                          </Stack>
                          </div>
                      <Box mt={2} display="flex" justifyContent="flex-end" gap={1}>
                        <Button variant="outlined" onClick={handleClose}>취소</Button>
                        <Button variant="contained" onClick={handleSave}>저장</Button>
                      </Box>
                    </Box>
                  </Modal>
                </>
            </div>
            <p className="intro-text">
              {memberData.introduce != null ? memberData.introduce : "소개멘트를 작성해주세요"}
            </p>
            <div className="myStack-container">
             {memberData?.preferredTechStacks?.map((stack, index) => (
                <span key={index} className="stack-pill"># {stack}</span>
              ))}

            </div>
            </div>
            <div className='profile-info'>
              <div>
                <span>작성글</span>
                <span>{postData.length}</span>
              </div>
              <div>
                <span>신청내역</span>
                <span>{applyData.length}</span>
              </div>
              <div>
                <span>참여중</span>
                <span>{acceptData.length}</span>
              </div>
              <div>
                <span>찜한글</span>
                <span>{likeData.length}</span>
              </div>
            </div>
          </>
        ) : (
          <p>불러오는 중...</p>
        )}
      </div>


      <div className="myAct-container">
        <Box sx={{ width: '100%' }}>
          <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
            <Tabs value={value} onChange={handleChange} aria-label="basic tabs example">
              <Tab label="작성글" {...a11yProps(0)} />
              <Tab label="신청내역" {...a11yProps(1)} />
              <Tab label="참여중" {...a11yProps(2)} />
              <Tab label="찜한글" {...a11yProps(3)} />
            </Tabs>
          </Box>
          <CustomTabPanel value={value} index={0}>
            <div className='postData-container'>
            { postData.length > 0 ? postData.map((post)=>(
              <div className='postItemTop' key={post.id} >
              <div className='postItem'>
                 {post.category === 'study' ?
                <span id='studylabel'>스터디</span>
                :<span id='projectlabel'>프로젝트</span>}
                <span className='myTitle' onClick={() => navigate(post.category === 'study'? `/study/${post.id}`: `/post/${post.id}`)}>
                  {post.title}</span>
                {post.status === 'END' ? (
                  <span className='status' data-type="end">모집완료</span>
                ) : (
                  <span className='status' data-type="active">모집중</span>
                )}
                <div className='dataInner'>
                  <span className='myDate'> 작성일자 : {!post.updated ? post.created : post.updated}</span><span className='line'>|</span>
                  <span className='myDate'> 기간 : {post.startDate} - {post.endDate}  {post.postStatus === 'END' ? '종료' : '진행중'}</span><span className='line'>|</span>
                  <span className='myDate'> 조회수 {post.viewCount}</span><span className='line'>|</span>
                  <span className='myDate'> ❤️ {post.likeCount}</span>
                </div>
                </div>
                <button id='applyMngBtn' onClick={() => handleOpenModal(post.id)}>👤 신청자관리</button>
              </div>
            )):<div><p>데이터 없음.</p></div>}
            </div>
          </CustomTabPanel>
          <CustomTabPanel value={value} index={1}>
            {applyData.length > 0 ?
             applyData.map((post)=>(
              <div key={post.postId} className='postItem'>
                {post.category === 'study' ?
                <span id='studylabel'>스터디</span>
                :<span id='projectlabel'>프로젝트</span>}
                <span className='myTitle' onClick={() => navigate(post.category === 'study'? `/study/${post.postId}`: `/post/${post.postId}`)}>
                  {post.postTitle}</span>
                <span
                  className={`like ${
                    post.applyStatus === 'APPLY'
                      ? 'status-pending'
                      : post.applyStatus === 'ACCEPT'
                      ? 'status-accepted'
                      : post.applyStatus === 'REJECT'
                      ? 'status-rejected'
                      : 'status-unknown'
                  }`}
                >
                  {post.applyStatus === 'APPLY'
                    ? '대기'
                    : post.applyStatus === 'ACCEPT'
                    ? '승인'
                    : post.applyStatus === 'REJECT'
                    ? '거절'
                    : '알 수 없음'}
                </span>

                <div className='dataInner'>
                  <span className='myDate'> 신청일자 : {post.appliedAt}</span>
                  {post.category === 'project' && <><span className='line'>|</span><span className='myDate'> 신청분야 : {roleTextMap[post.positionName]}</span></>}
                </div>
              </div>
            ))
            : <div><p>데이터 없음.</p></div>
          }
          </CustomTabPanel>
          <CustomTabPanel value={value} index={2}>
            {acceptData.length > 0 ? acceptData.map((post)=>(
              <div key={post.postId} className='postItem'>
                {post.category === 'study' ?
                <span id='studylabel'>스터디</span>
                :<span id='projectlabel'>프로젝트</span>}
                <span className='myTitle' onClick={() => navigate(post.category === 'study'? `/study/${post.postId}`: `/post/${post.postId}`)}>
                  {post.postTitle}</span>
                {post.postStatus === 'END' ? (
                  <span className='status' data-type="end">진행완료</span>
                ) : (
                  <span className='status' data-type="active">진행중</span>
                )}
                <div className='dataInner'>
                  <span className='myDate'>참여일자 : {post.acceptedAt}</span><span className='line'>|</span>
                  <span className='myDate'> 기간 : {post.startDate} - {post.endDate}</span>
                  {post.category === 'project' && <><span className='line'>|</span><span className='myDate'> 참여분야 : {roleTextMap[post.positionName]}</span></>}
              </div>
              </div>
            )): <div><p>데이터 없음.</p></div>}
          </CustomTabPanel>
          <CustomTabPanel value={value} index={3}>
            {likeData.length > 0 ? likeData.map((post)=>(
              <div key={post.id} className='postItem'>
                 {post.category === 'study' ?
                <span id='studylabel'>스터디</span>
                :<span id='projectlabel'>프로젝트</span>}
                <span className='myTitle' onClick={() => navigate(post.category === 'study'? `/study/${post.id}`: `/post/${post.id}`)}>
                  {post.title}</span>
                {post.status === 'END' ? (
                  <span className='status' data-type="end">모집완료</span>
                ) : (
                  <span className='status' data-type="active">모집중</span>
                )}

                <div className='dataInner'>
                  <span className='myDate'> 작성일자 : {!post.updated ? post.created : post.updated}</span><span className='line'>|</span>
                  <span className='myDate'> 기간 : {post.startDate} - {post.endDate}  {post.postStatus === 'END' ? '종료' : '진행중'}</span><span className='line'>|</span>
                  <span className='myDate'> 조회수 {post.viewCount}</span><span className='line'>|</span>
                  <span className='myDate'> ❤️ {post.likeCount}</span>
                </div>

              </div>
            )): <div><p>데이터 없음.</p></div>}
          </CustomTabPanel>
        </Box>
      </div>
    </div>
     {/* 신청자 관리 모달 */}
      <Modal open={openModal} onClose={handleCloseModal} closeAfterTransition>
        <Box sx={{
          position: 'absolute',
          top: '50%',
          left: '50%',
          transform: 'translate(-50%, -50%)',
          width: 500,
          maxWidth: '90%',
          bgcolor: '#fff',
          borderRadius: 3,
          boxShadow: 40,
          p: 4,
          display: 'flex',
          flexDirection: 'column',
          gap: 3, // 요소 간 간격
          textAlign: 'left',
        }}>
          <Typography variant="h6" sx={{ fontWeight: 'bold', fontSize: 22, mb: 2 }}>
            신청자 관리
          </Typography>

          {selectedPost && selectedPost.length > 0 ? (
            <List sx={{ maxHeight: 300, overflowY: 'auto', '&::-webkit-scrollbar': { width: 6 }, '&::-webkit-scrollbar-thumb': { backgroundColor: '#ccc', borderRadius: 3 } }}>
              {selectedPost.map((post) => (
                <ListItem key={post.postPositionId} sx={{ flexDirection: 'column', alignItems: 'flex-start', mb: 1, p: 2, borderRadius: 2, bgcolor: '#f9f9f9' }}>
                  <Typography
                    sx={{ color: '#1976d2', cursor: 'pointer', fontWeight: 500, mb: 0.5, fontSize: 20 }}
                    onClick={() => handleOpenApplicantModal(post.memberId)}
                  >
                    {post.nickname}
                  </Typography>
                  <Typography sx={{ fontSize: 16, color: '#555', mb: 0.5 }}>
                    신청일: {post.appliedAt}
                  </Typography>
                  <Typography sx={{ fontSize: 16, color: '#555', mb: 0.5 }}>
                    상태: <span className={`like ${post.status === 'APPLY' ? 'status-pending' : post.status === 'ACCEPT' ? 'status-accepted' : post.status === 'REJECT' ? 'status-rejected' : 'status-unknown'}`}>
                      {post.status === 'APPLY' ? '대기' : post.status === 'ACCEPT' ? '승인' : post.status === 'REJECT' ? '거절' : '알 수 없음'}
                    </span>
                  </Typography>
                  {post.category === 'project' && (
                    <Typography sx={{ fontSize: 16, color: '#555', mb: 0.5 }}>
                      신청분야: {roleTextMap[post.role]}
                    </Typography>
                  )}
                  {post.status === 'APPLY' && (
                    <Box sx={{ mt: 1 }}>
                      <Button size="small" variant="outlined" sx={{ mr: 1 }} onClick={() => handleAccept(post.postId, post.positionId, post.postPositionId)}>승인</Button>
                      <Button size="small" variant="outlined" color="error" onClick={() => handleReject(post.postId, post.positionId, post.postPositionId)}>거절</Button>
                    </Box>
                  )}
                </ListItem>
              ))}
            </List>
          ) : (
            <Typography sx={{ color: '#555' }}>신청자가 없습니다.</Typography>
          )}

          <Box mt={3} textAlign="right">
            <Button variant="contained" onClick={handleCloseModal} sx={{ borderRadius: 2 }}>닫기</Button>
          </Box>
        </Box>
      </Modal>
      <Modal open={openApplicantModal} onClose={handleCloseApplicantModal}>
       <Box sx={{
            position: 'absolute',
            top: '50%',
            left: '50%',
            transform: 'translate(-50%, -50%)',
            width: 500,
            maxWidth: '90%',
            bgcolor: '#f9f9fb', // 부드러운 배경
            borderRadius: 4,     // 더 부드러운 모서리
            boxShadow: '0 8px 24px rgba(0,0,0,0.2)', // 그림자 변경
            p: 4,
            display: 'flex',
            flexDirection: 'column',
            gap: 2,
            textAlign: 'left',
            transition: 'all 0.3s ease-in-out', // 등장 애니메이션 느낌
          }}>
          {selectedApplicant ? (
            <>
              <Typography variant="h6" sx={{
                  fontWeight: 700,
                  fontSize: 22,
                  mb: 1,
                  color: '#222',
                  borderBottom: '1px solid #eee',
                  pb: 1
                }}>
                  {selectedApplicant.nickname}
                </Typography>
              <Typography sx={{ fontSize: 16, color: '#555', lineHeight: 1.6 }}>
                <strong>이메일:</strong> {selectedApplicant.email}
              </Typography>
              <Typography sx={{ fontSize: 16, color: '#555', lineHeight: 1.6 }}>
                <strong>자기소개:</strong> {selectedApplicant.introduce}
              </Typography>
              <Typography sx={{ fontSize: 16, color: '#555', lineHeight: 1.6 }}>
                <strong>선호 포지션:</strong> {roleTextMap[selectedApplicant.preferredPosition ?? ""]}
              </Typography>
              <Typography sx={{ fontSize: 16, color: '#555', lineHeight: 1.6 }}>
                <strong>기술스택:</strong> {selectedApplicant.preferredTechStacks?.join(", ")}
              </Typography>
            </>
          ) : (
            <Typography>정보를 불러오는 중...</Typography>
          )}
          <Box mt={3} textAlign="right">
            <Button variant="contained" onClick={handleCloseApplicantModal} sx={{ borderRadius: 2 }}>닫기</Button>
          </Box>
        </Box>
      </Modal>

    </div>
  );
}

export default MyPage;
