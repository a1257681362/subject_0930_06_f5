package com.evops.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 备件入库入参：
 * 序列号件填 serialNo（一件一码，数量恒 1）；
 * 批次件填 batchNo 与 quantity。
 */
@Data
public class InboundRequest {
    /** 库存流水号；为空时自动生成 */
    private String stockNo;
    /** 序列号件必填 */
    private String serialNo;
    /** 批次件必填 */
    private String batchNo;
    /** 批次数量；序列号件可不填（恒为 1） */
    private Double quantity;
    /** 入库时刻；为空取当前时刻 */
    private LocalDateTime inboundAt;
}
