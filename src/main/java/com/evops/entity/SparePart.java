package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.SpareCategory;
import com.evops.enums.TrackingMode;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 备件定义：叶片螺栓 / 传感器 / 涂层材料。
 * 追踪模式（序列号/批次）一经入库使用即锁定（trackingLocked=true），不能再改。
 * currentRuleVersion 指向当前生效规则版本；规则调整只生成新版本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_spare_part")
public class SparePart extends BaseEntity {
    /** 备件编号（物料编码），唯一 */
    private String partNo;
    private String name;
    private SpareCategory category;
    /** 追踪模式：SERIAL 序列号 / BATCH 批次 */
    private TrackingMode trackingMode;
    /** 追踪模式是否已锁定（已有入库记录后锁定） */
    private Boolean trackingLocked;
    /** 当前生效规则版本号 */
    private Integer currentRuleVersion;
}
