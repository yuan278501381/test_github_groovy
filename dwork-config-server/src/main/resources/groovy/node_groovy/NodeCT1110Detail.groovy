package groovy.node

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.tengnat.dwork.modules.basic_data.service.PackSchemeService
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import org.apache.commons.lang3.ObjectUtils

/**
 * 翻包-详情脚本
 * 写入物料的包装方案，每包数量
 * * CU0001 翻包场景  CT1110 翻包移动应用
 * @袁英杰
 * @UpdateDate 2024-12-06
 *
 * */
class NodeCT1110Detail extends NodeGroovyClass {

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
            //nodeData.put("ext_each_pack_amount", BigDecimal.ZERO)
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
                if (!bigDecimal){bigDecimal=nodeData.getBigDecimal("ext_quantity")}else{bigDecimal}
                nodeData.put("ext_package_scheme_code", isPackScheme.getString("code"))
                nodeData.put("ext_package_scheme_name", isPackScheme.getString("name"))
                nodeData.put("ext_single_box_quantity", bigDecimal)
                basicGroovyService.updateByPrimaryKeySelective(nodeData)
            }
        }

    }


    //外部系统特殊处理
    private void reverseExternalWriting(BmfObject nodeData) {

    }


}