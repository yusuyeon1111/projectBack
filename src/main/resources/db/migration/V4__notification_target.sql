-- 알림이 글 말고 팀도 가리킬 수 있게 대상(target_type, target_id)을 둔다. 팀 알림 종류 이름이 10자를 넘어 type도 늘린다.
ALTER TABLE notification
  MODIFY COLUMN type VARCHAR(30) NOT NULL,
  ADD COLUMN target_type VARCHAR(10) NULL,
  ADD COLUMN target_id BIGINT NULL;

-- 지금까지의 알림은 모두 글 알림이다(로컬 개발 데이터)
UPDATE notification SET target_type = 'POST', target_id = post_id;

ALTER TABLE notification MODIFY COLUMN target_type VARCHAR(10) NOT NULL;
