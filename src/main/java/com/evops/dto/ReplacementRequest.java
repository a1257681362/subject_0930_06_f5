package com.evops.dto;

import com.evops.enums.BladePosition;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** 换件入参：旧件方位 + 新叶片档案 */
@Data
public class ReplacementRequest {
    @NotNull(message = "换件方位不能为空")
    private BladePosition bladePosition;
    @NotNull(message = "新叶片不能为空")
    private BladeRequest newBlade;
    @NotNull(message = "换件时刻不能为空")
    private LocalDateTime replacedAt;
    private String reason;
}
