package com.mine.safety.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mine.safety.domain.SysOperationLog;
import com.mine.safety.domain.SysRole;
import com.mine.safety.domain.SysUser;
import com.mine.safety.domain.SysUserRole;
import com.mine.safety.repository.SysRoleRepository;
import com.mine.safety.repository.SysUserRepository;
import com.mine.safety.repository.SysUserRoleRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;
    private final AuthService authService;

    public IPage<SysUser> getUsers(String keyword, Integer status, int page, int size) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getRealName, keyword)
                    .or().like(SysUser::getPhone, keyword));
        }
        if (status != null) {
            wrapper.eq(SysUser::getStatus, status);
        }
        wrapper.orderByDesc(SysUser::getCreatedAt);
        IPage<SysUser> userPage = userRepository.selectPage(new Page<>(page, size), wrapper);

        for (SysUser user : userPage.getRecords()) {
            user.setRoles(roleRepository.findRolesByUserId(user.getId()));
        }

        return userPage;
    }

    public SysUser getUserById(Long id) {
        SysUser user = userRepository.selectById(id);
        if (user != null) {
            user.setRoles(roleRepository.findRolesByUserId(id));
        }
        return user;
    }

    public SysUser getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional
    public SysUser createUser(SysUser user, List<Long> roleIds, HttpServletRequest request) {
        SysUser existUser = userRepository.findByUsername(user.getUsername());
        if (existUser != null) {
            throw new RuntimeException("用户名已存在");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        userRepository.insert(user);

        if (roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds) {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(user.getId());
                userRole.setRoleId(roleId);
                userRoleRepository.insert(userRole);
            }
        }

        operationLogService.logOperationWithDetail(
                authService.getCurrentUserId(),
                authService.getCurrentUsername(),
                authService.getCurrentUser() != null ? authService.getCurrentUser().getRealName() : null,
                SysOperationLog.OperationType.CREATE.getCode(),
                SysOperationLog.OperationModule.USER.getCode(),
                "创建用户: " + user.getUsername(),
                user.getId().toString(),
                "USER",
                null,
                user,
                request
        );

        return user;
    }

    @Transactional
    public SysUser updateUser(Long id, SysUser user, List<Long> roleIds, HttpServletRequest request) {
        SysUser oldUser = userRepository.selectById(id);
        if (oldUser == null) {
            throw new RuntimeException("用户不存在");
        }

        user.setId(id);
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(null);
        }
        userRepository.updateById(user);

        if (roleIds != null) {
            userRoleRepository.deleteByUserId(id);
            for (Long roleId : roleIds) {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(id);
                userRole.setRoleId(roleId);
                userRoleRepository.insert(userRole);
            }
        }

        SysUser updatedUser = userRepository.selectById(id);
        operationLogService.logOperationWithDetail(
                authService.getCurrentUserId(),
                authService.getCurrentUsername(),
                authService.getCurrentUser() != null ? authService.getCurrentUser().getRealName() : null,
                SysOperationLog.OperationType.UPDATE.getCode(),
                SysOperationLog.OperationModule.USER.getCode(),
                "更新用户: " + user.getUsername(),
                id.toString(),
                "USER",
                oldUser,
                updatedUser,
                request
        );

        return updatedUser;
    }

    @Transactional
    public void deleteUser(Long id, HttpServletRequest request) {
        SysUser user = userRepository.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        userRepository.deleteById(id);
        userRoleRepository.deleteByUserId(id);

        operationLogService.logOperationWithDetail(
                authService.getCurrentUserId(),
                authService.getCurrentUsername(),
                authService.getCurrentUser() != null ? authService.getCurrentUser().getRealName() : null,
                SysOperationLog.OperationType.DELETE.getCode(),
                SysOperationLog.OperationModule.USER.getCode(),
                "删除用户: " + user.getUsername(),
                id.toString(),
                "USER",
                user,
                null,
                request
        );
    }

    public List<SysRole> getUserRoles(Long userId) {
        return roleRepository.findRolesByUserId(userId);
    }
}
