package groovy.node_groovy

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.BmfUtils
import com.chinajay.virgo.utils.SpringUtils
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService
/**
 * @Creator 袁英杰
 * @CreateTime: 2024/12/28
 * @Description:
 * 翻包任务（CT1110,一个周转箱一个任务）提交时 创建入库待确认任务(CT1118)
 */
class NodeCT1110BusinessExecute extends  NodeGroovyClass {

    //基础类
    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    //通用业务类
    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)

    @Override
    Object runScript(BmfObject nodeData) {

        List<BmfObject> batchList = new ArrayList<>()


        if (nodeData.containsKey("batchList")) {
            batchList = nodeData.getList("batchList")
        } else {
            batchList.add(nodeData)
        }
        batchList.forEach(item -> {
            //创建入库待确认任务
            createCt1118(nodeData, item)

        })
        return null


    }
    //创建入库待确认任务
    private void createCt1118(BmfObject nodeData, BmfObject item) {

        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        passBoxes.forEach({ passBox ->
            //创建入库待确认任务
            BmfObject ct1118 = new BmfObject("CT1118")
            ct1118.put("ext_warehouse_In_application_code", item.getString("ext_warehouse_in_application_code"))//入库申请单编码
            ct1118.put("ext_warehouse_in_type", item.getString("ext_warehouse_in_type"))//入库类型 生产完工；采购入库等
            ct1118.put("ext_material_code", passBox.getString("materialCode"))//物料编码
            ct1118.put("ext_material_name", passBox.getString("materialName"))//物料描述
            ct1118.put("ext_quantity", passBox.getBigDecimal("quantity"))//入库数量
            ct1118.put("warehouseCode", item.getString("warehouseCode"))//仓库代码
            ct1118.put("warehouseName", item.getString("warehouseName"))//仓库名称

            //组装移动应用的任务表
            List<BmfObject> tasks = new ArrayList<>()
            BmfObject objTask = new BmfObject("CT1118Tasks")
            objTask.put("materialCode", passBox.getString("materialCode"))//物料编码
            objTask.put("materialName", passBox.getString("materialName"))//物料描述
            objTask.put("quantityUnit",  basicGroovyService.getByCode ("material",passBox.getString("materialCode")).getAndRefreshBmfObject("flowUnit"))//计量单位
            tasks.add(objTask)//添加任务表
            ct1118.put("tasks", tasks);  //添加任务表

            //组装移动应用的周转箱表
            //从当前界面passbox数据复制到指定BmfClass 中,然后将id和submit置空
            BmfObject passBoxb =  BmfUtils.genericFromJsonExt(passBox, "CT1118PassBoxes")
            passBoxb.put("id", null)
            passBoxb.put("submit", false)
            ct1118.put("passBoxes", Collections.singletonList(passBoxb));//添加周转箱表


            sceneGroovyService.buzSceneStart("CT1118",ct1118)

            //回入库申请单的状态为:Received-已收货
            BmfObject warehouseInApplication=new BmfObject("WarehouseInApplication")
            warehouseInApplication.put("status","Received")
            warehouseInApplication.put("id", basicGroovyService.getByCode("WarehouseInApplication",item.getString("ext_warehouse_in_application_code")).getInteger("id"))
            basicGroovyService.updateByPrimaryKeySelective(warehouseInApplication)
        })



    }
}