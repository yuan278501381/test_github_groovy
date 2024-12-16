package groovy.node_groovy

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService


/**
 * 生成入库结果单
 */
class NodeCreateWarehouseInResultDetail extends NodeGroovyClass {
    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)

    @Override
    Object runScript(BmfObject nodeData) {
        //获取周转箱信息
        List<BmfObject> passBoxes = nodeData.getList("passBoxes")

        def businessType = nodeData.getString("ext_business_type")
        if ("采购入库".equals(businessType)){
            //同步REP采购


        }
        return nodeData
    }
}
