package org.example.project.post.dto;

import org.example.project.post.entity.ApplyFile;

import java.util.List;

/** 신청 시 질문 하나에 대한 답. FILE 질문은 files에, 나머지는 values에 담는다. 파일 크기는 서버가 S3에서 다시 읽는다. */
public record ApplyAnswerRequest(String questionKey, List<String> values, List<ApplyFile> files) {
}
