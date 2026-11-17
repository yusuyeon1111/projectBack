package org.example.project.post.entity;

import java.util.List;

/**
 * 신청서 질문 하나. 글 작성자가 만들고 apply_form.questions에 JSON으로 저장된다.
 * description은 제목 아래 보이는 보충 설명(선택)이다.
 * key는 질문을 고쳐도 바뀌지 않는 식별자라서 답변은 라벨이 아니라 key로 질문과 짝지어진다.
 */
public record ApplyQuestion(String key, String type, String label, String description, boolean required, List<String> options) {

    /** TEXT·TEXTAREA·URL은 값 하나, SELECT·RADIO는 선택지 하나, CHECKBOX는 선택지 여러 개, FILE은 첨부 파일 */
    public static final List<String> TYPES = List.of("TEXT", "TEXTAREA", "URL", "SELECT", "RADIO", "CHECKBOX", "FILE");

    public boolean hasOptions() {
        return "SELECT".equals(type) || "RADIO".equals(type) || "CHECKBOX".equals(type);
    }
}
