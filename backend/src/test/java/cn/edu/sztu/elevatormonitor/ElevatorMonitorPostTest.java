package cn.edu.sztu.elevatormonitor;

import cn.edu.sztu.elevatormonitor.utils.TimeDiff;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;

public class ElevatorMonitorPostTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElevatorMonitorPostTest.class);

    @Resource
    private RestTemplate restTemplate=new RestTemplate();

    @Test
    void tempSimple(){
        String time = "18:00:40.888";
        String time2 = "20:00:45.055";
        LOGGER.info("TimeDiff: {}", TimeDiff.getSecondDiff(time,time2));
    }
}
