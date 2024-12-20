package com.chinajey.dwork.company.modules.modbus.controller;

import com.alibaba.fastjson.JSONObject;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.company.modules.modbus.service.ModbusService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description:
 * @author: ZSL
 * @date: 2024/12/18 10:30
 */
@RestController()
@RequestMapping("/call-back/modbus")
public class ModbusController {
    private final ModbusService modbusService;
    public ModbusController(ModbusService modbusService) {
        this.modbusService = modbusService;
    }
    /**
     * ICS滚筒线按钮判断滚筒线可继续执行
     */
    @PostMapping("/roller-line/can-release")
    public InvokeResult rollerLineCanRelease(@RequestBody JSONObject json) {
        Boolean result = this.modbusService.rollerLineCanRelease(json);
        return InvokeResult.success(result);
    }

    @PostMapping("/roller-line/close-task")
    public InvokeResult closeRollerLineTask(@RequestBody JSONObject json) {
         this.modbusService.closeRollerLineTask(json);
        return InvokeResult.success();
    }
}
