package org.example.project.post.entity;

import java.util.List;

/**
 * 제출된 답변 하나. 질문의 라벨·타입·선택지를 제출 시점 그대로 복사해 둔다.
 * 작성자가 나중에 질문을 고치거나 지워도 이미 낸 신청서는 제출 당시 모습 그대로 보인다.
 */
public record ApplyAnswer(String questionKey, String label, String type, List<String> options,
                          List<String> values, List<ApplyFile> files) {
}
