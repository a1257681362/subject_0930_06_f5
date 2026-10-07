package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 备件规则调整入参：调整只能生成新版本（旧版本退役保留），不改动旧版本内容。
 */
@Data
public class RuleAdjustRequest {
    @NotBlank(message = "规格不能为空")
    private String specification;
    @NotBlank(message = "单位不能为空")
    private String unit;
    private Double safetyStock;
    private Integer shelfLifeDays;
    /** 调整说明 */
    private String remark;
}
