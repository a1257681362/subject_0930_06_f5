package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/** 退库验收入参：pass=true 合格回库；false 不合格报废 */
@Data
public class ReturnInspectRequest {
    @NotNull(message = "验收结论不能为空")
    private Boolean pass;
    @NotBlank(message = "验收人不能为空")
    private String inspector;
    private String opinion;
}
