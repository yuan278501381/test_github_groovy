package groovy.node_groovy

import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.dwork.company.modules.modbus.enums.UnBoxingPassBox
import com.chinajey.dwork.standard.common.util.JsonUtils
import com.tengnat.dwork.modules.manufacture.service.PassBoxRealService
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass

import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService
import org.apache.commons.lang3.StringUtils

/**
 * 滚筒线下架任务提交脚本
 */
class NodeCT1121Submit extends NodeGroovyClass {
    def basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    def sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)
    def passBoxRealService = SpringUtils.getBean(PassBoxRealService.class)

    @Override
    JSONObject runScript(BmfObject nodeData) {
        //是否拆箱
        def isUnloadBox = nodeData.getString("ext_unboxing")
        def passBoxCode = nodeData.getString("ext_pass_box_code")
        def currentRequiredQuantity = nodeData.getBigDecimal("ext_current_required_quantity")
        def docType = nodeData.getString("ext_warehouse_out_type")
        def docEntry = nodeData.getString("ext_warehouse_out_application_code")

        //是否跳过拆箱
        def isSkipUnLoadBox = nodeData.getBoolean("ext_is_skip_unboxing_passbox")


        //不需要拆箱
        if (isUnloadBox == UnBoxingPassBox.NOT_REQUIRED.getCode()) {
            return null
        }
        //待拆箱
        if (isUnloadBox == UnBoxingPassBox.UN_BOXING.getCode()) {
            def tasks = nodeData.getList("tasks")
            if (tasks == null || tasks.size() == 0) {
                throw new BusinessException("当前任务没有任务明细")
            }
            if (tasks.size() > 1) {
                throw new BusinessException("当前任务只允许扫描一个任务明细")
            }
            if (isSkipUnLoadBox) {
                //创建线下拆箱任务
                createCT1114Task(nodeData)
            } else {
                //创建滚筒线搬运任务
                createCT1112Task(nodeData)
            }
        }
        return null
    }

    void createCT1114Task(BmfObject nodeData) {
        BmfObject object = new BmfObject("CT1114")
        //来源
        object.put("dataSourceCode", nodeData.getPrimaryKeyValue())
        //来源明细type
        object.put("dataSourceType", nodeData.getBmfClassName())
        //工作台编码
        object.put("inventoryWorkbenchCode", nodeData.getString("inventoryWorkbenchCode"))
        //工作台名称
        object.put("inventoryWorkbenchName", nodeData.getString("inventoryWorkbenchName"))
        //出库申请单编码
        object.put("ext_outbound_order_code", nodeData.getString("ext_warehouse_out_application_code"))
        //出库类型
        object.put("ext_outbound_type", nodeData.getString("ext_warehouse_out_type"))
        //单位
        object.put("ext_unit", nodeData.getString("ext_unit_name"))
        //拆箱周转箱编码
        object.put("ext_unboxing_passbox_code", nodeData.getString("ext_pass_box_code"))
        //本次需求数量
        object.put("ext_current_required_quantity", nodeData.getBigDecimal("ext_current_required_quantity"))

        List<JSONObject> tasks = new ArrayList<>()
        JSONObject task = new JSONObject()
        JsonUtils.jsonMerge(nodeData.getList("tasks").get(0), task, "id", "mainData")
        tasks.add(task)

        object.put("tasks", tasks)
        sceneGroovyService.buzSceneStart("CT1114", object)
    }

    void createCT1112Task(BmfObject nodeData) {
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
        //滚筒线下架任务明细
        def downTask = nodeData.getList("tasks").get(0)

        //枚举：inventoryWorkbenchType out in return
        //出库口
        def outLocationCode = null
        def outLocationName = null
        def outLocation = locations.stream()
                .filter(location -> "out" == location.getString("value"))
                .findFirst().ifPresent(
                location -> {
                    outLocationCode = location.getString("locationCode")
                    outLocationName = location.getString("locationName")
                })

        outLocationCode = outLocation.getString("locationCode")
        //回库口
        def returnLocationCode = null
        def returnLocationName = null
        def returnLocation = locations.stream()
                .filter(location -> "return" == location.getString("value"))
                .findFirst().ifPresent(location -> {
            returnLocationCode = location.getString("locationCode")
            returnLocationName = location.getString("locationName")
        })

        if (outLocation == null) {
            throw new BusinessException("工作台没有配置出库口")
        }
        if (returnLocation == null) {
            throw new BusinessException("工作台没有配置回库口")
        }


        BmfObject object = new BmfObject("CT1112")
        //来源
        object.put("dataSourceCode", nodeData.getPrimaryKeyValue())
        //来源明细type
        object.put("dataSourceType", nodeData.getBmfClassName())
        //源位置编码
        object.put("sourceLocationCode", outLocationCode)
        //源位置名称
        object.put("sourceLocationName", outLocationName)
        //目标位置编码
        object.put("targetLocationCode", returnLocationCode)
        //目标位置名称
        object.put("targetLocationName", returnLocationName)
        //业务类型
        object.put("ext_business_type", nodeData.getString("ext_warehouse_out_type"))
        //业务来源
        object.put("ext_business_source",nodeData.getBmfClassName())
        //来源编码
        object.put("ext_business_source_code",nodeData.getString("ext_warehouse_out_application_code"))
        //物料编码
        object.put("ext_material_code", downTask.getString("materialCode"))
        //物料名称
        object.put("ext_material_name", downTask.getString("materialName"))
        //周转箱编码
        object.put("ext_pass_box_code", nodeData.getString("ext_pass_box_code"))
        //滚筒线目标位置
        object.put("ext_end_point", nodeData.getString("returnLocationCode"))

        //------------------------任务明细 START------------------------
        List<JSONObject> tasks = new ArrayList<>()
        JSONObject task = new JSONObject()
        JsonUtils.jsonMerge(nodeData.getList("tasks").get(0), task, "id", "mainData")
        tasks.add(task)
        object.put("tasks", tasks)
        //------------------------任务明细 END--------------------------

        //------------------------任务周转箱 START-----------------------

        //获取回库周转箱号
        def returnPassBoxCode = downTask.getString("ext_return_pass_box")
        def originalPassBoxCode = nodeData.getString("ext_pass_box_code")
        if (StringUtils.isNotBlank(returnPassBoxCode)){
            throw new BusinessException("回库周转箱编号不能为空")
        }
        //回库周转箱 = 原箱 更新原箱周转箱实时的数量 将原箱塞入回库任务周转箱列表中
        //回库周转箱 = 新箱 更新原箱周转箱实时的数量 交新箱塞入回库任务周转箱列表中
        List<JSONObject> passBoxes = new ArrayList<>()
        if (returnPassBoxCode == originalPassBoxCode) {
            def originalPassBoxReal = basicGroovyService.findOne("passBoxReal", "passBoxCode", originalPassBoxCode)
            if (originalPassBoxReal == null) {
                throw new BusinessException("回库周转箱不存在:" + originalPassBoxCode)
            }
            originalPassBoxReal.put("quantity", originalPassBoxReal.getBigDecimal("quantity") - nodeData.getBigDecimal("ext_current_required_quantity"))
            basicGroovyService.updateByPrimaryKeySelective(originalPassBoxReal)
            passBoxRealService.synchronizePassBoxInfo(originalPassBoxReal, "PAD", "CT1121", "CT1121滚筒线下架任务提交")
            passBoxes.add(originalPassBoxReal)
        } else {
            passBoxes.add(nodeData.getList("passBoxes").get(0))
        }
        object.put("passBoxes", passBoxes)
        //------------------------任务周转箱 END-----------------------
        sceneGroovyService.buzSceneStart("CT1112", object)
    }

}