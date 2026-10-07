package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.dto.IssueCreateRequest;
import com.evops.dto.IssueDetailDto;
import com.evops.dto.IssueItemDto;
import com.evops.dto.RepairRequest;
import com.evops.dto.ReturnCreateRequest;
import com.evops.dto.ReturnInspectRequest;
import com.evops.dto.SpareReplacementRequest;
import com.evops.entity.Blade;
import com.evops.entity.Defect;
import com.evops.entity.InspectionOrder;
import com.evops.entity.SpareIssue;
import com.evops.entity.SpareIssueItem;
import com.evops.entity.SparePart;
import com.evops.entity.SparePartRule;
import com.evops.entity.SpareReplacement;
import com.evops.entity.SpareReturn;
import com.evops.entity.SpareStock;
import com.evops.enums.DefectStatus;
import com.evops.enums.OldPartDestination;
import com.evops.enums.IssueStatus;
import com.evops.enums.ReturnSource;
import com.evops.enums.ReturnStatus;
import com.evops.enums.StockStatus;
import com.evops.enums.TrackingMode;
import com.evops.mapper.SpareIssueItemMapper;
import com.evops.mapper.SpareIssueMapper;
import com.evops.mapper.SpareReplacementMapper;
import com.evops.mapper.SpareReturnMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 备件维修领用：绑定风机与缺陷单；替换装机登记旧件去向与新件状态；退库必须验收。
 *
 * 口径：
 * - 领用即预占库存（序列号件置 RESERVED；批次件扣减余量），已装机（INSTALLED）/
 *   待验收（RETURN_PENDING）备件不能再次出库；
 * - 替换装机在同一事务内更新三处：库存（新件 INSTALLED + 风机/叶片/时刻资产档案）、
 *   维修进度（缺陷回填维修结果、工单推进）、替换留痕（旧件去向 + 新件状态）；
 * - 退库（未用退库/旧件回收）生成待验收记录，验收合格才回库，不合格报废；
 * - 一张领用单全部明细装机后 COMPLETED；存在进行中退库时 RETURNING。
 */
@Service
public class SpareIssueService extends ServiceImpl<SpareIssueMapper, SpareIssue> {

    private final TurbineService turbineService;
    private final InspectionService inspectionService;
    private final BladeService bladeService;
    private final SpareStockService spareStockService;
    private final SparePartService sparePartService;
    private final SpareIssueItemMapper itemMapper;
    private final SpareReplacementMapper replacementMapper;
    private final SpareReturnMapper returnMapper;
    private final AuditService auditService;

    public SpareIssueService(TurbineService turbineService, InspectionService inspectionService,
                             BladeService bladeService, SpareStockService spareStockService,
                             SparePartService sparePartService, SpareIssueItemMapper itemMapper,
                             SpareReplacementMapper replacementMapper, SpareReturnMapper returnMapper,
                             AuditService auditService) {
        this.turbineService = turbineService;
        this.inspectionService = inspectionService;
        this.bladeService = bladeService;
        this.spareStockService = spareStockService;
        this.sparePartService = sparePartService;
        this.itemMapper = itemMapper;
        this.replacementMapper = replacementMapper;
        this.returnMapper = returnMapper;
        this.auditService = auditService;
    }

    /**
     * 创建维修领用单：绑定风机与缺陷单（缺陷单须属于该风机），同时预占库存。
     */
    @Transactional
    public SpareIssue createIssue(IssueCreateRequest req) {
        turbineService.mustGet(req.getTurbineId());
        Defect defect = inspectionService.mustGetDefect(req.getDefectId());
        if (defect.getStatus() == DefectStatus.ACCEPTED) {
            throw new IllegalArgumentException("缺陷已关闭，不能再领用备件: " + defect.getId());
        }
        InspectionOrder order = inspectionService.mustGetOrder(defect.getOrderId());
        if (!order.getTurbineId().equals(req.getTurbineId())) {
            throw new IllegalArgumentException("缺陷单不属于该风机，不能绑定领用: " + req.getDefectId());
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new IllegalArgumentException("领用备件不能为空");
        }

        String issueNo = StringUtils.hasText(req.getIssueNo())
                ? req.getIssueNo()
                : "IS" + System.currentTimeMillis();
        Long dup = count(new QueryWrapper<SpareIssue>().eq("issue_no", issueNo));
        if (dup > 0) {
            throw new IllegalArgumentException("领用单号已存在: " + issueNo);
        }

        SpareIssue issue = new SpareIssue();
        issue.setIssueNo(issueNo);
        issue.setTurbineId(req.getTurbineId());
        issue.setDefectId(req.getDefectId());
        issue.setIssuedBy(req.getIssuedBy());
        issue.setStatus(IssueStatus.RESERVED);
        save(issue);

        Set<Long> seen = new HashSet<>();
        for (IssueItemDto itemDto : req.getItems()) {
            if (itemDto.getStockId() == null || !seen.add(itemDto.getStockId())) {
                throw new IllegalArgumentException("领用备件重复或缺少库存实例");
            }
            reserveItem(issue.getId(), itemDto);
        }

        auditService.record("SPARE_ISSUE", issue.getId(), "ISSUE_CREATE",
                "维修领用单创建，绑定风机 " + req.getTurbineId() + " 缺陷单 " + req.getDefectId()
                        + "，明细 " + req.getItems().size() + " 条", req.getIssuedBy());
        return issue;
    }

    /** 预占一条明细：序列号件整件预留；批次件扣减余量；已装机/待验收不能出库 */
    private void reserveItem(Long issueId, IssueItemDto dto) {
        SpareStock stock = spareStockService.mustGet(dto.getStockId());
        if (stock.getStatus() == StockStatus.INSTALLED) {
            throw new IllegalArgumentException("已安装备件不能再次出库: " + stock.getStockNo());
        }
        if (stock.getStatus() == StockStatus.SCRAPPED) {
            throw new IllegalArgumentException("已报废备件不能出库: " + stock.getStockNo());
        }
        if (stock.getStatus() == StockStatus.RETURN_PENDING) {
            throw new IllegalArgumentException("备件退库待验收，不能出库: " + stock.getStockNo());
        }

        double qty;
        SparePart part = sparePartService.mustGet(stock.getPartId());
        if (part.getTrackingMode() == TrackingMode.SERIAL) {
            qty = 1d;
        } else {
            qty = dto.getQuantity() == null ? 0d : dto.getQuantity();
            if (qty <= 0) {
                throw new IllegalArgumentException("批次领用数量必须为正数");
            }
            if (qty > stock.getQuantity()) {
                throw new IllegalArgumentException("批次余量不足: " + stock.getBatchNo()
                        + " 余量 " + stock.getQuantity());
            }
        }

        stock.setQuantity(stock.getQuantity() - qty);
        // 余量为 0 时整批预留；尚有余额的批次其余数量仍可被其他领用单使用
        if (stock.getQuantity() <= 0.000001d) {
            stock.setQuantity(0d);
            stock.setStatus(StockStatus.RESERVED);
        }
        spareStockService.updateById(stock);

        SpareIssueItem item = new SpareIssueItem();
        item.setIssueId(issueId);
        item.setStockId(stock.getId());
        item.setQuantity(qty);
        itemMapper.insert(item);
    }

    /** 出库：预留备件正式发放给维修（ISSUED 后才能替换装机） */
    @Transactional
    public SpareIssue issueOut(Long issueId) {
        SpareIssue issue = mustGet(issueId);
        if (issue.getStatus() != IssueStatus.RESERVED && issue.getStatus() != IssueStatus.RETURNING) {
            throw new IllegalArgumentException("领用单状态不允许出库: " + issue.getStatus());
        }
        issue.setStatus(IssueStatus.ISSUED);
        issue.setIssuedAt(LocalDateTime.now());
        updateById(issue);
        auditService.record("SPARE_ISSUE", issueId, "ISSUE_OUT",
                "领用备件出库发放", issue.getIssuedBy());
        return issue;
    }

    /**
     * 替换装机：同一事务更新库存（新件 INSTALLED + 资产档案）、维修进度（缺陷/工单）
     * 与替换留痕（旧件去向 + 新件状态）。
     */
    @Transactional
    public SpareReplacement install(Long issueId, SpareReplacementRequest req) {
        SpareIssue issue = mustGet(issueId);
        if (issue.getStatus() != IssueStatus.ISSUED) {
            throw new IllegalArgumentException("领用单未出库或已结案，不能替换装机: " + issue.getStatus());
        }
        Defect defect = inspectionService.mustGetDefect(issue.getDefectId());
        if (defect.getStatus() == DefectStatus.ACCEPTED) {
            throw new IllegalArgumentException("缺陷已验收关闭，不能再替换装机: " + defect.getId());
        }
        SpareIssueItem item = getItem(issueId, req.getStockId());
        if (StringUtils.hasText(item.getNewPartStatus())) {
            throw new IllegalArgumentException("该备件已完成替换，不能重复装机: " + req.getStockId());
        }
        SpareStock stock = spareStockService.mustGet(req.getStockId());
        if (stock.getStatus() == StockStatus.INSTALLED) {
            throw new IllegalArgumentException("已安装备件不能再次出库: " + stock.getStockNo());
        }
        if (req.getBladeId() != null) {
            Blade blade = bladeService.mustGet(req.getBladeId());
            if (!blade.getTurbineId().equals(issue.getTurbineId())) {
                throw new IllegalArgumentException("装机叶片不属于领用风机: " + req.getBladeId());
            }
        }
        LocalDateTime replacedAt = req.getReplacedAt() != null ? req.getReplacedAt() : LocalDateTime.now();

        // 1) 库存 + 资产档案：新件状态 INSTALLED，固化风机/叶片/装机时刻。
        //    序列号件整件装机；批次件仅在整批出清（余量为 0）时置 INSTALLED 并记录资产位置，
        //    仍有余量的批次保持在库（余量可再次出库），本次装机量由领用明细与替换记录承载。
        SparePart part = sparePartService.mustGet(stock.getPartId());
        boolean serialPart = part.getTrackingMode() == TrackingMode.SERIAL;
        if (serialPart || stock.getQuantity() <= 0.000001d) {
            stock.setStatus(StockStatus.INSTALLED);
            stock.setInstalledTurbineId(issue.getTurbineId());
            stock.setInstalledBladeId(req.getBladeId());
            stock.setInstalledAt(replacedAt);
        }
        spareStockService.updateById(stock);

        // 2) 替换留痕：旧件去向 + 新件状态
        SpareReplacement replacement = new SpareReplacement();
        replacement.setIssueId(issueId);
        replacement.setDefectId(issue.getDefectId());
        replacement.setTurbineId(issue.getTurbineId());
        replacement.setBladeId(req.getBladeId());
        replacement.setOldPartName(req.getOldPartName());
        replacement.setOldSerialNo(req.getOldSerialNo());
        replacement.setOldPartDestination(req.getOldPartDestination());
        replacement.setOldPartRemark(req.getOldPartRemark());
        replacement.setNewStockId(stock.getId());
        replacement.setNewSerialNo(stock.getSerialNo());
        replacement.setNewPartStatus(StockStatus.INSTALLED.name());
        replacement.setReplacedAt(replacedAt);
        replacementMapper.insert(replacement);

        // 3) 新件状态回写领用明细
        item.setNewPartStatus(StockStatus.INSTALLED.name());
        itemMapper.updateById(item);

        // 4) 维修进度：缺陷回填维修结果并推进状态（待维修 -> 已维修，工单联动）
        String repairResult = StringUtils.hasText(req.getRepairResult())
                ? req.getRepairResult()
                : "备件替换装机：新件 " + stock.getStockNo()
                        + "，旧件去向 " + req.getOldPartDestination();
        if (defect.getStatus() == DefectStatus.OPEN) {
            RepairRequest repair = new RepairRequest();
            repair.setRepairResult(repairResult);
            inspectionService.repair(defect.getId(), repair);
        }

        // 5) 全部明细装机 -> 领用完成
        if (allItemsInstalled(issueId)) {
            issue.setStatus(IssueStatus.COMPLETED);
            updateById(issue);
        }

        auditService.record("SPARE_ISSUE", issueId, "REPLACEMENT_INSTALL",
                "替换装机：新件 " + stock.getStockNo() + " -> 风机 " + issue.getTurbineId()
                        + "；旧件「" + req.getOldPartName() + "」去向 " + req.getOldPartDestination()
                        + "；库存/维修进度/资产档案同事务更新", issue.getIssuedBy());
        return replacement;
    }

    /** 发起退库：UNUSED 未装机退库 / OLD_PART 旧件回收（须经过验收） */
    @Transactional
    public SpareReturn createReturn(Long issueId, ReturnCreateRequest req) {
        SpareIssue issue = mustGet(issueId);
        double qty;
        Long stockId;

        if (req.getSource() == ReturnSource.UNUSED) {
            if (req.getStockId() == null) {
                throw new IllegalArgumentException("未用退库必须指定备件实例");
            }
            SpareIssueItem item = getItem(issueId, req.getStockId());
            if (StockStatus.INSTALLED.name().equals(item.getNewPartStatus())) {
                throw new IllegalArgumentException("已装机备件不能按未用退库: " + req.getStockId());
            }
            qty = resolveReturnQty(item, req.getQuantity());
            assertReturnCapacity(issueId, item, qty);
            stockId = item.getStockId();

            SpareStock stock = spareStockService.mustGet(stockId);
            if (stock.getStatus() != StockStatus.RETURN_PENDING) {
                // 序列号件整件进入待验收；批次件余量本就已扣减，保持现状等待验收结论
                if (qty >= 1d && sparePartService.mustGet(stock.getPartId()).getTrackingMode()
                        == TrackingMode.SERIAL) {
                    stock.setStatus(StockStatus.RETURN_PENDING);
                    spareStockService.updateById(stock);
                }
            }
        } else {
            // 旧件回收：来源替换记录，去向须为待检/修复再用（报废与返厂不回库验收）
            if (req.getReplacementId() == null) {
                throw new IllegalArgumentException("旧件回收必须指定替换记录");
            }
            SpareReplacement replacement = replacementMapper.selectById(req.getReplacementId());
            if (replacement == null || !replacement.getIssueId().equals(issueId)) {
                throw new IllegalArgumentException("替换记录不属于该领用单: " + req.getReplacementId());
            }
            if (replacement.getOldPartDestination() != OldPartDestination.INSPECTION
                    && replacement.getOldPartDestination() != OldPartDestination.REUSE) {
                throw new IllegalArgumentException(
                        "旧件去向为 " + replacement.getOldPartDestination() + "，不进入回库验收");
            }
            stockId = replacement.getNewStockId();
            qty = req.getQuantity() != null && req.getQuantity() > 0 ? req.getQuantity() : 1d;
        }

        SpareReturn ret = new SpareReturn();
        ret.setReturnNo("RT" + System.currentTimeMillis() + "-" + stockId);
        ret.setIssueId(issueId);
        ret.setStockId(stockId);
        ret.setSource(req.getSource());
        ret.setQuantity(qty);
        ret.setReason(req.getReason());
        ret.setStatus(ReturnStatus.PENDING);
        returnMapper.insert(ret);

        // 存在进行中的退库：领用单进入 RETURNING（COMPLETED 也可有旧件回收）
        if (issue.getStatus() != IssueStatus.RETURNING) {
            issue.setStatus(IssueStatus.RETURNING);
            updateById(issue);
        }

        auditService.record("SPARE_RETURN", ret.getId(), "RETURN_CREATE",
                "退库待验收：来源 " + req.getSource() + " 数量 " + qty, issue.getIssuedBy());
        return ret;
    }

    private double resolveReturnQty(SpareIssueItem item, Double requested) {
        SpareStock stock = spareStockService.mustGet(item.getStockId());
        SparePart part = sparePartService.mustGet(stock.getPartId());
        if (part.getTrackingMode() == TrackingMode.SERIAL) {
            return 1d;
        }
        if (requested == null || requested <= 0) {
            throw new IllegalArgumentException("批次退库数量必须为正数");
        }
        return requested;
    }

    /** 退库数量不得超过该明细尚未退库处理（含待验收）的数量 */
    private void assertReturnCapacity(Long issueId, SpareIssueItem item, double qty) {
        List<SpareReturn> existing = returnMapper.selectList(new QueryWrapper<SpareReturn>()
                .eq("issue_id", issueId)
                .eq("stock_id", item.getStockId()));
        double handled = existing.stream().mapToDouble(SpareReturn::getQuantity).sum();
        if (handled + qty > item.getQuantity() + 0.000001d) {
            throw new IllegalArgumentException("退库数量超过该备件领用数量: " + item.getStockId());
        }
    }

    /**
     * 退库验收：合格回库（序列号件回 IN_STOCK；批次件补回余量；旧件回收生成在库实例），
     * 不合格报废。验收后领用单若无待处理退库，恢复其既有进度状态。
     */
    @Transactional
    public SpareReturn inspectReturn(Long returnId, ReturnInspectRequest req) {
        SpareReturn ret = returnMapper.selectById(returnId);
        if (ret == null) {
            throw new IllegalArgumentException("退库记录不存在: " + returnId);
        }
        if (ret.getStatus() != ReturnStatus.PENDING) {
            throw new IllegalArgumentException("退库已验收，不能重复验收: " + ret.getStatus());
        }
        SpareIssue issue = mustGet(ret.getIssueId());
        LocalDateTime now = LocalDateTime.now();
        ret.setInspector(req.getInspector());
        ret.setInspectedAt(now);
        ret.setInspectOpinion(req.getOpinion());

        if (Boolean.TRUE.equals(req.getPass())) {
            ret.setStatus(ReturnStatus.ACCEPTED);
            ret.setResult("PASS");
            acceptReturn(ret);
        } else {
            ret.setStatus(ReturnStatus.REJECTED);
            ret.setResult("FAIL");
            rejectReturn(ret);
        }
        returnMapper.updateById(ret);

        if (countPendingReturns(issue.getId()) == 0) {
            issue.setStatus(deriveIssueStatus(issue.getId()));
            updateById(issue);
        }

        auditService.record("SPARE_RETURN", returnId, "RETURN_INSPECT",
                "退库验收 " + (Boolean.TRUE.equals(req.getPass()) ? "合格回库" : "不合格报废")
                        + "，验收人 " + req.getInspector(), req.getInspector());
        return ret;
    }

    /** 验收合格：按来源回库 */
    private void acceptReturn(SpareReturn ret) {
        if (ret.getSource() == ReturnSource.UNUSED) {
            SpareStock stock = spareStockService.mustGet(ret.getStockId());
            SparePart part = sparePartService.mustGet(stock.getPartId());
            if (part.getTrackingMode() == TrackingMode.SERIAL) {
                stock.setStatus(StockStatus.IN_STOCK);
            } else {
                stock.setQuantity(stock.getQuantity() + ret.getQuantity());
                stock.setStatus(StockStatus.IN_STOCK);
            }
            spareStockService.updateById(stock);
        } else {
            // 旧件回收验收合格：作为修复再用备件重新入库（沿用新件物料与当前规则快照）
            SpareReplacement replacement = replacementMapper.selectOne(new QueryWrapper<SpareReplacement>()
                    .eq("issue_id", ret.getIssueId())
                    .eq("new_stock_id", ret.getStockId())
                    .orderByDesc("id")
                    .last("LIMIT 1"));
            SpareStock ref = spareStockService.mustGet(ret.getStockId());
            SparePartRule rule = sparePartService.currentRule(ref.getPartId());
            SpareStock refurbished = new SpareStock();
            refurbished.setPartId(ref.getPartId());
            refurbished.setQuantity(ret.getQuantity());
            refurbished.setRuleVersionSnapshot(rule.getVersionNo());
            refurbished.setSpecSnapshot(rule.getSpecification());
            refurbished.setUnitSnapshot(rule.getUnit());
            refurbished.setInboundAt(ret.getInspectedAt());
            refurbished.setStatus(StockStatus.IN_STOCK);
            if (sparePartService.mustGet(ref.getPartId()).getTrackingMode() == TrackingMode.SERIAL) {
                refurbished.setStockNo("RT" + ret.getId());
                refurbished.setSerialNo(replacement != null && StringUtils.hasText(replacement.getOldSerialNo())
                        ? replacement.getOldSerialNo() : "OLD-" + ret.getId());
                refurbished.setQuantity(1d);
            } else {
                refurbished.setStockNo("RT" + ret.getId());
                refurbished.setBatchNo("OLD-RT" + ret.getId());
            }
            spareStockService.save(refurbished);
        }
    }

    /** 验收不合格：序列号件/整件报废；批次件不补回余量（领用数量核销） */
    private void rejectReturn(SpareReturn ret) {
        if (ret.getSource() == ReturnSource.UNUSED) {
            SpareStock stock = spareStockService.mustGet(ret.getStockId());
            SparePart part = sparePartService.mustGet(stock.getPartId());
            if (part.getTrackingMode() == TrackingMode.SERIAL) {
                stock.setStatus(StockStatus.SCRAPPED);
                spareStockService.updateById(stock);
            }
            // 批次件余量保持扣减即核销，不写回
        }
        // OLD_PART 验收不合格：旧件报废，不生成在库实例
    }

    private long countPendingReturns(Long issueId) {
        return returnMapper.selectCount(new QueryWrapper<SpareReturn>()
                .eq("issue_id", issueId)
                .eq("status", ReturnStatus.PENDING.name()));
    }

    /** 退库结清后恢复领用单进度：全部装机为 COMPLETED，已出库为 ISSUED，否则 RESERVED */
    private IssueStatus deriveIssueStatus(Long issueId) {
        if (allItemsInstalled(issueId)) {
            return IssueStatus.COMPLETED;
        }
        SpareIssue issue = getById(issueId);
        return issue.getIssuedAt() != null ? IssueStatus.ISSUED : IssueStatus.RESERVED;
    }

    private boolean allItemsInstalled(Long issueId) {
        List<SpareIssueItem> items = itemMapper.selectList(new QueryWrapper<SpareIssueItem>()
                .eq("issue_id", issueId));
        if (items.isEmpty()) {
            return false;
        }
        return items.stream().allMatch(i -> StockStatus.INSTALLED.name().equals(i.getNewPartStatus()));
    }

    private SpareIssueItem getItem(Long issueId, Long stockId) {
        SpareIssueItem item = itemMapper.selectOne(new QueryWrapper<SpareIssueItem>()
                .eq("issue_id", issueId)
                .eq("stock_id", stockId));
        if (item == null) {
            throw new IllegalArgumentException("领用单不含该备件: " + stockId);
        }
        return item;
    }

    /** 领用单详情：主单 + 明细 + 替换记录 + 退库验收记录 */
    public IssueDetailDto detail(Long issueId) {
        SpareIssue issue = mustGet(issueId);
        IssueDetailDto dto = new IssueDetailDto();
        dto.setIssue(issue);
        dto.setItems(itemMapper.selectList(new QueryWrapper<SpareIssueItem>()
                .eq("issue_id", issueId).orderByAsc("id")));
        dto.setReplacements(replacementMapper.selectList(new QueryWrapper<SpareReplacement>()
                .eq("issue_id", issueId).orderByAsc("id")));
        dto.setReturns(returnMapper.selectList(new QueryWrapper<SpareReturn>()
                .eq("issue_id", issueId).orderByAsc("id")));
        return dto;
    }

    public SpareIssue mustGet(Long id) {
        SpareIssue issue = getById(id);
        if (issue == null) {
            throw new IllegalArgumentException("维修领用单不存在: " + id);
        }
        return issue;
    }
}
