package groovy.issued_groovy

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.BmfUtils
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.application.script.exception.ScriptInterruptedException
import com.tengnat.dwork.modules.script.abstracts.AopAfterGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService

import java.util.stream.Collectors

/** @author 袁英杰
 * @CreateTime: 2024-12-17
 *
 * 入库申请单下达脚本
 * 支持批量下达 ：
 */
class warehouseInApplicationIssued extends AopAfterGroovyClass {
    //基础类
    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    //通用业务类
    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)

    @Override
    void runScript(Object data) {
        //入库申请单下达的单据ID集合 data 为List<BmfObject>
        List<BmfObject> warehouseInApplicationList = data as List<BmfObject>
        // 遍历集合中的每一个入库申请单
        for (BmfObject warehouseInApplication : warehouseInApplicationList) {
            //业务规则项 ,默认为warehouseInApplicationIssued-其它入库申请单, 可按入库类型改判为:warehouseInApplicationIssued2 -生成采购收货任务/委外收货任务的 规则项
            String restfulCode = "warehouseInApplicationIssued"
            //入库申请单状态
            String status = warehouseInApplication.getString("status")
            if (!"notReceive".equals(status)) {
                throw new BusinessException("入库申请单状态不是待收货，无法下达")
            }
            //判断入库类型，如果入库类型是采购收货，则调用warehouseInApplicationIssued2-生成采购收货任务/委外收货任务的/分销采购入库 规则项,否则是普通规则
            if (warehouseInApplication.getString("warehouseInType") == "purchaseDeliver" || warehouseInApplication.getString("warehouseInType") == "purchaseDeliverExt"
                    || warehouseInApplication.getString("warehouseInType") == "purchaseDeliverFx") {
                restfulCode = "warehouseInApplicationIssued2"

                //深拷贝一份，防止修改循环外的数据源
                warehouseInApplication = warehouseInApplication.deepClone()
                // 入库申请单明细
                List<BmfObject> details = warehouseInApplication.getAndRefreshList("main_idAutoMapping")
                //根据物料分组,并为分组后的物料进行遍历，key是物料编码，value是该物料下的明细,比如一个物料有5行明细，那么该物料就对应5行明细

                Map<String, List<BmfObject>> groupByMaterialCode = details.stream().collect(Collectors.groupingBy(detail -> detail.getString("materialCode")))
                for (String key : groupByMaterialCode.keySet()) {
                    //物料下面的明细（一个物料在入库申请单中有多行）
                    List<BmfObject> detailList = groupByMaterialCode.get(key)
                    //数量：汇总同一个物料的多行数量，如果一个物料多行，汇总后的数据写到第一行，get(0)
                    BigDecimal totalQuantity = detailList.stream().map(detail -> detail.getBigDecimal("quantity")).reduce(BigDecimal.ZERO, BigDecimal::add)
                    //相同物料只取第一行明细,即get(0)
                    BmfObject detail = detailList.get(0)
                    detail.put("quantity", totalQuantity)
                    detail.getAndRefreshBmfObject("unit")
                    //是否翻包
                    String getmaterialExtSQL = "select t1.ext_packet_change from dwk_material t\n" +
                            "INNER JOIN dwk_material_ext t1 on t.id=t1.ext_material_id\n" +
                            "where t.code='" + detail.getString("materialCode") + "'" +
                            "Limit 1"
                    def SQLResult = basicGroovyService.findOne(getmaterialExtSQL)
                    def packetChange = false
                    if (!SQLResult) {
                        packetChange = false
                    } else {
                        packetChange = SQLResult.get("ext_packet_change")
                    }
                    detail.put("packetChange", packetChange)
                    //将
                    warehouseInApplication.put("main_idAutoMapping", Collections.singletonList(detail))
                    sceneGroovyService.restfulCodeStart(restfulCode, warehouseInApplication)
                }
            }
            //如果不为采购收货则创建入库待确认任务-PDA
            else {

                //深拷贝一份，防止修改循环外的数据源
                warehouseInApplication = warehouseInApplication.deepClone()
                // 入库申请单明细
                List<BmfObject> details = warehouseInApplication.getAndRefreshList("main_idAutoMapping")
                //根据物料分组,并为分组后的物料进行遍历，key是物料编码，value是该物料下的明细,比如一个物料有5行明细，那么该物料就对应5行明细

                Map<String, List<BmfObject>> groupByMaterialCode = details.stream().collect(Collectors.groupingBy(detail -> detail.getString("materialCode")))
                for (String key : groupByMaterialCode.keySet()) {
                    //物料下面的明细（一个物料在入库申请单中有多行）
                    List<BmfObject> detailList = groupByMaterialCode.get(key)
                    //数量：汇总同一个物料的多行数量，如果一个物料多行，汇总后的数据写到第一行，get(0)
                    BigDecimal totalQuantity = detailList.stream().map(detail -> detail.getBigDecimal("quantity")).reduce(BigDecimal.ZERO, BigDecimal::add)
                    //相同物料只取第一行明细,即get(0)
                    BmfObject detail = detailList.get(0)
                    //detail.put("quantity",totalQuantity)

                    BmfObject objCT1118 = new BmfObject("CT1118")
                    objCT1118.put("ext_warehouse_in_application_id", warehouseInApplication.getString("id"))
                    objCT1118.put("ext_warehouse_In_application_code", warehouseInApplication.getString("code"))
                    //入库申请单编码
                    objCT1118.put("ext_warehouse_in_type", warehouseInApplication.getString("warehouseInType"))
                    //入库类型 生产完工；采购入库等
                    objCT1118.put("ext_material_code", detailList.get(0).getString("materialCode"))//物料编码
                    objCT1118.put("ext_material_name", detailList.get(0).getString("materialName"))//物料描述
                    objCT1118.put("ext_quantity", totalQuantity)//入库数量
                    objCT1118.put("ext_batch_number", "ZD")//批次编码

                    //组装移动应用任务表
                    List<BmfObject> tasks = new ArrayList<>()
                    BmfObject objTask = new BmfObject("CT1118Tasks")
                    objTask.put("materialCode", detailList.get(0).getString("materialCode"))
                    //为task表的物料描述赋值
                    objTask.put("materialName", detailList.get(0).getString("materialName"))

                    objTask.put("quantityUnit", basicGroovyService.getByCode("material", detailList.get(0).getString("materialCode")).getAndRefreshBmfObject("flowUnit"))
                    tasks.add(objTask)
                    objCT1118.put("tasks", tasks)
                    sceneGroovyService.buzSceneStart("CT1118", objCT1118)
                }
            }

            //回入库申请单的状态为:issued-已下达
            warehouseInApplication.put("status", "issued")

            //用方法updateByPrimaryKeySelective,更新入库申请单据的状态, warehouseInApplication中包含了ID和Status的值, 所以可以直接更新
            basicGroovyService.updateByPrimaryKeySelective(warehouseInApplication)
        }
    }
}