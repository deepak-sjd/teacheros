package com.teacheros.modules.user;

import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.tenant.TenantRepository;
import com.teacheros.modules.user.dto.UserDtos.MyProfileResponse;
import com.teacheros.modules.user.dto.UserDtos.UpdateProfileRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final CurrentTenantProvider currentTenantProvider;

    public UserService(UserRepository userRepository, TenantRepository tenantRepository,
            CurrentTenantProvider currentTenantProvider) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.currentTenantProvider = currentTenantProvider;
    }

    public MyProfileResponse getMyProfile() {
        User user = currentUser();
        String tenantName = tenantRepository.findById(user.getTenantId())
                .map(t -> t.getName())
                .orElse(null);
        return toResponse(user, tenantName);
    }

    @Transactional
    public MyProfileResponse updateMyProfile(UpdateProfileRequest request) {
        User user = currentUser();
        user.setDisplayName(request.displayName());
        user.setEmail(request.email());
        user = userRepository.save(user);

        String tenantName = tenantRepository.findById(user.getTenantId())
                .map(t -> t.getName())
                .orElse(null);
        return toResponse(user, tenantName);
    }

    private User currentUser() {
        return userRepository.findById(currentTenantProvider.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", currentTenantProvider.getCurrentUserId()));
    }

    private MyProfileResponse toResponse(User user, String tenantName) {
        return new MyProfileResponse(user.getId(), user.getPhoneNumber(), user.getDisplayName(),
                user.getEmail(), user.getRole(), tenantName);
    }
}
