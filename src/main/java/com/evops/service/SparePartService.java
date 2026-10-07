package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.dto.RuleAdjustRequest;
import com.evops.dto.SparePartCreateRequest;
import com.evops.entity.SparePart;
import com.evops.entity.SparePartRule;
import com.evops.enums.RuleVersionStatus;
import com.evops.mapper.SparePartMapper;
import com.evops.mapper.SparePartRuleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 备件定义与规则版本：
 * 叶片螺栓 / 传感器 / 涂层材料，建档时生成首个规则版本 V1（直接发布）。
 *
 * 口径：规则调整只能生成新版本——旧版本退役但保留，内容不可修改；
 * 历史备件序列追踪沿用入库时固化的旧版规则（见 SpareStock.ruleVersionSnapshot），旧结论不可变。
 */
@Service
public class SparePartService extends ServiceImpl<SparePartMapper, SparePart> {

    private final SparePartRuleMapper ruleMapper;
    private final AuditService auditService;

    public SparePartService(SparePartRuleMapper ruleMapper, AuditService auditService) {
        this.ruleMapper = ruleMapper;
        this.auditService = auditService;
    }

    /** 备件建档：编号唯一，同时发布首个规则版本 V1 */
    @Transactional
    public SparePart create(SparePartCreateRequest req) {
        Long dup = count(new QueryWrapper<SparePart>().eq("part_no", req.getPartNo()));
        if (dup > 0) {
            throw new IllegalArgumentException("备件编号已存在: " + req.getPartNo());
        }
        SparePart part = new SparePart();
        part.setPartNo(req.getPartNo());
        part.setName(req.getName());
        part.setCategory(req.getCategory());
        part.setTrackingMode(req.getTrackingMode());
        part.setTrackingLocked(false);
        part.setCurrentRuleVersion(1);
        save(part);

        SparePartRule v1 = newRule(part.getId(), 1, req.getSpecification(), req.getUnit(),
                req.getSafetyStock() != null ? req.getSafetyStock() : 0d,
                req.getShelfLifeDays(), "建档初始版本");
        v1.setStatus(RuleVersionStatus.PUBLISHED);
        v1.setPublishedAt(LocalDateTime.now());
        ruleMapper.insert(v1);

        auditService.record("SPARE_PART", part.getId(), "PART_CREATE",
                "备件建档 " + req.getPartNo() + "，追踪模式 " + req.getTrackingMode()
                        + "，规则 V1 发布", null);
        return part;
    }

    /**
     * 规则调整：只能生成新版本。当前发布版本退役保留（内容不动），
     * 按其内容叠加调整项生成 V(n+1) 并发布，备件指向新版本。
     */
    @Transactional
    public SparePartRule adjustRule(Long partId, RuleAdjustRequest req) {
        SparePart part = mustGet(partId);
        SparePartRule current = currentRule(partId);

        current.setStatus(RuleVersionStatus.RETIRED);
        ruleMapper.updateById(current);

        int nextVersion = current.getVersionNo() + 1;
        SparePartRule next = newRule(partId, nextVersion, req.getSpecification(), req.getUnit(),
                req.getSafetyStock() != null ? req.getSafetyStock() : current.getSafetyStock(),
                req.getShelfLifeDays() != null ? req.getShelfLifeDays() : current.getShelfLifeDays(),
                req.getRemark());
        next.setStatus(RuleVersionStatus.PUBLISHED);
        next.setPublishedAt(LocalDateTime.now());
        ruleMapper.insert(next);

        part.setCurrentRuleVersion(nextVersion);
        updateById(part);

        auditService.record("SPARE_PART", partId, "RULE_ADJUST",
                "规则调整生成新版本 V" + nextVersion + "（V" + current.getVersionNo()
                        + " 退役保留，旧版本内容不可改）", null);
        return next;
    }

    private SparePartRule newRule(Long partId, int versionNo, String specification, String unit,
                                  Double safetyStock, Integer shelfLifeDays, String remark) {
        SparePartRule rule = new SparePartRule();
        rule.setPartId(partId);
        rule.setVersionNo(versionNo);
        rule.setSpecification(specification);
        rule.setUnit(unit);
        rule.setSafetyStock(safetyStock);
        rule.setShelfLifeDays(shelfLifeDays);
        rule.setStatus(RuleVersionStatus.DRAFT);
        rule.setRemark(remark);
        return rule;
    }

    /** 当前发布规则 */
    public SparePartRule currentRule(Long partId) {
        SparePartRule rule = ruleMapper.selectOne(new QueryWrapper<SparePartRule>()
                .eq("part_id", partId)
                .eq("status", RuleVersionStatus.PUBLISHED.name()));
        if (rule == null) {
            throw new IllegalArgumentException("备件没有已发布规则版本: " + partId);
        }
        return rule;
    }

    /** 按版本号取规则（含已退役的历史版本） */
    public SparePartRule getRule(Long partId, Integer versionNo) {
        SparePartRule rule = ruleMapper.selectOne(new QueryWrapper<SparePartRule>()
                .eq("part_id", partId)
                .eq("version_no", versionNo));
        if (rule == null) {
            throw new IllegalArgumentException("规则版本不存在: 备件 " + partId + " V" + versionNo);
        }
        return rule;
    }

    /** 全部规则版本（含已退役保留的历史版本） */
    public List<SparePartRule> listRules(Long partId) {
        mustGet(partId);
        return ruleMapper.selectList(new QueryWrapper<SparePartRule>()
                .eq("part_id", partId)
                .orderByAsc("version_no"));
    }

    public List<SparePart> listAll() {
        return list(new QueryWrapper<SparePart>().orderByAsc("id"));
    }

    public SparePart mustGet(Long id) {
        SparePart part = getById(id);
        if (part == null) {
            throw new IllegalArgumentException("备件不存在: " + id);
        }
        return part;
    }
}
