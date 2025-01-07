package groovy.node_scan_groovy

import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.tengnat.dwork.modules.manufacturev2.domain.DomainScanResult
import com.tengnat.dwork.modules.script.abstracts.NodeScanGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
/**
 * @author 袁英杰
 * @Create 2025-01-05
 * @Dscription 翻包界面-PDA 扫描周转箱时自动按包装数量和总数量计算当前周转箱的数量，考虑尾箱
 * */
class NodeCT1110Scan extends NodeScanGroovyClass {

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
        //如果扫描模块是物料关联虚拟周转箱，则进行判断。
        if (model.contains("PassBox") ) {
            /*
            virtualPassBox-虚拟周转箱控件
            materialRelevancePassBox-物料关联周转箱
             */
            def objNode = nodeData.deepClone()
            def suggestedQuantity = BigDecimal.ZERO
            def quantity = objNode.getBigDecimal("ext_quantity") ?: BigDecimal.ZERO //取得总数量 即PDA界面上的装箱数量
            def singleBoxQuantity = objNode.getBigDecimal("ext_single_box_quantity") ?: quantity
            //取得每包数量,每包数量为空时，取总数量，即1包
            def passBoxList = objNode.getList("passBoxes")
            JSONObject resultData = new JSONObject();
            //获得PDA界面上所有周转箱数量之和
            BigDecimal allocedSum = passBoxList.stream()
                    .map(passBox -> passBox.getBigDecimal("receiveQuantity") ?: BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
            // 计算剩余数量
            BigDecimal remainingQuantity = quantity - allocedSum ?: BigDecimal.ZERO

            // 计算自动填入的数量:首先检查剩余数量是否大于等于包装方案的数量，若买足则按包装方案数量填入，若不满足，再检查是否大于零，否则赋值为零。
            if (remainingQuantity >= singleBoxQuantity) {
                suggestedQuantity = singleBoxQuantity
            } else if (remainingQuantity > BigDecimal.ZERO) {
                suggestedQuantity = remainingQuantity
            } else {
                suggestedQuantity = BigDecimal.ZERO//考虑手动改数量后导致剩余数量为负的情况
            }
            def tasklist = objNode.getList("tasks")
            def material = basicGroovyService.getByCode("material", tasklist.get(0).getString("materialCode"))
            //new一个JSONObject,然后为新增周转箱字段赋值
            def objPassBox = basicGroovyService.getByCode("passBox", passBoxCode)
            def passBoxJson = new JSONObject()
            passBoxJson.put("virtualPassBox", false)
            passBoxJson.put("boxSelect",true)
            passBoxJson.put("materialCode", material.getString("code"))
            passBoxJson.put("materialName", material.getString("name"))
            passBoxJson.put("material", material.getInteger("id"))//物料主数据id
            passBoxJson.put("quantityUnit", material.getAndRefreshBmfObject("flowUnit"))//流转单位
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
            passBoxJson.put("ext_batch_number", objNode.getString("ext_batch_number"))

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