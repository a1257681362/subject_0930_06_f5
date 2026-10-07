package com.evops.enums;

/**
 * 维修领用单状态：
 * RESERVED 已预留未出库；ISSUED 已出库待装机；COMPLETED 全部明细已替换装机；
 * RETURNING 存在退库流程进行中；CLOSED 领用结案。
 */
public enum IssueStatus {
    RESERVED,
    ISSUED,
    COMPLETED,
    RETURNING,
    CLOSED
}
