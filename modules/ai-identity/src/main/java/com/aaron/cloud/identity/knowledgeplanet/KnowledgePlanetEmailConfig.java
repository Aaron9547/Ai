package com.aaron.cloud.identity.knowledgeplanet;

import com.aaron.cloud.identity.open.AuthRegisterVerificationConfig.EmailChannel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** 租户 {@code KNOWLEDGE_PLANET_EMAIL_JSON}。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgePlanetEmailConfig {

    private boolean enabled = true;
    /** 为 true 时 SMTP 取自注册验证码通道，模板仍用本对象字段。 */
    private boolean reuseRegisterSmtp = true;

    private EmailChannel email = new EmailChannel();

    public KnowledgePlanetEmailConfig() {
        email.setSubjectTemplate("【{tenantName}】本周知识星球成长提醒");
        email.setBodyTemplate(
                """
                {userName}，您好：

                {weekLabel} 个人知识星球周报已生成。

                {summary}

                【建议思考方向】
                {thinkDirections}

                【可弥补的不足】
                {gapAreas}

                【推荐阅读】
                {books}

                登录对话页右侧「知识星球」可查看完整图谱。
                """);
    }
}
