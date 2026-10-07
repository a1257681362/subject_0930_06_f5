package com.evops.enums;

/** 退库来源：未装机退库 / 装机后旧件回收入库 */
public enum ReturnSource {
    /** 领用后未装机直接退库 */
    UNUSED,
    /** 替换换下的旧件回收 */
    OLD_PART
}
