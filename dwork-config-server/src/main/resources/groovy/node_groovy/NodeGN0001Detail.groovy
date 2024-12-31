package groovy.node

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.tengnat.dwork.modules.basic_data.service.PackSchemeService
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import org.apache.commons.lang3.ObjectUtils

/**
 * 采购收货-详情脚本
 * @袁英杰
 * @UpdateDate 2024-12-06
 * 为中大新增了写入 是否翻包 的信息字段
 * */
class NodeGN0001Detail extends NodeGroovyClass {

    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    PackSchemeService packSchemeService = SpringUtils.getBean(PackSchemeService.class)

    @Override
    BmfObject runScript(BmfObject nodeData) {
        validate(nodeData)
        businessExecute(nodeData)
        reverseExternalWriting(nodeData)
        return nodeData
    }

    //校验
    private void validate(BmfObject nodeData) {

    }

    //dwork内部处理
    private void businessExecute(BmfObject nodeData) {
        //获取包装方案信息
        String materialCode = nodeData.getString("ext_material_code")
        if (ObjectUtils.isNotEmpty(materialCode)) {
            nodeData.put("ext_each_pack_amount", BigDecimal.ZERO)
            List<BmfObject> packSchemes = packSchemeService.findSchemesByMaterialCode(materialCode)
            BmfObject isPackScheme = null
            if (packSchemes.size() > 0) {
                packSchemes.forEach(packScheme -> {
                    //是否默认
                    if (packScheme.getBoolean("defaultStatus")) {
                        isPackScheme = packScheme
                    }
                })
                if (isPackScheme == null) {
                    isPackScheme = packSchemes.get(0)
                }
            }
            if (isPackScheme != null) {
                BigDecimal bigDecimal = isPackScheme.getBigDecimal("packageQuantity")
                nodeData.put("ext_package_scheme_code", isPackScheme.getString("code"))
                nodeData.put("ext_package_scheme_name", isPackScheme.getString("name"))
                nodeData.put("ext_single_box_quantity", bigDecimal)
                basicGroovyService.updateByPrimaryKeySelective(nodeData)
            }
        }
        // 佛山中大详情逻辑
        //获取物料的翻包标记
        if (ObjectUtils.isNotEmpty(materialCode)) {

            String sqlmain = "select t1.ext_packet_change from dwk_material t\n" +
                    "inner join dwk_material_ext t1 on t.id=t1.ext_material_id\n" +
                    "where t.code='" + materialCode + "'\n" +
                    "LIMIT 1"

            def SQLMainResult = basicGroovyService.findOne(sqlmain)
            //将SQL取得的是否翻包赋值给nodeData
            nodeData.put("ext_packet_change",SQLMainResult.get("ext_packet_change"))
        }
    }


    //外部系统特殊处理
    private void reverseExternalWriting(BmfObject nodeData) {

    }


}
