package com.teacheros.modules.auth;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.security.JwtService;
import com.teacheros.modules.auth.dto.TokenResponse;
import com.teacheros.modules.permission.PermissionService;
import com.teacheros.modules.student.StudentRepository;
import com.teacheros.modules.tenant.TenantRepository;
import com.teacheros.modules.user.Role;
import com.teacheros.modules.user.User;
import com.teacheros.modules.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Deactivated staff can't log in, can't be re-signed-up as a new owner, and can't refresh. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceDeactivationTest {

    private static final String PHONE = "919876543210";

    @Mock OtpCodeRepository otpCodeRepository;
    @Mock UserRepository userRepository;
    @Mock TenantRepository tenantRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock StudentRepository studentRepository;
    @Mock OtpSender otpSender;
    @Mock JwtService jwtService;
    @Mock PermissionService permissionService;
    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(otpCodeRepository, userRepository, tenantRepository, refreshTokenRepository,
                studentRepository, otpSender, jwtService, permissionService, 5, 15, 30);
        OtpCode otp = new OtpCode(PHONE, BCrypt.hashpw("123456", BCrypt.gensalt()), Instant.now().plusSeconds(300));
        when(otpCodeRepository.findTopByPhoneNumberOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.of(otp));
        when(jwtService.issueAccessToken(any(), any(), any())).thenReturn("access");
    }

    @Test
    void aPreCreatedStaffUserLogsIntoTheirOwnTenantWithTheirRole() {
        UUID tenant = UUID.randomUUID();
        User accountant = new User(tenant, PHONE, "Asha", Role.ACCOUNTANT);
        accountant.setId(UUID.randomUUID());
        when(userRepository.findFirstByPhoneNumberAndActiveTrue(PHONE)).thenReturn(Optional.of(accountant));

        TokenResponse tokens = service.verifyOtp(PHONE, "123456", null, null);

        assertThat(tokens.accessToken()).isEqualTo("access");
        verify(jwtService).issueAccessToken(accountant.getId(), tenant, "ACCOUNTANT");
        verify(tenantRepository, never()).save(any()); // no new tenant was created
    }

    @Test
    void aDeactivatedPhoneIsRefusedInsteadOfBeingSignedUpAsANewOwner() {
        when(userRepository.findFirstByPhoneNumberAndActiveTrue(PHONE)).thenReturn(Optional.empty());
        when(userRepository.existsByPhoneNumberAndActiveFalse(PHONE)).thenReturn(true);

        assertThatThrownBy(() -> service.verifyOtp(PHONE, "123456", "Asha", "My School"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getCode()).isEqualTo("ACCOUNT_DEACTIVATED");
                });
        verify(tenantRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void refreshFailsForADeactivatedUserAndDoesNotRotateTheToken() {
        UUID userId = UUID.randomUUID();
        RefreshToken token = new RefreshToken(userId, "hash", Instant.now().plusSeconds(3600));
        User inactive = new User(UUID.randomUUID(), PHONE, "Asha", Role.ACCOUNTANT);
        inactive.setActive(false);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.refresh("raw-token"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getCode()).isEqualTo("ACCOUNT_DEACTIVATED");
                });
        verify(jwtService, never()).issueAccessToken(any(), any(), any());
        assertThat(token.getRevokedAt()).isNull();
    }
}
