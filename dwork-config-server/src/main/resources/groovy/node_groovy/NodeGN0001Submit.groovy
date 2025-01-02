package groovy.node_groovy


import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.BmfUtils
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService

/**
 *
 * @Creator 袁英杰
 * @CreateTime: 2024/12/27
 * @Description:
 * 采购收货提交时，按翻包标记进行分流：
 *      1\如果标记为翻包，则创建翻包任务；
 *      2\如果标记不翻包，则创建入库待确认任务
 */

class NodeGN0001Submit extends NodeGroovyClass {
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

            //1、校验
            GN0001Validate(nodeData, item)
            //2、标记为翻包，那么创建翻包任务
            if (item.getBoolean("ext_packet_change")) {
                createCt1110(nodeData, item)
            }
            //3、标记为不翻包，那么创建入库待确认任务
            else {
                createCt1118(nodeData, item)
            }
        })
        return nodeData

    }
//创建入库待确认任务
    private void createCt1118(BmfObject nodeData, BmfObject item) {

        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        passBoxes.forEach({ passBox ->
            //创建入库待确认任务
            BmfObject ct1118 = new BmfObject("CT1118")

            //默认批次编码为：ZD
            String batchNumber="ZD"
            if  (!item.get("ext_batch_number")){batchNumber="ZD"}else{batchNumber=item.get("ext_batch_number")}
            ct1118.put("ext_warehouse_In_application_code", item.getString("ext_warehouse_in_application_code"))//入库申请单编码
            ct1118.put("ext_warehouse_in_type", item.getString("ext_warehouse_in_type"))//入库类型 生产完工；采购入库等
            ct1118.put("ext_material_code", passBox.getString("materialCode"))//物料编码
            ct1118.put("ext_material_name", passBox.getString("materialName"))//物料描述
            ct1118.put("ext_quantity", passBox.getBigDecimal("quantity"))//入库数量
            ct1118.put("ext_batch_number",batchNumber)//批次编码

            //组装移动应用的任务表
            List<BmfObject> tasks = new ArrayList<>()
            BmfObject objTask = new BmfObject("CT1118Tasks")
            objTask.put("materialCode", passBox.getString("materialCode"))//物料编码
            objTask.put("materialName", passBox.getString("materialName"))//物料描述

            objTask.put("quantityUnit",  basicGroovyService.getByCode ("material",passBox.getString("materialCode")).getAndRefreshBmfObject("flowUnit"))//计量单位
            tasks.add(objTask)//添加任务表
            ct1118.put("tasks", tasks)  //添加任务表

            //组装移动应用的周转箱表
            BmfObject passBoxc =  BmfUtils.genericFromJsonExt(passBox, "CT1118PassBoxes")
            passBoxc.put("id", null)
            passBoxc.put("submit", false)
            passBoxc.put("ext_batch_number",batchNumber)//批次编码
            ct1118.put("passBoxes", Collections.singletonList(passBoxc))//添加周转箱表
            //更新周转箱实时表的批次编码
           BmfObject passBoxReal= basicGroovyService.getByCode("passBoxReal", passBox.getString("code"))
            if (!passBoxReal){
                throw new  BusinessException("周转箱实时信息不存在")
            }
            else {
                passBoxReal.put("ext_batch_number",batchNumber)
                 basicGroovyService.updateByPrimaryKeySelective(passBoxReal)
                 }
            sceneGroovyService.buzSceneStart("CT1118",ct1118)

            //回入库申请单的状态为:Received-已收货
            BmfObject warehouseInApplication=new BmfObject("WarehouseInApplication")
            warehouseInApplication.put("status","Received")
            warehouseInApplication.put("id", basicGroovyService.getByCode("WarehouseInApplication",item.getString("ext_warehouse_in_application_code")).getInteger("id"))
            basicGroovyService.updateByPrimaryKeySelective(warehouseInApplication)
        })

    }
    //创建翻包任务
    private void createCt1110(BmfObject nodeData, BmfObject item) {

        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        passBoxes.forEach({ passBox ->
            //创建翻包检任务
            //按入库申请单号获得来源单据号码 sourceOrderCode
            String sourceOrderCode =basicGroovyService.getByCode("WarehouseInApplication",item.getString("ext_warehouse_in_application_code")).getString("sourceOrderCode")
            BmfObject ct1110 = new BmfObject("CT1110")

            //默认批次编码为：ZD
            String batchNumber="ZD"
            if  (!item.get("ext_batch_number")){batchNumber="ZD"}else{batchNumber=item.get("ext_batch_number")}

            //获得当前物料的对象
            def  thisItem=basicGroovyService.getByCode("material", passBox.getString("materialCode"))
            ct1110.put("ext_operateSourceEnum","GN0001")//业务类型：采购入库GN0001等
            ct1110.put("ext_warehouse_in_type","purchaseDeliver")//入库类型：采购收货-purchaseDeliver 等 （enum_name=WarehouseInType）
            ct1110.put("ext_warehouse_in_application_code",item.getString("ext_warehouse_in_application_code"))//入库申请单编码
            ct1110.put("ext_sourceType", "procurementOrder")//业务来源：采购订单等
            ct1110.put("ext_sourceCode", sourceOrderCode)//来源编码
            ct1110.put("ext_material_code", passBox.getString("materialCode"))//物料编码
            ct1110.put("ext_materialName", passBox.getString("materialName"))//物料描述
            ct1110.put("ext_specifications",thisItem.getString("specifications"))//物料规格
            ct1110.put("ext_passboxcode",passBox.getString("passBoxCode"))//周转箱代码
            ct1110.put("ext_passboxname",passBox.getString("passBoxName"))//周转箱名称
            ct1110.put("ext_quantity", passBox.getBigDecimal("quantity"))//装箱数量
            ct1110.put("ext_batch_number", batchNumber)//批次编码
            def flowUnitname= basicGroovyService.getByCode ("material",passBox.getString("materialCode")).getAndRefreshBmfObject("flowUnit")
            ct1110.put("ext_Unit", flowUnitname.getString("name"))//翻包主表单位


            //组装移动应用的任务表
            List<BmfObject> tasks = new ArrayList<>()
            BmfObject objTask = new BmfObject("CT1110Tasks")
            objTask.put("materialCode", passBox.getString("materialCode"))//物料编码
            objTask.put("materialName", passBox.getString("materialName"))//物料描述
            objTask.put("quantityUnit",  basicGroovyService.getByCode ("material",passBox.getString("materialCode")).getAndRefreshBmfObject("flowUnit"))//计量单位
            tasks.add(objTask)//添加任务表
            ct1110.put("tasks", tasks)  //添加任务表

            //更新周转箱实时表的批次编码
            BmfObject passBoxReal= basicGroovyService.getByCode("passBoxReal", passBox.getString("code"))
            if (!passBoxReal){
                throw new  BusinessException("周转箱实时信息不存在")
            }
            else {
                passBoxReal.put("ext_batch_number",batchNumber)
                basicGroovyService.updateByPrimaryKeySelective(passBoxReal)
            }
            //组装移动应用的周转箱表,
            //翻包界面的周转箱,用户按实际箱号扫描,所以不代入,考虑原来的周转箱要清空
            //从当前界面passbox数据复制到指定BmfClass 中,然后将id和submit置空
//            BmfObject passBoxb =  BmfUtils.genericFromJsonExt(passBox, "CT1110PassBoxes")
//            passBoxb.put("id", null)
//            passBoxb.put("submit", false)
//            ct1110.put("passBoxes", Collections.singletonList(passBoxb))//添加周转箱表
            sceneGroovyService.buzSceneStart("CT1110",ct1110)



            //回写入库申请单的状态为:Received-已收货
            BmfObject warehouseInApplication=new BmfObject("WarehouseInApplication")
            warehouseInApplication.put("status","Received")
            warehouseInApplication.put("id", basicGroovyService.getByCode("WarehouseInApplication",item.getString("ext_warehouse_in_application_code")).getInteger("id"))
            basicGroovyService.updateByPrimaryKeySelective(warehouseInApplication)
        })


    }

    private void GN0001Validate(BmfObject nodeData, BmfObject item) {

        BigDecimal ext_quantity=item.getBigDecimal("ext_current_received_quantity")

        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        BigDecimal sum = passBoxes.stream().peek(passBox -> {
            BmfObject passBoxReal = basicGroovyService.findOne("passBoxReal", "passBoxCode", passBox.getString("passBoxCode"))
            if (passBoxReal == null) {
                throw new BusinessException("周转箱[" + passBox.getString("passBoxCode") + "]实时信息不存在或生成失败")
            }
        }).map(passBox -> passBox.getBigDecimal("receiveQuantity") == null ? BigDecimal.ZERO : passBox.getBigDecimal("receiveQuantity"))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
        if (ext_quantity != sum) {
            throw new BusinessException("周转箱总数量必须等于本次收货数量")
        }
    }
}
