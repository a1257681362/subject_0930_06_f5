package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.InboundRequest;
import com.evops.dto.StockTraceDto;
import com.evops.entity.SpareStock;
import com.evops.service.SpareStockService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 备件库存：序列号/批次入库与序列追踪 */
@RestController
@RequestMapping("/api")
public class SpareStockController {

    private final SpareStockService spareStockService;

    public SpareStockController(SpareStockService spareStockService) {
        this.spareStockService = spareStockService;
    }

    /** 入库（序列号件填 serialNo；批次件填 batchNo + quantity），固化规则版本快照 */
    @PostMapping("/spare-parts/{partId}/inbound")
    public ApiResponse<SpareStock> inbound(@PathVariable Long partId,
                                           @Validated @RequestBody InboundRequest req) {
        return ApiResponse.ok(spareStockService.inbound(partId, req));
    }

    /** 某备件的全部库存实例（含已装机/报废，台账视角） */
    @GetMapping("/spare-parts/{partId}/stocks")
    public ApiResponse<List<SpareStock>> listByPart(@PathVariable Long partId) {
        return ApiResponse.ok(spareStockService.listByPart(partId));
    }

    /** 当前可用库存量 */
    @GetMapping("/spare-parts/{partId}/available")
    public ApiResponse<Double> available(@PathVariable Long partId) {
        return ApiResponse.ok(spareStockService.availableQuantity(partId));
    }

    /** 库存实例详情 */
    @GetMapping("/spare-stocks/{id}")
    public ApiResponse<SpareStock> detail(@PathVariable Long id) {
        return ApiResponse.ok(spareStockService.mustGet(id));
    }

    /** 序列/批次追踪：入库快照规则、当前规则、装机资产档案、替换与退库验收 */
    @GetMapping("/spare-stocks/{id}/trace")
    public ApiResponse<StockTraceDto> trace(@PathVariable Long id) {
        return ApiResponse.ok(spareStockService.trace(id));
    }
}
