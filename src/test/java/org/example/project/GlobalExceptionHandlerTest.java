package org.example.project;

import org.example.project.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 스프링 컨텍스트 없이 처리기만 부른다
class GlobalExceptionHandlerTest {

    final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 메시지_없는_예외도_본문에_error를_담아_응답한다() {
        assertThat(handler.handleRuntimeException(new RuntimeException()).getBody()).containsKey("error");
        assertThat(handler.handleConflict(new IllegalStateException()).getStatusCode().value()).isEqualTo(409);
        assertThat(handler.handleBadRequest(new IllegalArgumentException()).getBody()).containsKey("error");
        assertThat(handler.handleNotFound(new java.util.NoSuchElementException()).getBody()).containsKey("error");
    }
}
