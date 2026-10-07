package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 领用明细：领用单 × 备件实例唯一；记录本次领用数量与替换后新件状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_spare_issue_item")
public class SpareIssueItem extends BaseEntity {
    private Long issueId;
    private Long stockId;
    /** 领用数量：序列号件为 1；批次件为本批次领用数量 */
    private Double quantity;
    /** 替换后记录的新件状态，如 INSTALLED */
    private String newPartStatus;
}
