# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 명령어

모든 명령은 `frontend/` 폴더 안에서 실행한다 (`cd frontend`).

Create React App(`react-scripts` 5) + TypeScript(strict) 기반. 별도 lint 스크립트 없음(CRA 내장 ESLint가 start/build 시 동작).

`.npmrc`에 `legacy-peer-deps=true`가 설정되어 있음 — `@toast-ui/react-editor`가 React 17 peer를 요구해서 이 옵션 없이는 `npm install`이 실패한다.

```bash
npm start                              # 개발 서버 (localhost:3000, /api → setupProxy.js 프록시)
npm run build                          # 프로덕션 빌드 → build/
npm test                               # Jest watch 모드
CI=true npm test                       # 단발 실행
npm test -- src/types.test.ts          # 단일 파일
npm test -- -t "모두 읽음"              # 단일 테스트 이름
npx tsc --noEmit                       # 타입 체크만 (빌드보다 빠름)
```

CI는 `CI=false npm run build`로 빌드한다. CRA는 `CI=true`면 ESLint 경고를 오류로 처리하는데, 기존 코드에 경고(안 쓰는 import 등)가 남아 있기 때문이다. 경고를 정리하면 CI에서 `CI=false`를 지운다. 알림 기능 테스트는 `src/types.test.ts`, `src/components/NotificationBell.test.tsx`에 있다(axios와 `EventSource`를 가짜로 바꿔 끼운다). react-router-dom 7을 jest 27이 못 읽어서 `package.json`의 `jest.moduleNameMapper`로 CJS 파일을 직접 가리킨다.

## 환경 변수

`.env`는 gitignore 대상이며 커밋되지 않음. 실행에 필요한 값:

- `REACT_APP_API_URL` — 백엔드 베이스 URL. 모든 axios 호출이 `${REACT_APP_API_URL}/api/...` 형태.
- `REACT_APP_KAKAO_API_KEY` — `public/index.html`의 Kakao Maps SDK `<script>`에 `%REACT_APP_KAKAO_API_KEY%`로 치환됨.

`src/setupProxy.js`(CRA 제약상 유일하게 JS로 남김)는 `/api`를 EC2 백엔드(`http://3.36.157.46:8080`)로 프록시하지만, 코드가 절대 URL(`REACT_APP_API_URL`)을 쓰므로 `REACT_APP_API_URL`을 비워두거나 `/`로 둘 때만 프록시가 의미 있음.

## 아키텍처

스터디/프로젝트 팀원 모집 플랫폼 프론트엔드. 백엔드는 별도 Spring 서버(`/api/member`, `/api/post`, `/api/admin`).

**타입**: 백엔드 DTO와 1:1로 맞춘 도메인 타입(`Post`, `Member`, `ApplyMember` 등)과 공용 상수(`regions`, `roleTextMap`)는 모두 `src/types.ts`에 있음. API 응답은 `axios.get<T>()` 제네릭으로 받는다. Kakao Maps 전역은 `src/global.d.ts`에서 `window.kakao: any`로 선언.

**라우팅**: `src/index.tsx`가 진입점이자 라우트 테이블. 라우트는 세 그룹:
- `<Layout/>`(Header + Outlet) 아래: `/`, `/myPage`, `/postform`, `/studyform`, `/post/:postId`, `/study/:postId`
- 레이아웃 없음: `/signin`, `/membersignup`, `/oauth2/success`
- `<AdminLayout/>`(`components/Admin/Layout.tsx`) 아래: `/admin`

**인증**: 서버 측 세션 없음. 로그인(`SignIn.tsx`) 또는 OAuth2 콜백(`OAuth2Success.tsx`, 쿼리스트링으로 토큰 수신)이 `localStorage`에 `accessToken`, `refreshToken`, `username`, `role`을 저장. `src/auth.ts`의 axios 전역 인터셉터가 모든 요청에 `Authorization: Bearer` 헤더를 붙이고, 401이면 `/api/member/reissue`로 토큰을 새로 받아 원래 요청을 한 번 다시 보내고, 재발급도 거절되면 저장값을 지우고 `/signin`으로 보낸다(`index.tsx`에서 import). 동시에 난 401은 재발급 요청 하나를 같이 기다린다(서버가 refresh token을 한 번 쓰면 바꾸므로). `src/auth.test.ts`가 검증한다. 백엔드는 토큰으로 사용자를 식별하므로 요청에 username을 실어 보낼 필요가 없다. 로그아웃 등 인증값 정리는 `clearAuth()`를 쓴다. 관리자 판별은 `role === 'ROLE_ADMIN'`.

**게시글 = project / study 두 종류**: 같은 `/api/post/*` 엔드포인트를 쓰되 `category` 필드로 구분. 그래서 UI가 거의 복제된 쌍으로 존재함:
- `PostForm.tsx` (category `'project'`, 등록 후 `/post/:id`) ↔ `StudyForm.tsx` (category `'study'`, 등록 후 `/study/:id`)
- `PostView.tsx` ↔ `StudyView.tsx` (조회, 수정, 삭제, 좋아요, 지원)

한쪽을 고치면 다른 쪽도 같이 봐야 함. `Post.tsx`는 placeholder. `PostContext.tsx`(`PostProvider`/`usePost`)는 정의만 있고 어디서도 사용하지 않음.

**메인 페이지**: `Main.tsx`가 필터 상태(category, status, region, position, keyword, sortType, projectType, stackKeyword, page, size)를 들고 `/api/post/list`에 POST → `PostList.tsx`에 넘김. `KakaoMap.tsx`는 `hooks/useLocation.ts`(geolocation, 실패 시 서울 기본 좌표)로 현재 위치를 받아 전역 `kakao.maps` 객체로 지도를 그림. 지역 목록은 `src/types.ts`의 `regions`와 `src/data/region.json` 양쪽에 있음.

**마이페이지 탭**은 용도별 세 개다: 내 팀(기본), 모집 · 지원(안쪽 전환 버튼으로 내가 모집한 글 / 내가 지원한 글), 찜한 글. 승인된 글만 따로 보여 주던 "참여중" 탭은 내 팀·신청 상태와 겹쳐 없앴다(서버 응답의 `acceptPosts`는 쓰지 않는다).

**지원/수락 흐름**: 게시글 상세에서 `/api/post/apply` → 작성자가 `MyPage.tsx` 모집 · 지원 탭에서 `/api/post/accept` 또는 `/api/post/reject`. `ApplyMemberModal.tsx`, `LoginRequiredModal.tsx`는 `isOpen`/`onClose` props를 받는 모달.

**팀**: 신청이 처음 승인되면 서버가 팀을 만든다(작성자가 팀장). 마이페이지 "내 팀" 탭(`MyTeams.tsx`)이 `/api/teams/my` 목록을 보여 주고, 누르면 `/api/teams/{id}`로 팀원을 연다. 팀장은 그 창에서 시작·완료·해산·위임·퇴출을, 팀원은 탈퇴를 한다. 탈퇴한 신청은 "모집 · 지원" 탭의 내가 지원한 글에서 다시 신청할 수 있다. 팀 알림은 `/myPage?tab=team`으로 보내 "내 팀" 탭을 연다(`MyPage.test.tsx`). 팀 창은 왼쪽에 팀원·팀 관리(팀장은 팀원 옆 ⋮ 메뉴로 위임·퇴출), 오른쪽에 채팅·업무·활동 탭을 둔다. 탭을 바꿔도 채팅이 끊기지 않게 세 탭을 모두 띄워 두고 가린다. 팀원 얼굴은 `TeamAvatar`(회원 id로 색 고정). 업무 보드(할 일·진행 중·완료 칸, 카드 화살표로 상태 이동)와 댓글은 `TeamTasks.tsx`, 활동 타임라인은 `TeamActivityFeed.tsx`(탭을 열 때마다 다시 읽음)이고, 업무는 수정할 때 받은 `version`을 돌려보내며 409면 알리고 다시 읽는다. 팀 채팅(`TeamChat.tsx`)은 팀 상세의 `myMemberId`로 내 말풍선을 오른쪽에 두고, 알림 SSE 연결 하나를 같이 쓴다. `useNotifications`가 `chat` 이벤트를 window의 `team-chat` 이벤트로, 연결(재연결)을 `sse-connected`로 다시 쏘고, `TeamChat`은 재연결·탭 복귀 때 마지막 id 이후를 `?after=`로 메우며 id로 중복을 거른다. `MyTeams.test.tsx`, `TeamTasks.test.tsx`, `TeamChat.test.tsx`, `TeamActivityFeed.test.tsx`가 검증한다.

**토스트**: 화면을 막지 않는 짧은 안내는 `components/Toast.tsx`의 `toast(문구)`를 쓴다. `<Toaster/>`가 `index.tsx`의 라우트 바깥에 하나 있어서 띄운 뒤 페이지를 옮겨도 보인다. `alert`는 브라우저를 멈추게 해서 확인이 필요 없는 안내에는 쓰지 않는다.

**관리자**: `components/Admin/` — `Dashboard.tsx`가 recharts로 `/api/admin/chart*` 통계, `User.tsx`가 회원 목록/승인/차단(`/api/admin/memberAll`, `acceptUser`, `banUser`), `Post.tsx`가 게시글 비공개·공개. 차단·비공개는 확인 창을 거치고, 본인 계정 줄에는 차단 버튼이 없다(서버도 400). 비공개 글은 서버가 작성자 외에는 404로 막고, 상세 화면은 조회에 실패하면 토스트를 띄우고 메인으로 보낸다. 로그인 시 관리자 판별은 `roles`에 `ROLE_ADMIN`이 있는지로 한다(순서 없음, `SignIn.test.tsx`). `Admin/Layout.tsx`는 저장된 `role`이 `ROLE_ADMIN`이 아니면 메인으로 보낸다(`Layout.test.tsx`). 카카오 로그인은 `role`을 저장하지 않으므로 카카오 계정은 관리자 화면에 들어올 수 없다.

**스타일**: 컴포넌트별 일반 CSS 파일(`src/css/*.css`, `src/css/admin/*.css`)을 import하고, 폼/상세/마이페이지는 MUI(`TextField`, `Chip`, `Stack`, `Tabs` 등)를 섞어 씀. Tailwind는 devDependency에 있지만 설정 파일도 없고 실제로 쓰이지 않음. 글 작성 에디터는 `@toast-ui/react-editor`.

**언어**: UI 문구, 주석, 커밋 메시지 모두 한국어.
