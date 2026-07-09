package com.aaron.cloud.common.web;

import com.aaron.cloud.common.api.ErrorCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局 REST 异常映射。租户成员相关分支与业务侧 {@code EX_MSG_*} 字面量契约见 {@code PROJECT.md}「成员状态与业务错误」；变更映射或字面量时须遵守 {@code .cursorrules}
 * §7.2（同步注释与专节）。
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;

    /**
     * 可预期非法状态（用户名冲突、CORS 行缺失、租户上下文缺失等）。其中租户成员「重复在册」分支：仅当 {@code ex.getMessage()} 等于 {@link
     * ErrorCodes#EX_MSG_TENANT_MEMBER_ALREADY_ACTIVE} 时映射 {@link ErrorCodes#TENANT_MEMBER_ALREADY_ACTIVE}（409）；业务侧禁止手写同义字符串，见 {@code
     * PROJECT.md}「成员状态与业务错误」。
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> illegalState(IllegalStateException ex) {
        if ("tenant context missing".equals(ex.getMessage())) {
            log.warn(
                    "bad request http=\"{}\" message={}",
                    RequestLogSupport.currentRequestLine(),
                    ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(
                            ApiErrorResponse.builder()
                                    .code(ErrorCodes.TENANT_REQUIRED)
                                    .message(ex.getMessage())
                                    .build());
        }
        if (ErrorCodes.EX_MSG_LOGIN_NAME_CONFLICT.equals(ex.getMessage())) {
            log.warn(
                    "conflict http=\"{}\" message={}",
                    RequestLogSupport.currentRequestLine(),
                    ex.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(
                            ApiErrorResponse.builder()
                                    .code(ErrorCodes.LOGIN_NAME_CONFLICT)
                                    .message("该登录名已被占用")
                                    .build());
        }
        if ("duplicate origin".equals(ex.getMessage())) {
            log.warn(
                    "conflict http=\"{}\" message={}",
                    RequestLogSupport.currentRequestLine(),
                    ex.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(
                            ApiErrorResponse.builder()
                                    .code(ErrorCodes.CONFLICT)
                                    .message(ex.getMessage())
                                    .build());
        }
        if ("cors origin row not found".equals(ex.getMessage())) {
            log.warn(
                    "not found http=\"{}\" message={}",
                    RequestLogSupport.currentRequestLine(),
                    ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(
                            ApiErrorResponse.builder()
                                    .code(ErrorCodes.NOT_FOUND)
                                    .message(ex.getMessage())
                                    .build());
        }
        if (ErrorCodes.EX_MSG_TENANT_MEMBER_ALREADY_ACTIVE.equals(ex.getMessage())) {
            log.warn(
                    "conflict http=\"{}\" message={}",
                    RequestLogSupport.currentRequestLine(),
                    ex.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(
                            ApiErrorResponse.builder()
                                    .code(ErrorCodes.TENANT_MEMBER_ALREADY_ACTIVE)
                                    .message("该用户在本租户已有在册成员身份")
                                    .build());
        }
        log.error(
                "illegal state http=\"{}\" message={} (returning 500 to client)",
                RequestLogSupport.currentRequestLine(),
                ex.getMessage(),
                ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ApiErrorResponse.builder()
                                .code(ErrorCodes.INTERNAL)
                                .message(ex.getMessage())
                                .build());
    }

    /**
     * 客户端/参数类错误的主入口。租户成员「非在册」分支：仅当 {@code ex.getMessage()} 等于 {@link ErrorCodes#EX_MSG_TENANT_MEMBER_INACTIVE} 时映射
     * {@link ErrorCodes#TENANT_MEMBER_INACTIVE}（400）；约定见 {@code PROJECT.md}。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> badRequest(IllegalArgumentException ex) {
        if (ErrorCodes.EX_MSG_TENANT_MEMBER_INACTIVE.equals(ex.getMessage())) {
            log.warn(
                    "bad request http=\"{}\" message={}",
                    RequestLogSupport.currentRequestLine(),
                    ex.getMessage(),
                    ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(
                            ApiErrorResponse.builder()
                                    .code(ErrorCodes.TENANT_MEMBER_INACTIVE)
                                    .message("该成员已不在册，无法执行此操作")
                                    .build());
        }
        if (ErrorCodes.EX_MSG_CANNOT_OPERATE_ON_SELF.equals(ex.getMessage())) {
            log.warn(
                    "bad request http=\"{}\" message={}",
                    RequestLogSupport.currentRequestLine(),
                    ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(
                            ApiErrorResponse.builder()
                                    .code(ErrorCodes.FORBIDDEN)
                                    .message("不能对本人执行此操作")
                                    .build());
        }
        if ("该模型没额度了".equals(ex.getMessage())) {
            log.warn("quota http=\"{}\": {}", RequestLogSupport.currentRequestLine(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(
                            ApiErrorResponse.builder()
                                    .code(ErrorCodes.QUOTA_EXCEEDED)
                                    .message(ex.getMessage())
                                    .build());
        }
        log.warn(
                "bad request http=\"{}\" message={}",
                RequestLogSupport.currentRequestLine(),
                ex.getMessage(),
                ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiErrorResponse.builder()
                                .code(ErrorCodes.NOT_FOUND)
                                .message(ex.getMessage())
                                .build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> accessDenied(AccessDeniedException ex) {
        log.warn(
                "forbidden http=\"{}\" message={}",
                RequestLogSupport.currentRequestLine(),
                ex.getMessage(),
                ex);
        String msg = ex.getMessage() != null ? ex.getMessage() : "forbidden";
        String code = ErrorCodes.FORBIDDEN;
        if ("用户不在当前租户".equals(msg)) {
            code = ErrorCodes.USER_NOT_IN_CURRENT_TENANT;
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.builder().code(code).message(msg).build());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> noResource(NoResourceFoundException ex) {
        log.warn(
                "no handler for resource http=\"{}\" path={}",
                RequestLogSupport.currentRequestLine(),
                ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(
                        ApiErrorResponse.builder()
                                .code(ErrorCodes.NOT_FOUND)
                                .message("no api mapping for " + ex.getResourcePath())
                                .build());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> responseStatus(ResponseStatusException ex) {
        var http = HttpStatus.resolve(ex.getStatusCode().value());
        if (http == null) {
            http = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (http.is4xxClientError()) {
            log.warn(
                    "client error http=\"{}\" status={} reason={}",
                    RequestLogSupport.currentRequestLine(),
                    http.value(),
                    ex.getReason());
        } else {
            log.error(
                    "response status http=\"{}\" status={}",
                    RequestLogSupport.currentRequestLine(),
                    http,
                    ex);
        }
        String code =
                http == HttpStatus.CONFLICT
                        ? ErrorCodes.CONFLICT
                        : http == HttpStatus.NOT_FOUND
                                ? ErrorCodes.NOT_FOUND
                                : http == HttpStatus.BAD_REQUEST
                                        ? ErrorCodes.VALIDATION
                                        : http == HttpStatus.FORBIDDEN
                                                ? ErrorCodes.FORBIDDEN
                                                : http.is5xxServerError()
                                                        ? ErrorCodes.INTERNAL
                                                        : ErrorCodes.NOT_FOUND;
        return ResponseEntity.status(http)
                .body(
                        ApiErrorResponse.builder()
                                .code(code)
                                .message(ex.getReason() != null ? ex.getReason() : http.getReasonPhrase())
                                .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException ex) {
        var br = ex.getBindingResult();
        String detail =
                br.getFieldErrors().stream()
                        .map(fe -> fe.getField() + ":" + fe.getDefaultMessage())
                        .limit(16)
                        .collect(Collectors.joining("; "));
        if (detail.isEmpty()) {
            detail = ex.getMessage();
        }
        log.warn(
                "validation failed http=\"{}\" fields=[{}]",
                RequestLogSupport.currentRequestLine(),
                detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiErrorResponse.builder()
                                .code(ErrorCodes.VALIDATION)
                                .message(br.getFieldError() != null
                                        ? br.getFieldError().getDefaultMessage()
                                        : "validation failed")
                                .build());
    }

    @ExceptionHandler(IOException.class)
    public void clientIoDisconnect(IOException ex, HttpServletResponse response) {
        if (response.isCommitted()) {
            log.debug(
                    "client io disconnect http=\"{}\" message={}",
                    RequestLogSupport.currentRequestLine(),
                    ex.getMessage());
            return;
        }
        log.warn(
                "io exception http=\"{}\" message={}",
                RequestLogSupport.currentRequestLine(),
                ex.getMessage(),
                ex);
    }

    /**
     * SSE 等异步请求在客户端主动断开时，容器会通知 {@code AsyncRequestNotUsableException}；属预期行为，勿记 ERROR。
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void asyncClientDisconnected(AsyncRequestNotUsableException ex, HttpServletResponse response) {
        if (response.isCommitted()) {
            log.debug(
                    "async client disconnected http=\"{}\" message={}",
                    RequestLogSupport.currentRequestLine(),
                    ex.getMessage());
            return;
        }
        log.warn(
                "async client disconnected http=\"{}\" message={}",
                RequestLogSupport.currentRequestLine(),
                ex.getMessage());
    }

    /**
     * 末兜底：须直接写入 {@link HttpServletResponse}，避免 SSE 等仅 {@code Accept: text/event-stream} 的请求在
     * {@code ResponseEntity + HttpMessageConverter} 路径上触发 {@code HttpMediaTypeNotAcceptableException}。
     */
    @ExceptionHandler(Exception.class)
    public void generic(Exception ex, HttpServletResponse response) throws IOException {
        log.error(
                "unhandled exception http=\"{}\" type={} message={}",
                RequestLogSupport.currentRequestLine(),
                ex.getClass().getName(),
                ex.getMessage(),
                ex);
        if (response.isCommitted()) {
            return;
        }
        response.resetBuffer();
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String json =
                objectMapper.writeValueAsString(
                        ApiErrorResponse.builder()
                                .code(ErrorCodes.INTERNAL)
                                .message("unexpected error")
                                .build());
        response.getWriter().write(json);
    }
}
