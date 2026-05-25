package com.aaron.cloud;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.aaron.cloud.common.tenant.SysTenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration")
@EnabledIfSystemProperty(named = "ai.integration", matches = "true")
class AiApplicationIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private SysTenantRepository sysTenantRepository;

    @Test
    void contextLoads() {
        assertNotNull(applicationContext);
    }

    @Test
    void tenantRepositoryUsesEnumWrapper() {
        var list = sysTenantRepository.findAllActive();
        assertFalse(list.isEmpty());
    }
}
