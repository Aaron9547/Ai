package com.aaron.cloud.notification.message.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AliyunSmsChannelConfig {

    private String accessKeyId = "";
    private String region = "cn-hangzhou";
    private String signName = "";
    private String templateCode = "";
}
