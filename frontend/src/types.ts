// 백엔드 DTO(org.example.project.*.dto)와 1:1로 맞춘 타입. 날짜는 JSON 직렬화된 문자열.

export const roleTextMap: Record<string, string> = {
  backend: "BACKEND",
  frontend: "FRONTEND",
  pm: "기획",
  design: "디자인",
  etc: "기타",
  totalCount: "스터디원", // 스터디 글은 포지션 없이 인원만 받아서 역할이 totalCount로 저장된다
};

// 스케줄러가 아직 안 돈 새 글은 postStatus가 비어 있어 예정으로 본다
export const postStatusText = (status?: string | null) =>
  status === 'END' ? '진행완료' : status === 'IN_PROGRESS' ? '진행중' : '예정';

// 페이지 버튼은 현재 페이지가 속한 10개 묶음만 그린다 (0부터 시작하는 페이지 번호 목록)
export const pageNumbers = (page: number, totalPages: number) => {
  const start = Math.floor(page / 10) * 10;
  return Array.from({ length: Math.max(0, Math.min(start + 10, totalPages) - start) }, (_, i) => start + i);
};

// NotificationResponse (/api/notifications)
export interface NotificationItem {
  id: number;
  type: 'APPLY' | 'ACCEPT' | 'REJECT'
    | 'TEAM_STARTED' | 'TEAM_COMPLETED' | 'TEAM_DISBANDED' | 'TEAM_LEADER' | 'TEAM_LEFT' | 'TEAM_KICKED'
    | 'TASK_ASSIGNED' | 'TASK_COMMENT';
  targetType: 'POST' | 'TEAM';
  targetId: number;
  postId: number | null; // 팀 알림은 글이 지워졌으면 null
  postTitle: string; // 팀 알림은 팀 이름
  postCategory: 'project' | 'study' | null;
  role: string | null;
  actor: string | null;
  read: boolean;
  createdAt: string;
}

const teamMessages: Partial<Record<NotificationItem['type'], string>> = {
  TEAM_STARTED: '활동을 시작했습니다.',
  TEAM_COMPLETED: '활동을 마쳤습니다.',
  TEAM_DISBANDED: '해산되었습니다.',
  TEAM_LEADER: '팀장이 되었습니다.',
  TEAM_KICKED: '퇴출되었습니다.',
};

export const notificationMessage = (n: NotificationItem) => {
  if (n.targetType === 'TEAM') {
    if (n.type === 'TASK_ASSIGNED') return `'${n.postTitle}' 팀에서 ${n.actor}님이 업무를 맡겼습니다.`;
    if (n.type === 'TASK_COMMENT') return `'${n.postTitle}' 팀 업무에 ${n.actor}님이 댓글을 남겼습니다.`;
    if (n.type === 'TEAM_LEFT') return `'${n.postTitle}' 팀에서 ${n.actor}님이 탈퇴했습니다.`;
    if (n.type === 'TEAM_LEADER' || n.type === 'TEAM_KICKED') return `'${n.postTitle}' 팀에서 ${teamMessages[n.type]}`;
    return `'${n.postTitle}' 팀이 ${teamMessages[n.type]}`;
  }
  const target = `'${n.postTitle}' ${roleTextMap[n.role ?? ''] ?? n.role} 포지션`;
  if (n.type === 'APPLY') return `${target}에 ${n.actor}님이 신청했습니다.`;
  return `${target} 신청이 ${n.type === 'ACCEPT' ? '승인' : '거절'}되었습니다.`;
};

// 팀 화면은 마이페이지 '내 팀' 탭에 있다. 퇴출·해산으로 팀을 못 보는 사람도 있어 팀 id로 바로 열지 않는다
export const notificationLink = (n: NotificationItem) =>
  n.targetType === 'TEAM' ? '/myPage?tab=team' : `/${n.postCategory === 'study' ? 'study' : 'post'}/${n.postId}`;

export const regions = [
  '서울특별시','부산광역시','대구광역시','인천광역시','광주광역시','대전광역시','울산광역시','세종특별자치시','경기도',
  '강원도','충청북도','충청남도','전라북도','전라남도','경상북도','경상남도','제주특별자치도',
];

export interface PostPosition {
  id: number;
  role: string;
  count: number;
  status: string;
}

// 신청서 질문(백엔드 ApplyQuestion). key는 질문을 고쳐도 바뀌지 않는 식별자다.
export type ApplyQuestionType = 'TEXT' | 'TEXTAREA' | 'URL' | 'SELECT' | 'RADIO' | 'CHECKBOX' | 'FILE';

export interface ApplyQuestion {
  key: string;
  type: ApplyQuestionType;
  label: string;
  description?: string; // 제목 아래 보충 설명(선택)
  required: boolean;
  options: string[];
}

// 글로 답하는 질문의 최대 글자 수. 서버(ApplyFormService.MAX_TEXT_LENGTH)와 같은 값이어야 한다.
export const APPLY_TEXT_MAX: Partial<Record<ApplyQuestionType, number>> = { TEXT: 200, TEXTAREA: 2000, URL: 500 };

export const applyQuestionTypeText: Record<ApplyQuestionType, string> = {
  TEXT: '짧은 글', TEXTAREA: '긴 글', URL: '링크', SELECT: '드롭다운', RADIO: '하나 선택', CHECKBOX: '여러 개 선택', FILE: '파일 첨부',
};

export const hasOptions = (type: ApplyQuestionType) => type === 'SELECT' || type === 'RADIO' || type === 'CHECKBOX';

// 첨부 파일 제한. 서버(ApplyFormService)와 같은 값이어야 한다.
export const APPLY_FILE_LIMIT = { maxSize: 10 * 1024 * 1024, maxCount: 3, extensions: ['pdf', 'jpg', 'jpeg', 'png', 'zip'] };

export interface ApplyFile {
  key: string;
  name: string;
  size?: number;
}

// 신청 시 보내는 답. FILE 질문은 files에, 나머지는 values에 담는다.
export interface ApplyAnswerInput {
  values: string[];
  files: ApplyFile[];
}

// 저장된 답변(백엔드 ApplyAnswer). 제출 당시 질문 내용이 같이 들어 있다.
export interface ApplyAnswer {
  questionKey: string;
  label: string;
  type: ApplyQuestionType;
  options: string[];
  values: string[];
  files: ApplyFile[];
}

// PostResponseDto
export interface Post {
  id: number;
  title: string;
  content: string;
  projectType: string;
  region: string;
  author: string;
  nickname: string;
  category: 'project' | 'study';
  created: string;
  updated: string | null;
  status: string;
  delYn: 'Y' | 'N';
  techStacks: string[];
  positions: PostPosition[];
  likeCount: number;
  viewCount: number;
  startDate: string | null;
  endDate: string | null;
  postStatus: string;
  likedByUser: boolean;
  applyQuestions?: ApplyQuestion[]; // 상세 조회에만 있다
}

// PostController.listPostsNew 응답
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// MemberResponseDto
export interface Member {
  username: string;
  nickname: string;
  email: string;
  name: string;
  preferredPosition: string | null;
  preferredTechStacks: string[] | null;
  introduce: string | null;
  imgUrl: string | null;
}

// admin MemberResponse
export interface AdminMember {
  id: number;
  email: string;
  name: string;
  nickname: string;
  username: string;
  createdAt: string;
  delYn: 'Y' | 'N';
}

// ApplyResponseDto
export interface ApplyPost {
  applyId: number;
  memberId: number;
  memberName: string;
  positionId: number;
  positionName: string;
  applyStatus: string;
  positionStatus: string;
  postId: number;
  postTitle: string;
  category: string;
  appliedAt: string;
  acceptedAt: string | null;
  rejectedAt: string | null;
}

// PostAcceptDto
export interface AcceptPost {
  postId: number;
  postTitle: string;
  category: string;
  positionName: string;
  acceptedAt: string;
  endDate: string;
  startDate: string;
  postStatus: string;
}

// ApplyMemeberDto
export interface ApplyMember {
  nickname: string;
  memberId: number;
  postId: number;
  positionId: number;
  postPositionId: number;
  category: string;
  role: string;
  status: string;
  appliedAt: string;
  acceptedAt: string | null;
  rejectedAt: string | null;
}

// MyPageResponseDto
export interface MyPageResponse {
  member: Member;
  posts: Post[];
  applyPosts: ApplyPost[];
  acceptPosts: AcceptPost[];
  likePosts: Post[];
}

// MonthlyStatsDto / PostChartResponse / MonthlyPostDto / AdminChartResponse
export interface MonthlyStats { name: string; count: number; }
export interface CategoryStats { name: string; category: string; count: number; }
export interface PositionStats { month: number; name: string; applyCount: number; acceptCount: number; rejectCount: number; }
export interface AdminChart { memberStatus: MonthlyStats[]; postStatus: MonthlyStats[]; postStsStatus: MonthlyStats[]; }

// JwtToken
export interface JwtToken {
  grantType: string;
  accessToken: string;
  refreshToken: string;
  refreshTokenExpiresIn: number;
  roles: string[];
}

// 팀 (GET /api/teams/my, /api/teams/{id})
export interface TeamSummary {
  teamId: number;
  postId: number | null;
  name: string;
  status: string;
  leader: boolean;
}

export interface TeamMember {
  memberId: number;
  nickname: string | null;
  positionRole: string | null;
  leader: boolean;
  joinedAt: string;
}

export interface TeamDetail {
  teamId: number;
  postId: number | null;
  name: string;
  status: string;
  createdAt: string;
  members: TeamMember[];
  myMemberId: number; // 보는 사람 자신
}

// 신청 상태. WITHDRAWN(탈퇴)·EXPELLED(퇴출)는 팀에서 빠진 승인이다
export const applyStatusText: Record<string, string> = {
  APPLY: '대기', ACCEPT: '승인', REJECT: '거절', WITHDRAWN: '탈퇴', EXPELLED: '퇴출',
};
export const applyStatusClass: Record<string, string> = {
  APPLY: 'status-pending', ACCEPT: 'status-accepted', REJECT: 'status-rejected', WITHDRAWN: 'status-unknown', EXPELLED: 'status-rejected',
};

// 팀 상태 칩 색(MUI Chip color)
export const teamStatusColor: Record<string, 'primary' | 'success' | 'default' | 'error'> = {
  FORMING: 'primary', ACTIVE: 'success', COMPLETED: 'default', DISBANDED: 'error',
};

export const teamStatusText: Record<string, string> = {
  FORMING: '팀 구성 중',
  ACTIVE: '진행 중',
  COMPLETED: '완료',
  DISBANDED: '해산',
};

// 팀 업무 (GET /api/teams/{id}/tasks). 수정할 때 받은 version을 그대로 돌려보낸다
export interface Task {
  taskId: number;
  title: string;
  description: string | null;
  status: 'TODO' | 'IN_PROGRESS' | 'DONE';
  assigneeMemberId: number | null;
  creatorMemberId: number;
  dueDate: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
  deletable: boolean;
}

export const taskStatusText: Record<Task['status'], string> = {
  TODO: '할 일', IN_PROGRESS: '진행 중', DONE: '완료',
};

// 팀 활동 (GET /api/teams/{id}/activities). detail은 기록 당시 값이다
export interface TeamActivity {
  id: number;
  actorMemberId: number;
  actorName: string;
  type: string;
  targetType: string;
  targetId: number | null;
  detail: Record<string, string> | null;
  createdAt: string;
}

export function activityText(a: TeamActivity): string {
  const d = a.detail ?? {};
  const status = (s: string) => taskStatusText[s as Task['status']] ?? s;
  switch (a.type) {
    case 'TASK_CREATED': return `${a.actorName}님이 업무 "${d.title}"을(를) 만들었습니다`;
    case 'TASK_UPDATED': return `${a.actorName}님이 업무 "${d.title}"을(를) 수정했습니다`;
    case 'TASK_STATUS': return `${a.actorName}님이 "${d.title}"을(를) ${status(d.from)} → ${status(d.to)}(으)로 옮겼습니다`;
    case 'TASK_DELETED': return `${a.actorName}님이 업무 "${d.title}"을(를) 지웠습니다`;
    case 'COMMENT_CREATED': return `${a.actorName}님이 "${d.title}"에 댓글을 남겼습니다`;
    case 'COMMENT_DELETED': return `${a.actorName}님이 "${d.title}"의 댓글을 지웠습니다`;
    case 'MEMBER_JOINED': return `${a.actorName}님이 팀에 합류했습니다`;
    case 'MEMBER_LEFT': return `${a.actorName}님이 팀에서 탈퇴했습니다`;
    case 'MEMBER_KICKED': return `${a.actorName}님이 ${d.name}님을 퇴출했습니다`;
    case 'LEADER_CHANGED': return `${a.actorName}님이 ${d.name}님에게 팀장을 넘겼습니다`;
    case 'TEAM_STARTED': return `${a.actorName}님이 팀을 시작했습니다`;
    case 'TEAM_COMPLETED': return `${a.actorName}님이 팀을 완료했습니다`;
    case 'TEAM_DISBANDED': return `${a.actorName}님이 팀을 해산했습니다`;
    default: return `${a.actorName}님의 활동`;
  }
}

// 업무 댓글 (GET /api/teams/{id}/tasks/{taskId}/comments)
export interface TaskComment {
  commentId: number;
  authorMemberId: number;
  authorName: string;
  content: string;
  createdAt: string;
  deletable: boolean;
}

// 팀 채팅 (GET·POST /api/teams/{id}/messages). 실시간으로는 알림 SSE 연결의 chat 이벤트로 온다
export interface ChatMessage {
  id: number;
  teamId: number;
  senderMemberId: number;
  senderName: string;
  content: string;
  createdAt: string;
}

// useNotifications가 SSE를 받아 window에 다시 쏘는 브라우저 이벤트 이름
export const TEAM_CHAT = 'team-chat';
export const SSE_CONNECTED = 'sse-connected';
