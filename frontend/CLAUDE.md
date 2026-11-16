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

**라우팅**: `src/index.tsx`가 진입점이자 라우트 테이블. `src/App.tsx`는 사용되지 않는 잔재(렌더되지 않음). 라우트는 세 그룹:
- `<Layout/>`(Header + Outlet) 아래: `/`, `/myPage`, `/postform`, `/studyform`, `/post/:postId`, `/study/:postId`
- 레이아웃 없음: `/signin`, `/membersignup`, `/oauth2/success`
- `<AdminLayout/>`(`components/Admin/Layout.tsx`) 아래: `/admin`

**인증**: 서버 측 세션 없음. 로그인(`SignIn.tsx`) 또는 OAuth2 콜백(`OAuth2Success.tsx`, 쿼리스트링으로 토큰 수신)이 `localStorage`에 `accessToken`, `refreshToken`, `username`, `role`을 저장. `src/auth.ts`의 axios 전역 인터셉터가 모든 요청에 `Authorization: Bearer` 헤더를 붙이고, 401이면 저장값을 지우고 `/signin`으로 보낸다(`index.tsx`에서 import). 백엔드는 토큰으로 사용자를 식별하므로 요청에 username을 실어 보낼 필요가 없다. 로그아웃 등 인증값 정리는 `clearAuth()`를 쓴다. 관리자 판별은 `role === 'ROLE_ADMIN'`.

**게시글 = project / study 두 종류**: 같은 `/api/post/*` 엔드포인트를 쓰되 `category` 필드로 구분. 그래서 UI가 거의 복제된 쌍으로 존재함:
- `PostForm.tsx` (category `'project'`, 등록 후 `/post/:id`) ↔ `StudyForm.tsx` (category `'study'`, 등록 후 `/study/:id`)
- `PostView.tsx` ↔ `StudyView.tsx` (조회, 수정, 삭제, 좋아요, 지원)

한쪽을 고치면 다른 쪽도 같이 봐야 함. `Post.tsx`는 placeholder. `PostContext.tsx`(`PostProvider`/`usePost`)는 정의만 있고 어디서도 사용하지 않음.

**메인 페이지**: `Main.tsx`가 필터 상태(category, status, region, position, keyword, sortType, projectType, stackKeyword, page, size)를 들고 `/api/post/list`에 POST → `PostList.tsx`에 넘김. `KakaoMap.tsx`는 `hooks/useLocation.ts`(geolocation, 실패 시 서울 기본 좌표)로 현재 위치를 받아 전역 `kakao.maps` 객체로 지도를 그림. 지역 목록은 `src/types.ts`의 `regions`와 `src/data/region.json` 양쪽에 있음.

**지원/수락 흐름**: 게시글 상세에서 `/api/post/apply` → 작성자가 `MyPage.tsx`에서 `/api/post/accept` 또는 `/api/post/reject`. `ApplyMemberModal.tsx`, `LoginRequiredModal.tsx`는 `isOpen`/`onClose` props를 받는 모달.

**관리자**: `components/Admin/` — `Dashboard.tsx`가 recharts로 `/api/admin/chart*` 통계, `User.tsx`가 회원 목록/승인/차단(`/api/admin/memberAll`, `acceptUser`, `banUser`), `Post.tsx`가 게시글 상태 변경.

**스타일**: 컴포넌트별 일반 CSS 파일(`src/css/*.css`, `src/css/admin/*.css`)을 import하고, 폼/상세/마이페이지는 MUI(`TextField`, `Chip`, `Stack`, `Tabs` 등)를 섞어 씀. Tailwind는 devDependency에 있지만 설정 파일도 없고 실제로 쓰이지 않음. 글 작성 에디터는 `@toast-ui/react-editor`.

**언어**: UI 문구, 주석, 커밋 메시지 모두 한국어.
