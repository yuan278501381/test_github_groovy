package groovy.node_groovy

import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import org.apache.commons.lang3.StringUtils

/**
 * 滚筒线搬运提交脚本
 */
class NodeCT1112Submit extends NodeGroovyClass {
    def basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)

    @Override
    JSONObject runScript(BmfObject nodeData) {

        def passBoxes = nodeData.getList("passBoxes")
        if (passBoxes == null || passBoxes.size() == 0) {
            throw new BusinessException("周转箱不能为空")
        }
        def passBoxCode = passBoxes.get(0).getString("passBoxCode")
        if (StringUtils.isBlank(passBoxCode)) {
            throw new BusinessException("周转箱编码不能为空")
        }

        //ctu的开始位置=滚筒线的结束位置
        nodeData.put("sourceLocationCode", nodeData.getString("targetLocationCode"))
        nodeData.put("sourceLocationName", basicGroovyService.getByCode("location", nodeData.getString("sourceLocationCode")).getString("name"))
        nodeData.put("targetLocationCode", nodeData.getString("ext_end_point"))
        nodeData.put("ext_end_point", nodeData.getString("ext_end_point"))

//        nodeData.put("sourceLocationCode","WZ001287")
//        nodeData.put("targetLocationCode","WZ001287")

        // throw new BusinessException("测试回滚......")
        return nodeData

    }
}