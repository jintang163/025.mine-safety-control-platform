package com.mine.safety.security;

import com.mine.safety.domain.SysPermission;
import com.mine.safety.domain.SysRole;
import com.mine.safety.domain.SysUser;
import com.mine.safety.repository.SysPermissionRepository;
import com.mine.safety.repository.SysRoleRepository;
import com.mine.safety.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserRepository sysUserRepository;
    private final SysRoleRepository sysRoleRepository;
    private final SysPermissionRepository sysPermissionRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = sysUserRepository.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        List<SysRole> roles = sysRoleRepository.findRolesByUserId(user.getId());
        user.setRoles(roles);

        List<SysPermission> permissions = sysPermissionRepository.findPermissionsByUserId(user.getId());
        user.setPermissions(permissions);

        log.debug("Loaded user: {} with {} roles and {} permissions",
                username, roles.size(), permissions.size());

        return user;
    }
}
