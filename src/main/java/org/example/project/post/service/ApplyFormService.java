package org.example.project.post.service;

import lombok.RequiredArgsConstructor;
import org.example.project.post.dto.ApplyAnswerRequest;
import org.example.project.post.entity.ApplyAnswer;
import org.example.project.post.entity.ApplyFile;
import org.example.project.post.entity.ApplyForm;
import org.example.project.post.entity.ApplyQuestion;
import org.example.project.post.entity.Post;
import org.example.project.post.repositoty.ApplyFormRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

/** 글에 붙는 신청서 질문을 저장·조회한다. 질문은 글 작성자가 보낸 값이라 저장 전에 모양을 검사한다. */
@Service
@RequiredArgsConstructor
public class ApplyFormService {

    static final int MAX_QUESTIONS = 20;
    static final int MAX_LABEL_LENGTH = 100;
    static final int MAX_OPTIONS = 20;
    static final int MAX_DESCRIPTION_LENGTH = 300;
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9-]{1,36}");

    static final Map<String, Integer> MAX_TEXT_LENGTH = Map.of("TEXT", 200, "TEXTAREA", 2000, "URL", 500);
    static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    static final int MAX_FILES_PER_QUESTION = 3;
    static final Set<String> FILE_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png", "zip");
    static final int MAX_FILENAME_LENGTH = 200;

    private final ApplyFormRepository applyFormRepository;
    private final ApplyFileStorage fileStorage;

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
            String description = trim(q.description());
            if (description.length() > MAX_DESCRIPTION_LENGTH) {
                throw new IllegalArgumentException("'" + label + "' 질문의 설명은 " + MAX_DESCRIPTION_LENGTH + "자까지 입력할 수 있습니다.");
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
            result.add(new ApplyQuestion(key, q.type(), label, description, q.required(), options));
        }
        return result;
    }

    /**
     * 신청자가 보낸 답변을 글의 현재 질문에 맞춰 검사하고, 질문 내용을 복사한 답변 목록으로 만든다.
     * 질문이 없는 글이면 null을 돌려준다(예전처럼 답변 없는 신청). 클라이언트 검사는 믿지 않고 여기서 다시 확인한다.
     */
    public List<ApplyAnswer> buildAnswers(Long postId, Long memberId, List<ApplyAnswerRequest> requests) {
        List<ApplyQuestion> questions = getQuestions(postId);
        List<ApplyAnswerRequest> given = requests == null ? List.of() : requests;
        if (questions.isEmpty()) {
            if (!given.isEmpty()) {
                throw new IllegalArgumentException("신청서 질문이 없는 글입니다.");
            }
            return null;
        }
        Map<String, ApplyAnswerRequest> byKey = new HashMap<>();
        for (ApplyAnswerRequest r : given) {
            if (r == null || byKey.put(r.questionKey(), r) != null) {
                throw new IllegalArgumentException("답변이 올바르지 않습니다.");
            }
        }
        Set<String> questionKeys = new HashSet<>();
        questions.forEach(q -> questionKeys.add(q.key()));
        if (!questionKeys.containsAll(byKey.keySet())) {
            throw new IllegalArgumentException("신청서가 바뀌었습니다. 새로고침 후 다시 작성해주세요.");
        }

        List<ApplyAnswer> answers = new ArrayList<>();
        for (ApplyQuestion q : questions) {
            ApplyAnswerRequest r = byKey.get(q.key());
            List<String> values = r == null || r.values() == null ? List.of()
                    : r.values().stream().map(ApplyFormService::trim).filter(v -> !v.isEmpty()).toList();
            List<ApplyFile> files = r == null || r.files() == null ? List.of() : r.files();
            boolean isFile = "FILE".equals(q.type());
            if ((isFile && !values.isEmpty()) || (!isFile && !files.isEmpty())) {
                throw new IllegalArgumentException("'" + q.label() + "' 답변 형식이 올바르지 않습니다.");
            }
            if (q.required() && values.isEmpty() && files.isEmpty()) {
                throw new IllegalArgumentException("'" + q.label() + "'은(는) 필수 항목입니다.");
            }
            List<ApplyFile> checkedFiles = isFile ? checkFiles(q, files, memberId) : List.of();
            checkValues(q, values);
            answers.add(new ApplyAnswer(q.key(), q.label(), q.type(), q.options(), values, checkedFiles));
        }
        return answers;
    }

    private static void checkValues(ApplyQuestion q, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        if (!"CHECKBOX".equals(q.type()) && values.size() > 1) {
            throw new IllegalArgumentException("'" + q.label() + "'에는 하나만 답할 수 있습니다.");
        }
        if (q.hasOptions()) {
            if (!q.options().containsAll(values) || values.stream().distinct().count() != values.size()) {
                throw new IllegalArgumentException("'" + q.label() + "'에 없는 선택지입니다.");
            }
            return;
        }
        String value = values.get(0);
        int max = MAX_TEXT_LENGTH.get(q.type());
        if (value.length() > max) {
            throw new IllegalArgumentException("'" + q.label() + "'은(는) " + max + "자까지 입력할 수 있습니다.");
        }
        if ("URL".equals(q.type()) && !isHttpUrl(value)) {
            throw new IllegalArgumentException("'" + q.label() + "'에는 http(s)로 시작하는 주소를 입력해주세요.");
        }
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = new URI(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** 본인 경로에 실제로 올라간 파일인지, 개수·크기·확장자가 맞는지 S3에서 확인한다. 크기는 S3 값을 쓴다. */
    private List<ApplyFile> checkFiles(ApplyQuestion q, List<ApplyFile> files, Long memberId) {
        if (files.size() > MAX_FILES_PER_QUESTION) {
            throw new IllegalArgumentException("'" + q.label() + "'에는 파일을 " + MAX_FILES_PER_QUESTION + "개까지 올릴 수 있습니다.");
        }
        String ownPrefix = uploadPrefix(memberId);
        Set<String> seen = new HashSet<>();
        List<ApplyFile> result = new ArrayList<>();
        for (ApplyFile f : files) {
            if (f == null || f.key() == null || !f.key().startsWith(ownPrefix) || f.key().contains("..")
                    || !seen.add(f.key())) {
                throw new IllegalArgumentException("첨부 파일이 올바르지 않습니다.");
            }
            String name = checkFilename(f.name());
            long size = fileStorage.sizeOf(f.key())
                    .orElseThrow(() -> new IllegalArgumentException("'" + name + "' 파일이 업로드되지 않았습니다."));
            if (size > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("'" + name + "' 파일이 10MB를 넘습니다.");
            }
            result.add(new ApplyFile(f.key(), name, size));
        }
        return result;
    }

    /** 업로드할 S3 키를 서버가 만든다. 사용자가 보낸 파일명은 키에 넣지 않는다(덮어쓰기·경로 문자 방지). */
    public Map<String, String> issueUploadUrl(Long memberId, String filename) {
        checkFilename(filename);
        String key = uploadPrefix(memberId) + UUID.randomUUID();
        return Map.of("url", fileStorage.uploadUrl(key), "key", key);
    }

    public String issueDownloadUrl(ApplyFile file) {
        return fileStorage.downloadUrl(file.key(), file.name());
    }

    private static String uploadPrefix(Long memberId) {
        return "apply/" + memberId + "/";
    }

    private static String checkFilename(String filename) {
        String name = trim(filename);
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (name.isEmpty() || name.length() > MAX_FILENAME_LENGTH || !FILE_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("pdf, jpg, png, zip 파일만 올릴 수 있습니다.");
        }
        return name;
    }

    static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
