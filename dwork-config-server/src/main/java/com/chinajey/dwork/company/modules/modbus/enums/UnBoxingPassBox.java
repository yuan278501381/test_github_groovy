package com.chinajey.dwork.company.modules.modbus.enums;

/**
 * @description: 拆箱
 * @author: ZSL
 * @date: 2024/12/18 14:58
 */
public enum UnBoxingPassBox {
    UN_BOXING("unBoxing", "待拆箱"),
    NOT_REQUIRED("notRequired", "无需拆箱");

    private String code;
    private String name;

    private UnBoxingPassBox(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return this.code;
    }

    public String getName() {
        return this.name;
    }
}
