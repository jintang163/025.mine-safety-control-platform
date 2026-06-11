package com.mine.safety.controller;

import com.mine.safety.domain.SysUser;
import com.mine.safety.dto.ApiResponse;
import com.mine.safety.dto.LoginRequest;
import com.mine.safety.dto.LoginResponse;
import com.mine.safety.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        LoginResponse response = authService.login(request, httpRequest);
        return ApiResponse.success(response);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String token = extractToken(request);
        Long userId = authService.getCurrentUserId();
        authService.logout(token, userId);
        return ApiResponse.success();
    }

    @GetMapping("/me")
    public ApiResponse<LoginResponse.UserInfo> getCurrentUser() {
        SysUser user = authService.getCurrentUser();
        if (user == null) {
            return ApiResponse.error(401, "未登录");
        }
        return ApiResponse.success(convertToUserInfo(user));
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private LoginResponse.UserInfo convertToUserInfo(SysUser user) {
        LoginResponse.UserInfo info = new LoginResponse.UserInfo();
        info.setId(user.getId());
        info.setUsername(user.getUsername());
        info.setRealName(user.getRealName());
        info.setEmail(user.getEmail());
        info.setPhone(user.getPhone());
        info.setAvatar(user.getAvatar());
        info.setDepartment(user.getDepartment());
        if (user.getRoles() != null) {
            info.setRoles(user.getRoles().stream()
                    .map(r -> r.getRoleCode())
                    .toList());
        }
        if (user.getPermissions() != null) {
            info.setPermissions(user.getPermissions().stream()
                    .map(p -> p.getPermissionCode())
                    .toList());
        }
        return info;
    }
}
