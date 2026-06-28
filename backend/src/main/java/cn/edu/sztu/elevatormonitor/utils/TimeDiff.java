package cn.edu.sztu.elevatormonitor.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TimeDiff {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimeDiff.class);

    public static float getSecondDiff(String t1, String t2) {
        float seconds = 0;
        try {
            Date date1 = new SimpleDateFormat("HH:mm:ss.SSS").parse(t1);
            Date date2 = new SimpleDateFormat("HH:mm:ss.SSS").parse(t2);
            seconds = (date2.getTime() - date1.getTime()) / 1000.f;
        } catch (ParseException e) {
            LOGGER.error("[TimeDiff] 时间解析失败, t1={}, t2={}", t1, t2, e);
        }
        return seconds;
    }
}
