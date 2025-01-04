package groovy.node_groovy

import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.alibaba.fastjson.JSON
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

        List<BmfObject> batchList = new ArrayList<>()

        if (nodeData.containsKey("batchList")) {
            batchList = nodeData.getList("batchList")
        } else {
            batchList.add(nodeData)
        }
        batchList.forEach(item -> {



            //1、创建入库申请单-PC
            BmfObject warehouseInApplication = createWarehouseInApplication(nodeData, item)
            log.info("==============入库申请单-PDA，提交:创建入库申请单-PC 完成==============")
            //2、创建入库任务单-PC
            BmfObject warehouseInSheet = createWarehouseInTask (nodeData, item, warehouseInApplication)
            log.info("==============入库申请单-PDA，提交:创建入库任务单-PC 完成==============")
            //3、创建入库任务-
            if (basicGroovyService.getByCode("warehouseCategory",
                    basicGroovyService.getByCode("warehouse",item.get("warehouseCode")).getString("categoryCode"))
                    .getString("name") contains("平面"))//平面库

            {
                log.info("==============入库申请单-PDA，提交:按仓库类别名称识别为平面库，进入平面库业务逻辑==============")

                createFlatTask(nodeData,item,warehouseInApplication)


            }
           else if (basicGroovyService.getByCode("warehouseCategory",
                    basicGroovyService.getByCode("warehouse",item.get("warehouseCode")).getString("categoryCode"))
                    .getString("name") contains("CTU"))//CTU库
            {
                log.info("==============入库申请单-PDA，提交:按仓库类别名称识别为CTU库，进入CTU库业务逻辑==============")

                createIntelligenthandlingTask(nodeData, item,warehouseInApplication,warehouseInSheet)
            }
            else {throw new BusinessException("仓库类别名称识别失败,不包含平面仓、CTU仓中二者中的一种，未识别的业务逻辑！")}

        })

        return  null
    }

    private BmfObject createWarehouseInApplication(BmfObject nodeData, BmfObject item) {

        /*
        **  1、创建入库申请单
        */
        //为入库任务单赋值, 首先对入库任务单实例化一个对象,然后为其主表\子表\周转箱详情表赋值,然后用save方法保存
        BmfObject warehouseInApplication = new BmfObject("WarehouseInApplication")
        //入库类型
        warehouseInApplication.put("warehouseInType", item.getString("ext_warehouse_in_type"))
        //初始化单据状态
        //toConfirmed-待确认;pendingOutWarehouse-待出库;pendingInWarehouse-待入库;completed-已入库;cancel-取消;settlement
        // -结算;partialOutWarehouse-部分出库;partialInWarehouse-部分入库
        warehouseInApplication.put("status","pendingInWarehouse")

        //组装行表：Details
        List<BmfObject> details = new ArrayList<>()

        def passBoxes = item.getList("passBoxes")
        passBoxes.forEach { passBox ->
            BmfObject detail = new BmfObject("WarehouseInApplicationDetail")
            detail.put("materialCode", passBox.getString("materialCode"))//物料编码
            detail.put("materialName", passBox.getString("materialName"))//物料描述
            detail.put("quantity", passBox.getBigDecimal("quantity"))//数量
            detail.put("specifications", basicGroovyService.getByCode("material",passBox.getString("materialCode")).getString("specifications"))//规格型号
            detail.put("wait_inbound_quantity", passBox.getBigDecimal("quantity"))//待入库数量
            detail.put("warehoused_quantity", BigDecimal.ZERO)//已入库数量
            detail.put("unit",basicGroovyService.getByCode("material", passBox.getString("materialCode")).getAndRefreshBmfObject("flowUnit"))//计量单位
            detail.put("warehouseCode", item.getString("warehouseCode"))
            detail.put("warehouseName", item.getString("warehouseName"))
            detail.put("lineNum",-1)

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


    private BmfObject createWarehouseInTask(BmfObject nodeData, BmfObject item,BmfObject warehouseInApplication)
    {

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

        //组装行表：Details
        List<BmfObject> details = new ArrayList<>()
        def sourceDetail=warehouseInApplication.getAndRefreshList("main_idAutoMapping")
        sourceDetail.forEach { line->
            BmfObject detail = new BmfObject("warehouseInSheetDetail")
            detail.put("materialCode", line.getString("materialCode"))//物料编码
            detail.put("materialName", line.getString("materialName"))//物料描述
            detail.put("specifications",item.getString("specifications"))//规格
            detail.put("quantity", line.getBigDecimal("quantity"))//数量
            detail.put("wait_inbound_quantity", line.getBigDecimal("quantity"))//待入库数量
            detail.put("warehoused_quantity", BigDecimal.ZERO)//已入库数量
            detail.put("unit",basicGroovyService.getByCode("material", line.getString("materialCode")).getAndRefreshBmfObject("flowUnit"))//计量单位
            detail.put("warehouseCode", item.getString("warehouseCode"))//仓库编码
            detail.put("warehouseName", item.getString("warehouseName"))//仓库名称
            detail.put("lineNum",-1)
            detail.put("fatherId", -1)//从入库申请-PDA的单据没有fatherID，因为无关联的入库待确认任务，用默认值-1，
            detail.put("warehouseInApplicationCode", warehouseInApplication.getString("code")) //入库申请单编码，从函createWarehouseInApplication回调
            detail.put("warehouseInSheetDetailAutoMapping", item.getAndRefreshList("warehouseInSheetDetailAutoMapping"))

            details.add(detail)
        }

        warehouseInSheet.put("mainidAutoMapping", details)

        //设置入库任务单编码
        basicGroovyService.setCode(warehouseInSheet)
        //保存入库任务单
        basicGroovyService.saveOrUpdate(warehouseInSheet)
        return warehouseInSheet
    }
    private void createFlatTask(BmfObject nodeData, BmfObject item,BmfObject warehouseInApplication) {
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
        objCT1119.put("ext_warehouse_in_application_code",warehouseInApplication.getString("code"))
        //目标仓库编码
        objCT1119.put("ext_target_warehouse_code", item.getString("warehouseCode"))
        //目标仓库名称
        objCT1119.put("ext_target_warehouse_name", item.getString("warehouseName"))
        //入库类型
        objCT1119.put("ext_warehouse_in_type",item.getString("ext_warehouse_in_type"))
        objCT1119.put("warehouseCode", item.getString("warehouseCode"))
        objCT1119.put("warehouseName", item.getString("warehouseName"))


        //组装周转箱表
        def passBoxesb=item.getList("passBoxes")
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

    private void createIntelligenthandlingTask(BmfObject nodeData, BmfObject item,BmfObject warehouseInApplication,BmfObject warehouseInSheet) {
        /*
        **  4、创建 入库任务-智能搬运设备
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
            String sourceOrderCode = warehouseInApplication.getString("sourceOrderCode")
            //按传入的入库申请单号，从入库申请单表头获得入库类型
            String warehouseInType = warehouseInApplication.getString("warehouseInType")
            //按传入的入库申请单号，从入库申请单表头获得来源单据类型
            String sourceOrderType = warehouseInApplication.getString("sourceOrderType")

            objCT1112.put("ext_warehouse_in_application_code", item.getString("ext_warehouse_In_application_code"))
            //入库申请单号
            objCT1112.put("ext_business_type", warehouseInType)//业务类型 采购入库等
            objCT1112.put("ext_business_source", sourceOrderType)//业务来源 采购订单等
            objCT1112.put("ext_business_source_code", sourceOrderCode)//来源编码
            objCT1112.put("ext_pass_box_code", passBox.getString("passBoxCode"))//周转箱编码


            objCT1112.put("ext_end_point", getWarehouse2ByLocation(item.getString("warehouseCode")).getString("bindingResourceCode"))//滚筒线目标位置
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
            sceneGroovyService.buzSceneStart("CT1112", objCT1112);

            //为周转箱实时表的批次字段赋值
            BmfObject passBoxReal = basicGroovyService.getByCode("passBoxReal", passBox.getString("code"))
            if (!passBoxReal) {
                throw new BusinessException("周转箱实时信息不存在")
            } else {
                passBoxReal.put("ext_batch_number", batchNumber)
                basicGroovyService.updateByPrimaryKeySelective(passBoxReal)
            }

        })

    }

    def getWarehouse2ByLocation(String warehouseCode) {
        DomainBindResource domainBindResource = new DomainBindResource()
        //获取仓库对应库位
        List<ObjectResource> storageLocationResources = domainBindResource.getBindResources("warehouse", warehouseCode, "storageLocation")
        //获取库位对应位置
        List<String> storageLocationCodes = storageLocationResources.stream().map {
            it.getCode()
        }.collect(Collectors.toList())

        def locationBindingMap = resourceBindingService.batchGetResourceBinding("storageLocation", storageLocationCodes, "location")

        List<BmfObject> allLocationList = new ArrayList<>();
        locationBindingMap.forEach((location, locationList) -> {
            allLocationList.addAll(locationList);
        });

        def passBoxRealList = bmfService.find("passBoxReal")
        def passBoxLocations = passBoxRealList.stream().map {
            it.getString("locationCode")
        }.collect(Collectors.toList())

        allLocationList.remove(passBoxLocations)
        if (allLocationList.size() == 0) {
            throw new BusinessException("没有可推荐的位置")
        }
        // 按 code 排序并返回最小的元素
        def location = allLocationList.stream()
                .sorted(Comparator.comparing { ((BmfObject) it).getString("bindingResourceCode") })
                .findFirst()
                .orElseThrow { new BusinessException("排序后没有找到位置") }

        return location
    }
}
