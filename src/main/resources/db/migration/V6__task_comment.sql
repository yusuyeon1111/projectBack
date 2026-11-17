-- 업무 댓글. 업무가 지워지면 댓글도 같이 지운다(업무의 일부라 따로 남길 이유가 없다)
CREATE TABLE task_comment (
  id                BIGINT      NOT NULL AUTO_INCREMENT,
  task_id           BIGINT      NOT NULL,
  author_member_id  BIGINT      NOT NULL,
  content           TEXT        NOT NULL,
  created_at        DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_task_comment_task (task_id, id),
  CONSTRAINT fk_task_comment_task   FOREIGN KEY (task_id) REFERENCES task (id) ON DELETE CASCADE,
  CONSTRAINT fk_task_comment_author FOREIGN KEY (author_member_id) REFERENCES member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
