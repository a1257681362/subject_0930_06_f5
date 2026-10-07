package com.evops.enums;

/** 替换旧件去向：待检 / 修复再用 / 报废 / 返厂 */
public enum OldPartDestination {
    /** 待检验后再定去向 */
    INSPECTION,
    /** 修复后再利用 */
    REUSE,
    /** 报废 */
    SCRAP,
    /** 返厂（供应商索赔/返修） */
    SUPPLIER_R
}
