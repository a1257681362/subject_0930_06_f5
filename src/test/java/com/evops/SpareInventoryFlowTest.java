package com.evops;

import com.evops.dto.AcceptRequest;
import com.evops.dto.BladeRequest;
import com.evops.dto.DefectRegisterRequest;
import com.evops.dto.InboundRequest;
import com.evops.dto.InspectSubmitRequest;
import com.evops.dto.IssueCreateRequest;
import com.evops.dto.IssueItemDto;
import com.evops.dto.OrderCreateRequest;
import com.evops.dto.RepairRequest;
import com.evops.dto.ReturnCreateRequest;
import com.evops.dto.ReturnInspectRequest;
import com.evops.dto.RuleAdjustRequest;
import com.evops.dto.SparePartCreateRequest;
import com.evops.dto.SpareReplacementRequest;
import com.evops.dto.StockTraceDto;
import com.evops.dto.TurbineCreateRequest;
import com.evops.entity.Defect;
import com.evops.entity.InspectionOrder;
import com.evops.entity.SpareIssue;
import com.evops.entity.SparePart;
import com.evops.entity.SparePartRule;
import com.evops.entity.SpareReplacement;
import com.evops.entity.SpareReturn;
import com.evops.entity.SpareStock;
import com.evops.entity.Turbine;
import com.evops.enums.BladePosition;
import com.evops.enums.DefectLevel;
import com.evops.enums.DefectStatus;
import com.evops.enums.DefectType;
import com.evops.enums.IssueStatus;
import com.evops.enums.OldPartDestination;
import com.evops.enums.OrderStatus;
import com.evops.enums.ReturnSource;
import com.evops.enums.ReturnStatus;
import com.evops.enums.SpareCategory;
import com.evops.enums.StockStatus;
import com.evops.enums.TrackingMode;
import com.evops.service.BladeService;
import com.evops.service.InspectionService;
import com.evops.service.SpareIssueService;
import com.evops.service.SparePartService;
import com.evops.service.SpareStockService;
import com.evops.service.TurbineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * F5 风电叶片运维备件库存：
 * 序列号/批次入库、规则调整只生成新版本且不改变序列追踪旧结论、
 * 维修领用绑定风机与缺陷单、已装机不能再次出库、替换同事务更新
 * 库存/维修进度/资产档案、旧件去向、退库必须验收、库存更新原子性。
 */
@SpringBootTest
@Transactional
class SpareInventoryFlowTest {

    @Autowired
    private TurbineService turbineService;
    @Autowired
    private BladeService bladeService;
    @Autowired
    private InspectionService inspectionService;
    @Autowired
    private SparePartService sparePartService;
    @Autowired
    private SpareStockService spareStockService;
    @Autowired
    private SpareIssueService spareIssueService;

    private Turbine lastTurbine;
    private Long lastBladeId;

    private Turbine turbine(String no) {
        TurbineCreateRequest req = new TurbineCreateRequest();
        req.setTurbineNo(no);
        req.setSite("张北坝上风电场");
        req.setModel("WT2000-D110");
        req.setGridConnectedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        BladeRequest blade = new BladeRequest();
        blade.setBladeNo(no + "-B1");
        blade.setSerialNo("SN-" + no + "-1");
        blade.setBladePosition(BladePosition.A);
        blade.setMaterial("玻璃纤维增强复合材料");
        req.setBlades(Arrays.asList(blade));
        lastTurbine = turbineService.create(req);
        lastBladeId = bladeService.listInServiceAt(lastTurbine.getId(),
                LocalDateTime.of(2026, 1, 1, 0, 0)).get(0).getId();
        return lastTurbine;
    }

    /** 风机+叶片+工单+影像+缺陷登记，返回 OPEN 缺陷 */
    private Defect openDefect(String turbineNo) {
        return openDefect(turbineNo, DefectType.CRACK, DefectLevel.SERIOUS);
    }

    private Defect openDefect(String turbineNo, DefectType type, DefectLevel level) {
        Turbine t = turbine(turbineNo);
        OrderCreateRequest orderReq = new OrderCreateRequest();
        orderReq.setTurbineId(t.getId());
        InspectionOrder order = inspectionService.createOrder(orderReq);
        InspectSubmitRequest inspect = new InspectSubmitRequest();
        inspect.setDroneImageUrl("oss://evops/drone/" + turbineNo + "/full.jpg");
        inspect.setInspectTime(LocalDateTime.of(2026, 3, 1, 10, 30));
        inspectionService.submitInspection(order.getId(), inspect);
        DefectRegisterRequest defectReq = new DefectRegisterRequest();
        defectReq.setBladeId(lastBladeId);
        defectReq.setDefectType(type);
        defectReq.setDefectLevel(level);
        defectReq.setDescription("维修领用备件缺陷");
        return inspectionService.registerDefect(order.getId(), defectReq);
    }

    private SparePart part(String no, String name, SpareCategory category, TrackingMode mode,
                           String spec, String unit, Integer shelfLifeDays) {
        SparePartCreateRequest req = new SparePartCreateRequest();
        req.setPartNo(no);
        req.setName(name);
        req.setCategory(category);
        req.setTrackingMode(mode);
        req.setSpecification(spec);
        req.setUnit(unit);
        req.setShelfLifeDays(shelfLifeDays);
        return sparePartService.create(req);
    }

    private SpareStock inboundSerial(Long partId, String serialNo) {
        InboundRequest req = new InboundRequest();
        req.setSerialNo(serialNo);
        return spareStockService.inbound(partId, req);
    }

    private SpareStock inboundBatch(Long partId, String batchNo, double qty) {
        InboundRequest req = new InboundRequest();
        req.setBatchNo(batchNo);
        req.setQuantity(qty);
        return spareStockService.inbound(partId, req);
    }

    private IssueCreateRequest issueReq(Defect defect, Long stockId, Double qty) {        IssueCreateRequest req = new IssueCreateRequest();
        req.setTurbineId(lastTurbine.getId());
        req.setDefectId(defect.getId());
        req.setIssuedBy("仓管员-李四");
        IssueItemDto item = new IssueItemDto();
        item.setStockId(stockId);
        item.setQuantity(qty);
        req.setItems(Collections.singletonList(item));
        return req;
    }

    private SpareReplacementRequest installReq(Long stockId, String oldName, String oldSerial,
                                                OldPartDestination dest) {
        SpareReplacementRequest req = new SpareReplacementRequest();
        req.setStockId(stockId);
        req.setBladeId(lastBladeId);
        req.setOldPartName(oldName);
        req.setOldSerialNo(oldSerial);
        req.setOldPartDestination(dest);
        req.setRepairResult("更换备件并按工艺要求复位");
        return req;
    }

    @Test
    void 入库_序列号件一件一码_批次件按数量() {
        SparePart bolt = part("P-BOLT", "叶根螺栓 M36", SpareCategory.BOLT,
                TrackingMode.SERIAL, "M36×220 10.9级", "套", null);
        SpareStock s1 = inboundSerial(bolt.getId(), "BOLT-SN-001");
        inboundSerial(bolt.getId(), "BOLT-SN-002");
        assertTrue(sparePartService.mustGet(bolt.getId()).getTrackingLocked());
        assertEquals(1d, s1.getQuantity());
        assertEquals(StockStatus.IN_STOCK, s1.getStatus());
        assertEquals(1, s1.getRuleVersionSnapshot());
        // 序列号重复 / 缺失拒绝
        assertThrows(IllegalArgumentException.class, () -> inboundSerial(bolt.getId(), "BOLT-SN-001"));
        assertThrows(IllegalArgumentException.class,
                () -> spareStockService.inbound(bolt.getId(), new InboundRequest()));

        SparePart coating = part("P-COAT", "前缘防护涂层", SpareCategory.COATING,
                TrackingMode.BATCH, "聚氨酯 A组分 20kg/桶", "kg", 365);
        SpareStock batch = inboundBatch(coating.getId(), "COAT-202603-01", 100d);
        assertEquals(100d, batch.getQuantity());
        assertNotNull(batch.getExpireAt());
        assertThrows(IllegalArgumentException.class,
                () -> inboundBatch(coating.getId(), "COAT-202603-01", 10d));
        assertThrows(IllegalArgumentException.class,
                () -> inboundBatch(coating.getId(), "COAT-202603-02", 0d));

        assertEquals(2d, spareStockService.availableQuantity(bolt.getId()));
        assertEquals(100d, spareStockService.availableQuantity(coating.getId()));
    }

    @Test
    void 规则调整_只生成新版本_序列追踪旧结论不变() {
        SparePart sensor = part("P-SENSOR", "叶根振动传感器", SpareCategory.SENSOR,
                TrackingMode.SERIAL, "VIB-100 量程50g", "只", null);
        SpareStock stock = inboundSerial(sensor.getId(), "SENSOR-SN-009");

        RuleAdjustRequest adjust = new RuleAdjustRequest();
        adjust.setSpecification("VIB-200 量程100g");
        adjust.setUnit("只");
        adjust.setSafetyStock(5d);
        adjust.setRemark("升级新型号量程");
        SparePartRule v2 = sparePartService.adjustRule(sensor.getId(), adjust);
        assertEquals(2, v2.getVersionNo());
        assertEquals("VIB-200 量程100g", v2.getSpecification());
        assertEquals(2, sparePartService.mustGet(sensor.getId()).getCurrentRuleVersion());

        // V1 退役但保留，内容不可变
        SparePartRule v1 = sparePartService.getRule(sensor.getId(), 1);
        assertEquals("RETIRED", v1.getStatus().name());
        assertEquals("VIB-100 量程50g", v1.getSpecification());

        // 调整后新入库按 V2 快照
        SpareStock later = inboundSerial(sensor.getId(), "SENSOR-SN-010");
        assertEquals(2, later.getRuleVersionSnapshot());
        assertEquals("VIB-200 量程100g", later.getSpecSnapshot());

        // 旧序列追踪结论不变：入库快照仍为 V1/旧规格
        StockTraceDto trace = spareStockService.trace(stock.getId());
        assertEquals(1, trace.getInboundRule().getVersionNo());
        assertEquals("VIB-100 量程50g", trace.getInboundRule().getSpecification());
        assertEquals(2, trace.getCurrentRule().getVersionNo());
        assertEquals("VIB-100 量程50g", trace.getStock().getSpecSnapshot());
        assertEquals(1, trace.getStock().getRuleVersionSnapshot());
    }

    @Test
    void 领用绑定风机缺陷_替换同事务更新库存维修进度资产档案() {
        Defect defect = openDefect("F01");
        Long turbineId = lastTurbine.getId();
        Long bladeId = lastBladeId;
        SparePart bolt = part("P-BOLT2", "叶根螺栓 M36", SpareCategory.BOLT,
                TrackingMode.SERIAL, "M36×220 10.9级", "套", null);
        SpareStock stock = inboundSerial(bolt.getId(), "BOLT-SN-101");

        // 领用必须绑定风机与缺陷单
        IssueCreateRequest createReq = issueReq(defect, stock.getId(), 1d);
        SpareIssue issue = spareIssueService.createIssue(createReq);
        assertEquals(IssueStatus.RESERVED, issue.getStatus());
        assertEquals(StockStatus.RESERVED, spareStockService.mustGet(stock.getId()).getStatus());

        // 缺陷单不属于该风机时拒绝绑定（建档另一台风机，不影响本用例的 F01 上下文）
        Turbine other = turbine("F99");
        IssueCreateRequest wrong = new IssueCreateRequest();
        wrong.setTurbineId(other.getId());
        wrong.setDefectId(defect.getId());
        wrong.setIssuedBy("仓管员-李四");
        IssueItemDto wrongItem = new IssueItemDto();
        wrongItem.setStockId(stock.getId());
        wrong.setItems(Collections.singletonList(wrongItem));
        assertThrows(IllegalArgumentException.class, () -> spareIssueService.createIssue(wrong));

        // 恢复 F01 上下文（turbine("F99") 改写了共享字段），再出库装机
        this.lastTurbine = turbineService.getById(turbineId);
        this.lastBladeId = bladeId;
        spareIssueService.issueOut(issue.getId());
        SpareReplacement replacement = spareIssueService.install(issue.getId(),
                installReq(stock.getId(), "旧叶根螺栓", "OLD-BOLT-77", OldPartDestination.SCRAP));
        // 库存 + 资产档案
        SpareStock installed = spareStockService.mustGet(stock.getId());
        assertEquals(StockStatus.INSTALLED, installed.getStatus());
        assertEquals(turbineId, installed.getInstalledTurbineId());
        assertEquals(bladeId, installed.getInstalledBladeId());
        assertNotNull(installed.getInstalledAt());

        // 旧件去向 + 新件状态
        assertEquals(OldPartDestination.SCRAP, replacement.getOldPartDestination());
        assertEquals("OLD-BOLT-77", replacement.getOldSerialNo());
        assertEquals("INSTALLED", replacement.getNewPartStatus());

        // 维修进度：缺陷回填维修结果，工单联动
        Defect after = inspectionService.mustGetDefect(defect.getId());
        assertEquals(DefectStatus.REPAIRED, after.getStatus());
        assertEquals("更换备件并按工艺要求复位", after.getRepairResult());
        assertEquals(OrderStatus.REPAIRED,
                inspectionService.mustGetOrder(after.getOrderId()).getStatus());

        assertEquals(IssueStatus.COMPLETED, spareIssueService.mustGet(issue.getId()).getStatus());
    }

    @Test
    void 已安装备件不能再次出库_重复装机拒绝() {
        Defect defect = openDefect("F02", DefectType.LIGHTNING, DefectLevel.GENERAL);
        SparePart sensor = part("P-SENSOR2", "叶根振动传感器", SpareCategory.SENSOR,
                TrackingMode.SERIAL, "VIB-100", "只", null);
        SpareStock stock = inboundSerial(sensor.getId(), "SENSOR-SN-201");

        SpareIssue issue = spareIssueService.createIssue(issueReq(defect, stock.getId(), 1d));
        spareIssueService.issueOut(issue.getId());
        SpareReplacementRequest install = installReq(stock.getId(), "旧传感器", null,
                OldPartDestination.INSPECTION);
        spareIssueService.install(issue.getId(), install);

        Defect another = openDefect("F03");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> spareIssueService.createIssue(issueReq(another, stock.getId(), 1d)));
        assertTrue(ex.getMessage().contains("已安装备件不能再次出库"));

        // 同一领用单重复装机拒绝
        assertThrows(IllegalArgumentException.class,
                () -> spareIssueService.install(issue.getId(), install));
    }

    @Test
    void 批次件分次领用_余量可再出_整批装机后锁定() {
        Defect defect = openDefect("F04", DefectType.COATING_PEELING, DefectLevel.GENERAL);
        SparePart coating = part("P-COAT2", "前缘防护涂层", SpareCategory.COATING,
                TrackingMode.BATCH, "聚氨酯 20kg/桶", "kg", null);
        SpareStock batch = inboundBatch(coating.getId(), "COAT-B-01", 50d);

        SpareIssue issue = spareIssueService.createIssue(issueReq(defect, batch.getId(), 20d));
        assertEquals(30d, spareStockService.mustGet(batch.getId()).getQuantity());
        assertEquals(StockStatus.IN_STOCK, spareStockService.mustGet(batch.getId()).getStatus());
        spareIssueService.issueOut(issue.getId());
        spareIssueService.install(issue.getId(),
                installReq(batch.getId(), "旧涂层", null, OldPartDestination.SCRAP));

        // 余量 30 未整批出清，批次仍在库
        SpareStock stillInStock = spareStockService.mustGet(batch.getId());
        assertEquals(30d, stillInStock.getQuantity());
        assertEquals(StockStatus.IN_STOCK, stillInStock.getStatus());

        Defect d2 = openDefect("F05");
        spareIssueService.createIssue(issueReq(d2, batch.getId(), 30d));
        assertEquals(0d, spareStockService.mustGet(batch.getId()).getQuantity());
        SpareIssue issue2 = spareIssueService.mustGet(
                spareIssueService.list(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SpareIssue>()
                        .eq("defect_id", d2.getId())).get(0).getId());
        spareIssueService.issueOut(issue2.getId());
        spareIssueService.install(issue2.getId(),
                installReq(batch.getId(), "旧涂层二批", null, OldPartDestination.SCRAP));
        assertEquals(StockStatus.INSTALLED, spareStockService.mustGet(batch.getId()).getStatus());
        assertNotNull(spareStockService.mustGet(batch.getId()).getInstalledTurbineId());

        Defect d3 = openDefect("F06");
        assertThrows(IllegalArgumentException.class,
                () -> spareIssueService.createIssue(issueReq(d3, batch.getId(), 1d)));
    }

    @Test
    void 退库_必须验收_合格回库不合格报废() {
        Defect defect = openDefect("F07", DefectType.CRACK, DefectLevel.GENERAL);
        SparePart bolt = part("P-BOLT3", "叶根螺栓 M30", SpareCategory.BOLT,
                TrackingMode.SERIAL, "M30×200", "套", null);
        SpareStock stock = inboundSerial(bolt.getId(), "BOLT-SN-301");

        SpareIssue issue = spareIssueService.createIssue(issueReq(defect, stock.getId(), 1d));
        ReturnCreateRequest retReq = new ReturnCreateRequest();
        retReq.setSource(ReturnSource.UNUSED);
        retReq.setStockId(stock.getId());
        retReq.setReason("现场多发，暂不需要");
        SpareReturn ret = spareIssueService.createReturn(issue.getId(), retReq);
        assertEquals(ReturnStatus.PENDING, ret.getStatus());
        assertEquals(StockStatus.RETURN_PENDING, spareStockService.mustGet(stock.getId()).getStatus());

        // 待验收期间不能再次出库
        Defect other = openDefect("F08");
        assertThrows(IllegalArgumentException.class,
                () -> spareIssueService.createIssue(issueReq(other, stock.getId(), 1d)));

        ReturnInspectRequest inspect = new ReturnInspectRequest();
        inspect.setPass(true);
        inspect.setInspector("验收员-王五");
        inspect.setOpinion("外观完好，回库");
        ret = spareIssueService.inspectReturn(ret.getId(), inspect);
        assertEquals(ReturnStatus.ACCEPTED, ret.getStatus());
        assertEquals("PASS", ret.getResult());
        assertEquals(StockStatus.IN_STOCK, spareStockService.mustGet(stock.getId()).getStatus());
        // 退库结清，领用单恢复 RESERVED（尚未出库）
        assertEquals(IssueStatus.RESERVED, spareIssueService.mustGet(issue.getId()).getStatus());

        // 不合格路径：重新领用、出库、退库、验收不合格 -> 报废
        SpareIssue issue2 = spareIssueService.createIssue(issueReq(openDefect("F09"), stock.getId(), 1d));
        assertEquals(StockStatus.RESERVED, spareStockService.mustGet(stock.getId()).getStatus());
        spareIssueService.issueOut(issue2.getId());
        ReturnCreateRequest ret2 = new ReturnCreateRequest();
        ret2.setSource(ReturnSource.UNUSED);
        ret2.setStockId(stock.getId());
        ret2.setReason("外观磕碰");
        SpareReturn pending2 = spareIssueService.createReturn(issue2.getId(), ret2);
        ReturnInspectRequest reject = new ReturnInspectRequest();
        reject.setPass(false);
        reject.setInspector("验收员-王五");
        reject.setOpinion("螺纹损伤，不可再用");
        spareIssueService.inspectReturn(pending2.getId(), reject);
        assertEquals(StockStatus.SCRAPPED, spareStockService.mustGet(stock.getId()).getStatus());

        Defect d3 = openDefect("F10");
        assertThrows(IllegalArgumentException.class,
                () -> spareIssueService.createIssue(issueReq(d3, stock.getId(), 1d)));
        assertThrows(IllegalArgumentException.class,
                () -> spareIssueService.inspectReturn(pending2.getId(), reject));
    }

    @Test
    void 旧件回收_去向校验_待检修复再用才进入验收() {
        Defect defect = openDefect("F11", DefectType.LIGHTNING, DefectLevel.GENERAL);
        SparePart sensor = part("P-SENSOR3", "防雷接闪传感器", SpareCategory.SENSOR,
                TrackingMode.SERIAL, "LPS-1", "只", null);
        SpareStock stock = inboundSerial(sensor.getId(), "SENSOR-SN-401");
        SpareIssue issue = spareIssueService.createIssue(issueReq(defect, stock.getId(), 1d));
        spareIssueService.issueOut(issue.getId());

        SpareReplacement replacement = spareIssueService.install(issue.getId(),
                installReq(stock.getId(), "旧接闪传感器", "OLD-SEN-55", OldPartDestination.INSPECTION));
        assertEquals(IssueStatus.COMPLETED, spareIssueService.mustGet(issue.getId()).getStatus());

        ReturnCreateRequest oldReq = new ReturnCreateRequest();
        oldReq.setSource(ReturnSource.OLD_PART);
        oldReq.setReplacementId(replacement.getId());
        SpareReturn oldRet = spareIssueService.createReturn(issue.getId(), oldReq);
        ReturnInspectRequest pass = new ReturnInspectRequest();
        pass.setPass(true);
        pass.setInspector("验收员-王五");
        pass.setOpinion("检测合格，可修复再用");
        spareIssueService.inspectReturn(oldRet.getId(), pass);

        // 合格旧件以原序列号重新入库为可再用库存
        SpareStock refurbished = spareStockService.listByPart(sensor.getId()).stream()
                .filter(s -> "OLD-SEN-55".equals(s.getSerialNo()))
                .findFirst().orElse(null);
        assertNotNull(refurbished);
        assertEquals(StockStatus.IN_STOCK, refurbished.getStatus());
        // 旧件退库结清，原领用单仍为 COMPLETED
        assertEquals(IssueStatus.COMPLETED, spareIssueService.mustGet(issue.getId()).getStatus());

        // 报废/返厂去向不进入回库验收
        SpareStock stock2 = inboundSerial(sensor.getId(), "SENSOR-SN-402");
        Defect d2 = openDefect("F12");
        SpareIssue issue2 = spareIssueService.createIssue(issueReq(d2, stock2.getId(), 1d));
        spareIssueService.issueOut(issue2.getId());
        SpareReplacement r2 = spareIssueService.install(issue2.getId(),
                installReq(stock2.getId(), "旧传感器", null, OldPartDestination.SUPPLIER_R));
        ReturnCreateRequest bad = new ReturnCreateRequest();
        bad.setSource(ReturnSource.OLD_PART);
        bad.setReplacementId(r2.getId());
        assertThrows(IllegalArgumentException.class,
                () -> spareIssueService.createReturn(issue2.getId(), bad));
    }

    /**
     * 脱离测试事务（NOT_SUPPORTED），让服务事务真实提交/回滚，
     * 才能在断言时观察库存更新的原子回滚结果。
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 领用预占中途失败_库存更新整体回滚() {
        Defect defect = openDefect("F13", DefectType.CRACK, DefectLevel.GENERAL);
        SparePart bolt = part("P-BOLT4", "叶根螺栓 M42", SpareCategory.BOLT,
                TrackingMode.SERIAL, "M42×260", "套", null);
        SpareStock stockA = inboundSerial(bolt.getId(), "BOLT-SN-601");
        SpareStock stockB = inboundSerial(bolt.getId(), "BOLT-SN-602");

        // 先把 stockB 经另一张领用单装机
        SpareIssue blocker = spareIssueService.createIssue(issueReq(defect, stockB.getId(), 1d));
        spareIssueService.issueOut(blocker.getId());
        spareIssueService.install(blocker.getId(),
                installReq(stockB.getId(), "旧螺栓B", null, OldPartDestination.SCRAP));
        assertEquals(StockStatus.INSTALLED, spareStockService.mustGet(stockB.getId()).getStatus());

        // 新领用单含两条明细：A 在库可预占，B 已装机 -> 第二条失败
        Defect d2 = openDefect("F14");
        IssueCreateRequest req = new IssueCreateRequest();
        req.setTurbineId(lastTurbine.getId());
        req.setDefectId(d2.getId());
        req.setIssuedBy("仓管员-李四");
        IssueItemDto a = new IssueItemDto();
        a.setStockId(stockA.getId());
        IssueItemDto b = new IssueItemDto();
        b.setStockId(stockB.getId());
        req.setItems(Arrays.asList(a, b));

        assertThrows(IllegalArgumentException.class, () -> spareIssueService.createIssue(req));

        // 同一事务回滚：A 的预占撤销（仍在库），无领用单残留
        assertEquals(StockStatus.IN_STOCK, spareStockService.mustGet(stockA.getId()).getStatus());
        assertEquals(1d, spareStockService.mustGet(stockA.getId()).getQuantity());
        // 可用量仅剩 A 一件（B 已装机不在可用库存）
        assertEquals(1d, spareStockService.availableQuantity(bolt.getId()));
    }

    /** 脱离测试事务，验证替换失败时库存/维修进度/资产档案在同一真实事务内整体回滚 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 替换装机失败_库存维修进度资产档案整体回滚() {
        Defect defect = openDefect("F15", DefectType.CRACK, DefectLevel.GENERAL);
        SparePart bolt = part("P-BOLT5", "叶根螺栓 M48", SpareCategory.BOLT,
                TrackingMode.SERIAL, "M48×300", "套", null);
        SpareStock stock = inboundSerial(bolt.getId(), "BOLT-SN-701");
        SpareIssue issue = spareIssueService.createIssue(issueReq(defect, stock.getId(), 1d));
        spareIssueService.issueOut(issue.getId());

        // 缺陷在装机前被外部流程走完维修并验收关闭
        RepairRequest repair = new RepairRequest();
        repair.setRepairResult("外部流程先完成维修");
        inspectionService.repair(defect.getId(), repair);
        AcceptRequest accept = new AcceptRequest();
        accept.setPass(true);
        inspectionService.accept(defect.getId(), accept);

        // 已关闭缺陷不允许再替换装机
        assertThrows(IllegalArgumentException.class,
                () -> spareIssueService.install(issue.getId(),
                        installReq(stock.getId(), "旧螺栓", null, OldPartDestination.SCRAP)));

        // 库存仍为出库预留态，无资产档案写入
        SpareStock unchanged = spareStockService.mustGet(stock.getId());
        assertEquals(StockStatus.RESERVED, unchanged.getStatus());
        assertNull(unchanged.getInstalledTurbineId());
        assertNull(unchanged.getInstalledAt());
    }
}
