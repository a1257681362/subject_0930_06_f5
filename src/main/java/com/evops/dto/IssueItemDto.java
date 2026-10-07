package com.evops.dto;

import lombok.Data;

/** 领用明细：备件实例 + 本次领用数量（序列号件恒为 1） */
@Data
public class IssueItemDto {
    private Long stockId;
    /** 领用数量；序列号件可不填（恒为 1），批次件必填 */
    private Double quantity;
}
