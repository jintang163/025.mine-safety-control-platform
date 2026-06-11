package com.mine.safety.service;

import com.mine.safety.domain.SysOperationLog;
import com.mine.safety.domain.SysPermission;
import com.mine.safety.domain.SysRole;
import com.mine.safety.domain.SysUser;
import com.mine.safety.dto.LoginRequest;
import com.mine.safety.dto.LoginResponse;
import com.mine.safety.repository.SysUserRepository;
import com.mine.safety.security.JwtAuthenticationFilter;
import com.mine.safety.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;

    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        SysUser user = (SysUser) authentication.getPrincipal();

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("realName", user.getRealName());
        extraClaims.put("roles", user.getRoles().stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toList()));

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getId(), extraClaims);
        jwtAuthenticationFilter.cacheUserToken(user.getId(), token);

        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(getClientIp(httpRequest));
        sysUserRepository.updateById(user);

        operationLogService.logOperation(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                SysOperationLog.OperationType.LOGIN.getCode(),
                SysOperationLog.OperationModule.USER.getCode(),
                "用户登录",
                httpRequest
        );

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatar(),
                user.getDepartment(),
                user.getRoles().stream().map(SysRole::getRoleCode).collect(Collectors.toList()),
                user.getPermissions().stream().map(SysPermission::getPermissionCode).collect(Collectors.toList())
        );

        return new LoginResponse(
                token,
                "Bearer",
                jwtTokenProvider.getExpirationSeconds(),
                userInfo
        );
    }

    public void logout(String token, Long userId) {
        if (StringUtils.hasText(token)) {
            jwtAuthenticationFilter.blacklistToken(token, userId);
            jwtAuthenticationFilter.removeUserToken(userId);
        }
        SecurityContextHolder.clearContext();
        log.info("User logged out: userId={}", userId);
    }

    public SysUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SysUser) {
            return (SysUser) authentication.getPrincipal();
        }
        return null;
    }

    public Long getCurrentUserId() {
        SysUser user = getCurrentUser();
        return user != null ? user.getId() : null;
    }

    public String getCurrentUsername() {
        SysUser user = getCurrentUser();
        return user != null ? user.getUsername() : null;
    }

    public boolean hasRole(String roleCode) {
        SysUser user = getCurrentUser();
        if (user == null || user.getRoles() == null) {
            return false;
        }
        return user.getRoles().stream()
                .anyMatch(role -> role.getRoleCode().equals(roleCode));
    }

    public boolean hasPermission(String permissionCode) {
        SysUser user = getCurrentUser();
        if (user == null || user.getPermissions() == null) {
            return false;
        }
        return user.getPermissions().stream()
                .anyMatch(p -> p.getPermissionCode().equals(permissionCode));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
