package cn.edu.sztu.elevatormonitor.services.implementation;

import cn.edu.sztu.elevatormonitor.entity.ElevatorMessage;
import cn.edu.sztu.elevatormonitor.entity.repository.ElevatorMessageRepository;
import cn.edu.sztu.elevatormonitor.services.AlarmService;
import cn.edu.sztu.elevatormonitor.services.ElevatorService;
import cn.edu.sztu.elevatormonitor.services.HistoryPersistenceService;
import cn.edu.sztu.elevatormonitor.utils.GetTimeInSeconds;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;


@Service
public class MoNaKeService implements ElevatorService {

    @Value("${push.endpoint.monake}")
    private String moNaKeApi;
    private final ElevatorMessageRepository elevatorMessageRepository;
    private final HistoryPersistenceService historyPersistenceService;
    private final AlarmService alarmService;
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MoNaKeService.class);

    public MoNaKeService(ElevatorMessageRepository elevatorMessageRepository,
                         HistoryPersistenceService historyPersistenceService,
                         AlarmService alarmService) {
        this.elevatorMessageRepository = elevatorMessageRepository;
        this.historyPersistenceService = historyPersistenceService;
        this.alarmService = alarmService;
    }

    /** 协议要求: 报文至少3个逗号分隔的HEX字段; 时间格式 hh:mm:ss 至少8字符 */
    private static final int MONAKE_MIN_FIELDS = 3;
    private static final int MONAKE_TIME_MIN_LEN = 8;

    @Override
    public int uploadData(String data,String time,String elevatorID) {
        // -------------------------------------------------------
        //  第一层: 空值 / 长度预校验
        // -------------------------------------------------------
        if (data == null || data.isEmpty()) {
            LOGGER.error("[MoNaKe] 报文为空, elevatorID={}", elevatorID);
            return -1;
        }
        if (time == null || time.length() < MONAKE_TIME_MIN_LEN) {
            LOGGER.error("[MoNaKe] 时间格式异常(time过短或null), data={}, time={}, elevatorID={}",
                    data, time, elevatorID);
            return -2;
        }
        String[] sData = data.split(",");
        if (sData.length < MONAKE_MIN_FIELDS) {
            LOGGER.error("[MoNaKe] 报文字段不足(期望>={}实际={}), data={}, elevatorID={}",
                    MONAKE_MIN_FIELDS, sData.length, data, elevatorID);
            return -2;
        }

        // -------------------------------------------------------
        //  第二层: 协议解析 (try-catch 保护)
        // -------------------------------------------------------
        try {
            // 每次请求新建独立的 ElevatorMessage，避免多电梯并发时的数据污染
            ElevatorMessage elevatorMessage = new ElevatorMessage();
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
            Calendar calendar=Calendar.getInstance(TimeZone.getTimeZone("GMT+08:00"));

            int hour=Integer.parseInt(time.substring(0,2));
            int minute=Integer.parseInt(time.substring(3,5));
            int second=Integer.parseInt(time.substring(6,8));
            calendar.set(Calendar.HOUR,hour);
            calendar.set(Calendar.MINUTE,minute);
            calendar.set(Calendar.SECOND,second);
            int cloudYear=calendar.get(Calendar.YEAR);
            int cloudMonth=calendar.get(Calendar.MONTH)+1;
            int cloudDate=calendar.get(Calendar.DATE);
            int cloudHour=calendar.get(Calendar.HOUR_OF_DAY);

            int doorAndMove=Integer.parseInt(sData[0],16);//开门和上下行逻辑
            String doorStatus = "";

            // 开关门信号
            switch (doorAndMove&0xF0){
                case 0x00:
                    doorStatus="00";elevatorMessage.setDoorFlag(false);
                    break;//关门到位
                case 0xC0:
                    doorStatus="01";elevatorMessage.setDoorFlag(true);
                    break;//开门到位
                case 0x40:
                    doorStatus="02";break;//关门中
                case 0x80:
                    doorStatus="03";break;// 开门中
                default:
                    LOGGER.error("[MoNaKe] 非法开关门信号 0x{}, data={}, elevatorID={}",
                            Integer.toHexString(doorAndMove&0xF0), data, elevatorID);
                    return -2;
            }
            elevatorMessage.setDoorStatus(doorStatus);

            //运行信号
            String direction;
            switch (doorAndMove&0x0F){
                case 0x05://上行
                    direction="01";elevatorMessage.setDoorFlag(false);
                    break;
                case 0x08://下行
                    direction="02";elevatorMessage.setDoorFlag(true);
                    break;
                case 0x00://静止
                    direction="00";
                    break;
                default:
                    LOGGER.error("[MoNaKe] 非法运行方向信号 0x{}, data={}, elevatorID={}",
                            Integer.toHexString(doorAndMove&0x0F), data, elevatorID);
                    return -2;
            }
            elevatorMessage.setDirection(direction);

            calendar.set(cloudYear,cloudMonth,cloudDate,cloudHour,minute,second);


            int ics;
            //内招信号
            if(elevatorID.equals("2"))
                if((Integer.parseInt(sData[1],16)&0x10)==0x10)
                    ics=0x01;
                else ics=0x00;
            else
                ics = Integer.parseInt(sData[1],16);

            LOGGER.debug("ics"+ics);
            LOGGER.debug("d1"+sData[1]+Integer.parseInt(sData[1],16));
            if(sData[1].equals("01"))
            {
                elevatorMessage.setTargetFloor("有");//暂时有内招信号 等于有目标楼层
                elevatorMessage.setPassenger();//有内招认为有乘客
            }
            else
            {   elevatorMessage.setTargetFloor("无");
                elevatorMessage.setPassenger();//TODO:需要等内招信号进一步确定以后再修改
            }


            String curFloor;
            //楼层信号
            curFloor = sData[2];
            //sData[1] 转为16进制字符
            if(curFloor.equals("01"))
            {
                curFloor="B2";
            }
            else if(curFloor.equals("02"))
            {
                curFloor="B1";
            }
            else
            {
                curFloor = Integer.toString(Integer.parseInt(curFloor,16)-2);//16进制转换
            }

            elevatorMessage.setCurrentFloor(curFloor);

            // 将电梯(STM32板子ID)填入Message
            elevatorMessage.setDeviceId(elevatorID);

            // 更新及计算速度
            elevatorMessage.updateFloorAndDirectionAndTime(time);

            // 报警
             elevatorMessage.setMalfunction(GetTimeInSeconds.getSeconds(time));

            if (elevatorMessage.getPassenger().equals("01"))
                elevatorMessage.setTargetFloor("有");//暂时有内招信号 等于有目标楼层
            else
            {   elevatorMessage.setTargetFloor("无");}

            if (elevatorMessage.getBeginTime() == 0) {
                Calendar calendar1=Calendar.getInstance(TimeZone.getTimeZone("GMT+08:00"));
                elevatorMessage.setBeginTime(calendar1.getTime().getTime()/1000);
            } else {
                elevatorMessage.setRuntime(calendar);
            }

            elevatorMessage.setDistance();
            elevatorMessage.setTimes();

            // 电梯状态默认为正常00
            elevatorMessage.setElevatorStatus("00");

            LOGGER.debug("[MoNaKe] 解析成功: {}", elevatorMessage);
            // 异步持久化历史数据 (不阻塞实时推送)
            historyPersistenceService.saveAsync(elevatorMessage, time);
            // 异步评估告警规则 (不阻塞实时推送)
            alarmService.evaluateAsync(elevatorMessage);
            return elevatorMessageRepository.sendToFrontEnd(elevatorMessage, moNaKeApi) ? 0 : 1;

        } catch (NumberFormatException e) {
            LOGGER.error("[MoNaKe] HEX解析失败(非法数值), data={}, time={}, elevatorID={}",
                    data, time, elevatorID, e);
            return -2;
        } catch (IndexOutOfBoundsException e) {
            LOGGER.error("[MoNaKe] 报文长度异常(索引越界), data={}, time={}, elevatorID={}",
                    data, time, elevatorID, e);
            return -2;
        } catch (RuntimeException e) {
            LOGGER.error("[MoNaKe] 协议解析未知异常, data={}, time={}, elevatorID={}",
                    data, time, elevatorID, e);
            return -2;
        }
    }


}
