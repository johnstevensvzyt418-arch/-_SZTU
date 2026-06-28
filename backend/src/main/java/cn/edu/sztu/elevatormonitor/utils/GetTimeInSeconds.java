package cn.edu.sztu.elevatormonitor.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class GetTimeInSeconds {
    private static final Logger LOGGER = LoggerFactory.getLogger(GetTimeInSeconds.class);

    /** 获取时间字符串对应的秒数(HH:mm:ss) */
    public static long getSeconds(String s) {
        try {
            Date date = new SimpleDateFormat("HH:mm:ss").parse(s);
            return date.getTime() / 1000;
        } catch (ParseException e) {
            LOGGER.error("[GetTimeInSeconds] 时间解析失败(HH:mm:ss), s={}", s, e);
            return 0;
        }
    }

    /** 获取日期时间字符串对应的秒数(yyyy/MM/dd HH:mm:ss) */
    public static long getYMDSeconds(String s) {
        try {
            Date date = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").parse(s);
            return date.getTime() / 1000;
        } catch (ParseException e) {
            LOGGER.error("[GetTimeInSeconds] 时间解析失败(yyyy/MM/dd HH:mm:ss), s={}", s, e);
            return 0;
        }
    }

    // 将秒数转为天&小时&分钟&秒
    public static String formatDateTime(long mss) {
        String DateTimes = null;
        long days = mss / ( 60 * 60 * 24);
        long hours = (mss % ( 60 * 60 * 24)) / (60 * 60);
        long minutes = (mss % ( 60 * 60)) /60;
        long seconds = mss % 60;
        if(days>0){
            DateTimes= days + "天" + hours + "小时" + minutes + "分钟"
                    + seconds + "秒";
        }else if(hours>0){
            DateTimes=hours + "小时" + minutes + "分钟"
                    + seconds + "秒";
        }else if(minutes>0){
            DateTimes=minutes + "分钟"
                    + seconds + "秒";
        }else{
            DateTimes=seconds + "秒";
        }

        return DateTimes;
    }
}
