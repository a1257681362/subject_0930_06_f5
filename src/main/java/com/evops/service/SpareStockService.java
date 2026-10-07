package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.dto.InboundRequest;
import com.evops.dto.StockTraceDto;
import com.evops.entity.SparePart;
import com.evops.entity.SparePartRule;
import com.evops.entity.SpareReplacement;
import com.evops.entity.SpareReturn;
import com.evops.entity.SpareStock;
import com.evops.enums.StockStatus;
import com.evops.enums.TrackingMode;
import com.evops.mapper.SpareReplacementMapper;
import com.evops.mapper.SpareReturnMapper;
import com.evops.mapper.SpareStockMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 备件库存：序列号件一件一码、批次件按批次数量入库。
 *
 * 口径：
 * - 入库按备件追踪模式校验（序列号件必须给序列号、数量恒 1；批次件必须给批次号与正数数量）；
 * - 首次入库锁定备件追踪模式（同物料序列号/批次口径不可再改）；
 * - 入库时固化当前规则版本与规格、单位快照，规则后续调整不改变历史序列追踪结论；
 * - 已装机（INSTALLED）实例不能再次出库（在领用环节拦截）。
 */
@Service
public class SpareStockService extends ServiceImpl<SpareStockMapper, SpareStock> {

    private final SparePartService sparePartService;
    private final SpareReplacementMapper replacementMapper;
    private final SpareReturnMapper returnMapper;
    private final AuditService auditService;

    public SpareStockService(SparePartService sparePartService,
                             SpareReplacementMapper replacementMapper,
                             SpareReturnMapper returnMapper, AuditService auditService) {
        this.sparePartService = sparePartService;
        this.replacementMapper = replacementMapper;
        this.returnMapper = returnMapper;
        this.auditService = auditService;
    }

    /** 入库：序列号或批次；固化规则版本与规格快照 */
    @Transactional
    public SpareStock inbound(Long partId, InboundRequest req) {
        SparePart part = sparePartService.mustGet(partId);
        LocalDateTime inboundAt = req.getInboundAt() != null ? req.getInboundAt() : LocalDateTime.now();

        SpareStock stock = new SpareStock();
        stock.setPartId(partId);
        stock.setInboundAt(inboundAt);
        stock.setStatus(StockStatus.IN_STOCK);

        if (part.getTrackingMode() == TrackingMode.SERIAL) {
            if (!StringUtils.hasText(req.getSerialNo())) {
                throw new IllegalArgumentException("序列号件入库必须提供序列号");
            }
            Long dup = count(new QueryWrapper<SpareStock>()
                    .eq("part_id", partId).eq("serial_no", req.getSerialNo()));
            if (dup > 0) {
                throw new IllegalArgumentException("备件序列号已入库: " + req.getSerialNo());
            }
            stock.setSerialNo(req.getSerialNo());
            stock.setQuantity(1d);
        } else {
            if (!StringUtils.hasText(req.getBatchNo())) {
                throw new IllegalArgumentException("批次件入库必须提供批次号");
            }
            if (req.getQuantity() == null || req.getQuantity() <= 0) {
                throw new IllegalArgumentException("批次入库数量必须为正数");
            }
            Long dup = count(new QueryWrapper<SpareStock>()
                    .eq("part_id", partId).eq("batch_no", req.getBatchNo()));
            if (dup > 0) {
                throw new IllegalArgumentException("备件批次已入库: " + req.getBatchNo());
            }
            stock.setBatchNo(req.getBatchNo());
            stock.setQuantity(req.getQuantity());
        }

        // 固化入库时的规则版本与规格/单位快照：后续规则调整不影响历史追踪结论
        SparePartRule rule = sparePartService.currentRule(partId);
        stock.setRuleVersionSnapshot(rule.getVersionNo());
        stock.setSpecSnapshot(rule.getSpecification());
        stock.setUnitSnapshot(rule.getUnit());
        if (rule.getShelfLifeDays() != null) {
            stock.setExpireAt(inboundAt.plusDays(rule.getShelfLifeDays()));
        }

        String stockNo = StringUtils.hasText(req.getStockNo())
                ? req.getStockNo()
                : "STK" + System.currentTimeMillis();
        Long dupNo = count(new QueryWrapper<SpareStock>().eq("stock_no", stockNo));
        if (dupNo > 0) {
            throw new IllegalArgumentException("入库流水号已存在: " + stockNo);
        }
        stock.setStockNo(stockNo);
        save(stock);

        // 首次入库锁定追踪模式
        if (!Boolean.TRUE.equals(part.getTrackingLocked())) {
            part.setTrackingLocked(true);
            sparePartService.updateById(part);
        }

        auditService.record("SPARE_STOCK", stock.getId(), "INBOUND",
                (part.getTrackingMode() == TrackingMode.SERIAL ? "序列号件 " : "批次件 ")
                        + part.getPartNo() + " 入库，规则快照 V" + rule.getVersionNo(), null);
        return stock;
    }

    /** 按备件列出全部库存实例（含已装机/报废，用于台账） */
    public List<SpareStock> listByPart(Long partId) {
        sparePartService.mustGet(partId);
        return list(new QueryWrapper<SpareStock>()
                .eq("part_id", partId)
                .orderByAsc("id"));
    }

    /** 当前可用库存（IN_STOCK），序列号件计数 1，批次件汇总余量 */
    public double availableQuantity(Long partId) {
        List<SpareStock> stocks = list(new QueryWrapper<SpareStock>()
                .eq("part_id", partId)
                .eq("status", StockStatus.IN_STOCK.name()));
        return stocks.stream().mapToDouble(SpareStock::getQuantity).sum();
    }

    public SpareStock mustGet(Long id) {
        SpareStock stock = getById(id);
        if (stock == null) {
            throw new IllegalArgumentException("备件库存实例不存在: " + id);
        }
        return stock;
    }

    /**
     * 序列/批次追踪：入库快照规则（历史结论依据）+ 当前规则 + 装机资产档案 +
     * 作为新件的替换记录 + 退库验收记录。
     */
    public StockTraceDto trace(Long stockId) {
        SpareStock stock = mustGet(stockId);
        SparePart part = sparePartService.mustGet(stock.getPartId());
        StockTraceDto dto = new StockTraceDto();
        dto.setStock(stock);
        dto.setPart(part);
        dto.setInboundRule(sparePartService.getRule(part.getId(), stock.getRuleVersionSnapshot()));
        dto.setCurrentRule(sparePartService.currentRule(part.getId()));
        dto.setReplacements(replacementMapper.selectList(new QueryWrapper<SpareReplacement>()
                .eq("new_stock_id", stockId)
                .orderByAsc("id")));
        dto.setReturns(returnMapper.selectList(new QueryWrapper<SpareReturn>()
                .eq("stock_id", stockId)
                .orderByAsc("id")));
        return dto;
    }
}
