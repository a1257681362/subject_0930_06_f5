package com.evops.enums;

/**
 * 备件库存状态。
 * IN_STOCK 可用；RESERVED 已被领用单预留；INSTALLED 已装机（不能再次出库）；
 * RETURN_PENDING 退库待验收；SCRAPPED 报废（验收不合格或旧件报废）。
 */
public enum StockStatus {
    IN_STOCK,
    RESERVED,
    INSTALLED,
    RETURN_PENDING,
    SCRAPPED
}
