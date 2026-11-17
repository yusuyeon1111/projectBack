package org.example.project.post.entity;

/** 신청서 첨부 파일. key는 S3 객체 키(apply/{username}/{uuid}), name은 사용자가 올린 원래 파일명. */
public record ApplyFile(String key, String name, long size) {
}
