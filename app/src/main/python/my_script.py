import datetime

import requests


def to_timestamp_ms(s: str, fmt: str = "%Y-%m-%d %H:%M:%S"):
    dt = datetime.datetime.strptime(s, fmt)
    return int(dt.timestamp() * 1000)


def his_log(deviceid, from_time, to_time, base_url, token, session_obj=None):
    sess = session_obj or requests.Session()
    history_data = []
    total = 0
    headers = {
        "accept": "application/json",
        "Authorization": token,
    }
    history_url = f"{base_url.rstrip('/')}/v1/devices/history"
    for current in range(1, 1000):
        params = {
            "current": current,
            "pageSize": 100,
            "devEui": deviceid,
            "from": from_time,
            "to": to_time,
        }
        resp = sess.get(history_url, headers=headers, params=params, timeout=30)
        if resp.status_code == 200:
            data = resp.json()
            total = data.get("total", total)
            page = data.get("data") or []
            if not page:
                break
            history_data.append(page)
            if current * params["pageSize"] >= total:
                break
        else:
            print(f"\u8bf7\u6c42\u5931\u8d25\uff0c\u72b6\u6001\u7801: {resp.status_code}")
            break
    return [history_data, total]


def analyze_gps_data(groups):
    empty = 0
    lora_unique = 0
    ble = 0
    seen_fcnt = set()

    for g in groups:
        for p in g:
            if p.get("source") == "bluetooth":
                ble += 1
                continue
            if not (p.get("source") == "chirpstack" and isinstance(p.get("metadata"), dict)):
                continue
            fcnt = p["metadata"].get("fCnt")
            if fcnt is None:
                continue
            if fcnt in seen_fcnt:
                continue
            seen_fcnt.add(fcnt)
            lora_unique += 1

            gps_power = None
            gps_pos = None
            for item in p.get("payload", []):
                name = item.get("name")
                if name == "GPS Power Status":
                    gps_power = item.get("value")
                elif name == "GPS Position Status":
                    gps_pos = item.get("value")
            if gps_power == 1 and gps_pos == 0:
                empty += 1
    return [empty, lora_unique, ble]


def run_query(devices_csv: str, from_str: str, to_str: str, base_url: str, token: str) -> str:
    from_ms = to_timestamp_ms(from_str)
    to_ms = to_timestamp_ms(to_str)
    out_lines = []
    with requests.Session() as sess:
        for dev in [d.strip() for d in devices_csv.split(",") if d.strip()]:
            his = his_log(dev, from_ms, to_ms, base_url, token, session_obj=sess)
            stats = analyze_gps_data(his[0])
            ble_count = stats[2]
            lora_unique = stats[1]
            line = (
                f"{from_str}\u5230{to_str}\u65f6\u533a, {dev}\u5171\u6536\u5230"
                f"lora>{lora_unique}<\u5305\u6570\u636e\uff0c"
                f"ble>{ble_count}<\u5305\u6570\u636e\uff0c"
                f"\u7a7a\u5305>{stats[0]}<"
            )
            out_lines.append(line)
    return "\\n".join(out_lines) + "\\n"
