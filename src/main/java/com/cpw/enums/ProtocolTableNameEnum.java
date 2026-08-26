package com.cpw.enums;

/**
 * 协议表名称
 * 规则：cha2c1_status_data_{modelCode}_{locoNo}_{ab}_{xylx}
 * modelCode：车辆型号，locomotive_registration.model_code字段
 * locoNo：机车车号，locomotive_registration.locomotive_number字段
 * ab：业务上只需要a
 * xylx：协议，例如：ccu_ddu
 */
public enum ProtocolTableNameEnum {

    BMS12_CCU("cha2c1_status_data_%s_%s_a_bms12_ccu"),
    BMS34_CCU("cha2c1_status_data_%s_%s_a_bms34_ccu"),
    TCU1_CCU("cha2c1_status_data_%s_%s_a_tcu1_ccu"),
    TCU2_CCU("cha2c1_status_data_%s_%s_a_tcu2_ccu"),
    ;

    private final String tableName;

    ProtocolTableNameEnum(String tableName) {
        this.tableName = tableName;
    }

    public String getTableName() {
        return this.tableName;
    }

    public String getTableName(String modelCode, String locoNo) {
        return String.format(this.tableName, modelCode, locoNo);
    }

}
