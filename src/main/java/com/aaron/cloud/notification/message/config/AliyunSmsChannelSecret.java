package com.aaron.cloud.notification.message.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AliyunSmsChannelSecret {
    private String accessKeySecret = "";
}
