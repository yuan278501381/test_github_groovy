package groovy.node_groovy

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.bmf.service.BmfService
import com.chinajay.virgo.utils.BmfUtils
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.application.common.utils.DateUtils
import com.tengnat.dwork.modules.basic_data.domain.DomainBindResource
import com.tengnat.dwork.modules.basic_data.service.ResourceBindingService
import com.tengnat.dwork.modules.manufacturev2.domain.dto.ObjectResource
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService

import java.util.stream.Collectors

/**
 * @Author 袁英杰
 * @Create 2025-01-03
 * @Dscription 发起入库申请-PDA 提交时 创建入库申请单-PC，状态为非初始值，以及生成 入库任务-平面库、入库任务-滚筒线中的一个
 *
 * */
class NodeCT1126Submit extends NodeGroovyClass {

    //基础类
    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    //通用业务类
    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)

    ResourceBindingService resourceBindingService = SpringUtils.getBean(ResourceBindingService.class)
    BmfService bmfService = SpringUtils.getBean(BmfService.class)

    @Override
    Object runScript(BmfObject nodeData) {
        //这个是功能型不会批量提交的情况肯定是单个
        //1、创建入库申请单-PC
        BmfObject warehouseInApplication = createWarehouseInApplication(nodeData)
        log.info("==============入库申请单-PDA，提交:创建入库申请单-PC 完成==============")
        //2、创建入库任务单-PC
        BmfObject warehouseInSheet = createWarehouseInTask(nodeData, warehouseInApplication)
        log.info("==============入库申请单-PDA，提交:创建入库任务单-PC 完成==============")
        //3、创建入库任务-
        def warehouse = basicGroovyService.getByCode("warehouse", nodeData.getString("warehouseCode"))
        if (warehouse == null) {
            throw new BusinessException("未找到仓库信息，warehouseCode：[" + nodeData.getString("warehouseCode") + "]")
        }
        def warehouseCategory = basicGroovyService.getByCode("warehouseCategory", warehouse.getString("categoryCode"))
        if (warehouseCategory == null) {
            throw new BusinessException("未找到仓库类别信息，categoryCode：[" + nodeData.getString("categoryCode") + "]")
        }
        if (warehouseCategory.getString("name")?.contains("平面")) {
            //平面库
            log.info("==============入库申请单-PDA，提交:按仓库类别名称识别为平面库，进入平面库业务逻辑==============")
            createFlatTask(nodeData, warehouseInApplication)
        } else if (warehouseCategory.getString("name")?.contains("CTU")) {
            //CTU库
            log.info("==============入库申请单-PDA，提交:按仓库类别名称识别为CTU库，进入CTU库业务逻辑==============")
            createIntelligenthandlingTask(nodeData, warehouseInApplication, warehouseInSheet)
        } else {
            throw new BusinessException("仓库类别名称识别失败,不包含平面仓、CTU仓中二者中的一种，未识别的业务逻辑！")
        }
        //throw new BusinessException("ttttttttttt")
        return null
    }

    private BmfObject createWarehouseInApplication(BmfObject nodeData) {
        /*
        **  1、创建入库申请单
        */
        //为入库任务单赋值, 首先对入库任务单实例化一个对象,然后为其主表\子表\周转箱详情表赋值,然后用save方法保存
        BmfObject warehouseInApplication = new BmfObject("WarehouseInApplication")
        //入库类型
        warehouseInApplication.put("warehouseInType", nodeData.getString("ext_warehouse_in_type"))
        //初始化单据状态
        //toConfirmed-待确认;pendingOutWarehouse-待出库;pendingInWarehouse-待入库;completed-已入库;cancel-取消;settlement
        // -结算;partialOutWarehouse-部分出库;partialInWarehouse-部分入库
        warehouseInApplication.put("status", "pendingInWarehouse")
        //组装行表：Details
        List<BmfObject> details = new ArrayList<>()

        def passBoxes = nodeData.getList("passBoxes")
        //按照物料编码合并入库申请单明细
        def passBoxesMapByMaterialCode = passBoxes.stream().collect(Collectors.groupingBy { it -> ((BmfObject) it).getString("materialCode") })
        int lineNum = 0
        for (final def key in passBoxesMapByMaterialCode.keySet()) {
            ++lineNum
            List<BmfObject> passBoxesByMaterialCode = passBoxesMapByMaterialCode.get(key)
            BigDecimal sumQuantity = passBoxesByMaterialCode.stream().map(bmfObject -> bmfObject.getBigDecimal("quantity")).reduce(BigDecimal.ZERO, BigDecimal::add)
            BmfObject passBox = passBoxesByMaterialCode.get(0)
            def matrial = basicGroovyService.getByCode("material", passBox.getString("materialCode"))
            BmfObject detail = new BmfObject("WarehouseInApplicationDetail")
            detail.put("materialCode", passBox.getString("materialCode"))//物料编码
            detail.put("materialName", passBox.getString("materialName"))//物料描述
            detail.put("quantity", sumQuantity)//数量
            detail.put("specifications", matrial?.getString("specifications"))
            //规格型号
            detail.put("wait_inbound_quantity", sumQuantity)//待入库数量
            detail.put("warehoused_quantity", BigDecimal.ZERO)//已入库数量
            detail.put("unit", matrial?.getAndRefreshBmfObject("flowUnit"))
            //计量单位
            detail.put("warehouseCode", nodeData.getString("warehouseCode"))
            detail.put("warehouseName", nodeData.getString("warehouseName"))
            detail.put("lineNum", lineNum)
            details.add(detail)
        }
        //对入库任务单行表赋值
        warehouseInApplication.put("main_idAutoMapping", details)
        //获得当前日期写入备注
        warehouseInApplication.put("remark", DateUtils.dateToStr(new Date(), DateUtils.DATE_TIME_FORMAT))

        //设置入库任务单编码
        basicGroovyService.setCode(warehouseInApplication)
        //保存入库任务单
        basicGroovyService.saveOrUpdate(warehouseInApplication)
        return warehouseInApplication
    }


    private BmfObject createWarehouseInTask(BmfObject nodeData, BmfObject warehouseInApplication) {
        /*
        **  2、创建入库任务单
        */
        //为入库任务单赋值, 首先对入库任务单实例化一个对象,然后为其主表\子表\周转箱详情表赋值,然后用save方法保存
        BmfObject warehouseInSheet = new BmfObject("warehouseInSheet")
        //入库类型
        warehouseInSheet.put("warehouseInType", warehouseInApplication.getString("warehouseInType"))
        //初始化单据状态
        //toConfirmed-待确认;pendingOutWarehouse-待出库;pendingInWarehouse-待入库;completed-已入库;cancel-取消;settlement
        // -结算;partialOutWarehouse-部分出库;partialInWarehouse-部分入库
        warehouseInSheet.put("status", "pendingInWarehouse")
        //组装周转箱
        def passBoxes = nodeData.getList("passBoxes")
        Map<String, List<BmfObject>> passBoxesMapByMaterialCode = passBoxes.stream().collect(Collectors.groupingBy { it -> ((BmfObject) it).getString("materialCode") })
        //组装行表：Details
        List<BmfObject> details = new ArrayList<>()
        def sourceDetail = warehouseInApplication.getAndRefreshList("main_idAutoMapping")
        sourceDetail.forEach { line ->
            //组装周转箱表
            List<BmfObject> warehouseInSheetPassBoxs = new ArrayList<>()
            List<BmfObject> passBoxesByMaterialCode = passBoxesMapByMaterialCode.get(line.getString("materialCode"))
            if (passBoxesByMaterialCode != null) {
                passBoxesByMaterialCode.forEach { passBox ->
                    BmfObject warehouseInSheetPassBox = new BmfObject("warehouseInSheetPassBox")
                    warehouseInSheetPassBox.put("quantity", passBox.getBigDecimal("quantity"))//数量
                    warehouseInSheetPassBox.put("passBoxName", passBox.getString("passBoxName"))//周转箱名称
                    warehouseInSheetPassBox.put("passBoxCode", passBox.getString("passBoxCode"))//周转箱编码
                    warehouseInSheetPassBox.put("unit", line.get("unit"))//单位
                    warehouseInSheetPassBox.put("passBoxRealCode", passBox.getString("code"))//周转箱实时编码
                    warehouseInSheetPassBoxs.add(warehouseInSheetPassBox)
                }
            }
            BmfObject detail = new BmfObject("warehouseInSheetDetail")
            detail.put("materialCode", line.getString("materialCode"))//物料编码
            detail.put("materialName", line.getString("materialName"))//物料描述
            detail.put("specifications", nodeData.getString("specifications"))//规格
            detail.put("quantity", line.getBigDecimal("quantity"))//数量
            detail.put("wait_inbound_quantity", line.getBigDecimal("quantity"))//待入库数量
            detail.put("warehoused_quantity", BigDecimal.ZERO)//已入库数量
            detail.put("unit", line.get("unit"))//计量单位
            detail.put("warehouseCode", nodeData.getString("warehouseCode"))//仓库编码
            detail.put("warehouseName", nodeData.getString("warehouseName"))//仓库名称
            detail.put("lineNum", line.get("lineNum"))
            detail.put("fatherId", -1)//从入库申请-PDA的单据没有fatherID，因为无关联的入库待确认任务，用默认值-1，
            detail.put("warehouseInApplicationCode", warehouseInApplication.getString("code")) //入库申请单编码，从函createWarehouseInApplication回调
            detail.put("warehouseInSheetDetailAutoMapping", warehouseInSheetPassBoxs)
            details.add(detail)
        }
        warehouseInSheet.put("mainidAutoMapping", details)

        //设置入库任务单编码
        basicGroovyService.setCode(warehouseInSheet)
        //保存入库任务单
        basicGroovyService.saveOrUpdate(warehouseInSheet)

        return warehouseInSheet
    }

    private void createFlatTask(BmfObject nodeData, BmfObject warehouseInApplication) {
        /*
        **  3、创建 入库任务-平面库
        */
        //默认批次编码为：ZD
        String batchNumber = "ZD"
        if (!nodeData.get("ext_batch_number")) {
            batchNumber = "ZD"
        } else {
            batchNumber = nodeData.get("ext_batch_number")
        }

        BmfObject objCT1119 = new BmfObject("CT1119")

        //入库申请单号
        objCT1119.put("ext_warehouse_in_application_code", warehouseInApplication.getString("code"))
        //目标仓库编码
        objCT1119.put("ext_target_warehouse_code", nodeData.getString("warehouseCode"))
        //目标仓库名称
        objCT1119.put("ext_target_warehouse_name", nodeData.getString("warehouseName"))
        //入库类型
        objCT1119.put("ext_warehouse_in_type", nodeData.getString("ext_warehouse_in_type"))
        objCT1119.put("warehouseCode", nodeData.getString("warehouseCode"))
        objCT1119.put("warehouseName", nodeData.getString("warehouseName"))


        //组装周转箱表
        def passBoxesb = nodeData.getList("passBoxes")
        List<BmfObject> PassBoxes = new ArrayList<>()
        passBoxesb.forEach { passBox ->

            BmfObject objPassbox = BmfUtils.genericFromJsonExt(passBox, "CT1119PassBoxes")
            objPassbox.put("id", null)
            objPassbox.put("submit", false)
            PassBoxes.add(objPassbox)

            //为周转箱实时表的批次字段赋值
            BmfObject passBoxReal = basicGroovyService.getByCode("passBoxReal", passBox.getString("code"))
            if (!passBoxReal) {
                throw new BusinessException("周转箱实时信息不存在")
            } else {
                passBoxReal.put("ext_batch_number", batchNumber)
                basicGroovyService.updateByPrimaryKeySelective(passBoxReal)
            }
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
            // throw new BusinessException("测试")
        })

        objCT1119.put("tasks", tasks)//添加任务表
        objCT1119.put("passBoxes", PassBoxes)//添加周转箱表


        sceneGroovyService.buzSceneStart("CT1119", objCT1119)

        log.info("========入库申请单-PDA，提交:按仓库类别名称识别为平面库，进入平面库业务逻辑 完成=========主表")
        // log.info(JSONObject.toJSONString(objCT1119))
    }

    private void createIntelligenthandlingTask(BmfObject nodeData, BmfObject warehouseInApplication, BmfObject warehouseInSheet) {
        /*
        **  4、创建 入库任务-智能搬运设备
        */

        //默认批次编码为：ZD
        String batchNumber = "ZD"
        if (!nodeData.get("ext_batch_number")) {
            batchNumber = "ZD"
        } else {
            batchNumber = nodeData.get("ext_batch_number")
        }

        //组装周转箱表
        List<BmfObject> passBoxes2 = nodeData.getList("passBoxes")
        //            if (CollectionUtil.isEmpty(passBoxes)) {
        //                throw new BusinessException("周转箱数据不能为空")
        //            }

        passBoxes2.forEach(passBox -> {
            //CT1112 滚筒线搬运任务
            BmfObject objCT1112 = new BmfObject("CT1112")

            def warehouseCode=nodeData.getString("warehouseCode")
            def warehouseCategoryCode=basicGroovyService.getByCode("warehouse", warehouseCode).getString("categoryCode")

            //按传入的入库申请单号，从入库申请单表头获得来源单号
            String sourceOrderCode = warehouseInApplication.getString("sourceOrderCode")
            //按传入的入库申请单号，从入库申请单表头获得入库类型
            String warehouseInType = warehouseInApplication.getString("warehouseInType")
            //按传入的入库申请单号，从入库申请单表头获得来源单据类型
            String sourceOrderType = warehouseInApplication.getString("sourceOrderType")

            objCT1112.put("ext_warehouse_in_application_code", nodeData.getString("ext_warehouse_In_application_code"))
            //入库申请单号
            objCT1112.put("ext_business_type", warehouseInType)//业务类型 采购入库等
            objCT1112.put("ext_business_source", sourceOrderType)//业务来源 采购订单等
            objCT1112.put("ext_business_source_code", sourceOrderCode)//来源编码
            objCT1112.put("ext_pass_box_code", passBox.getString("passBoxCode"))//周转箱编码


            objCT1112.put("ext_end_point", getWarehouse2ByLocation(warehouseCategoryCode,warehouseCode))
//滚筒线目标位置
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
            passBoxb.put("quantityUnit", basicGroovyService.getByCode("material", passBox.getString("materialCode")).getAndRefreshBmfObject("flowUnit")
            )
            objCT1112.put("passBoxes", Collections.singletonList(passBoxb));//添加周转箱表

            sceneGroovyService.buzSceneStart("CT1112", objCT1112);

            //为周转箱实时表的批次字段赋值
            BmfObject passBoxReal = basicGroovyService.getByCode("passBoxReal", passBox.getString("code"))
            if (!passBoxReal) {
                throw new BusinessException("周转箱实时信息不存在")
            } else {
                passBoxReal.put("ext_batch_number", batchNumber)
                basicGroovyService.updateByPrimaryKeySelective(passBoxReal)
            }6

        })

    }

    def getWarehouse2ByLocation(String warehouseCategoryCode,String warehouseCode) {
        //传入仓库类别编码和仓库编码，优先返回该仓库编码下的第一个最小（按库位编码排序）空位置，如果前述逻辑返回空值，那么从该仓库类别下返回第一个最小空位置。
        // 示例：  call proc_getWarehouseLocationIn ('CB1002','CK0007') 返回值：WZ00012
        String sSQL
        sSQL= "call proc_getWarehouseLocationIn ('"
        sSQL+=warehouseCategoryCode+"','"
        sSQL+=warehouseCode
        sSQL+="')"

        def sqlResult = basicGroovyService.findOne(sSQL)
        def location = sqlResult.get("location_code")
        if (!location){
            throw new BusinessException("未找到最小空库位，请检查后重试！ 传入参数为: 仓库类别编码：$warehouseCategoryCode,仓库编码:$warehouseCode")
        }
        log.info("==============传入参数仓库类别编码：$warehouseCategoryCode，仓库编码：$warehouseCode，返回最小空库位$location==============")
        return location
    }
}
