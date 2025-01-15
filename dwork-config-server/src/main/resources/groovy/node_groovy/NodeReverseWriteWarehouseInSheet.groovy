package groovy.node_groovy

import com.alibaba.fastjson.JSON
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.bmf.sql.Conjunction
import com.chinajay.virgo.bmf.sql.OperationType
import com.chinajay.virgo.bmf.sql.Restriction
import com.chinajay.virgo.bmf.sql.Where
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.application.common.holder.ThreadLocalHolder
import com.chinajey.application.common.holder.UserAuthDto
import com.chinajey.application.common.utils.ValueUtil
import com.tengnat.dwork.common.enums.EquipSourceEnum
import com.tengnat.dwork.common.utils.CodeGenerator
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService
import org.springframework.util.ObjectUtils

import java.util.stream.Collectors


/**
 * 入库反写入库申请单
 */
class NodeReverseWriteWarehouseInSheet extends NodeGroovyClass {
    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)
    CodeGenerator codeGenerator = SpringUtils.getBean(CodeGenerator.class)

    @Override
    Object runScript(BmfObject nodeData) {
        //获取周转箱信息
        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        def businessType = nodeData.getString("ext_business_type")
        if (!passBoxes) {
            return nodeData
        }
        for (final def passBox in passBoxes) {
            //更新同步入库申请单
            updateWarehouseInApplication(passBox, nodeData)
        }
        //throw new BusinessException("ttttttttttttt")
        return nodeData
    }

    /**
     * 更新入库申请单
     * @param passBox
     * @param nodeData
     */
    private void updateWarehouseInApplication(BmfObject passBox, BmfObject nodeData) {
        BigDecimal sum = passBox.getBigDecimal("quantity")
        //匹配入库任务单
        Map<String, Object> params = new HashMap<String, Object>()
        params.put("passBoxCode", passBox.getString("passBoxCode"))
        params.put("completionStatus", false)
        BmfObject warehouseInSheetPassBox = basicGroovyService.findOne("warehouseInSheetPassBox", params)
        if (warehouseInSheetPassBox == null) {
            throw new BusinessException("未找到入库任务单周转箱编码" + passBox.getString("passBoxCode"))
        }
        warehouseInSheetPassBox.put("completionStatus", true)
        basicGroovyService.updateByPrimaryKeySelective(warehouseInSheetPassBox)
        //匹配出入库任务单理论上只能匹配出一个来
        List<BmfObject>  warehouseInSheetDetailUnfiltered = basicGroovyService.find("warehouseInSheetDetail", Where.builder()
                .restrictions(
                        Arrays.asList(
                                Restriction.builder()
                                        .conjunction(Conjunction.AND)
                                        .columnName("id")
                                        .operationType(OperationType.IN)
                                        .values(Collections.singletonList(warehouseInSheetPassBox.getJSONObject("warehouseInSheetDetail").getLong("id")))
                                        .build(),
                                Restriction.builder()
                                        .conjunction(Conjunction.AND)
                                        .columnName("wait_inbound_quantity")
                                        .operationType(OperationType.GRANT)
                                        .values(Collections.singletonList(BigDecimal.ZERO))
                                        .build()
                        )
                ).build())
        if (warehouseInSheetDetailUnfiltered == null || warehouseInSheetDetailUnfiltered.size() == 0) {
            throw new BusinessException("未找到未完成的入库任务单,周转箱编码" + passBox.getString("passBoxCode"))
        }
        def warehouseInSheetDetail = warehouseInSheetDetailUnfiltered.stream()
                .filter { it ->!Arrays.asList("completed", "closed","done","cancel",).contains(it.getAndRefreshBmfObject("mainid").getString("status"))}
                .findFirst().orElse(null)
        if (warehouseInSheetDetail == null ) {
            throw new BusinessException("未找到未完成的入库任务单明细,周转箱编码" + passBox.getString("passBoxCode"))
        }

        //获取入库申请单
        def warehouseInApplication = basicGroovyService.getByCode("WarehouseInApplication", warehouseInSheetDetail.getString("warehouseInApplicationCode"))
        if (warehouseInApplication == null) {
            throw new BusinessException("未找到入库申请单" + warehouseInSheetDetail.getString("warehouseInApplicationCode"))
        }
        if ("completed".equals(warehouseInApplication.getString("status"))) {
            throw new BusinessException("入库申请单" + warehouseInSheetDetail.getString("warehouseInApplicationCode") + "已完成")
        }


        List<BmfObject> warehouseInApplicationDetails = warehouseInApplication.getAndRefreshList("main_idAutoMapping")
        //匹配入库申请单子表
        //获取待修改数量
        List<BmfObject> receiptDetails = warehouseInApplicationDetails.stream()
                .filter(purchaseReceiptDetail -> {
                    BigDecimal noWarehousedQuantity = ValueUtil.toBigDecimal(purchaseReceiptDetail.getBigDecimal("wait_inbound_quantity"), BigDecimal.ZERO)
                    String materialCode = purchaseReceiptDetail.getString("materialCode")
                    return noWarehousedQuantity > 0 && materialCode == passBox.getString("materialCode")
                })
                .sorted(Comparator.comparingInt(t -> ((BmfObject) t).getInteger("lineNum"))
                        .thenComparingLong(t -> ((BmfObject) t).getPrimaryKeyValue()))
                .collect(Collectors.toList())

        //开始修改 采购收货通知单 数量
        for (BmfObject detail : receiptDetails) {
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

                //创建更新入库结果单据
                maintenanceWarehouseInResult(passBox, noWarehousedQuantity, warehouseInApplication, detail, warehouseInSheetDetail, nodeData)
                sum = sum.subtract(noWarehousedQuantity)
            } else {
                //本次数量 <= 待收货数量
                //下一行数量 = 0
                //待收货数量 = 待收货数量 - 本次数量
                warehousedQuantity = warehousedQuantity + sum
                detail.put("warehoused_quantity", warehousedQuantity)
                detail.put("wait_inbound_quantity", receivedQuantity - warehousedQuantity)
                //创建更新入库结果单据
                maintenanceWarehouseInResult(passBox, sum, warehouseInApplication, detail, warehouseInSheetDetail, nodeData)
                sum = BigDecimal.ZERO
            }
            //塞仓库
            detail.put("target_warehouse_code", warehouseInSheetDetail.getString("warehouseCode"))
            detail.put("target_warehouse_name", warehouseInSheetDetail.getString("warehouseName"))
            //更新采购收货通知单数据
            basicGroovyService.updateByPrimaryKeySelective(detail)
            //同步回sap
            sapSyncWarehouseInApplication(detail)
        }

        BigDecimal sumWarehousedQty = BigDecimal.ZERO
        for (BmfObject detail : warehouseInApplicationDetails) {
            sumWarehousedQty = sumWarehousedQty + ValueUtil.toBigDecimal(detail.getBigDecimal("wait_inbound_quantity"), BigDecimal.ZERO)
        }
        if (!Arrays.asList("completed", "closed").contains(warehouseInApplication.getString("status"))) {
            BmfObject warehouseInSheetUpdate = new BmfObject(warehouseInApplication.getBmfClassName())
            warehouseInSheetUpdate.put("id", warehouseInApplication.getPrimaryKeyValue())
            if (sumWarehousedQty.compareTo(BigDecimal.ZERO) == 0) {
                warehouseInSheetUpdate.put("status", "completed")
            } else {
                warehouseInSheetUpdate.put("status", "partWarehoused")
            }
            basicGroovyService.updateByPrimaryKeySelective(warehouseInSheetUpdate)
        }

        //更新周周转箱位置，取值于界面上的位置
        BmfObject passBoxReal = basicGroovyService.getByCode("passBoxReal", passBox.getString("code"))
        passBoxReal.put("location", basicGroovyService.getByCode("location", nodeData.getString("targetLocationCode")))
        passBoxReal.put("locationCode",nodeData.getString("targetLocationCode"))
        passBoxReal.put("locationName",nodeData.getString("targetLocationName"))
        sceneGroovyService.synchronizePassBoxInfo(passBoxReal, EquipSourceEnum.PDA.getCode(), nodeData.getBmfClassName())

        //更新入库任务单状态
        BigDecimal sumSheetQty = BigDecimal.ZERO

        BmfObject warehouseInSheet = warehouseInSheetDetail.getAndRefreshBmfObject("mainid")
        List<BmfObject> warehouseInSheetDetails = warehouseInSheet.getAndRefreshList("mainidAutoMapping")
        if(!warehouseInSheetDetails){
            throw new BusinessException("入库任务单明细不存在，请联系系统管理员！")
        }
        for (BmfObject detail : warehouseInSheetDetails) {
            sumSheetQty = sumSheetQty + ValueUtil.toBigDecimal(detail.getBigDecimal("wait_inbound_quantity"), BigDecimal.ZERO)
        }
        if (!Arrays.asList("completed", "closed", "done").contains(warehouseInSheet.getString("status"))) {
            BmfObject warehouseInSheetUpdate = new BmfObject(warehouseInSheet.getBmfClassName())
            warehouseInSheetUpdate.put("id", warehouseInSheet.getPrimaryKeyValue())
            if (sumSheetQty.compareTo(BigDecimal.ZERO) == 0) {
                warehouseInSheetUpdate.put("status", "completed")
            } else {
                warehouseInSheetUpdate.put("status", "partWarehoused")
            }
            basicGroovyService.updateByPrimaryKeySelective(warehouseInSheetUpdate)
        }

    }

/**
 * 入库结果单
 * 按目前表只能这么做，后续表改了再说
 * @param passBoxReal
 * @param quantity
 * @param warehouseInApplicationCode
 * @param warehouseInApplicationDetail
 * @param nodeData
 */
    private void maintenanceWarehouseInResult(BmfObject passBoxReal, BigDecimal quantity, BmfObject warehouseInApplication, BmfObject warehouseInApplicationDetail, BmfObject warehouseInSheetDetail, BmfObject nodeData) {
        def warehouseInApplicationCode = warehouseInApplication.getString("code")
        //查询是否已经存在入库申请单对应的入库结果单
        BmfObject warehouseInResult = basicGroovyService.findOne("warehouseInResult", "warehouseInApplicationCode", warehouseInApplicationCode)
        if (!warehouseInResult) {
            //直接新增
            warehouseInResult = new BmfObject("warehouseInResult")
            warehouseInResult.put("sourceOrderCode", nodeData.getString("code"))
            warehouseInResult.put("sourceOrderType", nodeData.getBmfClassName())
            warehouseInResult.put("warehouseInApplicationCode", warehouseInApplicationCode)
            warehouseInResult.put("warehouseInType", warehouseInApplication.getString("warehouseInType"))
            codeGenerator.setCode(warehouseInResult)
            basicGroovyService.saveOrUpdate(warehouseInResult)
        }
        List<BmfObject> warehouseInResultDetails = warehouseInResult.getAndRefreshList("mainidAutoMapping");
        BmfObject detail = warehouseInResultDetails.stream().filter {
            warehouseInApplicationDetail.getPrimaryKeyValue() == it.getLong("warehouseInApplicationDetailId")
        }.findFirst().orElse(null)
        if (ObjectUtils.isEmpty(detail)) {
            detail = new BmfObject("warehouseInResultDetail")
            detail.put("mainid", warehouseInResult)
            detail.put("materialName", warehouseInSheetDetail.getString("materialName"))
            detail.put("materialCode", warehouseInSheetDetail.getString("materialCode"))
            detail.put("specifications", warehouseInSheetDetail.getString("specifications"))
            detail.put("unit", warehouseInSheetDetail.get("unit"))
            detail.put("quantity", quantity)
            detail.put("warehouseName", warehouseInSheetDetail.getString("warehouseName"))
            detail.put("warehouseCode", warehouseInSheetDetail.getString("warehouseCode"))
            detail.put("sourceOrderLine", warehouseInApplicationDetail.getInteger("lineNum"))
            detail.put("warehouseInApplicationDetailId", warehouseInApplicationDetail.getPrimaryKeyValue())
            basicGroovyService.saveOrUpdate(detail)
        } else {
            detail.put("quantity", detail.getBigDecimal("quantity")== null ? quantity: detail.getBigDecimal("quantity").add(quantity))
            basicGroovyService.updateByPrimaryKeySelective(detail)
        }
        BmfObject warehouseInResultPassBox = new BmfObject("warehouseInResultPassBox")
        warehouseInResultPassBox.put("warehouseInResultDetailId", detail)
        warehouseInResultPassBox.put("passBoxName", passBoxReal.getString("passBoxName"))
        warehouseInResultPassBox.put("passBoxCode", passBoxReal.getString("passBoxCode"))
        warehouseInResultPassBox.put("passBoxRealCode", passBoxReal.getString("code"))
        warehouseInResultPassBox.put("quantity", quantity)
        warehouseInResultPassBox.put("unit", warehouseInSheetDetail.get("unit"))
        basicGroovyService.saveOrUpdate(warehouseInResultPassBox)


    }

    void sapSyncWarehouseInApplication(BmfObject warehouseInApplicationDetail) {
        def bmfObject = warehouseInApplicationDetail.getAndRefreshBmfObject("main_id")
        UserAuthDto.LoginInfo loginInfo = ThreadLocalHolder.getLoginInfo();
        if (loginInfo == null) {
            throw new BusinessException("未获取到当前登录人")
        }
        def user = basicGroovyService.find("user", loginInfo.getLoginId())
        if (user == null) {
            throw new BusinessException("未获取到当前登录人2")
        }
        def submitter = user.getString("ext_erp_submitter")
        bmfObject.put("inOutType", "in")
        List<BmfObject> details = bmfObject.getAndRefreshList("main_idAutoMapping")
        for (final def detail in details) {
            BmfObject warehouse = basicGroovyService.findOne("warehouse", "code", detail.getString("target_warehouse_code"))
            if (warehouse != null) {
                //塞仓库
                detail.put("ext_erp_warehouse_code", warehouse.getString("ext_erp_warehouse_code"))
                detail.put("ext_erp_submitter", submitter)
                detail.put("ext_erp_auditor", warehouse.getString("ext_erp_auditor"))
                detail.put("ext_erp_store_man", warehouse.getString("ext_erp_store_man"))
                detail.put("ext_erp_stock_loc_id", warehouse.getString("ext_erp_stock_loc_id"))
                detail.put("batchCode","ZD")
            }
            detail.put("materialCode",detail.getString("materialCode").substring(2))
            detail.put("materialName", detail.getString("materialName").substring(2))
        }
        BmfObject inventoryReceipt = new BmfObject("inventoryReceipt")
        //同步状态 0待同步 9失败 1成功
        inventoryReceipt.put("status", "0")
        inventoryReceipt.put("type", "1")
        inventoryReceipt.put("params", JSON.toJSONString(bmfObject))
        inventoryReceipt.put("description", "入库申请单同步")
        this.basicGroovyService.saveOrUpdate(inventoryReceipt)
    }
}
