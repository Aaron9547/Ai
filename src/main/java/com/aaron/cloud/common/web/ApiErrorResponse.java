package com.aaron.cloud.common.web;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ApiErrorResponse {
    String code;
    String message;
}
