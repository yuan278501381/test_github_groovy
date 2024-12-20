package com.chinajey.dwork.company.enums;

/**
 * @description: 拆箱
 * @author: ZSL
 * @date: 2024/12/18 14:58
 */
public enum WarehouseOutType {
    PRODUCTION_ISSUE("productionIssue","生产发料"),
    PRODUCTION_ISSUE_EXT("productionIssueExt","生产发料-外协"),
    SALES_DELIVER("salesDeliver","销售出库"),
    TRANSFER_OUT("transferOut","调拨出库"),
    PURCHASE_RETURN("purchaseReturn","采购退货"),
    PRODUCTION_REWORK("productionRework","生产返工"),
    OTHERS_OUTBOUND("othersOutbound","其他出库");

    private String code;
    private String name;

    private WarehouseOutType(String code, String name) {
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
