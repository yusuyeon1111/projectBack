# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 명령어

모든 명령은 `frontend/` 폴더 안에서 실행한다 (`cd frontend`).

Create React App(`react-scripts` 5) 기반. 별도 lint 스크립트 없음(CRA 내장 ESLint가 start/build 시 동작).

```bash
npm start                              # 개발 서버 (localhost:3000, /api → setupProxy.js 프록시)
npm run build                          # 프로덕션 빌드 → build/
npm test                               # Jest watch 모드
CI=true npm test                       # 단발 실행
npm test -- src/App.test.js            # 단일 파일
npm test -- -t "renders learn react"   # 단일 테스트 이름
```

`src/App.test.js`는 CRA 기본 템플릿 그대로라 현재 `App.js`와 맞지 않아 실패함. 실제 테스트 코드는 없음.

## 환경 변수

`.env`는 gitignore 대상이며 커밋되지 않음. 실행에 필요한 값:

- `REACT_APP_API_URL` — 백엔드 베이스 URL. 모든 axios 호출이 `${REACT_APP_API_URL}/api/...` 형태.
- `REACT_APP_KAKAO_API_KEY` — `public/index.html`의 Kakao Maps SDK `<script>`에 `%REACT_APP_KAKAO_API_KEY%`로 치환됨.

`src/setupProxy.js`는 `/api`를 EC2 백엔드(`http://3.36.157.46:8080`)로 프록시하지만, 코드가 절대 URL(`REACT_APP_API_URL`)을 쓰므로 `REACT_APP_API_URL`을 비워두거나 `/`로 둘 때만 프록시가 의미 있음.

## 아키텍처

스터디/프로젝트 팀원 모집 플랫폼 프론트엔드. 백엔드는 별도 Spring 서버(`/api/member`, `/api/post`, `/api/admin`).

**라우팅**: `src/index.js`가 진입점이자 라우트 테이블. `src/App.js`는 사용되지 않는 잔재(렌더되지 않음). 라우트는 세 그룹:
- `<Layout/>`(Header + Outlet) 아래: `/`, `/myPage`, `/postform`, `/studyform`, `/post/:postId`, `/study/:postId`
- 레이아웃 없음: `/signin`, `/membersignup`, `/oauth2/success`
- `<AdminLayout/>`(`components/Admin/Layout.jsx`) 아래: `/admin`

**인증**: 서버 측 세션 없음. 로그인(`SignIn.jsx`) 또는 OAuth2 콜백(`OAuth2Success.jsx`, 쿼리스트링으로 토큰 수신)이 `localStorage`에 `accessToken`, `refreshToken`, `username`, `role`을 저장. 각 컴포넌트가 직접 `localStorage.getItem("username")`을 읽고, 인증이 필요한 요청에만 `Authorization: Bearer` 헤더를 수동으로 붙임. axios 인스턴스/인터셉터 없음 — 새 API 호출을 추가할 때 기존 컴포넌트의 패턴을 그대로 따를 것. 관리자 판별은 `role === 'ROLE_ADMIN'`.

**게시글 = project / study 두 종류**: 같은 `/api/post/*` 엔드포인트를 쓰되 `category` 필드로 구분. 그래서 UI가 거의 복제된 쌍으로 존재함:
- `PostForm.jsx` (category `'project'`, 등록 후 `/post/:id`) ↔ `StudyForm.jsx` (category `'study'`, 등록 후 `/study/:id`)
- `PostView.jsx` ↔ `StudyView.jsx` (조회, 수정, 삭제, 좋아요, 지원)

한쪽을 고치면 다른 쪽도 같이 봐야 함. `Post.jsx`는 placeholder. `PostContext.js`(`PostProvider`/`usePost`)는 정의만 있고 어디서도 사용하지 않음.

**메인 페이지**: `Main.jsx`가 필터 상태(category, status, region, position, keyword, sortType, projectType, stackKeyword, page, size)를 들고 `/api/post/list`에 POST → `PostList.jsx`에 넘김. `KakaoMap.jsx`는 `hooks/useLocation.js`(geolocation, 실패 시 서울 기본 좌표)로 현재 위치를 받아 전역 `kakao.maps` 객체로 지도를 그림. 지역 목록은 `Main.jsx` 상수와 `src/data/region.json` 양쪽에 있음.

**지원/수락 흐름**: 게시글 상세에서 `/api/post/apply` → 작성자가 `MyPage.jsx`에서 `/api/post/accept` 또는 `/api/post/reject`. `ApplyMemberModal.jsx`, `LoginRequiredModal.jsx`는 `isOpen`/`onClose` props를 받는 모달이며 `index.js`에 라우트로도 등록되어 있음.

**관리자**: `components/Admin/` — `Dashboard.jsx`가 recharts로 `/api/admin/chart*` 통계, `User.jsx`가 회원 목록/승인/차단(`/api/admin/memberAll`, `acceptUser`, `banUser`), `Post.jsx`가 게시글 상태 변경.

**스타일**: 컴포넌트별 일반 CSS 파일(`src/css/*.css`, `src/css/admin/*.css`)을 import하고, 폼/상세/마이페이지는 MUI(`TextField`, `Chip`, `Stack`, `Tabs` 등)를 섞어 씀. Tailwind는 devDependency에 있지만 설정 파일도 없고 실제로 쓰이지 않음. 글 작성 에디터는 `@toast-ui/react-editor`.

**언어**: UI 문구, 주석, 커밋 메시지 모두 한국어.
