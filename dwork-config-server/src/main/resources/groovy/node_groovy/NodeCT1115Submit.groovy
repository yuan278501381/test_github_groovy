package groovy.node_groovy


import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.BmfUtils
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.modules.logistics.enums.LogisticsStatusType
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService
import org.apache.commons.lang3.StringUtils


/**
 * 出库确认生成滚筒线下架
 */
class NodeCT1115Submit extends NodeGroovyClass {
    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)

    @Override
    Object runScript(BmfObject nodeData) {

        return nodeData
    }

    //出库确认生成滚筒线下架
    void createCT1121(BmfObject nodeData, List<BmfObject> passBoxes) {
        BigDecimal outboundOrderQuantity = nodeData.getBigDecimal("ext_outbound_order_quantity")
        for (BmfObject passBox : passBoxes) {
            BmfObject passBoxReal = basicGroovyService.getByCode("passBoxReal", passBox.getString("code"))
            if (passBoxReal == null) {
                throw new BusinessException("找不到周转箱：" + passBox.getString("code"))
            }
            outboundOrderQuantity = outboundOrderQuantity.subtract(passBox.getBigDecimal("quantity"))
            def ct1121 = BmfUtils.genericFromJsonExt(nodeData, "CT1121")
            ct1121.put("id", null)
            ct1121.put("logisticsStatus", LogisticsStatusType.CREATED.getCode())
            ct1121.put("ext_warehouse_out_application_code", nodeData.getString("ext_outbound_order_code"))
            ct1121.put("ext_warehouse_out_type", nodeData.getString("ext_outbound_type"))
            ct1121.put("ext_current_required_quantity", passBox.getBigDecimal("quantity"))
            ct1121.put("ext_unit_name", nodeData.getString("ext_unit"))
            ct1121.put("ext_pass_box_code", passBox.getString("passBoxCode"))
            if (outboundOrderQuantity.compareTo(BigDecimal.ZERO) >= 0) {
                ct1121.put("ext_unboxing", false)
            } else {
                ct1121.put("ext_unboxing", true)
            }
            passBoxReal.put("id", null)
            passBoxReal.put("submit", false)
            BmfObject bmfObject = BmfUtils.genericFromJsonExt(passBoxReal, "CT1121PassBoxes")
            ct1121.put("passBoxes", Arrays.asList(bmfObject))
            BmfObject material = basicGroovyService.getByCode("material", passBoxReal.getString("material_code"))
            if (material == null) {
                throw new BusinessException("找不到物料：" + passBoxReal.getString("material_code"))
            }
            BmfObject ct1121Task = new BmfObject("CT1121Tasks")
            ct1121Task.put("materialCode", material.getString("code"))
            ct1121Task.put("materialName", material.getString("name"))
            ct1121Task.put("material", material)
            ct1121Task.put("quantityUnit", material.getAndRefreshBmfObject("flowUnit"))
            ct1121.put("tasks", Arrays.asList(ct1121Task))
            sceneGroovyService.buzSceneStart("CT1121", ct1121)
            createCT1111Task(nodeData, passBoxReal, material)
        }
    }

    //出库确认生成ctu搬运
    void createCT1111Task(BmfObject nodeData, BmfObject passBox, BmfObject material) {
        BmfObject location = passBox.getAndRefreshBmfObject("location")
        def inventoryWorkbenchCode = nodeData.getString("inventoryWorkbenchCode")
        if (StringUtils.isBlank(inventoryWorkbenchCode)) {
            throw new BusinessException("工作台编码不能为空")
        }
        def inventoryWorkbench = basicGroovyService.findOne("inventoryWorkbench", "code", inventoryWorkbenchCode)
        if (inventoryWorkbench == null) {
            throw new BusinessException("工作台不存在:" + inventoryWorkbenchCode)
        }
        def locations = inventoryWorkbench.getAndRefreshList("locations")
        if (locations == null || locations.size() == 0) {
            throw new BusinessException("工作台没有配置库位")
        }
        //出库口
        def inLocationCode = null
        def inLocationName = null
        def inLocation = locations.stream()
                .filter(b -> "in" == b.getString("value"))
                .findFirst().ifPresent(
                b -> {
                    inLocationCode = b.getString("locationCode")
                    inLocationName = b.getString("locationName")
                })
        if (inLocation == null) {
            throw new BusinessException("工作台没有配置出库口")
        }
        inLocationCode = inLocation.getString("locationCode")

        BmfObject object = new BmfObject("CT1111")
        //来源
        object.put("dataSourceCode", nodeData.getPrimaryKeyValue())
        //来源明细type
        object.put("dataSourceType", nodeData.getBmfClassName())
        //源位置编码
        object.put("sourceLocationCode", location.getString("code"))
        //源位置名称
        object.put("sourceLocationName", location.getString("name"))
        //目标位置编码
        object.put("targetLocationCode", inLocationCode)
        //目标位置名称
        object.put("targetLocationName", inLocationName)
        //业务类型
        object.put("ext_business_type", nodeData.getString("ext_outbound_type"))
        //业务来源
        object.put("ext_business_source", nodeData.getBmfClassName())
        //来源编码
        object.put("ext_business_source_code", nodeData.getString("ext_outbound_order_code"))
        //物料编码
        object.put("ext_material_code", nodeData.getString("materialCode"))
        //物料名称
        object.put("ext_material_name", nodeData.getString("materialName"))
        //周转箱编码
        object.put("ext_pass_box_code", passBox.getString("passBoxCode"))
        //ctu目标位置
        object.put("ext_end_point", inLocationCode)
        //------------------------任务明细 START------------------------
        BmfObject ct1111Task = new BmfObject("CT1111Tasks")
        ct1111Task.put("materialCode", material.getString("code"))
        ct1111Task.put("materialName", material.getString("name"))
        ct1111Task.put("material", material)
        ct1111Task.put("quantityUnit", material.getAndRefreshBmfObject("flowUnit"))
        object.put("tasks", Arrays.asList(ct1111Task))
        //------------------------任务明细 END--------------------------
        //------------------------任务周转箱 START-----------------------
        passBox.put("id", null)
        passBox.put("submit", false)
        BmfObject bmfObject = BmfUtils.genericFromJsonExt(passBox, "CT1111PassBoxes")
        object.put("passBoxes", Arrays.asList(bmfObject))
        //------------------------任务周转箱 END-----------------------
        sceneGroovyService.buzSceneStart("CT1111", object)
    }

}
