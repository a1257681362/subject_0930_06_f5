package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.RuleAdjustRequest;
import com.evops.dto.SparePartCreateRequest;
import com.evops.entity.SparePart;
import com.evops.entity.SparePartRule;
import com.evops.service.SparePartService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 备件定义与规则版本：叶片螺栓 / 传感器 / 涂层材料。
 * 规则调整只能生成新版本，历史版本退役保留、内容不可改。
 */
@RestController
@RequestMapping("/api/spare-parts")
public class SparePartController {

    private final SparePartService sparePartService;

    public SparePartController(SparePartService sparePartService) {
        this.sparePartService = sparePartService;
    }

    /** 备件建档（含首版本规则 V1，直接发布） */
    @PostMapping
    public ApiResponse<SparePart> create(@Validated @RequestBody SparePartCreateRequest req) {
        return ApiResponse.ok(sparePartService.create(req));
    }

    /** 备件列表 */
    @GetMapping
    public ApiResponse<List<SparePart>> list() {
        return ApiResponse.ok(sparePartService.listAll());
    }

    /** 备件详情 */
    @GetMapping("/{id}")
    public ApiResponse<SparePart> detail(@PathVariable Long id) {
        return ApiResponse.ok(sparePartService.mustGet(id));
    }

    /** 全部规则版本（含已退役保留的历史版本） */
    @GetMapping("/{id}/rules")
    public ApiResponse<List<SparePartRule>> rules(@PathVariable Long id) {
        return ApiResponse.ok(sparePartService.listRules(id));
    }

    /** 规则调整：生成新版本（旧版本退役保留，不改动其内容与历史追踪结论） */
    @PostMapping("/{id}/rule-versions")
    public ApiResponse<SparePartRule> adjustRule(@PathVariable Long id,
                                                  @Validated @RequestBody RuleAdjustRequest req) {
        return ApiResponse.ok(sparePartService.adjustRule(id, req));
    }
}
