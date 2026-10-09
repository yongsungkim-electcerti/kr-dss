package com.electcerti.krdss.poc.kisa.web;

import com.electcerti.krdss.poc.kisa.registry.RegistryException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 관리 화면 엔드포인트의 공통 오류 응답 {code, message}. */
@RestControllerAdvice(assignableTypes = {RegistryAdminController.class, IssuanceAdminController.class})
public class AdminErrorHandler {

    @ExceptionHandler(RegistryException.class)
    public ResponseEntity<Map<String, String>> registryError(RegistryException e) {
        var status = switch (e.code()) {
            case INVALID_INPUT -> HttpStatus.UNPROCESSABLE_ENTITY;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case REVISION_CONFLICT, IDENTITY_CONFLICT, REQUEST_CONFLICT, REQUEST_IN_PROGRESS, INTERRUPTED ->
                    HttpStatus.CONFLICT;
            case STATE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case STORAGE_FAILED, KEY_READ_FAILED, SIGNING_FAILED, SIGNATURE_CHECK_FAILED ->
                    HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", e.code().name(), "message", String.valueOf(e.getMessage())));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> badRequest(Exception e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", "INVALID_INPUT", "message", "요청 형식이 올바르지 않습니다(값·날짜·선택 항목 확인)."));
    }
}
