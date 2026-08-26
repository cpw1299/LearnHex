package com.cpw.enums;


/**
 * 0x1003        CCU_DDU
 * 0x1004        CCU_DDU(Flt)
 * 0x1007        CCU_ERM
 * 0x1009        CCU_WXCS
 * 0x1011        CCU_RIOM
 * 0x1020        CCU_TCU
 * 0x1034        CCU_TBAT1
 * 0x1035        CCU_TBAT2
 * 0x1036        CCU_TBAT3
 * 0x1037        CCU_TBAT4
 * 0x2003        DDU_CCU
 * 0x2007        ERM_CCU
 * 0x2009        WXCS_CCU
 * 0x2011        RIOM_CCU
 * 0x2020        TCU_CCU
 * 0x2034        TBAT1_CCU
 * 0x2035        TBAT2_CCU
 * 0x2036        TBAT3_CCU
 * 0x2037        TBAT4_CCU
 */
public enum ComIdProtocolEnum {

    COM_ID_0x1003("0x1003", "CCU", "DDU", "CCU_DDU"),
    COM_ID_0x1004("0x1004", "CCU", "DDU(Flt)", "CCU_DDU(Flt)"),
    COM_ID_0x1007("0x1007", "CCU", "ERM", "CCU_ERM"),
    COM_ID_0x1009("0x1009", "CCU", "WXCS", "CCU_WXCS"),
    COM_ID_0x1011("0x1011", "CCU", "RIOM", "CCU_RIOM"),
    COM_ID_0x1020("0x1020", "CCU", "TCU", "CCU_TCU"),
    COM_ID_0x1034("0x1034", "CCU", "TBAT1", "CCU_TBAT1"),
    COM_ID_0x1035("0x1035", "CCU", "TBAT2", "CCU_TBAT2"),
    COM_ID_0x1036("0x1036", "CCU", "TBAT3", "CCU_TBAT3"),
    COM_ID_0x1037("0x1037", "CCU", "TBAT4", "CCU_TBAT4"),


    COM_ID_0x2003("0x2003", "DDU", "CCU", "DDU_CCU"),
    COM_ID_0x2007("0x2007", "ERM", "CCU", "ERM_CCU"),
    COM_ID_0x2009("0x2009", "WXCS", "CCU", "WXCS_CCU"),
    COM_ID_0x2011("0x2011", "RIOM", "CCU", "RIOM_CCU"),
    COM_ID_0x2020("0x2020", "TCU", "CCU", "TCU_CCU"),
    COM_ID_0x2034("0x2034", "TBAT1", "CCU", "TBAT1_CCU"),
    COM_ID_0x2035("0x2035", "TBAT2", "CCU", "TBAT2_CCU"),
    COM_ID_0x2036("0x2036", "TBAT3", "CCU", "TBAT3_CCU"),
    COM_ID_0x2037("0x2037", "TBAT7", "CCU", "TBAT4_CCU"),


    ;

    /**
     * ComID(Hex)
     */
    private String comId;
    /**
     * 源_设备
     */
    private String source;
    /**
     * 宿_设备
     */
    private String host;
    /**
     * 协议名称
     */
    private String protocolName;


    ComIdProtocolEnum(String comId, String source, String host, String protocolName) {
        this.comId = comId;
        this.source = source;
        this.host = host;
        this.protocolName = protocolName;
    }
}
