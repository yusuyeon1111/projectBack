# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

"팀해요" — 스터디/프로젝트 팀원 모집 플랫폼의 **백엔드**(Spring Boot 3.4, Java 17, Gradle 8.12). 프론트엔드(React, CRA)는 이 저장소의 `frontend/` 폴더에 있으며(자체 CLAUDE.md 있음, 원래 별도 저장소 github.com/yusuyeon1111/FrontProject), 운영 도메인은 `https://teamhaeyo.kro.kr` 이다. CORS 허용 오리진과 OAuth2 redirect URI가 이 도메인으로 하드코딩되어 있어(`WebMvcConfig`, `application.yml`, `OAuth2SuccessHandler`) 로컬에서 프론트와 붙일 때는 이 값들을 바꿔야 한다.

## 명령어

```bash
./gradlew build                 # 컴파일 + 테스트
./gradlew bootRun               # 서버 실행 (환경변수 필요, 아래 참고)
./gradlew test                  # 전체 테스트
./gradlew test --tests 'org.example.project.ProjectApplicationTests'            # 클래스 단위
./gradlew test --tests 'org.example.project.ProjectApplicationTests.contextLoads' # 메서드 단위
./gradlew compileJava           # 빠른 컴파일 확인만
```

린트/포맷터 설정은 없다.

### 실행에 필요한 환경변수

`application.yml`은 모든 비밀값을 환경변수로 읽는다. 프로필 분리(`application-local.yml` 등)가 없으므로 로컬에서도 전부 지정해야 한다:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `JWT_SECRET`(Base64 인코딩된 HMAC 키), `KAKAO_CLIENT_ID`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`, `AWS_S3_BUCKET_NAME`.

- Redis는 `usessl: true`로 고정되어 있다(ElastiCache 기준). 로컬 `docker-compose.yml`(`src/main/java/org/example/project/` 아래에 있음)의 Redis는 SSL이 없으므로 로컬 실행 시 `RedisRepositoryConfig`의 `usessl` 값을 `false`로 바꿔야 연결된다.
- JPA `ddl-auto: update` — 엔티티를 바꾸면 기동 시 스키마가 자동 변경된다. 다이얼렉트는 MariaDB로 지정되어 있지만 드라이버는 MySQL이다.
- 유일한 테스트 `ProjectApplicationTests.contextLoads`는 `@SpringBootTest`라서 실제 DB/Redis 연결이 필요하다. 환경변수 없이 `./gradlew build`를 돌리면 테스트 단계에서 실패하므로 컴파일만 확인할 땐 `compileJava` 또는 `build -x test`를 쓴다.

## 아키텍처

패키지는 도메인별(`member`, `post`, `admin`)로 나뉘고 각각 `controller / dto / entity / repository / service` 구조다. (`post.repositoty`는 오타지만 실제 패키지명이므로 그대로 쓴다.) `admin`은 controller와 dto만 있고 서비스 로직은 `MemberService`/`PostService`에 함께 들어 있다.

### 인증 흐름 (JWT + Redis)

- `SecurityConfig`: stateless 세션, CSRF/formLogin 비활성. `/api/post/**`, `/api/member/**`, `/oauth2/**`는 `permitAll`, `/api/admin/**`은 `ROLE_ADMIN`. 즉 post/member 엔드포인트는 **Spring Security 레벨에서 인증을 강제하지 않으며** 대부분 `username`을 요청 파라미터/바디로 받아 동작한다. 새 엔드포인트를 추가할 때 이 점을 의식할 것.
- `JwtAuthenticationFilter`: `Authorization: Bearer <token>` 헤더를 파싱해 `SecurityContext`에 인증을 채운다. 토큰 자체가 Redis 키로 존재하면(`logout` 값) 블랙리스트로 간주해 인증을 건너뛴다. `/api/post/list`는 필터를 아예 통과시킨다.
- `JwtTokenProvider`: access 2시간 / refresh 7일, HS256. `roles` 클레임에 권한을 콤마로 저장한다.
- Redis 키 규약: `RT:{username}` → refresh token, `{accessToken}` → `"logout"` (남은 만료시간만큼 TTL).
- 일반 로그인은 `MemberService.signIn` → `AuthenticationManager` → `CustomerUserDetailsService`. 카카오 로그인은 `CustomerOAuth2UserService` → `OAuth2SuccessHandler`에서 회원 자동 생성 후 토큰을 **쿼리스트링에 실어** 프론트 `/success`로 리다이렉트한다.
- 계정 비활성화는 `Member.delYn = 'Y'`로 표현한다(soft delete). 로그인·OAuth 양쪽에서 이 값을 검사한다.

### 도메인 모델

- `Member` ↔ `MemberProfile`(1:1, cascade ALL) + `member_roles` ElementCollection.
- `Post` → `PostPosition`(모집 포지션, 1:N) → `PostPositionMember`(지원 내역, status: `apply`/`accept`/`reject`). `Post` → `PostStack`, `PostLike`.
- `Post.postStatus`(`SCHEDULED`/`IN_PROGRESS`/`END`)는 `PostStatusScheduler`가 매일 00:00 및 앱 기동 시 `startDate`/`endDate` 기준으로 일괄 갱신한다. `Post.delYn`은 관리자 숨김 처리용.
- 통계/차트 쿼리(`countMemberPerMonth` 등)는 리포지토리에 JPQL로 작성되어 `Object[]`를 반환하고 서비스에서 DTO로 매핑한다.

### 예외 처리

`GlobalExceptionHandler`가 `RuntimeException` → 403, `BadCredentialsException` → 401로 변환한다. 서비스 계층은 커스텀 예외 없이 메시지 담은 `RuntimeException`을 던지는 관례다. `MemberController`에는 별도 `@ExceptionHandler`(400)가 있어 해당 컨트롤러에서는 그것이 우선한다.

### S3 업로드

서버가 파일을 받지 않는다. `GET /api/member/presigned-url`이 PUT용 presigned URL을 발급하고 프론트가 직접 S3에 올린다. 버킷명이 컨트롤러에 하드코딩되어 있다(`AWS_S3_BUCKET_NAME` 환경변수는 선언만 되어 있고 미사용).

## 커밋 메시지 규칙

포트폴리오 저장소라서 면접관이 커밋 로그를 읽는다는 전제로 쓴다. diff에 이미 "무엇을"이 있으니 메시지에는 "왜"를 적는다.

- 형식은 `<type>(<scope>): <제목>`, 빈 줄, 본문 순서다. type은 `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `style` 중 하나다. scope는 `back` 또는 `front`이고, 양쪽을 다 바꾸면 생략한다. 제목과 본문은 한국어로 쓴다.
- 제목은 50자 안팎으로, 의도가 드러나게 쓴다. `Update SecurityConfig.java`처럼 파일 이름만 적은 제목은 쓰지 않는다.
- 판단이 들어간 커밋에는 본문이 필수다. 버그 수정, 설계 변경, 의존성이나 설정 변경이 여기에 해당한다. 본문은 문제, 원인, 해결 순서로 쓴다. 단순한 변경이면 본문을 생략해도 된다.
- 커밋 하나에는 의도를 하나만 담는다. 기능 추가, 버그 수정, 리팩터링이 섞이면 나눠서 커밋한다. 작업 중에 발견한 버그는 별도의 `fix:` 커밋으로 분리해서 로그에 드러나게 한다.
- README를 고칠 때도 내용을 적는다. 예: `docs: ERD 이미지 교체`
- 이미 push한 이력은 rebase로 다시 쓰지 않는다.

```
fix(back): 로그아웃 후에도 access token이 유효하던 문제 수정

만료 전 토큰은 서명만 검증하므로 로그아웃해도 계속 통과했다.
로그아웃 시 토큰을 남은 만료시간 TTL로 Redis에 저장하고,
JwtAuthenticationFilter에서 해당 키가 있으면 인증을 건너뛴다.
```
