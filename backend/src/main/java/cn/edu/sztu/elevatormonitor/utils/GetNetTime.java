package cn.edu.sztu.elevatormonitor.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;

public class GetNetTime {
    private static final Logger LOGGER = LoggerFactory.getLogger(GetNetTime.class);

    public static String getNetworkTime(String webUrl) {
        try {
            URL url = new URL(webUrl);
            URLConnection conn = url.openConnection();
            conn.connect();
            long dateL = conn.getDate();
            Date date = new Date(dateL);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            return dateFormat.format(date);
        } catch (IOException e) {
            LOGGER.error("[GetNetTime] 获取网络时间失败, webUrl={}", webUrl, e);
        }
        return "";
    }
}

