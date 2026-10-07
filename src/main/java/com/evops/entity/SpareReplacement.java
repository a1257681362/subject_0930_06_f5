package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.OldPartDestination;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 备件替换记录：旧件去向与新件装机状态。
 * 替换在同一事务内联动更新：库存（新件 INSTALLED）、维修进度（缺陷回填维修结果）、
 * 资产档案（备件装机位置/时刻）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_spare_replacement")
public class SpareReplacement extends BaseEntity {
    private Long issueId;
    private Long defectId;
    private Long turbineId;
    private Long bladeId;
    /** 旧件名称 */
    private String oldPartName;
    /** 旧件序列号（可空，旧涂层等批次材料无序列号） */
    private String oldSerialNo;
    /** 旧件去向 */
    private OldPartDestination oldPartDestination;
    private String oldPartRemark;
    /** 新件库存实例 */
    private Long newStockId;
    private String newSerialNo;
    /** 新件状态 */
    private String newPartStatus;
    private LocalDateTime replacedAt;
}
