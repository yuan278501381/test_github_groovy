package groovy.node_groovy

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService


/**
 * 出库搬运生成滚筒线下架
 */
class NodeRollerOfflineRack extends NodeGroovyClass {
    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)

    @Override
    Object runScript(BmfObject nodeData) {

    }
}
