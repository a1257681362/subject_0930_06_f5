package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import com.evops.enums.RuleVersionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 备件业务规则版本：规格、单位、安全库存、保质期。
 * 规则调整只能生成新版本：旧版本退役但保留，历史备件追踪沿用入库时的版本，结论不可变。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_spare_part_rule")
public class SparePartRule extends BaseEntity {
    private Long partId;
    /** 版本号，备件内从 1 递增 */
    private Integer versionNo;
    private String specification;
    private String unit;
    private Double safetyStock;
    /** 保质期天数（可空，表示不限） */
    private Integer shelfLifeDays;
    private RuleVersionStatus status;
    private LocalDateTime publishedAt;
    private String remark;
}
