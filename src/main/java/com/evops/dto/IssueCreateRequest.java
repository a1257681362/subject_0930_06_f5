package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/** 维修领用单创建：必须绑定风机与缺陷单，并指定领用备件 */
@Data
public class IssueCreateRequest {
    /** 领用单号；为空时自动生成 */
    private String issueNo;
    @NotNull(message = "风机不能为空")
    private Long turbineId;
    @NotNull(message = "缺陷单不能为空")
    private Long defectId;
    @NotBlank(message = "领用人不能为空")
    private String issuedBy;
    @NotEmpty(message = "领用备件不能为空")
    private List<IssueItemDto> items;
}
