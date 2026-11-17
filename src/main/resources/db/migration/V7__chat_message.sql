-- 팀 채팅. MySQL이 메시지의 유일한 원본이고 SSE는 전달 수단이다(설계 §6.3).
-- 전달이 실패해도 원본이 여기 있으므로 클라이언트가 after 조회로 빠진 메시지를 메운다
CREATE TABLE chat_message (
  id                BIGINT      NOT NULL AUTO_INCREMENT,
  team_id           BIGINT      NOT NULL,
  sender_member_id  BIGINT      NOT NULL,
  content           TEXT        NOT NULL,
  created_at        DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_chat_team (team_id, id),
  CONSTRAINT fk_chat_team   FOREIGN KEY (team_id) REFERENCES team (id),
  CONSTRAINT fk_chat_sender FOREIGN KEY (sender_member_id) REFERENCES member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
