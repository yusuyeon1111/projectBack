-- 팀 업무와 팀 활동 기록.
-- 업무 수정은 version으로 낙관적 락을 건다(동시 수정 시 나중 요청이 409). 상태 변경은 조건부 UPDATE로 하되 version도 올린다.
-- 담당자는 활성 팀원이어야 하고, 탈퇴·퇴출되면 서비스가 비운다.

CREATE TABLE task (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  team_id             BIGINT       NOT NULL,
  title               VARCHAR(100) NOT NULL,
  description         TEXT         NULL,
  status              VARCHAR(20)  NOT NULL,
  assignee_member_id  BIGINT       NULL,
  creator_member_id   BIGINT       NOT NULL,
  due_date            DATE         NULL,
  version             BIGINT       NOT NULL,
  created_at          DATETIME(6)  NOT NULL,
  updated_at          DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_task_team (team_id, id),
  KEY idx_task_assignee (assignee_member_id),
  CONSTRAINT fk_task_team     FOREIGN KEY (team_id) REFERENCES team (id),
  CONSTRAINT fk_task_assignee FOREIGN KEY (assignee_member_id) REFERENCES member (member_id),
  CONSTRAINT fk_task_creator  FOREIGN KEY (creator_member_id) REFERENCES member (member_id),
  CONSTRAINT ck_task_status CHECK (status IN ('TODO','IN_PROGRESS','DONE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 팀 안에서 일어난 일(업무, 멤버 변동, 팀 상태). 지워진 업무의 기록도 남도록 target은 FK 없이 id만 둔다
CREATE TABLE team_activity (
  id               BIGINT      NOT NULL AUTO_INCREMENT,
  team_id          BIGINT      NOT NULL,
  actor_member_id  BIGINT      NOT NULL,
  type             VARCHAR(30) NOT NULL,
  target_type      VARCHAR(10) NOT NULL,
  target_id        BIGINT      NULL,
  detail           JSON        NULL,
  created_at       DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_activity_team (team_id, id),
  KEY idx_activity_target (team_id, target_type, target_id, id),
  CONSTRAINT fk_activity_team  FOREIGN KEY (team_id) REFERENCES team (id),
  CONSTRAINT fk_activity_actor FOREIGN KEY (actor_member_id) REFERENCES member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
