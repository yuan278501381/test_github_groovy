package com.chinajey.dwork.company.enums;

/**
 * @description: 入库类型
 * @author: ZSL
 * @date: 2024/12/18 14:58
 */
public enum WarehouseInTypeEnums {
    PURCHASE_DELIVER("purchaseDeliver","采购收货"),
    PURCHASE_DELIVER_EXT("purchaseDeliverExt","采购收货-外协"),
    PRODUCTION_RETURN("productionReturn","生产退料"),
    PRODUCTION_COMPLETE("productionComplete","生产完工入库"),
    SALES_RETURN("salesReturn","销售退货"),
    TRANSFER_IN("transferIn","调拨入库"),
    OTHERS_INBOUND("othersInbound","其他入库");

    private String code;
    private String name;

    private WarehouseInTypeEnums(String code, String name) {
        this.code = code;
        this.name = name;
    }
    //获取入库类型枚举所有code集合
    public static String[] getCodes() {
        WarehouseInTypeEnums[] values = WarehouseInTypeEnums.values();
        String[] codes = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            codes[i] = values[i].getCode();
        }
        return codes;
    }

    public String getCode() {
        return this.code;
    }

    public String getName() {
        return this.name;
    }

}
