-- 신청서: 글마다 질문 목록 하나, 신청마다 답변(질문 내용을 복사해 둔다).
-- 글이 지워지면 질문도 같이 지운다. 질문은 글의 일부라 따로 남길 이유가 없다.

CREATE TABLE apply_form (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  post_id    BIGINT NOT NULL,
  questions  JSON   NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_apply_form_post (post_id),
  CONSTRAINT fk_apply_form_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE post_position_member ADD COLUMN answers JSON NULL;
