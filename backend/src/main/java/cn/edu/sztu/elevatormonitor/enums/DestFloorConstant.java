package cn.edu.sztu.elevatormonitor.enums;

public enum DestFloorConstant {
    // 内招信号
    // 无内召信号
    Null_Floor('0', "无"),
    // 有内召信号
    First_Floor('1', "1"),
    Second_Floor('2', "2"),
    Third_Floor('4', "3"),
    Fourth_Floor('8', "4"),

    FS_Floor('3', "1、2"),//二的零次方+二的一次方=3
    FT_Floor('5', "1、3"),//二的零次方+二的二次方=5 以此类推
    FF_Floor('9', "1、4"),
    ST_Floor('6', "2、3"),
    SF_Floor('a', "2、4"),
    TF_Floor('c', "3、4"),

    FST_Floor('7', "1、2、3"),
    FSF_Floor('b', "1、2、4"),
    FTF_Floor('d', "1、3、4"),
    STF_Floor('e', "2、3、4")

    ;

    public final char floor; // 字符串中的楼层标识字符
    public final String destFloorDesc; // 目标楼层

    DestFloorConstant(char floor, String destFloorDesc) {
        this.floor = floor;
        this.destFloorDesc = destFloorDesc;
    }

    public static DestFloorConstant getDestFloorConstant(char floor) {
        for (DestFloorConstant destFloorConstant : DestFloorConstant.values()) {
            if (destFloorConstant.floor == floor) {
                return destFloorConstant;
            }
        }
        throw new RuntimeException("错误 floor");
    }
}
