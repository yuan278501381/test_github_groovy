package groovy.cancel_groovy

import cn.hutool.core.collection.CollectionUtil
import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.modules.script.abstracts.AopAfterGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService
/**
 * @Creator 袁英杰
 * @CreateTime: 2024/12/20
 * @Description: 入库任务单-PC,取消时,做以下几个动作：
 * 1、检查当前任务单状态是能取消，如果部分入库则不能取消，否则进行以下操作：
 * 2、重新打开入库待确认任务
 * 3、取消关联的入库任务-平面库
 * 4、取消关联的滚动线搬运任务
 *
*/
class warehouseInSheetCancel extends AopAfterGroovyClass {

    //基础类
    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    //通用业务类
    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)

    @Override
    void runScript(Object data) {

        List<BmfObject> warehouseInSheetList = data as List<BmfObject>
        // 遍历集合中的每一个入库申请单
        for (BmfObject warehouseInSheet : warehouseInSheetList) {

            //先检查取消的任务单背后（按当前单据状态检查），有没有已经部分执行的CTU仓入库、平面仓入库记录，如果有则报错

             def thisStatus = warehouseInSheet.getString("status")

            if (thisStatus == "cancel") {

                throw new BusinessException("单据已取消，禁止再次取消。")
            }

            Map<String, String> mapInfo = new HashMap<>()

            mapInfo.put("notReceive", "待收货")
            mapInfo.put("notWarehoused", "待入库")
            mapInfo.put("create", "已创建")
            mapInfo.put("toConfirmed", "待确认")
            mapInfo.put("untreated", "未处理")
            mapInfo.put("pendingInWarehouse", "待入库")
            //下游单据执行后反写状态到入库任务单，因此，判断入库任务单是否可以取消，按状态这一个来判断，仅当未执行任务业务时，可以取消入库任务单

            //1、首先判断是否符合取消条件
            if (!mapInfo.containsKey(thisStatus)) {
                throw new BusinessException("单据存在关联交易，禁止取消")
            }
            //2、然后取消入库任务单
            BmfObject warehouseInSheet2 = new BmfObject("warehouseInSheet")
            warehouseInSheet2.put("status", "cancel")
            warehouseInSheet2.put("id", warehouseInSheet.getInteger("id"))
            //提交取消入库任务单
            basicGroovyService.updateByPrimaryKeySelective(warehouseInSheet2)

            // logisticsStatus
            //            1	已创建
            //            2	检验中
            //            3	已完成
            //            4	已取消

            def docLists=warehouseInSheet.getAndRefreshList("mainidAutoMapping")
            BmfObject CT1118 = new BmfObject("CT1118")
            docLists.forEach { List ->
                CT1118.put("id", List.getInteger("fatherId"))
                CT1118.put("logisticsStatus", 1)

                String SQLgetPassbox='select t1.id  from  dwk_logistics_custom_ct1118  t\n' +
                'inner join  dwk_logistics_custom_ct1118_passboxes t1 on  t.id=t1.main_id\n' +
                 'where t.id=?'


                List<Map<String, Object>> passboxList = basicGroovyService.findList(SQLgetPassbox, List.getInteger("fatherId"))

                for (final def passBox in passboxList) {
                    BmfObject CT1118PassBox =new BmfObject("CT1118PassBox")
                    CT1118PassBox.put("id",passBox.get("id"))
                    CT1118PassBox.put("submit", false)
                    //basicGroovyService.updateByPrimaryKeySelective(CT1118PassBox)
                    CT1118.put("passBoxes", Collections.singletonList( CT1118PassBox))
                }
            }
            basicGroovyService.updateByPrimaryKeySelective(CT1118)
            //3、取消关联的平面库入库任务（CT1119），CTU入库任务

            //从入库任务主表获取入库申请单号
            def listwarehouseInApplicationCode=warehouseInSheet.getAndRefreshList("mainidAutoMapping")

            listwarehouseInApplicationCode.forEach { List ->
                //按入库申请单号找到CT1119的主键id,然后遍历取消CT1119
                String getCT1119SQL = "select t.id from dwk_logistics_custom_ct1119 t\n" +
                        "LEFT JOIN dwk_logistics_custom_ct1119_ext t1 on t.id=t1.ext_CT1119_id\n" +
                        "where  COALESCE(ext_warehouse_in_application_code,'')='" + List.getString("warehouseInApplicationCode") + "' "

                List<Map<String, Object>> listCT1119s = basicGroovyService.findList(getCT1119SQL)
                for (final def listCT1119 in listCT1119s) {
                    BmfObject CT1119 = new BmfObject("CT1119")
                    CT1119.put("id", listCT1119.get("id"))
                    CT1119.put("logisticsStatus", 4)
                    basicGroovyService.updateByPrimaryKeySelective(CT1119)

                }
            }

            // 4、取消 滚筒线搬运任务 CT1112

            //sql获得CT1112的主键
            String getCT1112SQL = "select distinct t2.id ct1112_id\n" +
                    "from u_warehouse_in_sheet t\n" +
                    "         left join u_warehouse_in_sheet_detail t1 on t.id = t1.main_id\n" +
                    "         left join (dwk_logistics_custom_ct1112 t2 left join dwk_logistics_custom_ct1112_ext t3\n" +
                    "                    on t2.id = t3.ext_CT1112_id)\n" +
                    "                   on t.code = t3.ext_sheet_code\n" +
                    "where 1 = 1\n" +
                    "  and t.status <> 'cancel'\n" +
                    "  and coalesce(t3.ext_sheet_code, '') = '" + warehouseInSheet.getString("code") + "' "

            List<Map<String, Object>> listCT1112s = basicGroovyService.findList(getCT1112SQL)
            for (final def listCT1112 in listCT1112s) {
                BmfObject objCT1112 = new BmfObject("CT1112")
                objCT1112.put("id", listCT1112.get("id"))
                objCT1112.put("logisticsStatus", 4)
                basicGroovyService.updateByPrimaryKeySelective(objCT1112)
            }
        }
    }

}
