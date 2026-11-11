package org.example.project.post.service;

import lombok.RequiredArgsConstructor;
import org.example.project.post.entity.ApplyForm;
import org.example.project.post.entity.ApplyQuestion;
import org.example.project.post.entity.Post;
import org.example.project.post.repositoty.ApplyFormRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

/** 글에 붙는 신청서 질문을 저장·조회한다. 질문은 글 작성자가 보낸 값이라 저장 전에 모양을 검사한다. */
@Service
@RequiredArgsConstructor
public class ApplyFormService {

    static final int MAX_QUESTIONS = 20;
    static final int MAX_LABEL_LENGTH = 100;
    static final int MAX_OPTIONS = 20;
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9-]{1,36}");

    private final ApplyFormRepository applyFormRepository;

    @Transactional(readOnly = true)
    public List<ApplyQuestion> getQuestions(Long postId) {
        return applyFormRepository.findByPostId(postId).map(ApplyForm::getQuestions).orElse(List.of());
    }

    /**
     * 질문 목록을 통째로 바꾼다. null이면 손대지 않고, 빈 목록이면 신청서를 없앤다(바로 신청되는 글로 돌아간다).
     * 이미 들어온 신청서는 답변에 질문을 복사해 두었으므로 질문을 고쳐도 영향을 받지 않는다.
     */
    @Transactional
    public void replaceQuestions(Post post, List<ApplyQuestion> questions) {
        if (questions == null) {
            return;
        }
        List<ApplyQuestion> normalized = normalize(questions);
        Optional<ApplyForm> existing = applyFormRepository.findByPostId(post.getId());
        if (normalized.isEmpty()) {
            existing.ifPresent(applyFormRepository::delete);
            return;
        }
        existing.ifPresentOrElse(
                form -> form.setQuestions(normalized),
                () -> applyFormRepository.save(new ApplyForm(post, normalized)));
    }

    /** 질문 모양을 검사하고 공백을 다듬는다. 새 질문에는 key를 붙인다. 잘못된 입력은 IllegalArgumentException(400). */
    static List<ApplyQuestion> normalize(List<ApplyQuestion> questions) {
        if (questions.size() > MAX_QUESTIONS) {
            throw new IllegalArgumentException("질문은 " + MAX_QUESTIONS + "개까지 만들 수 있습니다.");
        }
        Set<String> keys = new HashSet<>();
        List<ApplyQuestion> result = new ArrayList<>();
        for (ApplyQuestion q : questions) {
            if (q == null || !ApplyQuestion.TYPES.contains(q.type())) {
                throw new IllegalArgumentException("알 수 없는 질문 형식입니다.");
            }
            String label = trim(q.label());
            if (label.isEmpty() || label.length() > MAX_LABEL_LENGTH) {
                throw new IllegalArgumentException("질문 내용은 1~" + MAX_LABEL_LENGTH + "자로 입력해주세요.");
            }
            String key = trim(q.key()).isEmpty() ? UUID.randomUUID().toString() : q.key();
            if (!KEY_PATTERN.matcher(key).matches() || !keys.add(key)) {
                throw new IllegalArgumentException("질문 식별자가 올바르지 않습니다.");
            }
            List<String> options = List.of();
            if (q.hasOptions()) {
                options = q.options() == null ? List.of()
                        : q.options().stream().map(ApplyFormService::trim).filter(o -> !o.isEmpty()).distinct().toList();
                if (options.isEmpty() || options.size() > MAX_OPTIONS
                        || options.stream().anyMatch(o -> o.length() > MAX_LABEL_LENGTH)) {
                    throw new IllegalArgumentException("'" + label + "' 질문의 선택지는 1~" + MAX_OPTIONS + "개, 각 "
                            + MAX_LABEL_LENGTH + "자 이내로 입력해주세요.");
                }
            }
            result.add(new ApplyQuestion(key, q.type(), label, q.required(), options));
        }
        return result;
    }

    static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
