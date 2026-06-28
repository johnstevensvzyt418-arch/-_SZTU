"""STEP 3 存储层验证脚本"""
import urllib.request
import json

# 3.1 验证 Redis — 通过 V2 Status API
print("=" * 60)
print("STEP 3.1 — Redis 存储验证 (via HTTP API)")
print("=" * 60)

# 测试之前日志中出现过的设备
test_devices = ["00000099", "00000030", "00000031", "00000041"]
for device_id in test_devices:
    try:
        url = f"http://127.0.0.1:10008/api/v2/status/{device_id}"
        r = urllib.request.urlopen(url, timeout=5)
        body = r.read().decode()
        print(f"[{device_id}] HTTP {r.status}: {body[:200]}")
    except Exception as e:
        print(f"[{device_id}] ERROR: {e}")

# 3.2 发送一个新请求到V2接口触发完整链路
print("\n" + "=" * 60)
print("STEP 3.2 — 发送新请求触发完整存储链路")
print("=" * 60)

import urllib.parse
data = urllib.parse.urlencode({
    "data": "AA5500C80000000000000000000101000001000000000000000000000700000005000000000000000000000000000000000000000000000000000A0000000000000000000000000000000000000000000064",
    "time": "21:00:00",
    "elevatorID": "VERIFY001"
}).encode()
try:
    r = urllib.request.urlopen("http://127.0.0.1:10008/api/v2/mnk", data, timeout=10)
    print(f"V2 MNK POST: HTTP {r.status}, Body: {r.read().decode()}")
except Exception as e:
    print(f"V2 MNK POST ERROR: {e}")

# 等待异步写入完成
import time
time.sleep(2)

# 3.3 读取刚写入的 Redis 数据
print("\n" + "=" * 60)
print("STEP 3.3 — 验证 Redis 新写入数据")
print("=" * 60)
try:
    url = "http://127.0.0.1:10008/api/v2/status/VERIFY001"
    r = urllib.request.urlopen(url, timeout=5)
    body = r.read().decode()
    print(f"[VERIFY001] HTTP {r.status}: {body}")
except Exception as e:
    print(f"[VERIFY001] ERROR: {e}")

print("\nDone.")
