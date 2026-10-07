package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.ReturnSource;
import com.evops.enums.ReturnStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 退库验收记录：退库必须经过验收。
 * PENDING 待验收 -> ACCEPTED 合格回库（IN_STOCK）/ REJECTED 不合格报废（SCRAPPED）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_spare_return")
public class SpareReturn extends BaseEntity {
    private String returnNo;
    private Long issueId;
    private Long stockId;
    /** 退库来源：UNUSED 未装机 / OLD_PART 旧件回收 */
    private ReturnSource source;
    private Double quantity;
    private String reason;
    private ReturnStatus status;
    /** 验收结论：PASS / FAIL */
    private String result;
    private String inspector;
    private LocalDateTime inspectedAt;
    private String inspectOpinion;
}
