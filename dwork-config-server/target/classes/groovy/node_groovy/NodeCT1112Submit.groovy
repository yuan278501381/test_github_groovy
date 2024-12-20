package groovy.node_groovy

import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.modules.script.abstracts.NodeScanGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import org.apache.commons.lang3.StringUtils

/**
 * 滚筒线搬运入库提交脚本
 */
class NodeCT1112Submit extends NodeScanGroovyClass {
    def basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)

    @Override
    JSONObject runScript(BmfObject nodeData) {

        def passBoxCode = nodeData.getList("passBoxes").get(0).getString("passBoxCode")
        if (StringUtils.isBlank(passBoxCode)) {
            throw new BusinessException("周转箱编码不能为空")
        }

        //ctu的开始位置=滚筒线的结束位置
        nodeData.put("sourceLocationCode", nodeData.getString("ext_end_point"))

        // throw new BusinessException("测试回滚......")
        return nodeData

    }
}