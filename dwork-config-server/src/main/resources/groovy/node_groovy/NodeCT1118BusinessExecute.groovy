package groovy.node_groovy


import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.bmf.service.BmfService
import com.chinajay.virgo.utils.BmfUtils
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.application.script.exception.ScriptInterruptedException
import com.tengnat.dwork.modules.basic_data.service.ResourceBindingService
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService

import java.util.stream.Collectors


/**
 * @Creator 袁英杰
 * @CreateTime: 2024/12/20
 * @Description: 入库待确认任务提交时，创建一张入库任务单-PC和 一张搬运任务-PDA，如果是CTU库，要推荐一个入库位置
 * 平面库和CTU的识别按 仓库代码识别
 *  按仓库类别名称识别 平面仓  CTU仓
 */
// 列表支持批量提交，移动应用列表提交脚本和物流节点挂的脚本为同一个；写法参照下面
class NodeCT1118BusinessExecute extends NodeGroovyClass {
    //基础类
    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    //通用业务类
    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)

    ResourceBindingService resourceBindingService = SpringUtils.getBean(ResourceBindingService.class)
    BmfService bmfService = SpringUtils.getBean(BmfService.class)

    @Override
    Object runScript(BmfObject nodeData) {

        List<BmfObject> batchList = new ArrayList<>()


        if (nodeData.containsKey("batchList")) {
            batchList = nodeData.getList("batchList")
        } else {
            batchList.add(nodeData)
        }
        batchList.forEach(item -> {
            //具体的业务逻辑

            if (!item.getString("ext_warehouse_In_application_code")) {
                throw new BusinessException("入库申请单编码不能为空")
            }
            //1、创建入库任务单-PC
            BmfObject warehouseInSheet = createWarehouseInTask(nodeData, item)

            //2、根据仓库类别名称判断，创建平面仓库入库任务-PDA和CTU仓入库任务-PDA中的一种
            if (basicGroovyService.getByCode("warehouseCategory",
                    basicGroovyService.getByCode("warehouse", item.getString("warehouseCode")).getString("categoryCode"))
                    .getString("name") contains("平面")) {

                log.info("==============按仓库类别名称识别为平面库，进入平面库业务逻辑==============")
                createFlatTask(nodeData, item)

            }
            //3、根据仓库名称判断，创建智能设备搬运任务-PDA CT1111
            else if (basicGroovyService.getByCode("warehouseCategory",
                    basicGroovyService.getByCode("warehouse", item.getString("warehouseCode")).getString("categoryCode"))
                    .getString("name") contains("CTU")) {
                log.info("==============按仓库类别名称识别为CTU库，进入CTU库业务逻辑==============")
                createIntelligenthandlingTask(nodeData, item, warehouseInSheet)
            } else {
                throw new BusinessException("仓库类别名称识别失败,不包含平面仓、CTU仓中二者中的一种，未识别的业务逻辑！")
            }
            //校验周转箱必填、物料正确性、数量必填
            CT1118Validate (nodeData, item)

            //4、未完成的数量生成新任务
            createNewTask(item)

            //按累计确认数量，改写入库确认单的状态，考虑部分确认后，任务要留在界面上，供下一次入库。
            //updateCT1118logisticsStatus(nodeData, item)
        })
        throw new ScriptInterruptedException("不流转")

    }

    private BmfObject createWarehouseInTask(BmfObject nodeData, BmfObject item) {

        /*
        **  1、创建入库任务单
        */
        //为入库任务单赋值, 首先对入库任务单实例化一个对象,然后为其主表\子表\周转箱详情表赋值,然后用save方法保存
        BmfObject warehouseInSheet = new BmfObject("warehouseInSheet")
        //入库类型
        warehouseInSheet.put("warehouseInType", item.getString("ext_warehouse_in_type"))
        //入库任务类型，可能是按所选仓库来判断
        warehouseInSheet.put("warehouseTaskType", item.getString("ext_warehouse_task_type"))
        //初始化单据状态
        warehouseInSheet.put("status", "pendingInWarehouse")

        //组装行表：Details
        List<BmfObject> details = new ArrayList<>()
        BmfObject detail = new BmfObject("warehouseInSheetDetail")
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

        List<BmfObject> passBoxes = item.getList("passBoxes")
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
            warehouseInSheetpassBox.put("completionStatus", false)
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
        return warehouseInSheet
    }

    private void createFlatTask(BmfObject nodeData, BmfObject item) {
        /*
        **  3、创建 入库任务-平面库
        */
        //默认批次编码为：ZD
        String batchNumber = "ZD"
        if (!item.get("ext_batch_number")) {
            batchNumber = "ZD"
        } else {
            batchNumber = item.get("ext_batch_number")
        }

        BmfObject objCT1119 = new BmfObject("CT1119")

        //入库申请单号
        objCT1119.put("ext_warehouse_in_application_code", item.getString("ext_warehouse_In_application_code"))
        //目标仓库编码
        objCT1119.put("ext_target_warehouse_code", item.getString("warehouseCode"))
        //目标仓库名称
        objCT1119.put("ext_target_warehouse_name", item.getString("warehouseName"))
        //入库类型
        objCT1119.put("ext_warehouse_in_type", item.getString("ext_warehouse_in_type"))
        objCT1119.put("warehouseCode", item.getString("warehouseCode"))
        objCT1119.put("warehouseName", item.getString("warehouseName"))


        //组装周转箱表
        def passBoxesb = item.getList("passBoxes")
        List<BmfObject> PassBoxes = new ArrayList<>()
        passBoxesb.forEach { passBox ->

            BmfObject objPassbox = BmfUtils.genericFromJsonExt(passBox, "CT1119PassBoxes")
            objPassbox.put("id", null)
            objPassbox.put("submit", false)
            PassBoxes.add(objPassbox)

        }

        List<BmfObject> tasks = new ArrayList<>()
        //根据物料分组
        Map<String, List<BmfObject>> passBoxRealGroup = PassBoxes.stream().collect(Collectors.groupingBy(passBox -> ((BmfObject) passBox).getString("materialCode")))
        passBoxRealGroup.forEach((materialCode, passBox) -> {
            BmfObject objTask = new BmfObject("CT1119Tasks")
            //task.put("thisPallet", true)
            //task.put("palletCode", palletCode)
            //task.put("palletName", palletName)

            //为task表的物理编码赋值
            objTask.put("materialCode", materialCode)
            //为task表的物料描述赋值
            objTask.put("materialName", passBox.get(0).getString("materialName"))
            //为task表的物料数量赋值
            objTask.put("quantityUnit", passBox.get(0).getAndRefreshBmfObject("quantityUnit"))

            tasks.add(objTask)
        })

        objCT1119.put("tasks", tasks)//添加任务表
        objCT1119.put("passBoxes", PassBoxes)//添加周转箱表


        sceneGroovyService.buzSceneStart("CT1119", objCT1119)

        //为周转箱实时表的批次字段赋值
        def passBoxesc = item.getList("passBoxes")
        passBoxesc.forEach { passBox ->
            BmfObject passBoxReal = basicGroovyService.getByCode("passBoxReal", passBox.getString("code"))
            if (!passBoxReal) {
                throw new BusinessException("周转箱实时信息不存在")
            } else {
                passBoxReal.put("ext_batch_number", batchNumber)

                if (!(item.getString("ext_warehouse_in_type")=="purchaseDeliver" ||item.getString("ext_warehouse_in_type")=="purchaseDeliverExt" ||
                        item.getString("ext_warehouse_in_type")=="purchaseDeliverFx"))

                {
                    passBoxReal.put("ext_prdLine", item.getString("ext_prdLine"))
                }
                basicGroovyService.updateByPrimaryKeySelective(passBoxReal)
            }
        }
        log.info("========入库申请单-PDA，提交:按仓库类别名称识别为平面库，进入平面库业务逻辑 完成=========主表")
        // log.info(JSONObject.toJSONString(objCT1119))
    }

    private void createIntelligenthandlingTask(BmfObject nodeData, BmfObject item, BmfObject warehouseInSheet) {

        /*
      **  4、创建智能设备搬运任务
       */


        //默认批次编码为：ZD
        String batchNumber = "ZD"
        if (!item.get("ext_batch_number")) {
            batchNumber = "ZD"
        } else {
            batchNumber = item.get("ext_batch_number")
        }

        //组装周转箱表
        List<BmfObject> passBoxes2 = item.getList("passBoxes")
        //            if (CollectionUtil.isEmpty(passBoxes)) {
        //                throw new BusinessException("周转箱数据不能为空")
        //            }

        passBoxes2.forEach(passBox -> {

            //CT1112 滚筒线搬运任务
            BmfObject objCT1112 = new BmfObject("CT1112")

            //按传入的入库申请单号，从入库申请单表头获得来源单号
            String sourceOrderCode = basicGroovyService.getByCode("WarehouseInApplication", item.getString("ext_warehouse_In_application_code")).getString("sourceOrderCode")
            //按传入的入库申请单号，从入库申请单表头获得入库类型
            String warehouseInType = basicGroovyService.getByCode("WarehouseInApplication", item.getString("ext_warehouse_In_application_code")).getString("warehouseInType")
            //按传入的入库申请单号，从入库申请单表头获得来源单据类型
            String sourceOrderType = basicGroovyService.getByCode("WarehouseInApplication", item.getString("ext_warehouse_In_application_code")).getString("sourceOrderType")

            def warehouseCode = item.getString("warehouseCode")
            def warehouseCategoryCode = basicGroovyService.getByCode("warehouse", warehouseCode).getString("categoryCode")


            objCT1112.put("ext_warehouse_in_application_code", item.getString("ext_warehouse_In_application_code"))
            //入库申请单号
            objCT1112.put("ext_business_type", warehouseInType)//业务类型 采购入库等
            objCT1112.put("ext_business_source", sourceOrderType)//业务来源 采购订单等
            objCT1112.put("ext_business_source_code", sourceOrderCode)//来源编码
            objCT1112.put("ext_pass_box_code", passBox.getString("passBoxCode"))//周转箱编码

            def endPoint = getWarehouse2ByLocation(warehouseCategoryCode, warehouseCode).toString()
            objCT1112.put("ext_end_point", endPoint)//滚筒线目标位置 -自定义字段
            objCT1112.put("targetLocationCode", endPoint)//目标位置编码
            objCT1112.put("targetLocationName", basicGroovyService.getByCode("location", endPoint).getString("name"))
//目标位置名称
            objCT1112.put("ext_in_out_type", "in")//出入类型
            objCT1112.put("ext_sheet_code", warehouseInSheet.getString("code"))//任务单编码
            objCT1112.put("ext_inventory_workbench_code", "")//工作台编码, 仅出库时有效,用于记录几号滚筒线
            objCT1112.put("ext_inventory_workbench_name", "")//工作台名称


            //为移动应用的任务表赋值
            List<BmfObject> tasks = new ArrayList<>()
            //NEW一个入库任务-任务表 的对象
            BmfObject objTask = new BmfObject("CT1112Tasks")
            //为task表的物理编码赋值
            objTask.put("materialCode", passBox.getString("materialCode"))
            //为task表的物料描述赋值
            objTask.put("materialName", passBox.getString("materialName"))

            objTask.put("quantityUnit", basicGroovyService.getByCode("material", passBox.getString("materialCode")).getAndRefreshBmfObject("flowUnit"))
//计量单位
            tasks.add(objTask)
            objCT1112.put("tasks", tasks)

            //从当前界面passbox数据复制到指定BmfClass 中,然后将id和submit置空
            BmfObject passBoxb = BmfUtils.genericFromJsonExt(passBox, "CT1112PassBoxes");
            passBoxb.put("id", null);
            passBoxb.put("submit", false);
            objCT1112.put("passBoxes", Collections.singletonList(passBoxb));//添加周转箱表

            //为周转箱实时表的批次字段赋值
            BmfObject passBoxReal = basicGroovyService.getByCode("passBoxReal", passBox.getString("code"))
            if (!passBoxReal) {
                throw new BusinessException("周转箱实时信息不存在")
            } else {
                passBoxReal.put("ext_batch_number", batchNumber)
                if (!(item.getString("ext_warehouse_in_type")=="purchaseDeliver" ||item.getString("ext_warehouse_in_type")=="purchaseDeliverExt" ||
                        item.getString("ext_warehouse_in_type")=="purchaseDeliverFx"))

                {
                    passBoxReal.put("ext_prdLine", item.getString("ext_prdLine"))
                }
                basicGroovyService.updateByPrimaryKeySelective(passBoxReal)
            }

            sceneGroovyService.buzSceneStart("CT1112", objCT1112);

        })

    }
    private void CT1118Validate(BmfObject nodeData, BmfObject item) {

        BigDecimal ext_quantity=item.getBigDecimal("ext_quantity")

        List<BmfObject> passBoxes = item.getList("passBoxes")
        if(!passBoxes){
            throw new BusinessException("周转箱必须填写，请检查后重试！")
        }
        BigDecimal sum = passBoxes.stream().peek(passBox -> {
            BmfObject passBoxReal = basicGroovyService.findOne("passBoxReal", "passBoxCode", passBox.getString("passBoxCode"))
            if (passBoxReal == null) {
                throw new BusinessException("周转箱[" + passBox.getString("passBoxCode") + "]实时信息不存在或生成失败")
            }
        }).map(passBox -> passBox.getBigDecimal("quantity") == null ? BigDecimal.ZERO : passBox.getBigDecimal("quantity"))
                .reduce(BigDecimal.ZERO, BigDecimal::add)

        passBoxes.forEach {passBox ->
            BmfObject passBoxReal = basicGroovyService.findOne("passBoxReal", "passBoxCode", passBox.getString("passBoxCode"))
            if (!passBoxReal) {
                throw new BusinessException("周转箱[" + passBox.getString("passBoxCode") + "]实时信息不存在或生成失败")

            }
            if(passBoxReal.getString("materialCode")!=(item.getString("ext_material_code"))){
                throw new BusinessException("周转箱[" + passBox.getString("passBoxCode") + "]物料不在任务中，请检查后重试！")
            }
            if(passBoxReal.getBigDecimal("quantity")<=BigDecimal.ZERO){
                throw new BusinessException("周转箱[" + passBox.getString("passBoxCode") + "]物料数量必须填写，请检查后重试！")
            }
        }
        if (ext_quantity < sum) {
            throw new BusinessException("周转箱数量之和不能超过本次入库数量")
        }

    }

    private void updateCT1118logisticsStatus(BmfObject nodeData, BmfObject item) {

        //比较原始数ext_quantity 与已提交数量ext_submittedQuantity，进而改写单据状态logisticsStatus
        def passBoxes = item.getList("passBoxes")
        BigDecimal submittedQuantity = passBoxes.stream()
                .map(passBox -> passBox.getBigDecimal("receiveQuantity") ?: BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)

        //获得当前界面上记录的已提交数量，用于累加
        def oldqty = item.getBigDecimal("ext_submitQty") ?: BigDecimal.ZERO
        //初始化单据状态logisticsStatus为全部完成
        String logisticsStatus = "3"
        //状态为部分提交的，任务停留，以及周转箱保持不提交
        if (submittedQuantity.add(oldqty) > BigDecimal.ZERO) {
            BmfObject updateNodeData = new BmfObject(item.getBmfClassName())
            updateNodeData.put("id", item.getPrimaryKeyValue())
            if (submittedQuantity.add(oldqty)  == BigDecimal.ZERO) {
                logisticsStatus = "1"
            } else if (submittedQuantity.add(oldqty)  < item.getBigDecimal("ext_quantity") && submittedQuantity.add(oldqty)  > BigDecimal.ZERO) {
                logisticsStatus = "2"
            } else {
                logisticsStatus = "3"
            }
            updateNodeData.put("logisticsStatus", logisticsStatus)
            updateNodeData.put("ext_submitQty", oldqty.add(submittedQuantity))
            basicGroovyService.updateByPrimaryKeySelective(updateNodeData)
        }

    }
    /**
     * 生成新任务
     * @param passBoxes 周转箱集合
     */
    private void createNewTask(BmfObject item) {
        //本次提交的周转箱数量之和
        BigDecimal quantity = item.getList("passBoxes").stream().map(passBox -> passBox.getBigDecimal("quantity")).reduce(BigDecimal.ZERO, BigDecimal::add)
        //待发货数量
        BigDecimal totalQuantity = item.getBigDecimal("ext_quantity")//界面上的数量，不可编辑

        //未完成生成新任务
        if (totalQuantity > quantity) {
            //计划数量大于本次周转箱数量之和时，生成新任务
            BmfObject clone = item.deepClone()
            clone = BmfUtils.genericFromJsonExt(clone, clone.getBmfClassName())
            clone.put("ext_quantity", totalQuantity.subtract(quantity))//新任务的待收货数量
            //clone.put("ext_current_received_quantity", totalQuantity.subtract(quantity))//新任务的本次收货数量（默认值，可修改））
            sceneGroovyService.saveBySelf(clone)
        }
    }
    def getWarehouse2ByLocation(String warehouseCategoryCode, String warehouseCode) {
        //传入仓库类别编码和仓库编码，优先返回该仓库编码背后关联库位上的第一个最小（按库位编码排序）空位置，如果前述逻辑返回空值，那么从该仓库类别下返回第一个最小空位置。
        // 示例：  call proc_getWarehouseLocationIn ('CB1002','CK0007') 返回值：WZ00012
        String sSQL
        sSQL = "call proc_getWarehouseLocationIn ('"
        sSQL += warehouseCategoryCode + "','"
        sSQL += warehouseCode
        sSQL += "')"

        def sqlResult = basicGroovyService.findOne(sSQL)
        def location = sqlResult.get("location_code")
        if (!location) {
            throw new BusinessException("未找到最小空库位，请检查后重试！ 传入参数为: 仓库类别编码：$warehouseCategoryCode,仓库编码:$warehouseCode")
        }
        log.info("==============传入参数仓库类别编码：$warehouseCategoryCode，仓库编码：$warehouseCode，返回最小空库位$location==============")
        return location
    }
}
