package groovy.node_groovy

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.common.enums.EquipSourceEnum
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService
import org.apache.commons.lang3.StringUtils

/**
 * ctu搬运提交脚本
 */
class NodeCT1111Submit extends NodeGroovyClass {
    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)
    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)

    @Override
    Object runScript(BmfObject nodeData) {
        log.info("CTU搬运任务结束，执行NodeCT1111Submit")
        def passBoxCode = nodeData.getList("passBoxes").get(0).getString("passBoxCode")
        if (StringUtils.isBlank(passBoxCode)) {
            throw new BusinessException("周转箱编码不能为空")
        }
        Map<String, Object> params = new HashMap<>()
        params.put("passBoxCode", passBoxCode)
        BmfObject passBoxReal = basicGroovyService.findOne("passBoxReal", params)
        if (passBoxReal == null) {
            throw new BusinessException("周转箱实时不能为空：" + passBoxCode)
        }
        def inOutType = nodeData.getString("ext_in_out_type")
        //如果是出库 清空周转箱位置
        if ("out" == inOutType) {
            log.info("CTU搬运出库完成清空周转箱位置" + passBoxCode)
            passBoxReal.put("location",0)
            passBoxReal.put("locationCode", "")
            passBoxReal.put("locationName", "")
            sceneGroovyService.synchronizePassBoxInfo(passBoxReal, EquipSourceEnum.PDA.getCode(), nodeData.getBmfClassName())
        }

//        //ctu的结束位置=滚筒线的开始位置
//        nodeData.put("sourceLocationCode", nodeData.getString("ext_end_point"))
//        nodeData.put("ext_end_point", nodeData.getString("targetLocationCode"))
        return nodeData
    }
}
