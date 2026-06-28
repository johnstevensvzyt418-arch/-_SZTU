package cn.edu.sztu.elevatormonitor.services.implementation;

import cn.edu.sztu.elevatormonitor.entity.ElevatorMessage;
import cn.edu.sztu.elevatormonitor.enums.DestFloorConstant;
import cn.edu.sztu.elevatormonitor.entity.repository.ElevatorMessageRepository;
import cn.edu.sztu.elevatormonitor.services.AlarmService;
import cn.edu.sztu.elevatormonitor.services.ElevatorService;
import cn.edu.sztu.elevatormonitor.services.HistoryPersistenceService;
import cn.edu.sztu.elevatormonitor.utils.GetTimeInSeconds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class XinShiDaService implements ElevatorService {
    private static final Logger LOGGER = LoggerFactory.getLogger(XinShiDaService.class);

    @Value("${push.endpoint.xinshida}")//配置文件 赋值
    private String xinShiDaServiceApi;

    private final ElevatorMessageRepository elevatorMessageRepository;
    private final HistoryPersistenceService historyPersistenceService;
    private final AlarmService alarmService;

    public XinShiDaService(ElevatorMessageRepository elevatorMessageRepository,
                           HistoryPersistenceService historyPersistenceService,
                           AlarmService alarmService) {
        this.elevatorMessageRepository = elevatorMessageRepository;
        this.historyPersistenceService = historyPersistenceService;
        this.alarmService = alarmService;
    }

    /** 协议格式: F + 19字节日期 + /8字节ID + 3×16字节HEX段 = 最少78字符 */
    private static final int XINSHIDA_MIN_LEN = 78;

    @Override
    public int uploadData(String data,String time ,String elevatorID) {
        // -------------------------------------------------------
        //  第一层: 空值 / 长度预校验
        // -------------------------------------------------------
        if (data == null || data.isEmpty()) {
            LOGGER.error("[XinShiDa] 报文为空, elevatorID={}", elevatorID);
            return -1;
        }
        if (data.length() < XINSHIDA_MIN_LEN) {
            LOGGER.error("[XinShiDa] 报文长度不足(期望>={}实际={}), data={}, elevatorID={}",
                    XINSHIDA_MIN_LEN, data.length(), data, elevatorID);
            return -2;
        }

        // -------------------------------------------------------
        //  第二层: 协议解析 (try-catch 保护)
        // -------------------------------------------------------
        try {
            // 每次请求新建独立的 ElevatorMessage，避免多电梯并发时的数据污染
            ElevatorMessage elevatorMessage = new ElevatorMessage();
            //        // data:F2020/06/18 09:26:58/00000001/4287000000000000  030a010000000000 0001020000020c00
            // 开关门信号
            String dss = data.substring(30, 46);
            // 内召信号
            String ics = data.substring(46, 62);
            // 楼层信号
            String fs = data.substring(62, 78);
            LOGGER.info("-----楼层信号:  "+fs);
            elevatorMessage.setDeviceId(data.substring(21, 29));
            // 开关门信号
            String doorStatus = "";
            switch (dss.substring(0, 2)) {
                case "41" : doorStatus = "01"; //开门到位
                    elevatorMessage.setDoorFlag(true);
                    break;
                case "42" : doorStatus = "00"; //关门到位
                    elevatorMessage.setDoorFlag(false);
                    break;
                case "43" :
                    if (elevatorMessage.isDoorFlag()) {
                        doorStatus = "02"; // 关门中
                    } else {
                        doorStatus = "03"; // 开门中
                    }
                    break;
                default:
                    LOGGER.error("[XinShiDa] 非法开关门信号 dss={}, data={}, elevatorID={}",
                            dss, data, elevatorID);
                    return -2;
            }
            // 设置门状态
            elevatorMessage.setDoorStatus(doorStatus);

            // 目标楼层
            DestFloorConstant destFloorConstant = DestFloorConstant.getDestFloorConstant(ics.charAt(5));
            elevatorMessage.setTargetFloor(destFloorConstant.destFloorDesc);

            // 运行方向
            String dit = fs.substring(6, 8);
            String direction;

            if (dit.matches("[01][0149c]")) {
                direction = "00"; // 平层
            } else if (dit.equals("02")) {
                direction = "01";  // 上行
            } else if (dit.equals("03")) {
                direction = "02"; // 下行
            } else {
                direction = "";
            }
            // 当前方向
            elevatorMessage.setDirection(direction);

            // 平层序列
            elevatorMessage.setLevelSeq(elevatorMessage.getLevelSeq() + fs.charAt(13));
            LOGGER.debug("平层序列为:{}", elevatorMessage.getLevelSeq());

            // 当前楼层
            String curFloor = fs.substring(4,6);
            if (!curFloor.equals(elevatorMessage.getCurrentFloor())) {
                // 判断平层故障
                String levelSeq = elevatorMessage.getLevelSeq();
                if (!levelSeq.contains("9")) {
                    LOGGER.debug("在{}楼发生平层不停车故障", curFloor);
                }
                elevatorMessage.setLevelSeq("");
            }
            elevatorMessage.setCurrentFloor(curFloor);

            // 更新及计算速度
            elevatorMessage.updateFloorAndDirectionAndTime(time);

            // 乘客
            elevatorMessage.setPassenger();

            //报警
            elevatorMessage.setMalfunction(GetTimeInSeconds.getSeconds(time));

            // 电梯状态默认为正常00
            elevatorMessage.setElevatorStatus("00");

            // 运行时间
            String date = data.substring(1, 20);
            if (elevatorMessage.getBeginTime() == 0) {
                elevatorMessage.setBeginTime(GetTimeInSeconds.getYMDSeconds(date));
            } else {
                elevatorMessage.setRuntime(date);
            }

            elevatorMessage.setDistance();
            elevatorMessage.setTimes();

            LOGGER.debug("[XinShiDa] 解析成功: {}", elevatorMessage);
            // 异步持久化历史数据 (不阻塞实时推送)
            historyPersistenceService.saveAsync(elevatorMessage, time);
            // 异步评估告警规则 (不阻塞实时推送)
            alarmService.evaluateAsync(elevatorMessage);
            return elevatorMessageRepository.sendToFrontEnd(elevatorMessage, xinShiDaServiceApi) ? 0 : 1;

        } catch (NumberFormatException e) {
            LOGGER.error("[XinShiDa] HEX解析失败(非法数值), data={}, time={}, elevatorID={}",
                    data, time, elevatorID, e);
            return -2;
        } catch (IndexOutOfBoundsException e) {
            LOGGER.error("[XinShiDa] 报文长度异常(索引越界), data={}, time={}, elevatorID={}",
                    data, time, elevatorID, e);
            return -2;
        } catch (RuntimeException e) {
            LOGGER.error("[XinShiDa] 协议解析未知异常, data={}, time={}, elevatorID={}",
                    data, time, elevatorID, e);
            return -2;
        }
    }
}
