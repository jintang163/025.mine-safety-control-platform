package com.mine.safety.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApprovalActionDTO {
    @NotBlank(message = "审批编号不能为空")
    private String approvalNo;

    @NotNull(message = "审批结果不能为空")
    private Integer result;

    @NotBlank(message = "审批人不能为空")
    private String approver;

    private String approveComment;
}
