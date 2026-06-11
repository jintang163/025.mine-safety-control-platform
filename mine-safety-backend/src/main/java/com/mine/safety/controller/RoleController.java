package com.mine.safety.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mine.safety.domain.SysPermission;
import com.mine.safety.domain.SysRole;
import com.mine.safety.dto.ApiResponse;
import com.mine.safety.service.RoleService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('system:role')")
    public ApiResponse<List<SysRole>> getAllRoles() {
        return ApiResponse.success(roleService.getAllRoles());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:role')")
    public ApiResponse<IPage<SysRole>> getRoles(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(roleService.getRoles(keyword, status, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role')")
    public ApiResponse<SysRole> getRoleById(@PathVariable Long id) {
        SysRole role = roleService.getRoleById(id);
        if (role == null) {
            return ApiResponse.error(404, "角色不存在");
        }
        return ApiResponse.success(role);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:role')")
    public ApiResponse<SysRole> createRole(@RequestBody RoleCreateRequest request,
                                           HttpServletRequest httpRequest) {
        SysRole role = new SysRole();
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        role.setSortOrder(request.getSortOrder());
        role.setStatus(request.getStatus());

        SysRole created = roleService.createRole(role, request.getPermissionIds(), httpRequest);
        return ApiResponse.success(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role')")
    public ApiResponse<SysRole> updateRole(@PathVariable Long id,
                                           @RequestBody RoleUpdateRequest request,
                                           HttpServletRequest httpRequest) {
        SysRole role = new SysRole();
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        role.setSortOrder(request.getSortOrder());
        role.setStatus(request.getStatus());

        SysRole updated = roleService.updateRole(id, role, request.getPermissionIds(), httpRequest);
        return ApiResponse.success(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role')")
    public ApiResponse<Void> deleteRole(@PathVariable Long id, HttpServletRequest request) {
        roleService.deleteRole(id, request);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('system:role')")
    public ApiResponse<List<SysPermission>> getRolePermissions(@PathVariable Long id) {
        return ApiResponse.success(roleService.getRolePermissions(id));
    }

    @Data
    public static class RoleCreateRequest {
        private String roleCode;
        private String roleName;
        private String description;
        private Integer sortOrder = 0;
        private Integer status = 1;
        private List<Long> permissionIds;
    }

    @Data
    public static class RoleUpdateRequest {
        private String roleName;
        private String description;
        private Integer sortOrder;
        private Integer status;
        private List<Long> permissionIds;
    }
}
