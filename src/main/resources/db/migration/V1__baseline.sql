-- 기준선: Flyway 도입 직전 엔티티로 Hibernate(ddl-auto)가 만들던 스키마.
-- ddl-auto로 테이블이 이미 만들어진 로컬 개발 DB에는 실행하지 않고 baseline으로 적용 표시만 한다(spring.flyway.baseline-on-migrate).
-- 빈 DB(CI, 첫 배포, 새 로컬 환경)에서 실행된다. 이후 스키마 변경은 V2부터 새 파일로 추가하고 이 파일은 고치지 않는다.

CREATE TABLE `member_profile` (
  `profile_id` bigint NOT NULL AUTO_INCREMENT,
  `img_url` varchar(255) DEFAULT NULL,
  `introduce` varchar(255) DEFAULT NULL,
  `preferred_position` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`profile_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `member` (
  `member_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `del_yn` char(1) NOT NULL DEFAULT 'N',
  `email` varchar(50) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `nickname` varchar(255) DEFAULT NULL,
  `password` varchar(100) DEFAULT NULL,
  `provider` varchar(255) DEFAULT NULL,
  `username` varchar(50) NOT NULL,
  `profile_id` bigint DEFAULT NULL,
  PRIMARY KEY (`member_id`),
  UNIQUE KEY `UKgc3jmn7c2abyo3wf6syln5t2i` (`username`),
  UNIQUE KEY `UK52psa23ioy6c74qam4i7p2aen` (`profile_id`),
  CONSTRAINT `FKi5sgf3h2nfnd3f0rj53phsoaw` FOREIGN KEY (`profile_id`) REFERENCES `member_profile` (`profile_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `member_roles` (
  `member_id` bigint NOT NULL,
  `role` varchar(255) DEFAULT NULL,
  KEY `FKet63dfllh4o5qa9qwm7f5kx9x` (`member_id`),
  CONSTRAINT `FKet63dfllh4o5qa9qwm7f5kx9x` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `member_stack` (
  `profile_id` bigint NOT NULL,
  `tech_stack` varchar(255) DEFAULT NULL,
  KEY `FKcpi9b35lxu1ky96o850597psq` (`profile_id`),
  CONSTRAINT `FKcpi9b35lxu1ky96o850597psq` FOREIGN KEY (`profile_id`) REFERENCES `member_profile` (`profile_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `notification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `actor` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `post_category` varchar(255) DEFAULT NULL,
  `post_id` bigint DEFAULT NULL,
  `post_title` varchar(255) DEFAULT NULL,
  `read_at` datetime(6) DEFAULT NULL,
  `receiver` varchar(50) NOT NULL,
  `role` varchar(255) DEFAULT NULL,
  `type` varchar(10) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notification_receiver_id` (`receiver`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `post` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `author` varchar(255) DEFAULT NULL,
  `category` varchar(255) DEFAULT NULL,
  `content` text,
  `created` datetime(6) DEFAULT NULL,
  `del_yn` char(1) NOT NULL DEFAULT 'N',
  `end_date` datetime(6) DEFAULT NULL,
  `last_modified` datetime(6) GENERATED ALWAYS AS (coalesce(`updated`,`created`)) VIRTUAL,
  `like_count` int DEFAULT NULL,
  `nickname` varchar(255) DEFAULT NULL,
  `post_status` varchar(255) DEFAULT NULL,
  `project_type` varchar(255) DEFAULT NULL,
  `region` varchar(255) DEFAULT NULL,
  `start_date` datetime(6) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `updated` datetime(6) DEFAULT NULL,
  `view_count` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_post_like_count` (`like_count`,`id`),
  KEY `idx_post_last_modified` (`last_modified`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `post_like` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created` datetime(6) DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `post_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6b8s3f8oiog3im43ddg35bxnn` (`post_id`,`member_id`),
  KEY `FKqjxwr6kkv6pw2e4pwy4yktxyk` (`member_id`),
  CONSTRAINT `FKj7iy0k7n3d0vkh8o7ibjna884` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`),
  CONSTRAINT `FKqjxwr6kkv6pw2e4pwy4yktxyk` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `post_position` (
  `position_id` bigint NOT NULL AUTO_INCREMENT,
  `count` int DEFAULT NULL,
  `role` varchar(255) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `post_id` bigint DEFAULT NULL,
  PRIMARY KEY (`position_id`),
  KEY `FKcty2y176n7an26cxuu48vhc0m` (`post_id`),
  CONSTRAINT `FKcty2y176n7an26cxuu48vhc0m` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `post_position_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `accepted_at` datetime(6) DEFAULT NULL,
  `applied_at` datetime(6) DEFAULT NULL,
  `rejected_at` datetime(6) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `post_position_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_position_member` (`post_position_id`,`member_id`),
  KEY `FKtqr57u3v9y2sk0vl31etqaq83` (`member_id`),
  CONSTRAINT `FKmqgyd9p4nikltgtoej2gmyp40` FOREIGN KEY (`post_position_id`) REFERENCES `post_position` (`position_id`),
  CONSTRAINT `FKtqr57u3v9y2sk0vl31etqaq83` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `post_stack` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tech_stack` varchar(255) DEFAULT NULL,
  `post_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK2vyxbyodku59xwvjd184lvjpp` (`post_id`),
  CONSTRAINT `FK2vyxbyodku59xwvjd184lvjpp` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
