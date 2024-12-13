package groovy.aop_groovy

import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.bmf.service.BmfService
import com.chinajay.virgo.bmf.sql.Conjunction
import com.chinajay.virgo.bmf.sql.OperationType
import com.chinajay.virgo.bmf.sql.Restriction
import com.chinajay.virgo.utils.SpringUtils
import com.tengnat.dwork.common.constant.BmfAttributeConst
import com.tengnat.dwork.common.constant.BmfClassNameConst
import com.tengnat.dwork.modules.basic_data.service.ResourceBindingService
import com.tengnat.dwork.modules.script.abstracts.AopAfterGroovyClass


/**
 * 位置库位同步
 */
class AopAfterLocationStorageSync extends AopAfterGroovyClass {
    BmfService bmfService = SpringUtils.getBean(BmfService.class)
    ResourceBindingService resourceBindingService = SpringUtils.getBean(ResourceBindingService.class)
    @Override
    void runScript(Object data) {
        JSONObject jsonObject = (JSONObject) data
        def bmfClass = jsonObject.getString("bmfClass")
        def code = jsonObject.getString("code")
        def name = jsonObject.getString("name")
        def bmfObject = bmfService.findByUnique(bmfClass, "code", code)

        //判断是新增库位还是位置
        if (bmfClass.equals("location")) {
            def newCode = code.replaceFirst("WZ", "KW")
            def storageLocation = bmfService.findByUnique("storageLocation", "code", newCode)
            if (storageLocation == null) {
                storageLocation = new BmfObject("storageLocation");
                storageLocation.put("code", newCode)
                storageLocation.put("barCode", "KW")
                storageLocation.put("name", name +"库位")
                storageLocation.put("status", true)
                storageLocation.put("locationCode", code)
                storageLocation.put("locationName", name)
                bmfService.saveOrUpdate(storageLocation)
                JSONArray jsonArray = new JSONArray()
                def object = new JSONObject()
                BmfObject storageLocationResource = bmfService.findOne(BmfClassNameConst.RESOURCE, Collections.singletonList(
                        Restriction.builder()
                                .conjunction(Conjunction.AND)
                                .attributeName(BmfAttributeConst.CODE)
                                .operationType(OperationType.EQUAL)
                                .values(Collections.singletonList("storageLocation"))
                                .build()
                ));
                object.put("bindingResourceCode", newCode)
                object.put("bindingResourceName", name +"库位")
                object.put("bindingResource", storageLocationResource)
                jsonArray.add(object)
                resourceBindingService.save(bmfObject, jsonArray)
            }

        } else {
            def newCode = code.replaceFirst("KW", "WZ")
            def location = bmfService.findByUnique("location", "code", newCode)
            if (location == null) {
                location = new BmfObject("location");
                location.put("code", newCode)
                location.put("barCode", "WZ")
                location.put("name", name +"位置")
                location.put("status", true)
                bmfService.saveOrUpdate(location)
                JSONArray jsonArray = new JSONArray()
                def object = new JSONObject()
                BmfObject locationResource = bmfService.findOne(BmfClassNameConst.RESOURCE, Collections.singletonList(
                        Restriction.builder()
                                .conjunction(Conjunction.AND)
                                .attributeName(BmfAttributeConst.CODE)
                                .operationType(OperationType.EQUAL)
                                .values(Collections.singletonList("location"))
                                .build()
                ));
                object.put("bindingResourceCode", newCode)
                object.put("bindingResourceName", name +"位置")
                object.put("bindingResource", locationResource)
                jsonArray.add(object)
                resourceBindingService.save(bmfObject, jsonArray)
            }
        }
    }
}