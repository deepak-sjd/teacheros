package com.teacheros.modules.auth;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.security.JwtService;
import com.teacheros.modules.auth.dto.TokenResponse;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentRepository;
import com.teacheros.modules.tenant.Tenant;
import com.teacheros.modules.tenant.TenantRepository;
import com.teacheros.modules.user.Role;
import com.teacheros.modules.user.User;
import com.teacheros.modules.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Phone-OTP authentication.
 *
 * Flow:
 * 1. requestOtp: generate a 6-digit code, hash it, store it with a TTL,
 * hand it to OtpSender (logs in dev, real SMS/WhatsApp provider in prod).
 * 2. verifyOtp: check the code against the stored hash, and either log the
 * user into their existing account, or provision a new one. A new
 * account is one of three kinds, decided by what this phone number
 * matches (see provisionAccountForPhone):
 * - a student's guardian_phone -> new PARENT account
 * - a student's own phone_number -> new STUDENT account
 * - neither -> new OWNER account + brand new tenant (teacher signup,
 * requires displayName + tenantName)
 * 3. refresh: rotate the refresh token and issue a new access token.
 *
 * Deliberately NOT handled here yet (out of MVP scope): a single phone
 * number belonging to accounts in more than one tenant (e.g. a parent with
 * children at two different tuition centers, or a guardian_phone that
 * happens to match students in two tenants) - resolves to the first match
 * found. Revisit if/when that becomes a real reported problem, not before.
 */
@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpCodeRepository otpCodeRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final StudentRepository studentRepository;
    private final OtpSender otpSender;
    private final JwtService jwtService;
    private final int otpTtlMinutes;
    private final int accessTokenTtlMinutes;
    private final int refreshTokenTtlDays;

    public AuthService(
            OtpCodeRepository otpCodeRepository,
            UserRepository userRepository,
            TenantRepository tenantRepository,
            RefreshTokenRepository refreshTokenRepository,
            StudentRepository studentRepository,
            OtpSender otpSender,
            JwtService jwtService,
            @Value("${app.otp.ttl-minutes}") int otpTtlMinutes,
            @Value("${app.jwt.access-token-ttl-minutes}") int accessTokenTtlMinutes,
            @Value("${app.jwt.refresh-token-ttl-days}") int refreshTokenTtlDays) {
        this.otpCodeRepository = otpCodeRepository;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.studentRepository = studentRepository;
        this.otpSender = otpSender;
        this.jwtService = jwtService;
        this.otpTtlMinutes = otpTtlMinutes;
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    @Transactional
    public void requestOtp(String phoneNumber) {
        String code = generateSixDigitCode();
        OtpCode otpCode = new OtpCode(phoneNumber, BCrypt.hashpw(code, BCrypt.gensalt()),
                Instant.now().plus(Duration.ofMinutes(otpTtlMinutes)));
        otpCodeRepository.save(otpCode);
        otpSender.send(phoneNumber, code);
    }

    @Transactional
    public TokenResponse verifyOtp(String phoneNumber, String code, String displayName, String tenantName) {
        OtpCode otpCode = otpCodeRepository.findTopByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .filter(OtpCode::isUsable)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "OTP_EXPIRED_OR_MISSING",
                        "This code has expired or was never requested. Please request a new one."));

        otpCode.setAttemptCount(otpCode.getAttemptCount() + 1);

        if (!BCrypt.checkpw(code, otpCode.getCodeHash())) {
            otpCodeRepository.save(otpCode);
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_INCORRECT", "That code is incorrect.");
        }

        otpCode.setConsumedAt(Instant.now());
        otpCodeRepository.save(otpCode);

        User user = userRepository.findFirstByPhoneNumberAndActiveTrue(phoneNumber)
                .orElseGet(() -> provisionAccountForPhone(phoneNumber, displayName, tenantName));

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return issueTokenPair(user);
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(tokenHash)
                .filter(RefreshToken::isValid)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                        "Please log in again."));

        // Rotate: revoke the old token and issue a brand new pair. This means
        // a stolen-and-reused refresh token is immediately detectable (both
        // the attacker's and the legitimate user's next refresh will fail),
        // rather than a single long-lived token being reusable indefinitely.
        existing.setRevokedAt(Instant.now());
        refreshTokenRepository.save(existing);

        User user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Please log in again."));

        return issueTokenPair(user);
    }

    /**
     * No active user exists for this phone yet. Figure out which of three
     * accounts to create, in this priority order:
     *
     * 1. Does this phone match a student's guardian_phone anywhere? ->
     * they're a parent. Auto-create a PARENT account in that student's
     * tenant. No separate "parent signup" flow needed - the teacher
     * already gave us this phone number when they added the student.
     * 2. Does this phone match a student's own phone_number anywhere? ->
     * they're a student. Auto-create a STUDENT account the same way.
     * 3. Neither -> this is a brand new teacher signing up. Requires
     * displayName + tenantName, same as before this change.
     *
     * displayName is optional for the parent/student paths (they didn't
     * fill out a signup form - the account is created on their behalf) and
     * falls back to a generic label; it's still just a label, not used for
     * authorization anywhere.
     */
    private User provisionAccountForPhone(String phoneNumber, String displayName, String tenantName) {
        Optional<Student> guardianMatch = studentRepository.findFirstByGuardianPhoneAndActiveTrue(phoneNumber);
        if (guardianMatch.isPresent()) {
            Student student = guardianMatch.get();
            String name = (displayName == null || displayName.isBlank()) ? "Parent" : displayName;
            User user = new User(student.getTenantId(), phoneNumber, name, Role.PARENT);
            return userRepository.save(user);
        }

        Optional<Student> studentMatch = studentRepository.findFirstByPhoneNumberAndActiveTrue(phoneNumber);
        if (studentMatch.isPresent()) {
            Student student = studentMatch.get();
            String name = (displayName == null || displayName.isBlank()) ? student.getFullName() : displayName;
            User user = new User(student.getTenantId(), phoneNumber, name, Role.STUDENT);
            return userRepository.save(user);
        }

        return provisionNewTeacherAccount(phoneNumber, displayName, tenantName);
    }

    private User provisionNewTeacherAccount(String phoneNumber, String displayName, String tenantName) {
        if (displayName == null || displayName.isBlank() || tenantName == null || tenantName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SIGNUP_INFO_REQUIRED",
                    "First-time login requires displayName and tenantName to create your account.");
        }

        Tenant tenant = tenantRepository.save(new Tenant(tenantName));
        User user = new User(tenant.getId(), phoneNumber, displayName, Role.OWNER);
        return userRepository.save(user);
    }

    private TokenResponse issueTokenPair(User user) {
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getTenantId(), user.getRole().name());

        String rawRefreshToken = generateOpaqueToken();
        RefreshToken refreshToken = new RefreshToken(
                user.getId(),
                hashToken(rawRefreshToken),
                Instant.now().plus(Duration.ofDays(refreshTokenTtlDays)));
        refreshTokenRepository.save(refreshToken);

        return new TokenResponse(accessToken, rawRefreshToken, Duration.ofMinutes(accessTokenTtlMinutes).toSeconds());
    }

    private String generateSixDigitCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        // Refresh tokens are high-entropy random values, so a fast hash
        // (unlike passwords/OTPs) is fine here - we're not defending against
        // brute force, just avoiding storing the usable token in plaintext.
        return sha256(rawToken);
    }

    private String sha256(String value) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
