-- 팀(모집글의 첫 승인 때 생긴다)과 팀원, 신청 상태 이력.
-- V2는 feat/apply-form(apply_form 테이블)을 위해 비워 둔다. Flyway는 번호 사이가 비어도 순서대로 실행한다.

CREATE TABLE team (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  post_id           BIGINT       NULL,
  name              VARCHAR(100) NOT NULL,
  leader_member_id  BIGINT       NOT NULL,   -- 칸이 하나라서 팀장은 항상 한 명이다
  status            VARCHAR(20)  NOT NULL,
  created_at        DATETIME(6)  NOT NULL,
  started_at        DATETIME(6)  NULL,
  ended_at          DATETIME(6)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_team_post (post_id),         -- 글 하나에 팀 하나
  KEY idx_team_leader (leader_member_id),
  CONSTRAINT fk_team_post   FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE SET NULL,
  CONSTRAINT fk_team_leader FOREIGN KEY (leader_member_id) REFERENCES member (member_id),
  CONSTRAINT ck_team_status CHECK (status IN ('FORMING','ACTIVE','COMPLETED','DISBANDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE team_member (
  id                     BIGINT      NOT NULL AUTO_INCREMENT,
  team_id                BIGINT      NOT NULL,
  member_id              BIGINT      NOT NULL,
  source_application_id  BIGINT      NULL,   -- 팀장은 신청 없이 들어오므로 NULL
  position_role          VARCHAR(30) NULL,
  status                 VARCHAR(10) NOT NULL,
  joined_at              DATETIME(6) NOT NULL,
  left_at                DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_team_member (team_id, member_id),                -- 한 사람은 한 팀에 한 행
  UNIQUE KEY uk_team_member_application (source_application_id), -- 신청 하나로 멤버십 하나
  KEY idx_team_member_member (member_id, status),                -- 내 팀 목록
  CONSTRAINT fk_tm_team        FOREIGN KEY (team_id) REFERENCES team (id),
  CONSTRAINT fk_tm_member      FOREIGN KEY (member_id) REFERENCES member (member_id),
  CONSTRAINT fk_tm_application FOREIGN KEY (source_application_id) REFERENCES post_position_member (id) ON DELETE SET NULL,
  CONSTRAINT ck_tm_status CHECK (status IN ('ACTIVE','LEFT','KICKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 신청 행은 현재 상태만 담고, 상태가 바뀔 때마다 여기에 한 줄씩 쌓는다(append-only)
CREATE TABLE application_event (
  id               BIGINT      NOT NULL AUTO_INCREMENT,
  application_id   BIGINT      NULL,          -- 글 삭제로 신청이 지워지면 NULL
  status           VARCHAR(10) NOT NULL,
  actor_member_id  BIGINT      NULL,
  created_at       DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_app_event_application (application_id, id),
  CONSTRAINT fk_app_event_application FOREIGN KEY (application_id) REFERENCES post_position_member (id) ON DELETE SET NULL,
  CONSTRAINT fk_app_event_actor       FOREIGN KEY (actor_member_id) REFERENCES member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
