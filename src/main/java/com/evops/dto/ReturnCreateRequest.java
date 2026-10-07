package com.evops.dto;

import com.evops.enums.ReturnSource;
import lombok.Data;

import javax.validation.constraints.NotNull;

/** 退库发起入参：UNUSED 未装机退库 / OLD_PART 旧件回收（来源替换记录） */
@Data
public class ReturnCreateRequest {
    @NotNull(message = "退库来源不能为空")
    private ReturnSource source;
    /** UNUSED：退回的备件实例（须为本领用单明细） */
    private Long stockId;
    /** OLD_PART：来源替换记录 */
    private Long replacementId;
    /** 退库数量；序列号件可不填（恒为 1） */
    private Double quantity;
    private String reason;
}
