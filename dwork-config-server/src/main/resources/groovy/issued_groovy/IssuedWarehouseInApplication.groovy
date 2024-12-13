package groovy.issued_groovy

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.tengnat.dwork.modules.script.abstracts.AopAfterGroovyClass
import com.tengnat.dwork.modules.script.service.SceneGroovyService

/**
 * 其它入库下达脚本
 */
class IssuedWarehouseInApplication extends AopAfterGroovyClass{
    //通用业务类
    SceneGroovyService sceneGroovyService= SpringUtils.getBean(SceneGroovyService.class)
    @Override
    void runScript(Object data) {
        //其它入库下达合集
        List<BmfObject> warehouseInApplicationList = data as List<BmfObject>

//        for (BmfObject warehouseInApplication :warehouseInApplicationList){
//            if(收货场景){
//                sceneGroovyService.buzSceneStart("issuedWarehouseInApplication",warehouseInApplication)
//            }else (不收货场景){
//
//            }
//        }
        println "其它入库下达合集:" + warehouseInApplicationList



    }
}
