package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.IssueCreateRequest;
import com.evops.dto.IssueDetailDto;
import com.evops.dto.ReturnCreateRequest;
import com.evops.dto.ReturnInspectRequest;
import com.evops.dto.SpareReplacementRequest;
import com.evops.entity.SpareIssue;
import com.evops.entity.SpareReplacement;
import com.evops.entity.SpareReturn;
import com.evops.service.SpareIssueService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 维修领用：绑定风机与缺陷单，出库、替换装机（旧件去向/新件状态）、退库验收。
 */
@RestController
@RequestMapping("/api")
public class SpareIssueController {

    private final SpareIssueService spareIssueService;

    public SpareIssueController(SpareIssueService spareIssueService) {
        this.spareIssueService = spareIssueService;
    }

    /** 创建维修领用单（绑定风机与缺陷单，同时预占库存） */
    @PostMapping("/spare-issues")
    public ApiResponse<SpareIssue> create(@Validated @RequestBody IssueCreateRequest req) {
        return ApiResponse.ok(spareIssueService.createIssue(req));
    }

    /** 领用单详情（明细 + 替换记录 + 退库验收记录） */
    @GetMapping("/spare-issues/{id}")
    public ApiResponse<IssueDetailDto> detail(@PathVariable Long id) {
        return ApiResponse.ok(spareIssueService.detail(id));
    }

    /** 出库发放 */
    @PostMapping("/spare-issues/{id}/issue-out")
    public ApiResponse<SpareIssue> issueOut(@PathVariable Long id) {
        return ApiResponse.ok(spareIssueService.issueOut(id));
    }

    /** 替换装机：同事务更新库存/维修进度/资产档案，记录旧件去向与新件状态 */
    @PostMapping("/spare-issues/{id}/replacements")
    public ApiResponse<SpareReplacement> install(@PathVariable Long id,
                                                 @Validated @RequestBody SpareReplacementRequest req) {
        return ApiResponse.ok(spareIssueService.install(id, req));
    }

    /** 发起退库（UNUSED 未用退库 / OLD_PART 旧件回收），进入待验收 */
    @PostMapping("/spare-issues/{id}/returns")
    public ApiResponse<SpareReturn> createReturn(@PathVariable Long id,
                                                 @Validated @RequestBody ReturnCreateRequest req) {
        return ApiResponse.ok(spareIssueService.createReturn(id, req));
    }

    /** 退库验收：合格回库 / 不合格报废 */
    @PostMapping("/spare-returns/{returnId}/inspect")
    public ApiResponse<SpareReturn> inspect(@PathVariable Long returnId,
                                            @Validated @RequestBody ReturnInspectRequest req) {
        return ApiResponse.ok(spareIssueService.inspectReturn(returnId, req));
    }
}
