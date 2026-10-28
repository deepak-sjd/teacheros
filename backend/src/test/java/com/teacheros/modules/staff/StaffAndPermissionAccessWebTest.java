package com.teacheros.modules.staff;

import com.teacheros.common.security.JwtAuthenticationFilter;
import com.teacheros.common.security.JwtService;
import com.teacheros.config.SecurityConfig;
import com.teacheros.modules.permission.PermissionAdminController;
import com.teacheros.modules.permission.PermissionAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Role gates on the new endpoints: staff = OWNER/ADMIN, matrix read = OWNER/ADMIN, matrix write = OWNER. */
@WebMvcTest(controllers = { StaffController.class, PermissionAdminController.class })
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class })
class StaffAndPermissionAccessWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwtService;
    @MockitoBean StaffService staffService;
    @MockitoBean PermissionAdminService permissionAdminService;

    private static final String ID = UUID.randomUUID().toString();
    private static final String NEW_STAFF = "{\"displayName\":\"Asha\",\"phoneNumber\":\"919876543210\",\"role\":\"ACCOUNTANT\"}";
    private static final String GRANT = "{\"role\":\"ACCOUNTANT\",\"permission\":\"FEES_EDIT\",\"granted\":false}";

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanManageStaffAndReadTheMatrixButNotEditIt() throws Exception {
        mvc.perform(get("/api/v1/staff")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/staff").contentType(MediaType.APPLICATION_JSON).content(NEW_STAFF))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/staff/" + ID + "/role").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TEACHER\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/staff/" + ID + "/deactivate")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/staff/" + ID + "/reactivate")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/permissions/matrix")).andExpect(status().isOk());

        mvc.perform(put("/api/v1/permissions/grants").contentType(MediaType.APPLICATION_JSON).content(GRANT))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void ownerCanEditTheMatrix() throws Exception {
        mvc.perform(put("/api/v1/permissions/grants").contentType(MediaType.APPLICATION_JSON).content(GRANT))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = { "ACCOUNTANT" })
    void accountantGetsNothingEvenWithAllFeePermissions() throws Exception {
        mvc.perform(get("/api/v1/staff")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/staff").contentType(MediaType.APPLICATION_JSON).content(NEW_STAFF))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/permissions/matrix")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/permissions/grants").contentType(MediaType.APPLICATION_JSON).content(GRANT))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = { "TEACHER" })
    void teacherAndStaffRolesAreDenied() throws Exception {
        mvc.perform(get("/api/v1/staff")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/permissions/matrix")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = { "PARENT" })
    void familyRolesAreDenied() throws Exception {
        mvc.perform(get("/api/v1/staff")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/permissions/grants").contentType(MediaType.APPLICATION_JSON).content(GRANT))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/v1/staff")).andExpect(status().is4xxClientError());
    }
}
