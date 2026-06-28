package cn.edu.sztu.elevatormonitor.services.implementation;

import cn.edu.sztu.elevatormonitor.entity.ElevatorMessage;
import cn.edu.sztu.elevatormonitor.entity.repository.ElevatorMessageRepository;
import cn.edu.sztu.elevatormonitor.services.AlarmService;
import cn.edu.sztu.elevatormonitor.services.ElevatorService;
import cn.edu.sztu.elevatormonitor.services.HistoryPersistenceService;
import cn.edu.sztu.elevatormonitor.utils.GetTimeInSeconds;
import cn.edu.sztu.elevatormonitor.utils.SightseeingTargetFloor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SightseeingService implements ElevatorService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SightseeingService.class);

    @Value("${push.endpoint.sightseeing}")
    private String sightseeingApi;

    private final ElevatorMessageRepository elevatorMessageRepository;
    private final HistoryPersistenceService historyPersistenceService;
    private final AlarmService alarmService;

    public SightseeingService(ElevatorMessageRepository elevatorMessageRepository,
                              HistoryPersistenceService historyPersistenceService,
                              AlarmService alarmService) {
        this.elevatorMessageRepository = elevatorMessageRepository;
        this.historyPersistenceService = historyPersistenceService;
        this.alarmService = alarmService;
    }

    /** 协议格式: F + 19字节日期 + /8字节ID + 3×16字节HEX段 = 最少78字符 */
    private static final int SIGHTSEEING_MIN_LEN = 78;

    @Override
    public int uploadData(String data,String time ,String elevatorID) {
        // -------------------------------------------------------
        //  第一层: 空值 / 长度预校验
        // -------------------------------------------------------
        if (data == null || data.isEmpty()) {
            LOGGER.error("[Sightseeing] 报文为空, elevatorID={}", elevatorID);
            return -1;
        }
        if (data.length() < SIGHTSEEING_MIN_LEN) {
            LOGGER.error("[Sightseeing] 报文长度不足(期望>={}实际={}), data={}, elevatorID={}",
                    SIGHTSEEING_MIN_LEN, data.length(), data, elevatorID);
            return -2;
        }

        // -------------------------------------------------------
        //  第二层: 协议解析 (try-catch 保护)
        // -------------------------------------------------------
        try {
            // 每次请求新建独立的实例，避免多电梯并发时的数据污染
            ElevatorMessage elevatorMessage = new ElevatorMessage();
            SightseeingTargetFloor sightseeingTargetFloor = new SightseeingTargetFloor();
            // 开关门信号
            String dss = data.substring(30, 46).toLowerCase();
            // 内召信号
            String ics = data.substring(46, 62).toLowerCase();
            // 楼层信号
            String fs = data.substring(62, 78).toLowerCase();

            elevatorMessage.setDeviceId(data.substring(21, 29));
            // 开关门信号
            String doorStatus = "";
            switch (dss.substring(10, 12)) {
                case "02" : doorStatus = "01"; //开门到位
                    elevatorMessage.setDoorFlag(true);
                    break;
                case "01" : doorStatus = "00"; //关门到位
                    elevatorMessage.setDoorFlag(false);
                    break;
                case "00" : // 开关门中
                    if (elevatorMessage.isDoorFlag()) {
                        doorStatus = "02"; // 关门中
                    } else {
                        doorStatus = "03"; // 开门中
                    }
                    break;
                default:
                    LOGGER.error("[Sightseeing] 非法开关门信号 dss={}, data={}, elevatorID={}",
                            dss, data, elevatorID);
                    return -2;
            }
            // 设置门状态
            elevatorMessage.setDoorStatus(doorStatus);

            // 目标楼层
            elevatorMessage.setTargetFloor(sightseeingTargetFloor.setTF(ics.substring(0, 6)));

            // 运行方向
            String dit = dss.substring(2, 4); // 上下行的字节：02 => 上行  03 => 下行
            String direction;
            if (dit.equals("10") || dit.equals("08") || dit.equals("00") ) {
                direction = "00"; // 平层
            } else if (dit.equals("12")) {
                direction = "01";  // 上行
            } else if (dit.equals("0a")) {
                direction = "02"; // 下行
            } else {
                direction = "";
            }
            // 当前方向
            elevatorMessage.setDirection(direction);

            // 当前楼层

            String curFloor;
            if (fs.matches("0001[0-9a-z]{12}")) {
                String curFloorDesc = fs.substring(14, 16);
                if (curFloorDesc.equals("17") || curFloorDesc.equals("18")) {
                    curFloor = "21";
                } else if (curFloorDesc.equals("01")) {
                    curFloor = "-2";
                } else if (curFloorDesc.equals("02")) {
                    curFloor = "-1";
                } else {
                    Integer decimal = Integer.valueOf(curFloorDesc, 16);
                    if (decimal < 12) {
                        curFloor = "0" + (decimal - 2);
                    } else {
                        curFloor = "" + (decimal - 2);
                    }
                }
            } else {
                LOGGER.error("[Sightseeing] 楼层信号格式异常 fs={}, data={}, elevatorID={}",
                        fs, data, elevatorID);
                return -2;
            }
            elevatorMessage.setCurrentFloor(curFloor);

            // 更新及计算速度
            elevatorMessage.updateFloorAndDirectionAndTime(time);

            // 乘客
            elevatorMessage.setPassenger();

            //报警
            elevatorMessage.setMalfunction(GetTimeInSeconds.getSeconds(time));

            // 运行时间
            String date = data.substring(1, 20);
            if (elevatorMessage.getBeginTime() == 0) {
                elevatorMessage.setBeginTime(GetTimeInSeconds.getYMDSeconds(date));
            } else {
                elevatorMessage.setRuntime(date);
            }

            elevatorMessage.setDistance();
            elevatorMessage.setTimes();

            // 电梯状态默认为正常00
            elevatorMessage.setElevatorStatus("00");

            LOGGER.debug("[Sightseeing] 解析成功: {}", elevatorMessage);
            // 异步持久化历史数据 (不阻塞实时推送)
            historyPersistenceService.saveAsync(elevatorMessage, time);
            // 异步评估告警规则 (不阻塞实时推送)
            alarmService.evaluateAsync(elevatorMessage);
            return elevatorMessageRepository.sendToFrontEnd(elevatorMessage, sightseeingApi) ? 0 : 1;

        } catch (NumberFormatException e) {
            LOGGER.error("[Sightseeing] HEX解析失败(非法数值), data={}, time={}, elevatorID={}",
                    data, time, elevatorID, e);
            return -2;
        } catch (IndexOutOfBoundsException e) {
            LOGGER.error("[Sightseeing] 报文长度异常(索引越界), data={}, time={}, elevatorID={}",
                    data, time, elevatorID, e);
            return -2;
        } catch (RuntimeException e) {
            LOGGER.error("[Sightseeing] 协议解析未知异常, data={}, time={}, elevatorID={}",
                    data, time, elevatorID, e);
            return -2;
        }
    }
}
