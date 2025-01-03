package groovy.node_groovy

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService

/**
 * @Author 袁英杰
 * @Create 2025-01-03
 * @Dscription 发起入库申请-PDA 提交时 创建入库申请单-PC，状态为非初始值，已经生成 入库任务-平面库、入库任务-滚筒线中的一个
 *
 * */
class NodeCT1126Submit extends NodeGroovyClass {

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

            //1、创建入库申请单-PC
            BmfObject warehouseInboundApplication = createWarehouseInboundApplication(nodeData, item)

            //2、创建入库任务
            if (basicGroovyService.getByCode("warehouseCategory",
                    basicGroovyService.getByCode("warehouse",nodeData.get("wareHouse")).getString("categoryCode"))
                    .getString("name") contains("平面"))//平面库

            {
                createFlatTask(nodeData)
            }
            if (basicGroovyService.getByCode("warehouseCategory",
                    basicGroovyService.getByCode("warehouse",nodeData.get("wareHouse")).getString("categoryCode"))
                    .getString("name") contains("平面"))//CTU库
            {
                createIntelligenthandlingTask(nodeData, item)
            }

        })
        return null
    }

    private void createWarehouseInboundApplication(BmfObject nodeData, BmfObject item) {

        /*
        **  1、创建入库任务单
        */
        //为入库任务单赋值, 首先对入库任务单实例化一个对象,然后为其主表\子表\周转箱详情表赋值,然后用save方法保存
        BmfObject warehouseInApplication = new BmfObject("WarehouseInApplication")
        //入库类型
        warehouseInApplication.put("warehouseInType", item.getString("ext_warehouse_in_type"))
        //入库任务类型，可能是按所选仓库来判断
        warehouseInApplication.put("warehouseTaskType", item.getString("ext_warehouse_task_type"))
        //初始化单据状态
        warehouseInApplication.put("status", "pendingInWarehouse")

        //组装行表：Details
        List<BmfObject> details = new ArrayList<>()
        BmfObject detail = new BmfObject("WarehouseInApplicationDetail")

        def passBoxes = nodeData.getList("passBoxes")
        detail.put("materialCode", item.getString("ext_material_code"))
        detail.put("materialName", item.getString("ext_material_name"))
        // detail.put("specifications",item.getString("specifications"))

        detail.put("quantity", item.getBigDecimal("ext_quantity"))
        detail.put("wait_inbound_quantity", item.getBigDecimal("ext_quantity"))
        detail.put("warehoused_quantity", BigDecimal.ZERO)
        //  detail.put("unit",item.getAndRefreshBmfObject("flowUnit"))
        detail.put("warehouseCode", item.getString("warehouseCode"))
        detail.put("warehouseName", item.getString("warehouseName"))
        //将待确认任务ID记录到入库任务单行表，用于取消任务单时，重新打开待确认任务
        detail.put("fatherId", item.getInteger("id"))
        detail.put("warehouseInApplicationCode", item.getString("ext_warehouse_In_application_code"))

        //组装周转箱表
        List<BmfObject> warehouseInSheetpassBoxes = new ArrayList<>()

        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
//            if (CollectionUtil.isEmpty(passBoxes)) {
//                throw new BusinessException("周转箱数据不能为空")
//            }

        passBoxes.forEach(passBox -> {
            //NEW一个入库任务单-周转箱的对象
            BmfObject warehouseInSheetpassBox = new BmfObject("warehouseInSheetPassBox")
            warehouseInSheetpassBox.put("passBoxCode", passBox.getString("passBoxCode"))
            warehouseInSheetpassBox.put("passBoxName", passBox.getString("passBoxName"))
            warehouseInSheetpassBox.put("quantity", passBox.getBigDecimal("quantity"))
            //BmfObject单位取值报错，java.lang.ClassCastException: java.lang.String cannot be cast to com.chinajay.virgo.bmf.obj.BmfObject
            warehouseInSheetpassBox.put("unit", basicGroovyService.getByCode("material", passBox.getString("materialCode")).getAndRefreshBmfObject("flowUnit"))
            warehouseInSheetpassBox.put("passBoxRealCode", passBox.getString("passBoxRealCode"))

            warehouseInSheetpassBoxes.add(warehouseInSheetpassBox)
            // warehouseInSheetpassBoxes.add(passBox)
        })

        //对入库任务单行表关联的的周转箱表赋值
        detail.put("warehouseInSheetDetailAutoMapping", warehouseInSheetpassBoxes)

        details.add(detail)
        //对入库任务单行表赋值
        warehouseInSheet.put("mainidAutoMapping", details)

        //设置入库任务单编码
        basicGroovyService.setCode(warehouseInSheet)
        //保存入库任务单
        basicGroovyService.saveOrUpdate(warehouseInSheet)
        return warehouseInboundApplication


    }

    private void createFlatTask(BmfObject nodeData, BmfObject item) {

    }

    private void createIntelligenthandlingTask(BmfObject nodeData, BmfObject item) {

    }
}
