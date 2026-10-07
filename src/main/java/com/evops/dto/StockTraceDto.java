package com.evops.dto;

import com.evops.entity.SparePart;
import com.evops.entity.SparePartRule;
import com.evops.entity.SpareReplacement;
import com.evops.entity.SpareReturn;
import com.evops.entity.SpareStock;
import lombok.Data;

import java.util.List;

/**
 * 备件序列/批次追踪：入库时固化的规则版本与规格快照、当前规则版本、
 * 装机资产档案（风机/叶片/时刻）、替换记录与退库验收记录。
 */
@Data
public class StockTraceDto {
    private SpareStock stock;
    private SparePart part;
    /** 入库时固化的规则版本（历史追踪依据，不随后续调整改变） */
    private SparePartRule inboundRule;
    /** 当前生效规则版本 */
    private SparePartRule currentRule;
    /** 作为新件参与的替换记录 */
    private List<SpareReplacement> replacements;
    /** 退库验收记录 */
    private List<SpareReturn> returns;
}
