-- 목록 검색 성능 측정용 더미 데이터 (project_perf 스키마 전용, 운영·개발 DB에 실행 금지)
-- 실행: docker exec -i teamhaeyo-mysql mysql --default-character-set=utf8mb4 -uroot -plocal project_perf < perf/seed.sql
--   (문자셋을 빼면 컨테이너 클라이언트 기본값 latin1로 해석돼 한글이 이중 인코딩되고, 앱에서 '서울특별시' 같은 검색이 0건이 된다)
-- 테이블은 앱을 project_perf로 한 번 기동해 ddl-auto로 만든 뒤 실행한다.
-- RAND(seed)와 n 기반 식만 써서 몇 번을 돌려도 같은 데이터가 나온다.
--
-- 규모: 회원 1만, 글 100만(프로젝트 60% / 스터디 40%), 포지션 약 160만, 기술스택 약 150만, 신청 약 100만

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE post_position_member;
TRUNCATE post_like;
TRUNCATE post_stack;
TRUNCATE post_position;
TRUNCATE post;
TRUNCATE member_roles;
TRUNCATE member_stack;
TRUNCATE member;
TRUNCATE member_profile;
SET FOREIGN_KEY_CHECKS = 1;

-- 1 ~ 1,000,000 번호표
DROP TABLE IF EXISTS seq;
CREATE TABLE seq (n INT PRIMARY KEY);
INSERT INTO seq
SELECT a.d + b.d * 10 + c.d * 100 + d.d * 1000 + e.d * 10000 + f.d * 100000 + 1
FROM (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a,
     (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b,
     (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c,
     (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d,
     (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) e,
     (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) f;

-- 회원 1만
INSERT INTO member (member_id, username, email, nickname, name, password, del_yn, created_at)
SELECT n, CONCAT('user', n), CONCAT('user', n, '@perf.test'), CONCAT('닉네임', n), CONCAT('회원', n), '{noop}perf', 'N',
       TIMESTAMP('2026-11-01') - INTERVAL n MINUTE
FROM seq WHERE n <= 10000;

-- 글 100만. id가 클수록 최근 글(약 90초 간격, 약 2.9년치)
INSERT INTO post (id, author, nickname, category, status, post_status, project_type, region,
                  title, content, created, updated, start_date, end_date, like_count, view_count, del_yn)
SELECT n, author, nickname, category, status,
       -- PostStatusScheduler와 같은 규칙. 날짜와 어긋나게 넣으면 앱 기동 시 스케줄러가 수십만 건을 고쳐 측정이 흔들린다.
       CASE WHEN DATE(created) + INTERVAL 7 DAY > NOW() THEN 'SCHEDULED'
            WHEN DATE(created) + INTERVAL 67 DAY < NOW() THEN 'END'
            ELSE 'IN_PROGRESS' END,
       project_type, region, title,
       CONCAT(title, '\n\n', body), created,
       IF(n % 10 < 3, created + INTERVAL (n % 1440) MINUTE, NULL),
       DATE(created) + INTERVAL 7 DAY, DATE(created) + INTERVAL 67 DAY,
       FLOOR(POW(RAND(n), 4) * 300), FLOOR(RAND(n + 7) * 2000),
       IF(n % 200 = 0, 'Y', 'N')
FROM (
  SELECT n,
         CONCAT('user', 1 + n % 10000) AS author,
         CONCAT('닉네임', 1 + n % 10000) AS nickname,
         IF(n % 10 < 6, 'project', 'study') AS category,
         IF((n DIV 10) % 10 < 7, 'ING', 'END') AS status,
         ELT(1 + (n DIV 3) % 3, 'online', 'offline', 'both') AS project_type,
         IF((n DIV 3) % 3 = 0, 'nothing',
            ELT(1 + (n DIV 7) % 17, '서울특별시','부산광역시','대구광역시','인천광역시','광주광역시','대전광역시','울산광역시',
                '세종특별자치시','경기도','강원도','충청북도','충청남도','전라북도','전라남도','경상북도','경상남도','제주특별자치도')) AS region,
         CONCAT(ELT(1 + n % 8, '[서울]', '[온라인]', '[주말]', '[평일 저녁]', '[초보 환영]', '[포트폴리오]', '[취준]', '[현직자]'), ' ',
                ELT(1 + (n DIV 8) % 10, 'Spring Boot', 'React', 'Next.js', 'Kotlin', 'Flutter', 'Node.js', 'Django', 'Vue', '알고리즘', 'CS 면접'), ' ',
                ELT(1 + (n DIV 80) % 6, '사이드 프로젝트 팀원 모집', '스터디원 구합니다', '함께 하실 분', '프로젝트 멤버 모집', '스터디 모집합니다', '팀원 찾아요')) AS title,
         REPEAT(CONCAT(ELT(1 + (n DIV 5) % 6,
                '매주 한 번 온라인으로 모여 진행 상황을 공유하고 코드 리뷰를 합니다. ',
                '배포까지 완주하는 것을 목표로 하고 있으며 역할은 첫 회의에서 정합니다. ',
                '경험이 많지 않아도 괜찮고 꾸준히 참여할 수 있는 분이면 좋겠습니다. ',
                '기획과 디자인은 어느 정도 정리되어 있어서 바로 개발을 시작할 수 있습니다. ',
                '깃허브 이슈와 PR로 협업하고 디스코드로 소통할 예정입니다. ',
                '서로 질문하고 배우는 분위기를 지향하며 마감 후 회고를 진행합니다. ')), 3 + n % 4) AS body,
         TIMESTAMP('2026-11-01') - INTERVAL (1000001 - n) * 90 SECOND AS created
  FROM seq
) t;

-- 프로젝트 글 기술스택 1~4개 (같은 글 안에서 중복 없음)
INSERT INTO post_stack (post_id, tech_stack)
SELECT s.n,
       ELT(1 + (s.n + k.k * 7) % 20, 'Java', 'Spring Boot', 'JPA', 'MySQL', 'Redis', 'React', 'TypeScript', 'Next.js', 'Node.js', 'Docker',
           'AWS', 'Kotlin', 'Python', 'Django', 'Vue', 'Flutter', 'Figma', 'Git', 'GraphQL', 'Go')
FROM seq s
JOIN (SELECT 1 k UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) k ON k.k <= 1 + s.n % 4
WHERE s.n % 10 < 6;

-- 프로젝트 글 포지션 1~3개, 스터디 글은 모집 인원 1행(role = totalCount)
INSERT INTO post_position (post_id, role, count, status)
SELECT s.n, ELT(1 + (s.n + k.k * 2) % 5, 'backend', 'frontend', 'pm', 'design', 'etc'), 1 + (s.n + k.k) % 3,
       IF((s.n + k.k) % 5 = 0, 'END', 'ING')
FROM seq s
JOIN (SELECT 1 k UNION ALL SELECT 2 UNION ALL SELECT 3) k ON k.k <= 1 + s.n % 3
WHERE s.n % 10 < 6;

INSERT INTO post_position (post_id, role, count, status)
SELECT n, 'totalCount', 2 + n % 5, IF(n % 5 = 0, 'END', 'ING')
FROM seq WHERE n % 10 >= 6;

-- 포지션의 30%에 신청 1~3건 (포지션 안에서 회원 중복 없음)
INSERT INTO post_position_member (post_position_id, member_id, status, applied_at)
SELECT pp.position_id, 1 + (pp.position_id * 31 + k.k * 977) % 10000,
       ELT(1 + (pp.position_id + k.k) % 3, 'APPLY', 'ACCEPT', 'REJECT'),
       TIMESTAMP('2026-11-01') - INTERVAL pp.position_id SECOND
FROM post_position pp
JOIN (SELECT 1 k UNION ALL SELECT 2 UNION ALL SELECT 3) k ON k.k <= 1 + pp.position_id % 3
WHERE pp.position_id % 10 < 3;

DROP TABLE seq;

ANALYZE TABLE member, post, post_stack, post_position, post_position_member;

SELECT 'member' t, COUNT(*) FROM member
UNION ALL SELECT 'post', COUNT(*) FROM post
UNION ALL SELECT 'post_stack', COUNT(*) FROM post_stack
UNION ALL SELECT 'post_position', COUNT(*) FROM post_position
UNION ALL SELECT 'post_position_member', COUNT(*) FROM post_position_member;
