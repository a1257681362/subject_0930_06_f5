package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.IssueStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 维修领用单：必须绑定风机与缺陷单。
 * RESERVED 预留 -> ISSUED 出库 -> COMPLETED 全部装机 -> CLOSED 结案；
 * 退库流程进行中为 RETURNING。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_spare_issue")
public class SpareIssue extends BaseEntity {
    private String issueNo;
    private Long turbineId;
    /** 绑定的缺陷单（必填） */
    private Long defectId;
    private String issuedBy;
    private LocalDateTime issuedAt;
    private IssueStatus status;
}
