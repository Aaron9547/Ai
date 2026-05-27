package com.aaron.cloud.notification.message.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TencentSmsChannelConfig {

    private String sdkAppId = "";
    private String signName = "";
    private String templateId = "";
    private String region = "ap-guangzhou";
}
