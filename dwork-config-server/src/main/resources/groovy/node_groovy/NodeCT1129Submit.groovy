package groovy.node_groovy

import com.alibaba.fastjson.JSON
import com.chinajay.virgo.bmf.obj.BmfObject
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
 * @author 袁英杰
 * @CreateDate 2025-01-15
 * @Dscription CT1129 入库任务-特殊（PDA）提交时，1\创建入库结果单，2\反写入库申请单，3\回传ERP
 * */
class NodeCT1129Submit extends NodeGroovyClass {
    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)
    CodeGenerator codeGenerator = SpringUtils.getBean(CodeGenerator.class)

    @Override
    Object runScript(BmfObject nodeData) {
        //获取周转箱信息
        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        def businessType = nodeData.getString("ext_business_type")
        def warehouseInApplicationCode = nodeData.getString("ext_warehouse_in_application_code")
        if (!warehouseInApplicationCode) {
            throw new BusinessException("入库申请单编码不能为空，请检查后重试！")
        }
        if (!passBoxes) {
            throw new BusinessException("周转箱信息必须填写，请检查后重试！")
        }
        def sum=passBoxes.sum(it->it.getBigDecimal("receiveQuantity"))?:BigDecimal.ZERO
        def plannedQty=nodeData.getBigDecimal("ext_quantity")?:BigDecimal.ZERO
        if(sum!=plannedQty){
            throw new BusinessException("周转箱累计数量必须等于任务数量，请检查后重试！")
        }
        for (final def passBox in passBoxes) {
            //更新同步入库申请单
            updateWarehouseInApplication(passBox, nodeData, warehouseInApplicationCode)
        }
        return nodeData
    }

    /**
     * 更新入库申请单
     */
    private void updateWarehouseInApplication(BmfObject passBox, BmfObject nodeData, String warehouseInApplicationCode) {

        //默认批次编码为：ZD
        String batchNumber = "ZD"
        if (!nodeData.get("ext_batch_number")) {
            batchNumber = "ZD"
        } else {
            batchNumber = nodeData.get("ext_batch_number")
        }

        if(nodeData.getString("ext_material_code")!=passBox.getString("materialCode"))
        {
           throw new BusinessException("周转箱物料编码与任务单物料编码不一致，请检查后重试！")
        }

        //本次填写的数量，而不是周转箱的quantity
        BigDecimal thisQuantity = passBox.getBigDecimal("receiveQuantity")
        //匹配入库申请单
        String materialCode = passBox.getString("materialCode")
        def warehouseInApplication1 = basicGroovyService.getByCode("WarehouseInApplication", warehouseInApplicationCode)

        // 取第一个“未完成”的 WarehouseInApplication
        def warehouseInApplication2 = warehouseInApplication1.stream()
                .filter { it ->
                    // 注意这里是 Java Lambda 写法，Groovy 里要么写 “->” 要么写匿名内部类
                    return !(it.getString("status") in ["completed", "closed", "done", "cancel"])
                }
                .findFirst()//取第一个
                .orElse(null)//无结果时返回默认值:null

        if (!warehouseInApplication2) {
            throw new BusinessException("未找到未完成的入库申请单编码: $warehouseInApplicationCode")
        }

        // 再取明细列表
        List<BmfObject> warehouseInApplicationDetailsAll = warehouseInApplication2.getAndRefreshList("main_idAutoMapping")

        List<BmfObject> warehouseInApplicationDetailsFilter = warehouseInApplicationDetailsAll.stream()
                .filter { detail ->
                    detail.getString("materialCode") == materialCode &&
                    detail.getBigDecimal("wait_inbound_quantity") > BigDecimal.ZERO
                }
                .sorted( Comparator .comparingInt({((BmfObject) it).getInteger("lineNum") } )
                                .thenComparingLong({ ((BmfObject)it).getPrimaryKeyValue() })
                        //((BmfObject) it)的意义为：将it转换为BmfObject类型
                )
                .collect(Collectors.toList())

        if (!warehouseInApplicationDetailsFilter) {
            throw new BusinessException("未找到未完成的入库申请单明细：$warehouseInApplicationCode")
        }

        //开始修改 入库申请单 行数量
        for (BmfObject detail : warehouseInApplicationDetailsFilter) {
            BigDecimal docLineQuantity = detail.getBigDecimal("quantity")
            BigDecimal warehousedQuantity = detail.getBigDecimal("warehoused_quantity")
            BigDecimal noWarehousedQuantity = docLineQuantity - warehousedQuantity
            if (thisQuantity == 0 || noWarehousedQuantity <= 0) {
                continue
            }
            //本次数量 > 未收货数量
            if (noWarehousedQuantity < thisQuantity) {

                warehousedQuantity = warehousedQuantity + noWarehousedQuantity
                detail.put("warehoused_quantity", warehousedQuantity)
                detail.put("wait_inbound_quantity", docLineQuantity - warehousedQuantity)

                //创建、更新入库结果单据
                maintenanceWarehouseInResult(passBox, noWarehousedQuantity, warehouseInApplication2, detail, nodeData)
                thisQuantity = thisQuantity-noWarehousedQuantity
            } else {
                warehousedQuantity = warehousedQuantity + thisQuantity
                detail.put("warehoused_quantity", warehousedQuantity)
                detail.put("wait_inbound_quantity", docLineQuantity - warehousedQuantity)

                //创建更新入库结果单据
                maintenanceWarehouseInResult(passBox, thisQuantity, warehouseInApplication2, detail, nodeData)
                thisQuantity = BigDecimal.ZERO
            }
            //塞仓库
            detail.put("target_warehouse_code", nodeData.getString("warehouseCode"))
            detail.put("target_warehouse_name", nodeData.getString("warehouseName"))
            //更新入库申请单行的数量
            basicGroovyService.updateByPrimaryKeySelective(detail)
            //同步回sap
            sapSyncWarehouseInApplication(detail)
        }

        BigDecimal sumWarehousedQty = BigDecimal.ZERO
        for (BmfObject detail : warehouseInApplicationDetailsAll) {
            sumWarehousedQty = sumWarehousedQty + ValueUtil.toBigDecimal(detail.getBigDecimal("wait_inbound_quantity"), BigDecimal.ZERO)
        }
        if (!Arrays.asList("completed", "closed").contains(warehouseInApplication2.getString("status"))) {
            BmfObject warehouseInApplicationUpdateUpdate = new BmfObject(warehouseInApplication2.getBmfClassName())
            warehouseInApplicationUpdateUpdate.put("id", warehouseInApplication2.getPrimaryKeyValue())
            if (sumWarehousedQty== 0) {
                warehouseInApplicationUpdateUpdate.put("status", "completed")
            } else {
                warehouseInApplicationUpdateUpdate.put("status", "partWarehoused")
            }
            basicGroovyService.updateByPrimaryKeySelective(warehouseInApplicationUpdateUpdate)
        }

        //更新周周转箱位置，取值于界面上的位置
        BmfObject passBoxReal = basicGroovyService.getByCode("passBoxReal", passBox.getString("code"))
        if (!passBoxReal){
            throw new  BusinessException("周转箱实时信息不存在")
        }
        passBoxReal.put("location", basicGroovyService.getByCode("location", nodeData.getString("targetLocationCode")))
        passBoxReal.put("locationCode", nodeData.getString("targetLocationCode"))
        passBoxReal.put("locationName", nodeData.getString("targetLocationName"))
        sceneGroovyService.synchronizePassBoxInfo(passBoxReal, EquipSourceEnum.PDA.getCode(), nodeData.getBmfClassName())

        //更新周转箱实时表的批次编码和产品线
        passBoxReal.put("ext_batch_number",batchNumber)
        passBoxReal.put("ext_prdLine", nodeData.getString("ext_prdLine"))
        basicGroovyService.updateByPrimaryKeySelective(passBoxReal)



    }

/**
 * 入库结果单
 *如果考虑与ERP的每笔业务的过账记录，那么这里永远应该是新增一条记录，而是不追加
 */
    private void maintenanceWarehouseInResult(BmfObject passBoxReal, BigDecimal quantity, BmfObject warehouseInApplication, BmfObject warehouseInApplicationDetail, BmfObject nodeData) {
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
            basicGroovyService.setCode(warehouseInResult)
            basicGroovyService.saveOrUpdate(warehouseInResult)
        }
        //匹配与当前入库申请单行ID一致的入库结果单行ID
        List<BmfObject> warehouseInResultDetails = warehouseInResult.getAndRefreshList("mainidAutoMapping");
        BmfObject detail = warehouseInResultDetails.stream().filter {
            warehouseInApplicationDetail.getPrimaryKeyValue() == it.getLong("warehouseInApplicationDetailId")
        }.findFirst().orElse(null)
        if (!detail) {//如果detail不存在，则新增

            detail = new BmfObject("warehouseInResultDetail")
            detail.put("mainid", warehouseInResult)//来至于前面添加后的warehouseInResult 的ID
            detail.put("materialName", warehouseInApplicationDetail.getString("materialName"))
            detail.put("materialCode", warehouseInApplicationDetail.getString("materialCode"))
            detail.put("specifications", warehouseInApplicationDetail.getString("specifications"))
            detail.put("unit", warehouseInApplicationDetail.get("unit"))
            detail.put("quantity", quantity)
            detail.put("warehouseName", warehouseInApplicationDetail.getString("target_warehouse_name"))
            detail.put("warehouseCode", warehouseInApplicationDetail.getString("target_warehouse_code"))
            detail.put("sourceOrderLine", warehouseInApplicationDetail.getInteger("lineNum"))
            detail.put("warehouseInApplicationDetailId", warehouseInApplicationDetail.getPrimaryKeyValue())
            basicGroovyService.saveOrUpdate(detail)
        } else {//如果detail存在，则更新
            detail.put("quantity", detail.getBigDecimal("quantity") == null ? quantity : detail.getBigDecimal("quantity").add(quantity))
            basicGroovyService.updateByPrimaryKeySelective(detail)
        }
        BmfObject warehouseInResultPassBox = new BmfObject("warehouseInResultPassBox")
        warehouseInResultPassBox.put("warehouseInResultDetailId", detail)//来自于前面添加后的warehouseInResultDetail 的ID
        warehouseInResultPassBox.put("passBoxName", passBoxReal.getString("passBoxName"))
        warehouseInResultPassBox.put("passBoxCode", passBoxReal.getString("passBoxCode"))
        warehouseInResultPassBox.put("passBoxRealCode", passBoxReal.getString("code"))
        warehouseInResultPassBox.put("quantity", quantity)
        warehouseInResultPassBox.put("unit", warehouseInApplicationDetail.get("unit"))
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
                detail.put("batchCode", "ZD")
            }
            detail.put("materialCode", detail.getString("materialCode").substring(2))
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
