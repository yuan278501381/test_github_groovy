package com.chinajey.dwork.company.modules.modbus.service;

import com.alibaba.fastjson.JSONObject;
import com.aspose.slides.internal.my.ask;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajey.dwork.company.modules.modbus.enums.UnBoxingPassBox;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @description:
 * @author: ZSL
 * @date: 2024/12/18 10:34
 */
@Service
public class ModbusService {

    private final BmfService bmfService;

    public ModbusService(BmfService bmfService) {
        this.bmfService = bmfService;
    }

    public boolean rollerLineCanRelease(JSONObject jsonObject) {
        boolean check = check(jsonObject);
        if (!check) {
            return false;
        }

        BmfObject rollerLineFrameTask = findRollerLineFrameTask(jsonObject);
        // 如果没任务，不滚动
        if (rollerLineFrameTask == null) {
            return false;
        }
        // 是否拆箱
        String unboxing = rollerLineFrameTask.getString("ext_unboxing");
        // 不拆箱任务，返回true，并且完成该任务
        if (UnBoxingPassBox.NOT_REQUIRED.getCode().equals(unboxing)) {
            rollerLineFrameTask.put("logisticsStatus", "3");
            bmfService.updateByPrimaryKeySelective(rollerLineFrameTask);
            return true;
        } else if (UnBoxingPassBox.UN_BOXING.getCode().equals(unboxing)) {
            List<BmfObject> passBoxes = rollerLineFrameTask.getAndRefreshList("passBoxes");
            // 判断是否扫入新周转箱 如果没扫箱 不滚动
            if (CollectionUtils.isEmpty(passBoxes)) {
                return false;
            } else {
                rollerLineFrameTask.put("logisticsStatus", "3");
                bmfService.updateByPrimaryKeySelective(rollerLineFrameTask);
                return true;
            }
        } else {
            return false;
        }
    }

    private BmfObject findRollerLineFrameTask(JSONObject jsonObject) {
        Map<String, Object> params = new HashMap<>();
        params.put("passBoxCode", jsonObject.getString("passBoxCode"));
        params.put("logisticsStatus", "1");
        return bmfService.findOne("CT1121", params);
    }

    private boolean check(JSONObject jsonObject) {
        boolean checkResult = true;
        String passBoxCode = jsonObject.getString("passBoxCode");
        if (StringUtils.isBlank(passBoxCode)) {
            return false;
        }
        return checkResult;
    }

    public void closeRollerLineTask(JSONObject jsonObject) {
        boolean check = check(jsonObject);
        if (!check) {
            return;
        }
        BmfObject rollerLineFrameTask = findRollerLineFrameTask(jsonObject);
        rollerLineFrameTask.put("logisticsStatus", "3");
        bmfService.updateByPrimaryKeySelective(rollerLineFrameTask);
    }
}
