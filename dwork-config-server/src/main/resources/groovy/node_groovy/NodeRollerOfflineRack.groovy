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
class NodeRollerOfflineRack extends NodeGroovyClass {
    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)

    @Override
    Object runScript(BmfObject nodeData) {

        return nodeData
    }

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
        }
    }

}
