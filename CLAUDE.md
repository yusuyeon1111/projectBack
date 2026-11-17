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

- Redis 장애 정책은 fail-closed다. 필터가 토큰 폐기 여부를 확인하지 못하면 인증하지 않고 비로그인으로 넘기며, 로그인이 필요한 API는 401 대신 503을 준다(401이면 프론트가 로그아웃시킨다). Lettuce 명령·연결 타임아웃은 1초(`RedisRepositoryConfig`, 기본 60초면 요청이 멈춘다). `RedisOutageTest`(연결 거부), `RedisHangTest`(응답 없음)가 검증한다.
- Redis는 `usessl: true`로 고정되어 있다(ElastiCache 기준). 로컬 `docker-compose.yml`(`src/main/java/org/example/project/` 아래에 있음)의 Redis는 SSL이 없으므로 로컬 실행 시 `RedisRepositoryConfig`의 `usessl` 값을 `false`로 바꿔야 연결된다.
- 스키마는 Flyway(`src/main/resources/db/migration`)가 관리하고 JPA는 `ddl-auto: validate`라서 엔티티와 테이블이 어긋나면 기동이 멈춘다. **엔티티를 바꾸면 `V{다음 번호}__설명.sql`을 함께 추가한다.** 이미 적용된 파일은 고치지 않는다(체크섬 불일치로 기동 실패). `V1__baseline.sql`은 Flyway 도입 전 `ddl-auto`가 만들던 스키마이고, ddl-auto로 테이블이 이미 만들어진 로컬 개발 DB에는 `baseline-on-migrate`로 실행 없이 적용 표시만 한다(운영 DB는 아직 없어서 첫 배포는 빈 DB에 V1부터 실행된다). 다이얼렉트는 MariaDB로 지정되어 있지만 드라이버는 MySQL이다.
- OSIV(`spring.jpa.open-in-view`)는 꺼져 있고, `WebMvcConfig`가 같은 인터셉터를 알림 구독(`/api/notifications/subscribe`)만 빼고 직접 건다. SSE 요청이 열려 있는 동안 DB 커넥션을 쥐지 않게 하려는 것이다. 다른 API는 여전히 OSIV에 기대어 지연 로딩할 수 있다.
- 테스트(`contextLoads`, `SecurityAccessTest`, `PositionConcurrencyTest`, `PostSearchTest`, `Notification*Test`, `SseTicketTest`, `TeamFormationTest`, `TeamMembershipTest`, `TaskTest`, `ChatTest`, `ApplyFormTest`)는 `@SpringBootTest`라서 실제 DB/Redis 연결이 필요하다. `NotificationStreamTest`는 임의 포트로 서버를 띄워 실제 HTTP로 SSE를 읽는다. `contextLoads`는 `JWT_SECRET`도 실제 길이의 키여야 한다(`openssl rand -base64 32`). `PositionConcurrencyTest`·`TeamFormationTest`·`TeamMembershipTest`·`TaskTest`·`ChatTest`는 동시 커밋을 재현하려고 롤백 없이 DB에 쓰고 직접 정리한다. 승인을 실행하는 테스트는 회원을 지우기 전에 `TeamCleanup.deleteFor`로 팀·이력을 먼저 지워야 한다(회원 FK). 환경변수 없이 `./gradlew build`를 돌리면 테스트 단계에서 실패하므로 컴파일만 확인할 땐 `compileJava` 또는 `build -x test`를 쓴다.

## 아키텍처

패키지는 도메인별(`member`, `post`, `admin`)로 나뉘고 각각 `controller / dto / entity / repository / service` 구조다. (`post.repositoty`는 오타지만 실제 패키지명이므로 그대로 쓴다.) `admin`은 controller와 dto만 있고 서비스 로직은 `MemberService`/`PostService`에 함께 들어 있다.

### 인증 흐름 (JWT + Redis)

- `SecurityConfig`: stateless 세션, CSRF/formLogin 비활성. 공개 API(회원가입·로그인·아이디/이메일 중복 체크, 게시글 목록·비로그인 상세, OAuth2 경로)만 `permitAll`이고 나머지는 인증 필요, `/api/admin/**`은 `ROLE_ADMIN`. 미인증 요청은 401(`HttpStatusEntryPoint`). **사용자 식별은 항상 컨트롤러의 `Principal`(토큰 subject = 회원 username)로 하고, 요청 본문·파라미터의 username은 믿지 않는다.** 글 수정·삭제와 신청 승인·거절은 `PostService.findOwnedPost` 등으로 작성자 본인인지 확인한다. 이 규칙은 `SecurityAccessTest`가 검증한다.
- `JwtAuthenticationFilter`: `Authorization: Bearer <token>` 헤더를 파싱해 `SecurityContext`에 인증을 채운다. 토큰 자체가 Redis 키로 존재하면(`logout` 값) 블랙리스트로 간주해 인증을 건너뛴다. `/api/post/list`는 필터를 아예 통과시킨다.
- `JwtTokenProvider`: access 2시간 / refresh 7일, HS256. access token은 `roles`(콤마 구분)와 `sid`, refresh token은 `sid`·`jti`·`typ=refresh`를 담고 권한은 담지 않는다. 필터는 refresh token을 인증에 쓰지 않는다.
- Refresh Token 회전(`RefreshTokenService`): 로그인(일반·카카오)마다 기기 세션 `sid`를 만들고, `POST /api/member/reissue`가 새 토큰 쌍을 주며 이전 refresh token을 무효로 한다. 이미 바뀐 토큰이 다시 오면 재사용(탈취)으로 보고 그 기기 세션을 지우고, 그 기기로 발급된 access token도 `RVK:{sid}`로 막는다. 탭 여러 개의 동시 재발급을 위해 바꾼 뒤 10초 동안은 같은 이전 토큰에 방금 만든 쌍을 그대로 준다. 비교·교체는 Lua 스크립트 하나로 원자 실행한다. 권한·차단 여부는 재발급 때 DB에서 읽는다. `RefreshTokenTest`가 검증한다.
- Redis 키 규약: `RTS:{username}` → 해시(필드 `sid` → 현재 refresh token의 `jti`, 7일 TTL, 로그아웃은 필드 삭제, 차단은 키 삭제), `RTG:{이전 jti}` → 재발급 유예용 토큰 쌍 JSON(10초), `RVK:{sid}` → 재사용이 감지된 기기의 access token 차단(access token 수명만큼 TTL, 필터가 `BAN:`과 함께 EXISTS 한 번으로 확인), `{accessToken}` → `"logout"` (남은 만료시간만큼 TTL), `BAN:{username}` → 관리자 차단 표시(access token 수명만큼 TTL, 필터가 이 키가 있으면 기존 토큰도 인증하지 않음), `SSE:{ticket}` → 알림 구독용 1회용 티켓(30초, 쓰면 바로 삭제).
- 일반 로그인은 `MemberService.signIn` → `AuthenticationManager` → `CustomerUserDetailsService`. 카카오 로그인은 `CustomerOAuth2UserService` → `OAuth2SuccessHandler`에서 회원 자동 생성 후, 회원 username과 DB 권한으로 토큰을 만들어 **쿼리스트링에 실어** 프론트 `/success`로 리다이렉트한다. (OAuth2 인증 객체의 이름은 카카오 숫자 id라서 그대로 쓰면 안 된다.)
- 계정 비활성화는 `Member.delYn = 'Y'`로 표현한다(soft delete). 로그인·OAuth 양쪽에서 이 값을 검사한다.

### 도메인 모델

- `Member` ↔ `MemberProfile`(1:1, cascade ALL) + `member_roles` ElementCollection.
- `Post` → `PostPosition`(모집 포지션, 1:N) → `PostPositionMember`(지원 내역, status: `APPLY`/`ACCEPT`/`REJECT`, (포지션, 회원) 유니크 제약으로 중복 신청 방지). `Post` → `PostStack`, `PostLike`.
- 신청 승인·거절(`PositionService`)은 `PostRepository.findByApplicationIdForUpdate`로 글 행을 비관적 락으로 잠근 뒤 정원을 확인한다. **이 락은 트랜잭션의 첫 조회여야 한다.** MySQL REPEATABLE READ는 첫 일반 조회 시점의 스냅샷을 읽으므로, 잠금 전에 조회가 있으면 다른 트랜잭션이 커밋한 승인 수를 못 봐서 정원을 넘긴다. 정원 초과·이미 처리된 신청은 `IllegalStateException` → 409.
- `Post.postStatus`(`SCHEDULED`/`IN_PROGRESS`/`END`)는 `PostStatusScheduler`가 매일 00:00 및 앱 기동 시 `startDate`/`endDate` 기준으로 일괄 갱신한다. `Post.delYn`은 관리자 숨김 처리용.
- 통계/차트 쿼리(`countMemberPerMonth` 등)는 리포지토리에 JPQL로 작성되어 `Object[]`를 반환하고 서비스에서 DTO로 매핑한다.

### 팀 (팀해요 2.0, 설계: `docs/design/team-2.0.md`)

- 글의 **첫 승인 때 팀이 자동으로 생긴다**(`TeamService.joinOnAccept`, 승인과 같은 트랜잭션·같은 글 잠금 안). 팀장은 첫 승인자가 아니라 **글 작성자**(`member.username = post.author`)이고, `team.leader_member_id` 한 칸에 둬서 팀장이 둘일 수 없다. 작성자 회원을 못 찾으면 409로 승인까지 롤백한다.
- 이미 그 팀의 활성 팀원이거나 퇴출된 사람은 같은 글의 다른 포지션에 신청·승인할 수 없다(409). 팀이 있는(해산되지 않은) 글은 삭제할 수 없다(409).
- 팀 상태는 `FORMING` → `ACTIVE`(시작) → `COMPLETED`, 또는 `DISBANDED`(해산)다. 완료·해산된 팀은 읽기 전용이라 쓰기는 409다. 상태 변경·위임·퇴출은 팀장만 한다(403).
- 탈퇴·퇴출하면 `team_member`는 `LEFT`/`KICKED`, 신청은 `WITHDRAWN`/`EXPELLED`가 되고, 포지션과 글을 다시 모집 중(`ING`)으로 연다. 팀장은 위임한 뒤에만 탈퇴할 수 있다. 탈퇴한 사람만 `POST /api/post/reapply`로 같은 신청 행을 `APPLY`로 되돌릴 수 있고(조건부 UPDATE), 다시 승인되면 같은 `team_member` 행이 살아난다.
- 팀 쓰기 작업은 승인과 같은 순서(글 행 → 팀 행)로 잠근다(`TeamService.lockTeam`). 잠그기 전에 `team.post_id`를 읽어 스냅샷이 먼저 잡히므로, 그 뒤 상태 확인은 잠금 읽기(`findForUpdate…`)나 조건부 UPDATE로 한다. `TeamMembershipTest`가 동시 탈퇴·승인, 동시 위임을 검증한다.
- 팀 조회(`/api/teams/my`, `/api/teams/{id}`)는 팀원만 된다. 관리자도 팀원이 아니면 404다(`NoSuchElementException` → 404).
- 신청 상태가 바뀔 때마다 `application_event`에 한 줄씩 남긴다(신청 행은 현재 상태만 담는다).
- 업무(`task`, `TaskService`, `/api/teams/{id}/tasks`)는 팀원만 보고 쓴다(아니면 404, 다른 팀 업무 id도 404). 수정은 칸 전체를 바꾸며 읽은 `version`을 함께 보내야 하고, 다르거나 저장 직전에 누가 고쳤으면(`@Version`) 409다. 상태 변경(`PATCH .../status {from, to}`)은 조건부 UPDATE라 연타해도 한 번만 바뀌고 version도 올린다. 삭제는 팀장·만든 사람만(403). 업무 쓰기는 팀 행을 공유 잠금(`findByIdForShare`)으로 먼저 잡아 완료·해산·퇴출과 줄을 선다. 탈퇴·퇴출하면 그 사람의 담당 업무가 비워진다. `TaskTest`가 검증한다.
- 업무 댓글(`task_comment`, `.../tasks/{taskId}/comments`)은 팀원이 쓰고 팀장·쓴 사람만 지운다(403). 업무를 지우면 DB의 ON DELETE CASCADE로 함께 지워진다. 담당자가 새로 정해지면 그 사람에게 `TASK_ASSIGNED`, 댓글이 달리면 업무를 만든 사람과 담당자에게 `TASK_COMMENT` 팀 알림을 보낸다(행동한 본인 제외, `actor`에 표시 이름).
- 업무·댓글·채팅의 팀원 확인은 `TeamAccess`(`requireMember` 읽기, `requireWritable` 쓰기: 팀 행 공유 잠금 → 팀원 → 끝난 팀 409)로 서비스 첫 줄에서 한다.
- 팀 채팅(`chat_message`, `ChatService`, `/api/teams/{id}/messages`)은 MySQL이 원본이고, 커밋 후(AFTER_COMMIT) 그 시점의 활성 팀원(보낸 사람 포함)에게 알림과 같은 회원별 SSE 연결로 `event: chat`을 보낸다. **채팅 이벤트에는 SSE `id`를 붙이지 않는다**(Last-Event-ID는 알림 재전송 기준). 빠진 메시지는 `?after={id}&limit=100`(오름차순), 이전 대화는 `?before={id}&limit=50`(내림차순)으로 받는다. `ChatTest`, `NotificationStreamTest`가 검증한다.
- 업무 변경과 멤버 변동·팀 상태 변경은 `team_activity`에 한 줄씩 남고(`detail` JSON에 당시 제목 등), `GET /api/teams/{id}/activities?cursor=&taskId=`로 최신순 30개씩 읽는다.
- `team`·`team_member`·`application_event`가 글·신청을 가리키는 FK는 `ON DELETE SET NULL`이다. 글 삭제는 JPA cascade로 신청 행을 지우므로 이 FK들이 막지 않아야 한다.

### 알림 (SSE)

- 포지션 신청 → 글 작성자, 승인·거절 → 신청자에게 알림을 만든다(`NotificationService`, `notification` 테이블). 글 제목·카테고리·역할은 만든 시점 값을 복사해 둔다. 팀 상태 변경·위임·탈퇴·퇴출도 `TEAM_*`, 업무 담당 지정·댓글은 `TASK_*` 알림을 만든다(`target_type = 'TEAM'`, `post_title`에 팀 이름). 프론트는 팀 알림을 마이페이지로 보낸다. 알림 행은 승인·거절과 같은 트랜잭션에서 저장되므로 409로 롤백되면 남지 않는다.
- 실시간 전송은 `NotificationCreatedEvent`를 `@TransactionalEventListener(AFTER_COMMIT)`로 받아 `SseEmitterRegistry`(회원별 열린 연결, 서버 메모리)로 보낸다. 커밋된 알림만 나간다. 연결이 서버 메모리에 있어 **서버가 한 대일 때만 맞다**(여러 대면 Redis Pub/Sub 필요).
- 구독 인증: `EventSource`는 헤더를 못 붙이므로 `POST /api/notifications/ticket`(Bearer)으로 1회용 티켓을 받고 `GET /api/notifications/subscribe?ticket=`로 연다. 구독 경로는 `permitAll`이고 컨트롤러가 티켓·`BAN:` 키를 확인한다.
- 재연결: 티켓이 1회용이라 브라우저 자동 재연결은 실패한다. 프론트(`useNotifications`)가 에러 시 직접 닫고 새 티켓 + `lastEventId` 쿼리로 다시 붙으면 서버가 그 이후 알림을 다시 보낸다. 로그아웃·차단 시 서버가 그 회원의 연결을 닫는다.

### 신청서 (서식)

- 글 작성자가 신청 때 받을 질문을 만든다. 질문은 `apply_form` 테이블(글당 1행, `questions` JSON)에 둔다. `Post` 컬럼이나 `Post` 쪽 연관으로 두지 않는다. 목록 API가 `Post`를 통째로 읽고, 주인이 아닌 쪽 1:1은 지연 로딩이 안 돼 목록 성능이 나빠진다. 글 삭제 시 DB의 ON DELETE CASCADE로 지워진다.
- 글 등록·수정 요청의 `applyQuestions`로 통째로 바꾼다(null이면 유지, 빈 목록이면 삭제 → 바로 신청되는 글). 질문마다 바뀌지 않는 `key`가 있다.
- 답변은 `post_position_member.answers`(JSON)에 질문의 라벨·타입·선택지를 복사해 저장한다. 그래서 질문을 고쳐도 이미 낸 신청서는 제출 당시 모습이고, 질문 버전 이력은 따로 없다.
- 답변은 `ApplyFormService.buildAnswers`가 서버에서 다시 검사한다(필수, 선택지, 길이, http(s) 주소, 모르는 키). 실패는 `IllegalArgumentException` → 400.
- 첨부: 키는 서버가 `apply/{memberId}/{uuid}`로 만들고, 제출 시 본인 경로·업로드 여부·10MB·3개·pdf/jpg/png/zip을 S3 메타데이터로 확인한다. 받을 때는 5분짜리 presigned GET. 제한값은 프론트 `APPLY_FILE_LIMIT`와 맞춰야 한다.
- 답변·첨부는 글 작성자와 신청자 본인만 본다(`PositionService.findReadableApplication`). 관리자도 못 본다. `ApplyFormTest`가 검증한다(S3만 가짜).
- 프론트: 편집기 `ApplyFormBuilder`, 입력 폼 `ApplyFormRenderer`, 신청 창 `ApplyModal`, 읽기 `ApplicationAnswersDialog`. S3 PUT은 axios 인터셉터의 Authorization 헤더를 피하려고 `fetch`로 한다.

### 예외 처리

`GlobalExceptionHandler`가 Redis·DB 연결 실패와 명령 시간 초과(`DataAccessResourceFailureException`, `QueryTimeoutException`) → 503, `RuntimeException` → 403, `IllegalStateException` → 409, `IllegalArgumentException` → 400, `BadCredentialsException` → 401로 변환한다(본문은 `{error: 메시지}`, 프론트는 `auth.ts`의 `errorMessage`로 꺼낸다). 서비스 계층은 커스텀 예외 없이 메시지 담은 `RuntimeException`을 던지는 관례다. `MemberController`에는 별도 `@ExceptionHandler`(400)가 있어 해당 컨트롤러에서는 그것이 우선한다.

### S3 업로드

서버가 파일을 받지 않는다. 프로필 이미지는 `GET /api/member/presigned-url`이 PUT용 presigned URL과 공개 URL을 주고, 버킷명이 컨트롤러에 하드코딩되어 있다. 신청서 첨부 파일은 `ApplyFileStorage`가 `AWS_S3_BUCKET_NAME` 버킷을 쓰고 공개 URL을 주지 않는다(위 신청서 참고).

## 커밋 메시지 규칙

포트폴리오 저장소라서 면접관이 커밋 로그를 읽는다는 전제로 쓴다. diff에 이미 "무엇을"이 있으니 메시지에는 "왜"를 적는다.

- 형식은 `<type>(<scope>): <제목>`, 빈 줄, 본문 순서다. type은 `feat`, `fix`, `perf`, `refactor`, `test`, `docs`, `ci`, `chore`, `style` 중 하나다. 측정해서 빨라진 변경은 `refactor`가 아니라 `perf`로 쓴다. scope는 `back` 또는 `front`이고, 양쪽을 다 바꾸면 생략한다. 제목과 본문은 한국어로 쓴다.
- 제목은 50자 안팎으로, 의도가 드러나게 쓴다. `Update SecurityConfig.java`처럼 파일 이름만 적은 제목은 쓰지 않는다.
- 판단이 들어간 커밋에는 본문이 필수다. 버그 수정, 설계 변경, 의존성이나 설정 변경이 여기에 해당한다. 본문은 문제, 원인, 해결 순서로 쓴다. 단순한 변경이면 본문을 생략해도 된다.
- 커밋 하나에는 의도를 하나만 담는다. 기능 추가, 버그 수정, 리팩터링이 섞이면 나눠서 커밋한다. 작업 중에 발견한 버그는 별도의 `fix:` 커밋으로 분리해서 로그에 드러나게 한다.
- README를 고칠 때도 내용을 적는다. 예: `docs: ERD 이미지 교체`
- 이미 push한 이력은 rebase로 다시 쓰지 않는다.

### 브랜치와 PR

GitHub Flow를 쓴다. `main`은 항상 테스트가 통과하는 상태로 두고 직접 커밋하지 않는다.

- 작업마다 `main`에서 브랜치를 딴다. 이름은 `<type>/<작업>` 형태의 소문자·하이픈이다. 예: `feat/sse-notification`, `fix/osiv-connection-leak`, `perf/post-list-search`
- PR 제목은 커밋 제목과 같은 형식으로 쓰고, 본문은 `.github/pull_request_template.md`를 따른다. 성능 작업은 전후 측정값을 본문에 남긴다.
- CI(`.github/workflows/ci.yml`)가 통과해야 병합한다.
- 병합은 **Merge commit**으로 한다. Squash하면 작업 중 분리해 둔 `fix:` 커밋이 기능 커밋 하나에 묻혀 로그에서 사라진다.
- 병합한 브랜치는 지운다.

```
fix(back): 로그아웃 후에도 access token이 유효하던 문제 수정

만료 전 토큰은 서명만 검증하므로 로그아웃해도 계속 통과했다.
로그아웃 시 토큰을 남은 만료시간 TTL로 Redis에 저장하고,
JwtAuthenticationFilter에서 해당 키가 있으면 인증을 건너뛴다.
```
