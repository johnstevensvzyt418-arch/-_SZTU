package cn.edu.sztu.elevatormonitor.controller.api;

import cn.edu.sztu.elevatormonitor.services.implementation.MNKService;
import cn.edu.sztu.elevatormonitor.services.implementation.MoNaKeService;
import cn.edu.sztu.elevatormonitor.services.implementation.SightseeingService;
import cn.edu.sztu.elevatormonitor.services.implementation.XinShiDaService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 电梯数据上报入口，支持4种品牌的协议解析。
 * 返回码: 0 成功, 1 推送失败, -1 报文为空, -2 格式异常
 */
@RestController
@RequestMapping("api")
public class MonitorController {
    private static final Logger LOGGER = LoggerFactory.getLogger(MonitorController.class);

    private final XinShiDaService xinShiDaService;
    private final MoNaKeService moNaKeService;
    private final SightseeingService sightseeingService;
    private final MNKService mnkService;

    public MonitorController(XinShiDaService xinShiDaService, MoNaKeService moNaKeService,
                             SightseeingService sightseeingService, MNKService mnkService) {
        this.xinShiDaService = xinShiDaService;
        this.moNaKeService = moNaKeService;
        this.sightseeingService = sightseeingService;
        this.mnkService = mnkService;
    }

    /** 莫纳克电梯 */
    @PostMapping("monake")
    public int uploadMonake(@RequestParam String data,
                            @RequestParam String time,
                            @RequestParam String elevatorID) {
        LOGGER.debug("[Monake] 收到数据, elevatorID={}", elevatorID);
        return moNaKeService.uploadData(data, time, elevatorID);
    }

    /** 新时达电梯 */
    @PostMapping("xinshida")
    public int uploadXinshida(@RequestParam String data,
                              @RequestParam String time,
                              @RequestParam String elevatorID) {
        LOGGER.debug("[XinShiDa] 收到数据, elevatorID={}", elevatorID);
        return xinShiDaService.uploadData(data, time, elevatorID);
    }

    /** 观光电梯 */
    @PostMapping("sightseeing")
    public int uploadSightseeing(@RequestParam String data,
                                 @RequestParam String time,
                                 @RequestParam String elevatorID) {
        LOGGER.debug("[Sightseeing] 收到数据, elevatorID={}", elevatorID);
        return sightseeingService.uploadData(data, time, elevatorID);
    }

    /** MNK电梯 */
    @PostMapping("mnk")
    public int uploadMnk(@RequestParam String data,
                         @RequestParam String time,
                         @RequestParam String elevatorID) {
        LOGGER.debug("[MNK] 收到数据, elevatorID={}", elevatorID);
        return mnkService.uploadData(data, time, elevatorID);
    }
}
