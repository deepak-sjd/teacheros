package com.teacheros.modules.owner;

import com.teacheros.modules.ai.AiUsageReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringJUnitConfig(OwnerAiUsageControllerSecurityTest.Config.class)
class OwnerAiUsageControllerSecurityTest {

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        AiUsageReportService reportService() {
            return mock(AiUsageReportService.class);
        }

        @Bean
        OwnerAiUsageController controller(AiUsageReportService reportService) {
            return new OwnerAiUsageController(reportService);
        }
    }

    @Autowired
    OwnerAiUsageController controller;
    @Autowired
    AiUsageReportService reportService;

    @Test
    @WithMockUser(roles = { "TEACHER" })
    void teacherIsForbidden() {
        assertThrows(AccessDeniedException.class, () -> controller.usage(30));
        verifyNoInteractions(reportService);
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    void adminIsForbidden() {
        assertThrows(AccessDeniedException.class, () -> controller.usage(30));
    }

    @Test
    @WithMockUser(roles = { "PARENT" })
    void parentIsForbidden() {
        assertThrows(AccessDeniedException.class, () -> controller.usage(30));
    }

    @Test
    @WithMockUser(roles = { "OWNER" })
    void ownerIsAllowed() {
        controller.usage(30); // no exception
    }
}
