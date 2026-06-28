package cn.edu.sztu.elevatormonitor;

import cn.edu.sztu.elevatormonitor.entity.Elevator;
import cn.edu.sztu.elevatormonitor.utils.ExcelReader;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

@SpringBootTest
class ElevatorMonitorApplicationTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElevatorMonitorApplicationTests.class);

    @Test
    void contextLoads() {
        String dss = "ff";
        short dssInt = Short.parseShort(dss, 16);
        int dss2 = dssInt & 0xff;
        LOGGER.info("dss2={}, dssInt={}", dss2, dssInt);

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+08:00"));
        LOGGER.info("Real NetTime is {}-{}-{} {}:{}:{}",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DATE),
                calendar.get(Calendar.HOUR),
                calendar.get(Calendar.MINUTE),
                calendar.get(Calendar.SECOND));
    }

    @Test
    public void rtPostObject() throws IOException {
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://127.0.0.1:10008/api/xinshida";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();

        HttpEntity<MultiValueMap<String, String>> request;
        ResponseEntity<String> response;

        ExcelReader excelReader = new ExcelReader();
        List<Elevator> dataList = excelReader.read("E:\\testData\\1-7.xlsx");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        int i = 0;
        for (Elevator data : dataList) {
            map.add("data", data.getData());
            LOGGER.info("发送数据: {}", data.getData());
            request = new HttpEntity<>(map, headers);
            response = restTemplate.postForEntity(url, request, String.class);

            map.remove("data");
            try {
                Thread.sleep(500);
                LOGGER.info("{} --循环执行第{}次", sdf.format(new Date()), i++);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("线程被中断", e);
            }
        }
    }
}
