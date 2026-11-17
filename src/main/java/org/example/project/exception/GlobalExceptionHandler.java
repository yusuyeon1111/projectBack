package org.example.project.exception;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.naming.AuthenticationException;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public static final String UNAVAILABLE_MESSAGE = "일시적인 장애로 요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.";

    // Redis·DB 연결 실패와 명령 시간 초과. 요청이 잘못된 게 아니므로 400/403이 아니라 503이다
    public static ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", UNAVAILABLE_MESSAGE));
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, QueryTimeoutException.class})
    public ResponseEntity<Map<String, String>> handleUnavailable(RuntimeException ex) {
        return unavailable();
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "아이디 또는 비밀번호가 잘못되었습니다."));
    }

    // 정원 초과, 이미 처리된 신청처럼 권한이 아니라 현재 상태와 충돌하는 요청
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    // 입력값이 규칙에 맞지 않는 요청(신청서 질문·답변 검증 등). RuntimeException 처리(403)보다 먼저 잡는다.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    // 남의 팀처럼 존재 자체를 알려 주지 않을 자원
    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(java.util.NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }
}
