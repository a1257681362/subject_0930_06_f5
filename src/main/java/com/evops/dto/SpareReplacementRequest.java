package com.evops.dto;

import com.evops.enums.OldPartDestination;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 备件替换装机入参：新件（领用明细中的备件）装机，登记旧件去向与维修结果。
 * 库存、维修进度与资产档案在同一事务内更新。
 */
@Data
public class SpareReplacementRequest {
    @NotNull(message = "新件库存实例不能为空")
    private Long stockId;
    /** 装机叶片，可空（如叶根螺栓等不直接挂叶片档案的部位） */
    private Long bladeId;
    @NotBlank(message = "旧件名称不能为空")
    private String oldPartName;
    /** 旧件序列号，可空（涂层等批次材料无序列号） */
    private String oldSerialNo;
    @NotNull(message = "旧件去向不能为空")
    private OldPartDestination oldPartDestination;
    private String oldPartRemark;
    /** 替换时刻；为空取当前时刻 */
    private LocalDateTime replacedAt;
    /** 维修结果（回填缺陷单，驱动维修进度） */
    private String repairResult;
}
