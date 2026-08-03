package com.aaron.cloud.notification.message.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SmtpChannelConfig {

    private String smtpHost = "";
    private int smtpPort = 465;
    private String username = "";
    private String from = "";
    private boolean ssl = true;
}
