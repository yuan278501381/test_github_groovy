package groovy.aop_groovy

import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.utils.SpringUtils
import com.tengnat.dwork.modules.script.abstracts.AopBeforeGroovyClass
import org.springframework.web.client.RestTemplate

/**
 * 脚本调用http Demo
 */
class AopBeforeHttpDemo extends AopBeforeGroovyClass {

    RestTemplate restTemplate = SpringUtils.getBean(RestTemplate.class)

    @Override
    Object[] runScript(Object[] args) {
        //Post Demo

        //参数
        JSONObject json = new JSONObject()
        json.put("参数", "参数1")


        JSONObject result = restTemplate.postForObject("请求地址", json, JSONObject.class)
        if (result != null) {
            //返回的数据
        }
        //RestTemplate使用教程:https://blog.csdn.net/weixin_43888891/article/details/125649613
        return args
    }

}