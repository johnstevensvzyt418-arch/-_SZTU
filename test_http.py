import urllib.request
import urllib.parse

url = "http://127.0.0.1:10008/api/v2/mnk"
data = {
    "data": "AA5500C80000000000000000000101000001000000000000000000000700000005000000000000000000000000000000000000000000000000000A0000000000000000000000000000000000000000000064",
    "time": "20:30:00",
    "elevatorID": "TEST001"
}
encoded = urllib.parse.urlencode(data).encode()
try:
    r = urllib.request.urlopen(url, encoded, timeout=10)
    print(f"HTTP_STATUS: {r.status}")
    print(f"RESPONSE_BODY: {r.read().decode()}")
except Exception as e:
    print(f"ERROR: {e}")
