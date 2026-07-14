#!/usr/bin/env python3
# ============================================================
# Elevator Monitor — MQTT 桥接器
# 订阅远程 EMQX Broker 的 /Elevator 主题，转发到后端 HTTP API
#
# 环境变量:
#   MQTT_BROKER_URL    - MQTT Broker 地址 (默认: tcp.sealosbja.site)
#   MQTT_BROKER_PORT   - MQTT Broker 端口 (默认: 35205)
#   MQTT_USERNAME      - MQTT 用户名 (默认: admin)
#   MQTT_PASSWORD      - MQTT 密码 (默认: SZTUbdi@1005)
#   BACKEND_URL        - 后端 HTTP API (默认: http://localhost:10008/api/v2/mnk)
# ============================================================

import os
import sys
import time
import urllib.request
import urllib.parse
import urllib.error

try:
    import paho.mqtt.client as mqtt
except ImportError:
    print("请先安装 paho-mqtt: pip install paho-mqtt")
    sys.exit(1)

# ---- 从环境变量读取配置 ----
BROKER_URL = os.getenv("MQTT_BROKER_URL", "tcp.sealosbja.site")
BROKER_PORT = int(os.getenv("MQTT_BROKER_PORT", "35205"))
MQTT_USERNAME = os.getenv("MQTT_USERNAME", "admin")
MQTT_PASSWORD = os.getenv("MQTT_PASSWORD", "SZTUbdi@1005")
BACKEND_URL = os.getenv("BACKEND_URL", "http://localhost:10008/api/v2/mnk")
TOPIC = "/Elevator"

print(f"[Bridge] 配置:")
print(f"  MQTT Broker: {BROKER_URL}:{BROKER_PORT}")
print(f"  MQTT Topic:  {TOPIC}")
print(f"  Backend URL: {BACKEND_URL}")
print(f"  MQTT User:   {MQTT_USERNAME}")


def on_connect(client, userdata, flags, rc, props):
    if rc == 0:
        print(f"[Bridge] ✅ 已连接到 MQTT Broker (rc={rc})")
        client.subscribe(TOPIC, qos=1)
        print(f"[Bridge] 已订阅主题: {TOPIC}")
    else:
        print(f"[Bridge] ❌ 连接失败 (rc={rc})，5秒后自动重连...")


def on_disconnect(client, userdata, flags, rc, props):
    if rc != 0:
        print(f"[Bridge] ⚠️ 意外断开 (rc={rc})，将自动重连...")


def on_message(client, userdata, msg):
    try:
        payload = msg.payload.decode().strip()
        print(f"[Bridge] 📩 收到报文 ({len(payload)}字符): {payload}")

        # 从报文中提取 deviceId (偏移21-28) 和设备时间 (偏移12-19)
        device_id = payload[21:29] if len(payload) >= 29 else "unknown"
        device_time = payload[12:20] if len(payload) >= 20 else time.strftime("%H:%M:%S")

        # 转发到后端 HTTP API
        data = urllib.parse.urlencode({
            "data": payload,
            "time": device_time,
            "elevatorID": device_id,
        }).encode()

        req = urllib.request.Request(BACKEND_URL, data=data, method="POST")
        with urllib.request.urlopen(req, timeout=5) as resp:
            result = resp.read().decode()
            print(f"[Bridge] device={device_id} → HTTP {result}")

    except urllib.error.URLError as e:
        print(f"[Bridge] device={device_id} → HTTP ERROR: {e}")
    except Exception as e:
        print(f"[Bridge] ❌ 处理消息异常: {e}")


def main():
    # 使用 MQTTv5 协议 + VERSION2 回调 API
    client = mqtt.Client(
        client_id="elevator-bridge",
        protocol=mqtt.MQTTv5,
        callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
    )
    client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)

    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    client.on_message = on_message

    # 自动重连配置
    client.reconnect_delay_set(min_delay=1, max_delay=30)

    print(f"[Bridge] 正在连接到 {BROKER_URL}:{BROKER_PORT} ...")
    client.connect(BROKER_URL, BROKER_PORT, 60)

    # 阻塞运行，自动重连
    client.loop_forever()


if __name__ == "__main__":
    main()
