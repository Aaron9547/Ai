package com.aaron.cloud.gateway.accessparty;

import com.aaron.cloud.common.api.ErrorCodes;
import com.aaron.cloud.common.web.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessPartyErrorWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, int httpStatus, String code, String message)
            throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                ApiErrorResponse.builder().code(code).message(message).build());
    }

    public void unauthorized(HttpServletResponse response) throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCodes.ACCESS_PARTY_UNAUTHORIZED, "接入方鉴权失败");
    }

    public void forbidden(HttpServletResponse response) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, ErrorCodes.ACCESS_PARTY_ENDPOINT_DENIED, "未授权访问该接口");
    }

    public void rateLimited(HttpServletResponse response) throws IOException {
        write(response, 429, ErrorCodes.ACCESS_PARTY_RATE_LIMITED, "接入方请求过于频繁");
    }
}
