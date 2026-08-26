package com.cpw.enums;

public enum YesOrNoEnum {

    NO(0, "No"),
    YES(1, "Yes")
    ;


    private final int code;
    private final String name;

    YesOrNoEnum(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public int getCode() {
        return this.code;
    }

    public String getName() {
        return this.name;
    }

    public String getStringCode() {
        return String.valueOf(this.code);
    }

}
