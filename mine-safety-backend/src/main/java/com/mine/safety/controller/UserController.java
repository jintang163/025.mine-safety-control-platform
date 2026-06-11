package com.mine.safety.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mine.safety.domain.SysRole;
import com.mine.safety.domain.SysUser;
import com.mine.safety.dto.ApiResponse;
import com.mine.safety.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:user')")
    public ApiResponse<IPage<SysUser>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(userService.getUsers(keyword, status, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user')")
    public ApiResponse<SysUser> getUserById(@PathVariable Long id) {
        SysUser user = userService.getUserById(id);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        return ApiResponse.success(user);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:user')")
    public ApiResponse<SysUser> createUser(@RequestBody UserCreateRequest request,
                                           HttpServletRequest httpRequest) {
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        user.setRealName(request.getRealName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setDepartment(request.getDepartment());
        user.setStatus(request.getStatus());

        SysUser created = userService.createUser(user, request.getRoleIds(), httpRequest);
        created.setPassword(null);
        return ApiResponse.success(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user')")
    public ApiResponse<SysUser> updateUser(@PathVariable Long id,
                                           @RequestBody UserUpdateRequest request,
                                           HttpServletRequest httpRequest) {
        SysUser user = new SysUser();
        user.setRealName(request.getRealName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setDepartment(request.getDepartment());
        user.setStatus(request.getStatus());
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(request.getPassword());
        }

        SysUser updated = userService.updateUser(id, user, request.getRoleIds(), httpRequest);
        updated.setPassword(null);
        return ApiResponse.success(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user')")
    public ApiResponse<Void> deleteUser(@PathVariable Long id, HttpServletRequest request) {
        userService.deleteUser(id, request);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('system:user')")
    public ApiResponse<List<SysRole>> getUserRoles(@PathVariable Long id) {
        return ApiResponse.success(userService.getUserRoles(id));
    }

    @Data
    public static class UserCreateRequest {
        private String username;
        private String password;
        private String realName;
        private String email;
        private String phone;
        private String department;
        private Integer status = 1;
        private List<Long> roleIds;
    }

    @Data
    public static class UserUpdateRequest {
        private String password;
        private String realName;
        private String email;
        private String phone;
        private String department;
        private Integer status;
        private List<Long> roleIds;
    }
}
