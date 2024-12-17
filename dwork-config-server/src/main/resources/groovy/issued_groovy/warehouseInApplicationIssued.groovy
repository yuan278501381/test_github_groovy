package groovy.issued_groovy

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.modules.script.abstracts.AopAfterGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService

import java.util.stream.Collectors

/**@author 袁英杰
 * @CreateTime: 2024-12-17
 *
 * 入库申请单下达脚本
 * 支持批量下达 ：
 */
class warehouseInApplicationIssued extends AopAfterGroovyClass{
    //基础类
    BasicGroovyService basicGroovyService= SpringUtils.getBean(BasicGroovyService.class)
    //通用业务类
    SceneGroovyService sceneGroovyService= SpringUtils.getBean(SceneGroovyService.class)
    @Override
    void runScript(Object data) {
        //入库申请单下达的单据ID集合 data 为List<BmfObject>
        List<BmfObject> warehouseInApplicationList = data as List<BmfObject>
        // 遍历结合中的每一个入库申请单
        for (BmfObject warehouseInApplication :warehouseInApplicationList){
            //业务规则项 ,默认为warehouseInApplicationIssued-其它入库申请单, 可按入库类型改判为:warehouseInApplicationIssued2 -生成采购收货任务/委外收货任务的 规则项
            String restfulCode="warehouseInApplicationIssued"
            //入库申请单状态
            String status=warehouseInApplication.getString("status")
            if (!"notReceive".equals(status)){
                throw BusinessException("入库申请单状态不是待收货，无法下达")
            }

            if (warehouseInApplication.getString("warehouseInType")=="purchaseDeliver"|| warehouseInApplication.getString("warehouseInType")=="purchaseDeliverExt" ){
                restfulCode="warehouseInApplicationIssued2"
            }


            //深拷贝一份，防止修改循环外的数据源
            warehouseInApplication=warehouseInApplication.deepClone()
            // 入库申请单明细
            List<BmfObject> details=warehouseInApplication.getAndRefreshList("main_idAutoMapping")
            //根据物料分组,并为分组后的物料进行遍历，key是物料编码，value是该物料下的明细,比如一个物料有5行明细，那么该物料就对应5行明细

            Map<String,List<BmfObject>> groupByMaterialCode=details.stream().collect(Collectors.groupingBy(detail->detail.getString("materialCode")))
            for (String key:groupByMaterialCode.keySet()){
                //物料下面的明细（一个物料在入库申请单中有多行）
                List<BmfObject> detailList=groupByMaterialCode.get(key)
                //数量：汇总同一个物料的多行数量，如果一个物料多行，汇总后的数据写到第一行，get(0)
                BigDecimal totalQuantity=detailList.stream().map(detail->detail.getBigDecimal("quantity")).reduce(BigDecimal.ZERO,BigDecimal::add)
                //一行明细
                BmfObject detail=detailList.get(0)
                detail.put("quantity",totalQuantity)
                //将
                warehouseInApplication.put("main_idAutoMapping",Collections.singletonList(detail))
                sceneGroovyService.restfulCodeStart(restfulCode,warehouseInApplication)
            }
            //回入库申请单的状态为:已收货
            warehouseInApplication.put("status","Received")

            //用方法updateByPrimaryKeySelective,更新入库申请单据的状态
            basicGroovyService.updateByPrimaryKeySelective(warehouseInApplication)
        }
        // throw BusinessException("test1111111111111111")
        // println "其它入库下达合集:" + warehouseInApplicationList



    }
}
