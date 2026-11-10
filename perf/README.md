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

## 1. 응답에서 신청자 목록 제거 (`52f6ba7`)

보안 문제(원인 4)를 고친 커밋이지만 목록 조회에도 영향이 있어 같은 조건으로 다시 쟀다. 결과: [`results/after-1-hide-applicants-queries.tsv`](results/after-1-hide-applicants-queries.tsv)

| 시나리오 | 요청당 SQL (전 → 후) | 응답 크기 (전 → 후) |
|---|---:|---:|
| S1_latest | 36 → **12** | 6,384B → 5,146B |
| S2_popular | 24 → **12** | 5,908B → 5,475B |
| S3_category_status | 26 → **12** | 5,298B → 4,772B |
| S4_region_position | 29 → **12** | 6,293B → 5,453B |
| S5_keyword_hit | 29 → **12** | 5,964B → 5,173B |
| S7_stack | 34 → **12** | 6,489B → 5,387B |
| S8_deep_page | 23 → **12** | 5,759B → 5,351B |

남은 12개는 목록 1 + 건수 1 + 글마다 기술스택·포지션 2개 × 5건이다. 응답 시간은 원인 1(조인 후 DISTINCT)이 대부분이라 그대로다.

## 2. 들어온 조건만 쿼리에 넣고, 포지션·스택은 EXISTS로 (`PostSearchSpec`)

원인 1을 고쳤다. 고정 JPQL을 Spring Data JPA `Specification`으로 바꿔 값이 있는 조건만 WHERE에 넣고,
포지션·기술스택 조건은 조인 대신 `EXISTS` 서브쿼리로 걸러 글이 중복되지 않게 했다. 그래서 `DISTINCT`가 필요 없어졌다.
정렬 방식(`COALESCE(updated, created)`)은 이 단계에서 바꾸지 않았다. 같은 페이지 안 순서가 흔들리지 않게 `id DESC`만 덧붙였다.

- QueryDSL 대신 `Specification`을 쓴 이유: 이미 쓰는 spring-data-jpa에 들어 있어 의존성·코드 생성 설정이 필요 없고, 조건 8개 정도의 동적 쿼리는 충분히 표현된다.
- 바꾸기 전에 [`PostSearchTest`](../src/test/java/org/example/project/PostSearchTest.java)로 기존 쿼리의 필터·정렬·중복 제거 동작을 먼저 고정하고, 바꾼 뒤 같은 테스트를 통과시켰다. 시나리오별 결과 건수도 기준선과 같다.

결과: [`results/after-2-spec-latency.json`](results/after-2-spec-latency.json), [`results/after-2-spec-queries.tsv`](results/after-2-spec-queries.tsv)

| 시나리오 | 기준선 중앙값 | 이번 중앙값 | 요청당 SQL |
|---|---:|---:|---:|
| S1_latest | 55.5초 | **1.2초** | 12 |
| S2_popular | 51.5초 | **1.2초** | 12 |
| S3_category_status | 31.2초 | **1.6초** | 12 |
| S4_region_position | 2.1초 | **1.8초** | 12 |
| S5_keyword_hit | 13.0초 | **3.8초** | 12 |
| S6_keyword_miss | 2.0초 | **2.0초** | 1 |
| S7_stack | 6.3초 | **2.6초** | 12 |
| S8_deep_page | 60.6초 | **7.0초** | 12 |

남은 비용(S1 기준 EXPLAIN ANALYZE): 필터가 없으면 여전히 100만 건 전체를 읽어 식(`COALESCE`)으로 정렬한다(약 1.4~1.6초). 건수 쿼리는 `count(id)`가 되어 0.2초.
키워드 검색(S5·S6)은 `LIKE '%키워드%'`로 본문을 훑는 비용이 그대로 남아 있다.

```
-> Limit: 5 row(s)                                        (actual time=1631)
  -> Sort: coalesce(updated, created) DESC, id DESC        (limit input to 5 rows)
    -> Table scan on post                                 (rows=1e+6, actual time=1444)
```

## 3. 정렬 기준을 인덱스로 (`last_modified` 생성 컬럼)

원인 2를 고쳤다. 최신순 기준인 `COALESCE(updated, created)`를 DB가 계산하는 생성 컬럼(`last_modified`, virtual)으로 만들고
`(last_modified, id)` 인덱스를, 인기순에는 `(like_count, id)` 인덱스를 걸었다. 정렬 인덱스를 거꾸로 읽다가 5건이 차면 멈춘다.

- 앱이 값을 채우는 일반 컬럼 대신 생성 컬럼을 쓴 이유: 기존 글 100만 건을 따로 채울(backfill) 필요가 없고, 수정 API를 새로 만들어도 값이 어긋날 수 없다.
- MySQL 함수 인덱스(`INDEX ((COALESCE(...)))`) 대신 생성 컬럼을 쓴 이유: 이 프로젝트는 마이그레이션 도구 없이 `ddl-auto`로 스키마를 만들어서, 엔티티의 `@Index`로 선언할 수 있어야 운영 DB에도 그대로 반영된다.

결과: [`results/after-3-sort-index-latency.json`](results/after-3-sort-index-latency.json), [`results/after-3-sort-index-queries.tsv`](results/after-3-sort-index-queries.tsv)

| 시나리오 | 기준선 | 2단계 | 이번 |
|---|---:|---:|---:|
| S1_latest | 55.5초 | 1.2초 | **90ms** |
| S2_popular | 51.5초 | 1.2초 | **95ms** |
| S3_category_status | 31.2초 | 1.6초 | **631ms** |
| S4_region_position | 2.1초 | 1.8초 | **870ms** |
| S5_keyword_hit | 13.0초 | 3.8초 | **1.9초** |
| S6_keyword_miss | 2.0초 | 2.0초 | **3.9초** |
| S7_stack | 6.3초 | 2.6초 | **2.9초** |
| S8_deep_page | 60.6초 | 7.0초 | **148ms** |

필터 없는 목록 쿼리는 1.6초 → 4ms, 50,001번째 행부터 읽는 깊은 페이지도 0.9초 → 0.1초가 됐다.

```
-> Limit: 5 row(s)                                            (actual time=4.5)
  -> Index scan on post using idx_post_last_modified (reverse) (rows=5)
```

**나빠진 것: 결과가 없는 키워드 검색(S6) 2.0초 → 3.9초.**
옵티마이저가 "정렬 인덱스를 거꾸로 읽다 보면 금방 5건을 찾는다"고 보고 이 인덱스를 고른다.
그런데 키워드가 아무 글에도 없으면 100만 건을 인덱스 순서대로 하나씩 본문을 찾아가며 끝까지 읽는다(4.3초).
같은 쿼리에서 인덱스를 막으면 테이블을 순서대로 다 읽고 정렬해 2.2초다. 결과가 많은 키워드(S5)는 앞쪽에서 5건이 금방 차서 빨라졌다.
키워드 조건이 자기 인덱스를 타지 못하는 것이 근본 원인이라 다음 단계(원인 5)에서 함께 다룬다.

남은 비용: 필터가 있을 때의 **건수 쿼리**. 목록은 앞쪽 몇 건만 읽고 끝나지만,
`count`는 조건에 맞는 글을 전부 세야 해서 테이블을 다 읽는다(S3 프로젝트+모집중 0.75초, S4 지역+포지션 1.6초).
