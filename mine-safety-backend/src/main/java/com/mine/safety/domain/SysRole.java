package com.mine.safety.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("role_code")
    private String roleCode;

    @TableField("role_name")
    private String roleName;

    @TableField("description")
    private String description;

    @TableField("sort_order")
    private Integer sortOrder = 0;

    @TableField("status")
    private Integer status = 1;

    @TableLogic
    @TableField("deleted")
    private Integer deleted = 0;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<SysPermission> permissions;

    public enum RoleCode {
        MINE_MANAGER("MINE_MANAGER", "矿长"),
        SAFETY_DIRECTOR("SAFETY_DIRECTOR", "安全科长"),
        SHIFT_SUPERVISOR("SHIFT_SUPERVISOR", "值班员"),
        MAINTENANCE_WORKER("MAINTENANCE_WORKER", "维修工"),
        ADMIN("ADMIN", "系统管理员");

        private final String code;
        private final String name;

        RoleCode(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }
    }
}
