package com.evops.enums;

/**
 * 备件追踪模式：
 * SERIAL 按序列号追踪（一件一码，如螺栓、传感器）；
 * BATCH 按批次追踪（批次 + 数量，如涂层材料）。
 * 备件一经入库使用即锁定，不能再改追踪模式。
 */
public enum TrackingMode {
    SERIAL,
    BATCH
}
