package com.teacheros.modules.user;

import com.teacheros.modules.user.dto.UserDtos.MyProfileResponse;
import com.teacheros.modules.user.dto.UserDtos.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// No @PreAuthorize - every role has their own profile and can edit it.
// Scoped entirely to the caller's own account via CurrentTenantProvider's
// userId, never a path variable, so there's no "view someone else's
// profile" surface to guard against in the first place.
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<MyProfileResponse> getMyProfile() {
        return ResponseEntity.ok(userService.getMyProfile());
    }

    @PatchMapping("/me")
    public ResponseEntity<MyProfileResponse> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateMyProfile(request));
    }
}
