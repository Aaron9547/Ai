package com.aaron.cloud.chat.dto;

import java.time.LocalDateTime;

public record ChatShareCreateView(String shareCode, String sharePath, LocalDateTime expiresAt) {}
