package groovy.node_scan_groovy

import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.tengnat.dwork.modules.manufacturev2.domain.DomainScanResult
import com.tengnat.dwork.modules.script.abstracts.NodeScanGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
/**
 * @author 袁英杰
 * @Create 2025-01-07
 * @Dscription CTU库期初盘点-PDA 扫描周转箱时自动按界面上的数量，为周转箱数量赋值
 * */
class NodeCT1124Scan extends NodeScanGroovyClass {

    //基础类
    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)

    @Override
    JSONObject runScript(BmfObject nodeData) {
        log.info(JSONObject.toJSONString(nodeData))
        def result = new DomainScanResult()
        //扫描的组件模块
        def model = nodeData.getString("model")
        //扫描的编码
        def passBoxCode = nodeData.getString("code")
        def objNode = nodeData.deepClone()
        def tasklist = objNode.getList("tasks")
        if (!tasklist &&  !nodeData.getString("code").contains("WL")) {
            return result.fail("请先扫描或选择一个物料编码！")
        }

        //如果扫描模块是物料关联虚拟周转箱，则进行判断。因为扫描物料编码也会进入周转箱模块，后面的getInteger取值会报错，所以这里排除掉，
        if (model.contains("PassBox") &&  !passBoxCode.contains("WL")) {
            /*
            virtualPassBox-虚拟周转箱控件
            materialRelevancePassBox-物料关联周转箱
             */

            if (tasklist.size()>=2) {
                return result.fail("立库期初盘点功能仅允许一个物料！")
            }
            //def taskSelect=tasklist.findAll { it -> it.getBoolean("judgeSelect")}.get(0)

            def material= basicGroovyService.getByCode("material",tasklist.get(0).getString("materialCode"))

            def suggestedQuantity = nodeData.getBigDecimal("ext_quantity")?: BigDecimal.ZERO //取得PDA界面上的数量
            def batchNumber = nodeData.getString("ext_batch_number")//取得PDA界面上的批次号

            JSONObject resultData = new JSONObject();

            //new一个JSONObject,然后为新增周转箱字段赋值
            def objPassBox = basicGroovyService.getByCode("passBox", passBoxCode)
            def passBoxJson = new JSONObject()
//立库期初盘点界面上已经选择了一个物料编码，因此这里不在需要写tasks
//            def tasksJson=new JSONObject()
//            tasksJson.put("materialCode",material.getString("code") )
//            tasksJson.put("materialName",material.getString("name"))
//            tasksJson.put("materialId", material.getInteger("id"))
//            resultData.put("tasks", Collections.singletonList(tasksJson))
            passBoxJson.put("virtualPassBox", false)
            passBoxJson.put("boxSelect",true)
            passBoxJson.put("materialCode", material.getString("code"))
            passBoxJson.put("materialName", material.getString("name"))
            passBoxJson.put("material", material.getInteger("id"))//物料主数据id
            passBoxJson.put("quantityUnit", material.getAndRefreshBmfObject("flowUnit"))//流转单位
            passBoxJson.put("warehouseCode", nodeData.getString("warehouseCode"))//仓库编码
            passBoxJson.put("warehouseName", nodeData.getString("warehouseName"))//仓库名称
            passBoxJson.put("passBox", objPassBox.getInteger("id"))//passBox主数据的id
            passBoxJson.put("passBoxCode", objPassBox.getString("code"))
            passBoxJson.put("passBoxName", objPassBox.getString("name"))
            passBoxJson.put("passBoxClassificationAttribute", objPassBox.getAndRefreshBmfObject("passBoxClassification"))
            //周转箱类别属性id
            passBoxJson.put("locationCode", objNode.getString("locationCode"))
            passBoxJson.put("locationName", objNode.getString("locationName"))
            passBoxJson.put("ownerId", objNode.getInteger("ownerId"))//创建人id
            passBoxJson.put("ownerCode", objNode.getString("ownerCode"))//创建人
            passBoxJson.put("ownerName", objNode.getString("ownerName"))//创建人名称
            passBoxJson.put("equipSourceCode", objNode.getString("equipCode"))
            passBoxJson.put("equipSourceType", "PDA")//来源设备
            passBoxJson.put("ext_batch_number", batchNumber)

            passBoxJson.put("submit", true)
            passBoxJson.put("receiveQuantity", suggestedQuantity)//新增周转箱的数量按计算结果获得
            passBoxJson.put("quantity", new BigDecimal(0))
            JSONObject jsonObject = new JSONObject()
            jsonObject.put("scanDataJoin", false)
            resultData.put("otherSettings", jsonObject)
            resultData.put("passBoxes", Collections.singletonList(passBoxJson))//将新的周转箱增加到原有周转箱list中
            log.info(JSONObject.toJSONString(resultData))
            return result.success(resultData)
        }
        return result.success()
    }

}