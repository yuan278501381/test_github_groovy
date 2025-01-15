package groovy.node_groovy

import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.bmf.service.BmfService
import com.chinajay.virgo.bmf.sql.Conjunction
import com.chinajay.virgo.bmf.sql.OperationType
import com.chinajay.virgo.bmf.sql.Restriction
import com.chinajay.virgo.bmf.sql.Where
import com.chinajay.virgo.utils.BmfUtils
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.common.enums.EquipSourceEnum
import com.tengnat.dwork.modules.basic_data.service.ResourceBindingService
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService
import org.springframework.util.ObjectUtils
import java.util.stream.Collectors

/**
 * 立库期初盘点提交脚本
 */
class NodeCT1124Submit extends NodeGroovyClass {
    def basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)
    ResourceBindingService resourceBindingService = SpringUtils.getBean(ResourceBindingService.class)
    BmfService bmfService = SpringUtils.getBean(BmfService.class)

    @Override
    JSONObject runScript(BmfObject nodeData) {
        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        if (ObjectUtils.isEmpty(passBoxes)) {
            throw new BusinessException("周转箱不能为空")
        }
        List<Object> codes = passBoxes.stream().map(it -> it.getString("code")).collect(Collectors.toList())
        Map<String, BmfObject> collect = passBoxes.stream().collect(Collectors.toMap(it -> ((BmfObject) it).getString("code"), it -> it))
        List<BmfObject> passBoxReals = basicGroovyService.find("passBoxReal", Where.builder()
                .restrictions(Collections.singletonList(
                        Restriction.builder()
                                .conjunction(Conjunction.AND)
                                .columnName("code")
                                .operationType(OperationType.IN)
                                .values(codes)
                                .build()
                )).build())
        //更新周转箱数量 覆盖值 receiveQuantity 和批次号
        for (BmfObject passBoxReal in passBoxReals) {
            BmfObject passBox = collect.get(passBoxReal.getString("code"))
            passBoxReal.put("quantity", passBox.get("receiveQuantity"))
            passBoxReal.put("ext_batch_number", nodeData.get("ext_batch_number"))
            passBoxReal.put("ext_prdLine", nodeData.getString("ext_prdLine"))

            sceneGroovyService.batchSynchronizePassBoxInfo(passBoxReals, EquipSourceEnum.PDA.getCode(), nodeData.getBmfClassName())
            //发起滚筒线搬运一个周转箱一个搬运任务
            createCT1112Task(nodeData, passBoxReal)
        }
        // throw new BusinessException("ttttttttttt")
        return nodeData

    }

    void createCT1112Task(BmfObject nodeData, BmfObject passBox) {

        def isCTU= basicGroovyService.getByCode("warehouseCategory",
                basicGroovyService.getByCode("warehouse", nodeData.getString("warehouseCode")).getString("categoryCode"))
                .getString("name") contains("CTU")

        if(!isCTU){
            throw new BusinessException("仓库类别不是CTU仓库，不能执行CTU期初入库")
        }

        //默认批次编码为：ZD
        String batchNumber = "ZD"
        if (!nodeData.get("ext_batch_number")) {
            batchNumber = "ZD"
        } else {
            batchNumber = nodeData.get("ext_batch_number")
        }




        BmfObject object = new BmfObject("CT1112")
        //来源
        object.put("dataSourceCode", nodeData.getPrimaryKeyValue())
        //来源明细type
        object.put("dataSourceType", nodeData.getBmfClassName())
        //业务类型
        object.put("ext_business_type", "ctuBeginIn")
        object.put("ext_business_type_name","CTU期初入库")
        object.put("ext_in_out_type", "in")
        //业务来源
        // object.put("ext_business_source", nodeData.getBmfClassName())
        def warehouseCode=nodeData.getString("warehouseCode")
        def warehouseCategoryCode=basicGroovyService.getByCode("warehouse", warehouseCode).getString("categoryCode")
        def location = getWarehouse2ByLocation(warehouseCategoryCode,warehouseCode).toString()
        log.info("location:{}", location)
        object.put("ext_end_point", location)//滚筒线目标位置
        object.put("targetLocationCode", location)//目标位置编码
        object.put("targetLocationName", basicGroovyService.getByCode("location", location).getString("name"))//目标位置名称

        //来源编码
        object.put("ext_business_source_code", nodeData.getPrimaryKeyValue())

        BmfObject material = passBox.getAndRefreshBmfObject("material")
        //物料编码
        object.put("ext_material_code", material.getString("code"))
        //物料名称
        object.put("ext_material_name", material.getString("name"))
        //周转箱编码
        object.put("ext_pass_box_code", passBox.getString("passBoxCode"))
        passBox = BmfUtils.genericFromJsonExt(passBox, "CT1112PassBoxes")
        passBox.put("id", null)
        passBox.put("submit", false)
        passBox.put("quantityUnit", material.getAndRefreshBmfObject("flowUnit"))
        object.put("passBoxes", Arrays.asList(passBox))
        BmfObject task = new BmfObject("CT1112Tasks")
        task.put("materialCode", material.getString("code"))
        task.put("materialName", material.getString("name"))
        task.put("material", material)
        task.put("quantityUnit", material.getAndRefreshBmfObject("flowUnit"));
        object.put("tasks", Arrays.asList(task))

        sceneGroovyService.buzSceneStart("CT1112", object)

        //为周转箱实时表的批次、产品线字段赋值
        BmfObject passBoxReal = basicGroovyService.getByCode("passBoxReal", passBox.getString("code"))
        if (!passBoxReal) {
            throw new BusinessException("周转箱实时信息不存在")
        } else {
            passBoxReal.put("ext_batch_number", batchNumber)
            //passBoxReal.put("ext_prdLine", nodeData.getString("ext_prdLine"))
            basicGroovyService.updateByPrimaryKeySelective(passBoxReal)
        }
    }

    def getWarehouse2ByLocation(String warehouseCategoryCode,String warehouseCode) {
        //传入仓库类别编码和仓库编码，优先返回该仓库编码背后关联库位上的第一个最小（按库位编码排序）空位置，如果前述逻辑返回空值，那么从该仓库类别下返回第一个最小空位置。
        // 示例：  call proc_getWarehouseLocationIn ('CB1002','CK0007') 返回值：WZ00012
        String sSQL
        sSQL= "call proc_getWarehouseLocationIn ('"
        sSQL+=warehouseCategoryCode+"','"
        sSQL+=warehouseCode
        sSQL+="')"

        def sqlResult = basicGroovyService.findOne(sSQL)
        def location = sqlResult.get("location_code")
        if (!location){
            throw new BusinessException("未找到最小空库位，请检查后重试！ 传入参数为: 仓库类别编码：$warehouseCategoryCode,仓库编码:$warehouseCode")
        }
        log.info("==============传入参数仓库类别编码：$warehouseCategoryCode，仓库编码：$warehouseCode，返回最小空库位$location==============")
        return location
    }
}

