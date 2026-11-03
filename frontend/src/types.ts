// 백엔드 DTO(org.example.project.*.dto)와 1:1로 맞춘 타입. 날짜는 JSON 직렬화된 문자열.

export const roleTextMap: Record<string, string> = {
  backend: "BACKEND",
  frontend: "FRONTEND",
  pm: "기획",
  design: "디자인",
  etc: "기타",
};

// 스케줄러가 아직 안 돈 새 글은 postStatus가 비어 있어 예정으로 본다
export const postStatusText = (status?: string | null) =>
  status === 'END' ? '진행완료' : status === 'IN_PROGRESS' ? '진행중' : '예정';

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
