package groovy.node_groovy

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import org.apache.commons.lang3.StringUtils

/**
 * ctu搬运创建滚筒线搬运任务
 */
class NodeCT1111Submit  extends NodeGroovyClass {
    @Override
    Object runScript(BmfObject nodeData) {
        def passBoxCode = nodeData.getList("passBoxes").get(0).getString("passBoxCode")
        if (StringUtils.isBlank(passBoxCode)) {
            throw new BusinessException("周转箱编码不能为空")
        }
        //ctu的结束位置=滚筒线的开始位置
        nodeData.put("sourceLocationCode", nodeData.getString("ext_end_point"))
        nodeData.put("ext_end_point", nodeData.getString("targetLocationCode"))
        return nodeData
    }
}
