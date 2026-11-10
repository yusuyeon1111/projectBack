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

목록 검색 성능 측정은 `perf/`에 있다(더미 데이터 `seed.sql`, k6 시나리오, 결과 기록 `perf/README.md`). 측정은 `project_perf` 스키마와 8081 포트의 별도 앱으로 하고, 개선할 때마다 같은 시나리오로 다시 재서 README에 전후를 남긴다.

### 실행에 필요한 환경변수

`application.yml`은 모든 비밀값을 환경변수로 읽는다. 프로필 분리(`application-local.yml` 등)가 없으므로 로컬에서도 전부 지정해야 한다:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `JWT_SECRET`(Base64 인코딩된 HMAC 키), `KAKAO_CLIENT_ID`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`, `AWS_S3_BUCKET_NAME`.

- Redis는 `usessl: true`로 고정되어 있다(ElastiCache 기준). 로컬 `docker-compose.yml`(`src/main/java/org/example/project/` 아래에 있음)의 Redis는 SSL이 없으므로 로컬 실행 시 `RedisRepositoryConfig`의 `usessl` 값을 `false`로 바꿔야 연결된다.
- JPA `ddl-auto: update` — 엔티티를 바꾸면 기동 시 스키마가 자동 변경된다. 다이얼렉트는 MariaDB로 지정되어 있지만 드라이버는 MySQL이다.
- 테스트(`contextLoads`, `SecurityAccessTest`, `PositionConcurrencyTest`)는 `@SpringBootTest`라서 실제 DB/Redis 연결이 필요하다. `contextLoads`는 `JWT_SECRET`도 실제 길이의 키여야 한다(`openssl rand -base64 32`). `PositionConcurrencyTest`는 동시 커밋을 재현하려고 롤백 없이 DB에 쓰고 직접 정리한다. 환경변수 없이 `./gradlew build`를 돌리면 테스트 단계에서 실패하므로 컴파일만 확인할 땐 `compileJava` 또는 `build -x test`를 쓴다.

## 아키텍처

패키지는 도메인별(`member`, `post`, `admin`)로 나뉘고 각각 `controller / dto / entity / repository / service` 구조다. (`post.repositoty`는 오타지만 실제 패키지명이므로 그대로 쓴다.) `admin`은 controller와 dto만 있고 서비스 로직은 `MemberService`/`PostService`에 함께 들어 있다.

### 인증 흐름 (JWT + Redis)

- `SecurityConfig`: stateless 세션, CSRF/formLogin 비활성. 공개 API(회원가입·로그인·아이디/이메일 중복 체크, 게시글 목록·비로그인 상세, OAuth2 경로)만 `permitAll`이고 나머지는 인증 필요, `/api/admin/**`은 `ROLE_ADMIN`. 미인증 요청은 401(`HttpStatusEntryPoint`). **사용자 식별은 항상 컨트롤러의 `Principal`(토큰 subject = 회원 username)로 하고, 요청 본문·파라미터의 username은 믿지 않는다.** 글 수정·삭제와 신청 승인·거절은 `PostService.findOwnedPost` 등으로 작성자 본인인지 확인한다. 이 규칙은 `SecurityAccessTest`가 검증한다.
- `JwtAuthenticationFilter`: `Authorization: Bearer <token>` 헤더를 파싱해 `SecurityContext`에 인증을 채운다. 토큰 자체가 Redis 키로 존재하면(`logout` 값) 블랙리스트로 간주해 인증을 건너뛴다. `/api/post/list`는 필터를 아예 통과시킨다.
- `JwtTokenProvider`: access 2시간 / refresh 7일, HS256. `roles` 클레임에 권한을 콤마로 저장한다.
- Redis 키 규약: `RT:{username}` → refresh token, `{accessToken}` → `"logout"` (남은 만료시간만큼 TTL), `BAN:{username}` → 관리자 차단 표시(access token 수명만큼 TTL, 필터가 이 키가 있으면 기존 토큰도 인증하지 않음).
- 일반 로그인은 `MemberService.signIn` → `AuthenticationManager` → `CustomerUserDetailsService`. 카카오 로그인은 `CustomerOAuth2UserService` → `OAuth2SuccessHandler`에서 회원 자동 생성 후, 회원 username과 DB 권한으로 토큰을 만들어 **쿼리스트링에 실어** 프론트 `/success`로 리다이렉트한다. (OAuth2 인증 객체의 이름은 카카오 숫자 id라서 그대로 쓰면 안 된다.)
- 계정 비활성화는 `Member.delYn = 'Y'`로 표현한다(soft delete). 로그인·OAuth 양쪽에서 이 값을 검사한다.

### 도메인 모델

- `Member` ↔ `MemberProfile`(1:1, cascade ALL) + `member_roles` ElementCollection.
- `Post` → `PostPosition`(모집 포지션, 1:N) → `PostPositionMember`(지원 내역, status: `APPLY`/`ACCEPT`/`REJECT`, (포지션, 회원) 유니크 제약으로 중복 신청 방지). `Post` → `PostStack`, `PostLike`.
- 신청 승인·거절(`PositionService`)은 `PostRepository.findByApplicationIdForUpdate`로 글 행을 비관적 락으로 잠근 뒤 정원을 확인한다. **이 락은 트랜잭션의 첫 조회여야 한다.** MySQL REPEATABLE READ는 첫 일반 조회 시점의 스냅샷을 읽으므로, 잠금 전에 조회가 있으면 다른 트랜잭션이 커밋한 승인 수를 못 봐서 정원을 넘긴다. 정원 초과·이미 처리된 신청은 `IllegalStateException` → 409.
- `Post.postStatus`(`SCHEDULED`/`IN_PROGRESS`/`END`)는 `PostStatusScheduler`가 매일 00:00 및 앱 기동 시 `startDate`/`endDate` 기준으로 일괄 갱신한다. `Post.delYn`은 관리자 숨김 처리용.
- 통계/차트 쿼리(`countMemberPerMonth` 등)는 리포지토리에 JPQL로 작성되어 `Object[]`를 반환하고 서비스에서 DTO로 매핑한다.

### 예외 처리

`GlobalExceptionHandler`가 `RuntimeException` → 403, `IllegalStateException` → 409, `BadCredentialsException` → 401로 변환한다(본문은 `{error: 메시지}`, 프론트는 `auth.ts`의 `errorMessage`로 꺼낸다). 서비스 계층은 커스텀 예외 없이 메시지 담은 `RuntimeException`을 던지는 관례다. `MemberController`에는 별도 `@ExceptionHandler`(400)가 있어 해당 컨트롤러에서는 그것이 우선한다.

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
