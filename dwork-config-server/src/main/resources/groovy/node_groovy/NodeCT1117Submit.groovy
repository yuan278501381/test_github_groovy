package groovy.node_groovy

import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass

import com.tengnat.dwork.modules.script.service.BasicGroovyService

/**
 * 出入库工作台绑定人员
 */
class NodeCT1117Submit extends NodeGroovyClass{
    def basicGroovyService= SpringUtils.getBean(BasicGroovyService.class)
    @Override
    JSONObject runScript(BmfObject nodeData) {
        def userCode = nodeData.getString("receiveObjectCode")
        def userName = nodeData.getString("receiveObjectName")
        def workbenchCode = nodeData.getString("inventoryWorkbenchCode")

        def workbench = basicGroovyService.findOne("inventoryWorkbench", "code", workbenchCode)
        if (workbench == null){
            throw new BusinessException("出入库工作台主数据不存在:" + workbenchCode)
        }
        workbench.put("userCode",userCode)
        workbench.put("userName",userName)
        basicGroovyService.updateByPrimaryKeySelective(workbench)
        //throw new BusinessException("测试回滚......")
        return null

    }
}