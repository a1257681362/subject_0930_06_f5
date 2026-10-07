package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.StockStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 备件库存实例：
 * 序列号件一件一码（serial_no 有值，quantity 恒为 1）；
 * 批次件一条批次记录（batch_no 有值，quantity 可分次领用）。
 * 入库时固化规则版本与规格快照，规则后续调整不影响历史序列追踪。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_spare_stock")
public class SpareStock extends BaseEntity {
    /** 库存流水号（入库单号），唯一 */
    private String stockNo;
    private Long partId;
    /** 序列号（序列号件必填） */
    private String serialNo;
    /** 批次号（批次件必填） */
    private String batchNo;
    /** 数量：序列号件恒为 1；批次件为批次余量 */
    private Double quantity;
    /** 入库时生效规则版本（快照依据，永不改变） */
    private Integer ruleVersionSnapshot;
    /** 规格快照 */
    private String specSnapshot;
    /** 单位快照 */
    private String unitSnapshot;
    private LocalDateTime inboundAt;
    /** 批次到期时刻（入库时刻 + 保质期），可空 */
    private LocalDateTime expireAt;
    /** 装机风机（装机后固化） */
    private Long installedTurbineId;
    /** 装机叶片（可空） */
    private Long installedBladeId;
    private LocalDateTime installedAt;
    private StockStatus status;
}
