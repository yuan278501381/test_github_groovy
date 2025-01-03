package groovy.node_groovy

import cn.hutool.core.collection.CollectionUtil
import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.bmf.service.BmfService
import com.chinajay.virgo.utils.BmfUtils
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.application.script.exception.ScriptInterruptedException
import com.tengnat.dwork.modules.basic_data.domain.DomainBindResource
import com.tengnat.dwork.modules.basic_data.service.ResourceBindingService
import com.tengnat.dwork.modules.manufacturev2.domain.dto.ObjectResource
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
                    .getString("name") contains("平面"))
            {

                log.info("==============按仓库类别名称识别为平面库，进入平面库业务逻辑==============")
                //createFlatTask(nodeData, item)
                //平面库入库任务-PDA 在本场景的第一个节点,且平面入库任务的周转箱不做拆分,所以直接返回nodedata,场景自动流转
                item.put("ext_warehouse_in_application_code", item.getString("ext_warehouse_In_application_code"))
                //目标仓库编码
                item.put("ext_target_warehouse_code", item.getString("warehouseCode"))
                //目标仓库名称
                item.put("ext_target_warehouse_name", item.getString("warehouseName"))
                //移动应用场景中第二个节点，dataFlow 自动流转
                sceneGroovyService.dataFlow(item)
            }
            //3、根据仓库名称判断，创建智能设备搬运任务-PDA CT1111
           else if (basicGroovyService.getByCode("warehouseCategory",
                    basicGroovyService.getByCode("warehouse", item.getString("warehouseCode")).getString("categoryCode"))
                    .getString("name") contains("CTU"))
            {
                log.info("==============按仓库类别名称识别为CTU库，进入CTU库业务逻辑==============")
                createIntelligenthandlingTask(nodeData, item, warehouseInSheet)
            }
            else {throw new BusinessException("仓库类别名称识别失败,不包含平面仓、CTU仓中二者中的一种，未识别的业务逻辑！")}
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
        return warehouseInSheet
    }

    private void createFlatTask(BmfObject nodeData, BmfObject item) {

        /*
      **  2、创建平面库入库任务 CT1119
       */

        // def jsonCT1119=new JSONObject()
        BmfObject ct1119 = BmfUtils.genericFromJsonExt(item.deepClone(), "CT1119")
        BmfObject tasks = BmfUtils.genericFromJsonExt(item.deepClone(), "CT1119Tasks")
        BmfObject passBoxc = BmfUtils.genericFromJsonExt(item.deepClone(), "CT1119PassBoxes")
        passBoxc.put("id", null)
        passBoxc.put("submit", false)
        ct1119.put("passBoxes", Arrays.asList(passBoxc))//添加周转箱表

        tasks.put("id", null)
        ct1119.put("tasks", Arrays.asList(tasks))

        //入库申请单号
        ct1119.put("ext_warehouse_in_application_code", item.getString("ext_warehouse_In_application_code"))
        //目标仓库编码
        ct1119.put("ext_target_warehouse_code", item.getString("warehouseCode"))
        //目标仓库名称
        ct1119.put("ext_target_warehouse_name", item.getString("warehouseName"))
        //jsonCT1119.putAll (ct1119)
        sceneGroovyService.buzSceneStart("CT1119", ct1119)

//        //为周转箱实时表的批次字段赋值
//        BmfObject passBoxReal= basicGroovyService.getByCode("passBoxReal", passBox.getString("code"))
//        if (!passBoxReal){
//            throw new  BusinessException("周转箱实时信息不存在")
//        }
//        else {
//            passBoxReal.put("ext_batch_number",batchNumber)
//            basicGroovyService.updateByPrimaryKeySelective(passBoxReal)
//        }
    }

    private void createIntelligenthandlingTask(BmfObject nodeData, BmfObject item, BmfObject warehouseInSheet) {

        /*
      **  3、创建智能设备搬运任务
       */


        //默认批次编码为：ZD
        String batchNumber = "ZD"
        if (!item.get("ext_batch_number")) {
            batchNumber = "ZD"
        } else {
            batchNumber = item.get("ext_batch_number")
        }

        //组装周转箱表
        List<BmfObject> passBoxes2 = nodeData.getList("passBoxes")
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

            //为周转箱实时表的批次字段赋值
            BmfObject passBoxReal = basicGroovyService.getByCode("passBoxReal", passBox.getString("code"))
            if (!passBoxReal) {
                throw new BusinessException("周转箱实时信息不存在")
            } else {
                passBoxReal.put("ext_batch_number", batchNumber)
                basicGroovyService.updateByPrimaryKeySelective(passBoxReal)
            }

            sceneGroovyService.buzSceneStart("CT1112", objCT1112);


        })

    }
/**
 * *入库位置推荐函数：
 *  推荐逻辑：输入一个仓库，,返回编码最小的空位置
 *  1、根据仓库代码获取库位
 *  2、根据库位获取位置
 *  3、剔除部分不可用的位置
 *  4、从剩下的位置中找到一个可用且排序最小的返回
 */

    //    ResourceBindingService resourceBindingService = SpringUtils.getBean(ResourceBindingService.class)
    //    BmfService bmfService = SpringUtils.getBean(BmfService.class)

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
