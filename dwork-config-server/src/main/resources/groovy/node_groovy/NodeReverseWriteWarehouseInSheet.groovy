package groovy.node_groovy

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.bmf.sql.Conjunction
import com.chinajay.virgo.bmf.sql.OperationType
import com.chinajay.virgo.bmf.sql.Restriction
import com.chinajay.virgo.bmf.sql.Where
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.application.common.utils.ValueUtil
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService

import java.util.stream.Collectors


/**
 * 入库反写入库申请单
 */
class NodeReverseWriteWarehouseInSheet extends NodeGroovyClass {
    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)

    @Override
    Object runScript(BmfObject nodeData) {
        //获取周转箱信息
        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        def businessType = nodeData.getString("ext_business_type")
        if (passBoxes == null || passBoxes.size() == 0 || !"采购入库".equals(businessType)) {
            return nodeData
        }
        for (final def passBox in passBoxes) {
            BigDecimal sum = passBox.getBigDecimal("quantity")
            //
//            def sourceOrderCode = nodeData.getString("ext_business_source_code")
//            //获取入库申请单
//            def warehouseInApplication = basicGroovyService.getByCode("WarehouseInApplication", sourceOrderCode)
//            if (warehouseInApplication == null) {
//                throw new BusinessException("未找到入库申请单" + sourceOrderCode)
//            }
//            List<BmfObject> warehouseInSheetDetails = warehouseInApplication.getAndRefreshList("main_idAutoMapping")
            //匹配入库任务单
            Map<String, Object> params = new HashMap<String, Object>()
            params.put("passBoxRealCode", passBox.getString("code"))
            List<BmfObject> warehouseInSheetPassBoxs = basicGroovyService.find("warehouseInSheetPassBox", params)
            if (warehouseInSheetPassBoxs == null || warehouseInSheetPassBoxs.size() == 0) {
                throw new BusinessException("未找到入库任务单周转箱编码" + passBox.getString("passBoxCode"))
            }
            //入库任务单子表id
            List<Object> ids = warehouseInSheetPassBoxs.stream().map {
                warehouseInSheetPassBox -> warehouseInSheetPassBox.getJSONObject("warehouseInSheetDetail").getLong("id")
            }.collect(Collectors.toList())
            //匹配出入库任务单理论上只能匹配出一个来
            BmfObject warehouseInSheetDetail = basicGroovyService.findOne("warehouseInSheetDetail", Where.builder()
                    .restrictions(
                            Arrays.asList(
                                    Restriction.builder()
                                            .conjunction(Conjunction.AND)
                                            .columnName("id")
                                            .operationType(OperationType.IN)
                                            .values(ids)
                                            .build(),
                                    Restriction.builder()
                                            .conjunction(Conjunction.AND)
                                            .columnName("wait_inbound_quantity")
                                            .operationType(OperationType.GRANT)
                                            .values(Collections.singletonList(BigDecimal.ZERO))
                                            .build()
                            )
                    ).build())
            if (warehouseInSheetDetail == null) {
                throw new BusinessException("未找到未完成的入库任务单,周转箱编码" + passBox.getString("passBoxCode"))
            }
            //获取入库申请单
            def warehouseInApplication = basicGroovyService.getByCode("WarehouseInApplication",  warehouseInSheetDetail.getString("warehouseInApplicationCode"))
            if (warehouseInApplication == null) {
                throw new BusinessException("未找到入库申请单" +  warehouseInSheetDetail.getString("warehouseInApplicationCode"))
            }
            List<BmfObject> warehouseInSheetDetails = warehouseInApplication.getAndRefreshList("main_idAutoMapping")
            //匹配瑞哭申请单子表
            //获取待修改数量
            List<BmfObject> receiptdetails = warehouseInSheetDetails.stream()
                    .filter(purchaseReceiptDetail -> {
                        BigDecimal noWarehousedQuantity = ValueUtil.toBigDecimal(purchaseReceiptDetail.getBigDecimal("wait_inbound_quantity"), BigDecimal.ZERO)
                        return noWarehousedQuantity > 0
                    }).sorted(Comparator.comparing(t -> ValueUtil.toLong(((BmfObject) t).getString("lineNum"))))
                    .collect(Collectors.toList())
            //记录行号对应数量
            Map<String, BigDecimal> lineNumUpdateMap = new HashMap<>()
            //开始修改 采购收货通知单 数量
            for (BmfObject detail : receiptdetails) {
                BigDecimal receivedQuantity = detail.getBigDecimal("quantity")
                BigDecimal warehousedQuantity = detail.getBigDecimal("warehoused_quantity")
                BigDecimal noWarehousedQuantity = receivedQuantity - warehousedQuantity
                if (sum == 0 || noWarehousedQuantity <= 0) {
                    continue
                }
                //本次数量 > 待收货数量
                if (noWarehousedQuantity < sum) {
                    //下一行数量 = 本次数量 - 待收货数量
                    //待收货数量 = 0
                    warehousedQuantity = warehousedQuantity + noWarehousedQuantity
                    detail.put("warehoused_quantity", warehousedQuantity)
                    detail.put("wait_inbound_quantity", receivedQuantity - warehousedQuantity)
                    lineNumUpdateMap.put(detail.getString("lineNum"), noWarehousedQuantity)
                } else {
                    //本次数量 <= 待收货数量
                    //下一行数量 = 0
                    //待收货数量 = 待收货数量 - 本次数量
                    lineNumUpdateMap.put(detail.getString("lineNum"), sum)
                    warehousedQuantity = warehousedQuantity + sum
                    sum = BigDecimal.ZERO
                    detail.put("warehoused_quantity", warehousedQuantity)
                    detail.put("wait_inbound_quantity", receivedQuantity - warehousedQuantity)
                }
                //更新采购收货通知单数据
                basicGroovyService.updateByPrimaryKeySelective(detail)
            }

            BigDecimal sumWarehousedQty = BigDecimal.ZERO
            for (BmfObject detail : warehouseInSheetDetails) {
                sumWarehousedQty = sumWarehousedQty + ValueUtil.toBigDecimal(detail.getBigDecimal("wait_inbound_quantity"), BigDecimal.ZERO)
            }
            if (!Arrays.asList("completed", "closed").contains(warehouseInApplication.getString("status"))) {
                BmfObject warehouseInSheetUpdate = new BmfObject(warehouseInApplication.getBmfClassName())
                warehouseInSheetUpdate.put("id", warehouseInApplication.getPrimaryKeyValue())
                if (sumWarehousedQty.compareTo(BigDecimal.ZERO) == 0) {
                    warehouseInSheetUpdate.put("status", "completed")
                } else {
                    warehouseInSheetUpdate.put("status", "pending")
                }
                basicGroovyService.updateByPrimaryKeySelective(warehouseInSheetUpdate)
            }
        }
        return nodeData
    }
}
