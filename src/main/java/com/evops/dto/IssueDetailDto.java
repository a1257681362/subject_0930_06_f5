package com.evops.dto;

import com.evops.entity.SpareIssue;
import com.evops.entity.SpareIssueItem;
import com.evops.entity.SpareReplacement;
import com.evops.entity.SpareReturn;
import lombok.Data;

import java.util.List;

/** 领用单详情：主单 + 明细 + 替换记录 + 退库验收记录 */
@Data
public class IssueDetailDto {
    private SpareIssue issue;
    private List<SpareIssueItem> items;
    private List<SpareReplacement> replacements;
    private List<SpareReturn> returns;
}
