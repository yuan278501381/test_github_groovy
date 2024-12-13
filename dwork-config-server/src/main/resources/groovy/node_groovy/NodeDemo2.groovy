package groovy.node_groovy

import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.holder.ThreadLocalHolder
import com.chinajey.application.common.holder.UserAuthDto
import com.tengnat.dwork.common.enums.PushTypeEnum
import com.tengnat.dwork.modules.script.abstracts.NodeScanGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService

/**
 * Groovy德沃克典型脚本应用02 Demo
 */
class NodeDemo2 extends NodeScanGroovyClass{

    //引入基础类
    BasicGroovyService basicGroovyService= SpringUtils.getBean(BasicGroovyService.class)

    //引入业务类
    SceneGroovyService sceneGroovyService= SpringUtils.getBean(SceneGroovyService.class)

    @Override
    JSONObject runScript(BmfObject nodeData) {

        /** ************************** 通过SQL查询数据 ****************************/
        //查询单条数据，查询这个周转箱在哪个位置下
        String sql = String.format("SELECT  location_code,location_name FROM dwk_pass_box WHERE  `code`=  '%s'  LIMIT 1", "ZZ00000004")
        //周转箱主数据
        Map<String, Object> passBox = basicGroovyService.findOne(sql)


        //查询多条数据，查询位置下面有哪些周转箱
        sql = String.format("SELECT  `code` AS passBoxCode FROM dwk_pass_box WHERE  `location_code`=   '%s' ", "WZ001284")
        //周转箱主数据
        List<Map<String, Object>> passBoxes = basicGroovyService.findList(sql)



        /** ************************** 脚本中调用消息通知 ****************************/
        //消息模板编码
        String templateCode="消息模板编码"
        //消息内容
        Map<String, String> mapInfo=new HashMap<>()
        //通知对象类型
        String resourceType=PushTypeEnum.USER.getCode()
        //通知对象编码
        List<String> resourceCodes=new ArrayList<>()
        resourceCodes.add("YG00001")
        sceneGroovyService.sendMessage(templateCode, mapInfo, resourceType, resourceCodes)


        return null
    }
}
