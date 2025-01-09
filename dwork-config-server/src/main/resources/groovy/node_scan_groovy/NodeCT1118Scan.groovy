package groovy.node_scan_groovy

import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.tengnat.dwork.modules.manufacturev2.domain.DomainScanResult
import com.tengnat.dwork.modules.script.abstracts.NodeScanGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService

/**
 * @author 袁英杰
 * @Create 2025-01-09
 * @Dscription 入库待确认扫描界面，同一个任务扫描周转箱时，如果存在相同的，那么将历史的扫描记录软删除  mainData=null  isDelete=true
 * */
class NodeCT1118Scan extends NodeScanGroovyClass {

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
        if (model.contains("PassBox") && !(passBoxCode.contains("WL"))) {
            /*
            virtualPassBox-虚拟周转箱控件
            materialRelevancePassBox-物料关联周转箱
             */
            //取得当前任务中，历史提交过相同周转箱编码的表主键
            String sSQL = "select id from dwk_logistics_custom_ct1118_passboxes "
            sSQL += " where main_id=" + nodeData.getLong("id")
            sSQL += " and pass_box_code='" + passBoxCode + "'"
            sSQL += " and submit=true"
            sSQL += " and is_delete=false"
            sSQL += " limit 1"
            def sqlRst = basicGroovyService.findOne(sSQL)
            if (!sqlRst) {
                return result.success()
            } else {

                ArrayList<BmfObject> updatePassBox = new ArrayList<>()

                def objPassBox = new BmfObject("CT1118PassBoxes")
                objPassBox.put("id", sqlRst.get("id"))//passBox.getPrimaryKeyValue()
                objPassBox.put("submit", true)
                objPassBox.put("mainData", null)
                objPassBox.put("isDelete", true)
                updatePassBox.add(objPassBox)
                basicGroovyService.updateByPrimaryKeySelective(updatePassBox)

                return result.success()

            }
        }


        return result.success()
    }

}