package com.teacheros.modules.feestructure;

import com.teacheros.common.security.JwtAuthenticationFilter;
import com.teacheros.common.security.JwtService;
import com.teacheros.config.SecurityConfig;
import com.teacheros.modules.permission.PermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FeePermissions now delegates to the permission matrix. "perm" is mocked per
 * test to mirror the seeded defaults (V100/V101) and the acceptance case
 * "remove FEES_EDIT from ACCOUNTANT -> fee writes return 403".
 */
@WebMvcTest(controllers = FeeHeadController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class })
class FeePermissionsWebTest {

    private static final String NEW_HEAD = "{\"name\":\"Tuition\"}";

    @Autowired MockMvc mvc;
    @MockitoBean(name = "perm") PermissionService perm;
    @MockitoBean JwtService jwtService;
    @MockitoBean FeeHeadService feeHeadService;

    private void grant(String... permissions) {
        for (String p : permissions) {
            when(perm.has(p)).thenReturn(true);
        }
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void teacherDefaultsCanViewButNotConfigureFeeStructure() throws Exception {
        grant("FEES_VIEW", "FEES_EDIT"); // seeded TEACHER fee permissions: no FEES_CONFIGURE

        mvc.perform(get("/api/v1/fee-heads")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/fee-heads").contentType(MediaType.APPLICATION_JSON).content(NEW_HEAD))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantDefaultsCanViewAndConfigure() throws Exception {
        grant("FEES_VIEW", "FEES_EDIT", "FEES_CONFIGURE");

        mvc.perform(get("/api/v1/fee-heads")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/fee-heads").contentType(MediaType.APPLICATION_JSON).content(NEW_HEAD))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void removingFeesEditFromAccountantMakesFeeWritesReturn403ButViewStillWorks() throws Exception {
        grant("FEES_VIEW", "FEES_CONFIGURE"); // FEES_EDIT toggled off in the matrix

        mvc.perform(get("/api/v1/fee-heads")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/fee-heads").contentType(MediaType.APPLICATION_JSON).content(NEW_HEAD))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COUNSELLOR")
    void aRoleWithNoFeePermissionsIsDeniedEverything() throws Exception {
        mvc.perform(get("/api/v1/fee-heads")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/fee-heads").contentType(MediaType.APPLICATION_JSON).content(NEW_HEAD))
                .andExpect(status().isForbidden());
    }
}
