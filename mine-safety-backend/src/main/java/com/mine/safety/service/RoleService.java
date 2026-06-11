package com.mine.safety.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mine.safety.domain.SysOperationLog;
import com.mine.safety.domain.SysPermission;
import com.mine.safety.domain.SysRole;
import com.mine.safety.domain.SysRolePermission;
import com.mine.safety.repository.SysPermissionRepository;
import com.mine.safety.repository.SysRolePermissionRepository;
import com.mine.safety.repository.SysRoleRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final SysRoleRepository roleRepository;
    private final SysPermissionRepository permissionRepository;
    private final SysRolePermissionRepository rolePermissionRepository;
    private final OperationLogService operationLogService;
    private final AuthService authService;

    public List<SysRole> getAllRoles() {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRole::getStatus, 1)
                .orderByAsc(SysRole::getSortOrder);
        return roleRepository.selectList(wrapper);
    }

    public IPage<SysRole> getRoles(String keyword, Integer status, int page, int size) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(SysRole::getRoleCode, keyword)
                    .or().like(SysRole::getRoleName, keyword));
        }
        if (status != null) {
            wrapper.eq(SysRole::getStatus, status);
        }
        wrapper.orderByAsc(SysRole::getSortOrder);
        return roleRepository.selectPage(new Page<>(page, size), wrapper);
    }

    public SysRole getRoleById(Long id) {
        SysRole role = roleRepository.selectById(id);
        if (role != null) {
            role.setPermissions(permissionRepository.findPermissionsByRoleId(id));
        }
        return role;
    }

    public SysRole getRoleByCode(String roleCode) {
        return roleRepository.findByRoleCode(roleCode);
    }

    @Transactional
    public SysRole createRole(SysRole role, List<Long> permissionIds, HttpServletRequest request) {
        SysRole existRole = roleRepository.findByRoleCode(role.getRoleCode());
        if (existRole != null) {
            throw new RuntimeException("角色编码已存在");
        }

        if (role.getStatus() == null) {
            role.setStatus(1);
        }
        roleRepository.insert(role);

        if (permissionIds != null && !permissionIds.isEmpty()) {
            for (Long permissionId : permissionIds) {
                SysRolePermission rolePermission = new SysRolePermission();
                rolePermission.setRoleId(role.getId());
                rolePermission.setPermissionId(permissionId);
                rolePermissionRepository.insert(rolePermission);
            }
        }

        operationLogService.logOperationWithDetail(
                authService.getCurrentUserId(),
                authService.getCurrentUsername(),
                authService.getCurrentUser() != null ? authService.getCurrentUser().getRealName() : null,
                SysOperationLog.OperationType.CREATE.getCode(),
                SysOperationLog.OperationModule.ROLE.getCode(),
                "创建角色: " + role.getRoleName(),
                role.getId().toString(),
                "ROLE",
                null,
                role,
                request
        );

        return role;
    }

    @Transactional
    public SysRole updateRole(Long id, SysRole role, List<Long> permissionIds, HttpServletRequest request) {
        SysRole oldRole = roleRepository.selectById(id);
        if (oldRole == null) {
            throw new RuntimeException("角色不存在");
        }

        role.setId(id);
        roleRepository.updateById(role);

        if (permissionIds != null) {
            rolePermissionRepository.deleteByRoleId(id);
            for (Long permissionId : permissionIds) {
                SysRolePermission rolePermission = new SysRolePermission();
                rolePermission.setRoleId(id);
                rolePermission.setPermissionId(permissionId);
                rolePermissionRepository.insert(rolePermission);
            }
        }

        SysRole updatedRole = roleRepository.selectById(id);
        operationLogService.logOperationWithDetail(
                authService.getCurrentUserId(),
                authService.getCurrentUsername(),
                authService.getCurrentUser() != null ? authService.getCurrentUser().getRealName() : null,
                SysOperationLog.OperationType.UPDATE.getCode(),
                SysOperationLog.OperationModule.ROLE.getCode(),
                "更新角色: " + role.getRoleName(),
                id.toString(),
                "ROLE",
                oldRole,
                updatedRole,
                request
        );

        return updatedRole;
    }

    @Transactional
    public void deleteRole(Long id, HttpServletRequest request) {
        SysRole role = roleRepository.selectById(id);
        if (role == null) {
            throw new RuntimeException("角色不存在");
        }

        roleRepository.deleteById(id);
        rolePermissionRepository.deleteByRoleId(id);

        operationLogService.logOperationWithDetail(
                authService.getCurrentUserId(),
                authService.getCurrentUsername(),
                authService.getCurrentUser() != null ? authService.getCurrentUser().getRealName() : null,
                SysOperationLog.OperationType.DELETE.getCode(),
                SysOperationLog.OperationModule.ROLE.getCode(),
                "删除角色: " + role.getRoleName(),
                id.toString(),
                "ROLE",
                role,
                null,
                request
        );
    }

    public List<SysPermission> getRolePermissions(Long roleId) {
        return permissionRepository.findPermissionsByRoleId(roleId);
    }
}
