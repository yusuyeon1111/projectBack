# 모집글 목록 검색 성능 측정

메인 화면의 모집글 목록(`POST /api/post/list`)을 데이터가 많은 상태에서 측정하고, 개선할 때마다 같은 조건으로 다시 잰다.
팀해요에는 실제 트래픽이 없으므로 **모든 수치는 아래 더미 데이터와 로컬 환경 기준**이다.

## 측정 환경

| 항목 | 값 |
|---|---|
| 데이터 | 회원 1만, 글 100만(프로젝트 60% / 스터디 40%), 포지션 160만, 기술스택 150만, 신청 96만 — [`seed.sql`](seed.sql) |
| DB | MySQL 8.0 (Docker, `project_perf` 스키마), `innodb_buffer_pool_size` 512MB |
| 앱 | Spring Boot 3.4 / Java 17, 로컬 1대, Hibernate 통계 로그 켬 |
| 장비 | MacBook (16GB), Docker Desktop VM 8GB |
| 요청 | 프론트(`Main.tsx`)가 보내는 형태 그대로: 빈 필터는 `""`, 페이지 크기 5 |

## 실행 방법

```bash
# 1) 측정용 스키마를 만들고 앱을 한 번 띄워 테이블 생성 (ddl-auto)
docker exec teamhaeyo-mysql mysql -uroot -plocal -e "CREATE DATABASE project_perf DEFAULT CHARACTER SET utf8mb4"
DB_URL='jdbc:mysql://localhost:3306/project_perf?...' ./gradlew bootRun --args='--server.port=8081 --spring.jpa.properties.hibernate.generate_statistics=true ...'

# 2) 더미 데이터 적재 (약 12분)
docker exec -i teamhaeyo-mysql mysql --default-character-set=utf8mb4 -uroot -plocal project_perf < perf/seed.sql

# 3) 측정 (앱 기동 직후에는 PostStatusScheduler가 1분 넘게 돌므로 끝난 뒤에 잰다)
k6 run -e WARMUP=1 -e RUNS=3 -e OUT=perf/results/<이름>.json perf/list-search.k6.js
```

요청당 SQL 개수는 Hibernate 통계 로그(`Session Metrics`의 `executing N JDBC statements`)에서 센다.

## 시나리오

| 이름 | 조건 | 결과 건수 |
|---|---|---|
| S1_latest | 필터 없음, 최신순 (첫 화면) | 1,000,000 |
| S2_popular | 인기순 | 1,000,000 |
| S3_category_status | 프로젝트 + 모집중 | 420,000 |
| S4_region_position | 서울 + 백엔드 포지션 | 7,846 |
| S5_keyword_hit | 키워드 `React` (결과 많음) | 100,000 |
| S6_keyword_miss | 결과 없는 키워드 (전체를 훑는 경우) | 0 |
| S7_stack | 기술스택 `Redis` | 100,000 |
| S8_deep_page | 필터 없음, 10,001번째 페이지 | 1,000,000 |

## 0. 기준선 (개선 전, 2026-11-10)

1명이 순서대로 호출, 시나리오마다 워밍업 1회 후 3회 측정. 결과: [`results/baseline-latency-s1-s5.json`](results/baseline-latency-s1-s5.json), [`results/baseline-latency-s5-s8.json`](results/baseline-latency-s5-s8.json)

| 시나리오 | 중앙값 | 평균 | 최대 | 요청당 SQL | 응답 크기 |
|---|---:|---:|---:|---:|---:|
| S1_latest | **55.5초** | 55.3초 | 57.1초 | 36 | 6,384B |
| S2_popular | **51.5초** | 53.1초 | 57.2초 | 24 | 5,908B |
| S3_category_status | **31.2초** | 31.8초 | 33.1초 | 26 | 5,298B |
| S4_region_position | **2.1초** | 2.2초 | 2.2초 | 29 | 6,293B |
| S5_keyword_hit | **13.0초** | 13.2초 | 13.8초 | 29 | 5,964B |
| S6_keyword_miss | **2.0초** | 2.0초 | 2.1초 | 1 | 65B |
| S7_stack | **6.3초** | 6.5초 | 8.1초 | 34 | 6,489B |
| S8_deep_page | **60.6초** | 73.4초 | 103.4초 | 23 | 5,759B |

요청당 SQL 개수와 응답 크기: [`results/baseline-queries.tsv`](results/baseline-queries.tsv)

동시 부하 테스트(`MODE=load`)는 기준선에서 생략했다. 요청 하나가 수십 초 걸리고 요청마다 수백 MB짜리 임시 테이블을 만들어, 동시에 보내면 처리량이 사실상 0이고 로컬 DB 컨테이너가 먼저 버티지 못한다. 개선 후에 처리량을 잰다.

### 원인

**1) 필터가 없어도 항상 조인하고 중복 제거한다** — 가장 큰 원인

모든 조건을 `(:x IS NULL OR :x = '' OR ...)` 형태로 쿼리 하나에 넣어 두어, 포지션·기술스택 필터를 쓰지 않아도 두 테이블을 항상 LEFT JOIN 한다.
글 100만 건이 조인으로 340만 행이 되고, 이를 `DISTINCT`로 되돌리려고 **본문(TEXT)까지 포함한 340만 행을 임시 테이블에 넣어 중복을 제거**한 뒤, 정렬해서 5건을 자른다.

```
S1 목록 쿼리 EXPLAIN ANALYZE (요약)
-> Limit: 5 row(s)                                   (actual time=39274)
  -> Sort: coalesce(updated, created) DESC
    -> Table scan on <temporary>                      (rows=1e+6)
      -> Temporary table with deduplication           (actual time=38305)   ← 여기서 약 30초
        -> Nested loop left join                      (rows=3.4e+6)
          -> Nested loop left join                    (rows=1.6e+6)
            -> Table scan on post                     (rows=1e+6)
            -> Index lookup on post_position (post_id)
          -> Covering index lookup on post_stack (post_id)
```

전체 건수를 세는 count 쿼리도 같은 340만 행을 조인해 `count(distinct id)`를 구한다(약 6초).

**2) 정렬 기준이 `COALESCE(updated, created)`라 인덱스를 쓸 수 없다**

식으로 정렬하므로 100만 건 전체를 정렬해야 첫 5건을 알 수 있다. 인기순(`like_count`)도 인덱스가 없어 같다.

**3) 목록 DTO가 글마다 연관 데이터를 따로 조회한다 (N+1)**

`PostResponseDto`가 글마다 기술스택·포지션을 조회하고, 포지션마다 신청 목록, 신청마다 회원(비밀번호 컬럼 포함)을 조회한다.
5건짜리 페이지 하나에 SQL이 23~36개 나간다. 목록 화면은 이 중 신청 목록을 쓰지 않는다.

**4) 공개 목록 응답에 신청자 정보가 포함된다** (성능과 별개인 버그)

로그인 없이 호출하는 목록 API가 포지션별 신청자의 회원 id와 아이디를 내려준다.

**5) 키워드 검색이 `LIKE '%키워드%'`로 본문 전체를 훑는다**

결과가 없는 키워드(S6)도 2초가 걸린다. 다만 지금은 1)이 훨씬 커서 상대적으로 작게 보인다.

### 같이 확인한 것: 기동 시 상태 갱신 스케줄러

`PostStatusScheduler`는 앱 기동 직후(메인 스레드)와 매일 0시에 진행 상태를 갱신한다.
글 100만 건(진행 중 54,720건) 기준으로, 바뀌는 행이 하나도 없는데도 기동 후 **약 87초** 동안 돌았다.

| 단계 | 값 |
|---|---|
| 종료 안 된 글 전체 조회 (`findByPostStatusNot`) | 4.4초, 54,720건을 메모리로 로드 |
| `saveAll`로 다시 저장 | SQL 164,160개, 엔티티 224,352개·컬렉션 251,712개 flush |

조회한 엔티티가 트랜잭션 밖에서 분리(detached)된 상태라 `saveAll`이 건마다 `merge`(재조회)하고, cascade로 포지션·스택·좋아요 컬렉션까지 다시 읽는다.
이 문제는 서버 2대 전환 작업(중복 실행)과 함께 다룬다.
