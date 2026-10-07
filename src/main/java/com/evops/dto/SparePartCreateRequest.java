package com.evops.dto;

import com.evops.enums.SpareCategory;
import com.evops.enums.TrackingMode;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/** 备件建档入参：同时生成首个规则版本 V1（直接发布） */
@Data
public class SparePartCreateRequest {
    @NotBlank(message = "备件编号不能为空")
    private String partNo;
    @NotBlank(message = "备件名称不能为空")
    private String name;
    @NotNull(message = "备件类别不能为空")
    private SpareCategory category;
    @NotNull(message = "追踪模式不能为空")
    private TrackingMode trackingMode;
    @NotBlank(message = "规格不能为空")
    private String specification;
    @NotBlank(message = "单位不能为空")
    private String unit;
    /** 安全库存，默认 0 */
    private Double safetyStock;
    /** 保质期天数，可空表示不限 */
    private Integer shelfLifeDays;
}
