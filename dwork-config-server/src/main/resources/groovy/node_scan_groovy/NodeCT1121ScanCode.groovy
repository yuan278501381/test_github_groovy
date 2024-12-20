package groovy.node_scan_groovy

import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.BmfUtils
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.holder.ThreadLocalHolder
import com.chinajey.application.common.holder.UserAuthDto
import com.chinajey.dwork.company.modules.modbus.enums.UnBoxingPassBox
import com.chinajey.dwork.standard.common.util.JsonUtils
import com.tengnat.dwork.common.cache.CacheBusiness
import com.tengnat.dwork.common.constant.BmfAttributeConst
import com.tengnat.dwork.common.enums.EquipSourceEnum
import com.tengnat.dwork.modules.manufacturev2.domain.DomainScanResult
import com.tengnat.dwork.modules.script.abstracts.NodeScanGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService

/**
 * 滚筒线下架任务扫描脚本
 */
class NodeCT1121ScanCode extends NodeScanGroovyClass {

    def basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)

    @Override
    JSONObject runScript(BmfObject nodeData) {
        def result = new DomainScanResult()
        //是否拆箱
        def isUnloadBox = nodeData.getString("ext_unboxing")
        def passBoxCode = nodeData.getString("ext_pass_box_code")
        def currentRequiredQuantity = nodeData.getBigDecimal("ext_current_required_quantity")
        def docType = nodeData.getString("ext_warehouse_out_type")
        def docEntry = nodeData.getString("ext_warehouse_out_application_code")
        //是否跳过拆箱
        def isSkipUnLoadBox = nodeData.getBoolean("ext_is_skip_unboxing_passbox")
        if (isSkipUnLoadBox) {
            return result.success()
        }
        //不需要拆箱
        if (isUnloadBox == UnBoxingPassBox.NOT_REQUIRED.getCode()) {
            return result.success()
        }

        //拆箱
        if (isUnloadBox == UnBoxingPassBox.UN_BOXING.getCode() && !isSkipUnLoadBox) {
            def tasks = nodeData.getList("tasks")
            if (tasks == null || tasks.size() == 0) {
                return result.fail("当前任务没有任务明细")
            }
            if (tasks.size() > 1) {
                return result.fail("当前任务只允许扫描一个任务明细")
            }
            def task = tasks.get(0)

            def passBoxList = nodeData.getList("passBoxes")
            if (passBoxList == null || passBoxList.size() == 0) {
                return result.success()
            }
            if (passBoxList.size() > 1) {
                return result.fail("当前任务只允许扫描一个周转箱")
            }
            def scanPassBoxReal = passBoxList.get(0)

            def passBoxReal = basicGroovyService.findOne("passBoxReal", "passBoxCode", passBoxCode)
            if (passBoxReal == null) {
                return result.fail("未找到周转箱实时信息:" + passBoxCode)
            }

            def scanPassBox = basicGroovyService.findOne("passBox", "code", scanPassBoxReal.getString("passBoxCode"))
            if (scanPassBox == null) {
                return result.fail("空周转箱" + scanPassBoxReal.getString("passBoxCode") + "不存在主数据")
            }
            def passBoxQty = passBoxReal.getBigDecimal("quantity")
            if (passBoxQty <= currentRequiredQuantity) {
                return result.fail("周转箱数量不能小于等于出库数量")
            }
            def newPassBoxQty = passBoxQty - currentRequiredQuantity

            scanPassBoxReal.put("quantity", newPassBoxQty)
            //源箱拆箱后数量
            def sourceBoxQty = passBoxQty - currentRequiredQuantity
            //如果源箱拆箱后数量和新箱数量和需求出库数量相等 ，则源箱回库，其他情况下，则是源箱和新箱 哪个数量等于出库数量，出哪个
            if (sourceBoxQty == currentRequiredQuantity && sourceBoxQty == newPassBoxQty) {
                task.put("ext_return_pass_box", passBoxCode)
            } else if (newPassBoxQty == currentRequiredQuantity) {
                task.put("ext_return_pass_box", scanPassBoxReal.getString("passBoxCode"))
            } else if (sourceBoxQty == currentRequiredQuantity) {
                task.put("ext_return_pass_box", passBoxCode)
            }
        }
        return result.success(nodeData)
    }

    def unboxingPassBox(BmfObject fromPassBoxReal,BmfObject scanPassBoxReal, BigDecimal currentRequiredQuantity,DomainScanResult result) {

        UserAuthDto.LoginInfo loginInfo = ThreadLocalHolder.getLoginInfo()
        //实时周转箱 去掉位置  不计入库存
        JSONObject passBoxRealObj = new JSONObject();
        JsonUtils.jsonMerge(fromPassBoxReal, passBoxRealObj, "id", "code")
        passBoxRealObj.put("passBoxCode", scanPassBox.getString("code"));
        passBoxRealObj.put("passBoxName", scanPassBox.getString("name"));
        passBoxRealObj.put("quantity", newPassBoxQty);
        passBoxRealObj.put("packTime", new Date());
        passBoxRealObj.put("resource",  CacheBusiness.getCacheResourceId("user"))
        passBoxRealObj.put("resourceCode", loginInfo.getResource().getResourceCode())
        passBoxRealObj.put("resourceName", loginInfo.getResource().getResourceName())
        passBoxRealObj.put("sourceOrderType", params.get("sourceOrderType"));
        passBoxRealObj.put("locationCode", params.get("locationCode"));
        passBoxRealObj.put("locationName", params.get("locationName"));
        passBoxRealObj.put("materialCode", fromPassBoxReal.getString("materialCode"))
        passBoxRealObj.put("materialName", fromPassBoxReal.getString("materialName"))
        passBoxRealObj.put("loadMaterialType", fromPassBoxReal.getString("loadMaterialType"))
        BmfObject passBoxReal = BmfUtils.genericFromJson(passBoxRealObj, "passBoxReal");

        codeGenerator.setCode(passBoxReal);
        basicGroovyService.saveOrUpdate(passBoxReal);

        passBoxRealService.batchSyncRecords(Collections.singletonList(passBoxReal));
        passBoxRealService.synchronizePassBoxInfo(passBoxReal, EquipSourceEnum.PDA, "MATERIALS_DELIVERY_OUTSOURCED", "物料交接-委外发出");
    }

}
