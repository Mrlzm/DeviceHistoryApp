import datetime
from concurrent.futures import ThreadPoolExecutor
import re

import requests

PAGE_SIZE = 20
REQUEST_TIMEOUT = (5, 15)
MAX_WORKERS = 4


def to_timestamp_ms(s: str, fmt: str = "%Y-%m-%d %H:%M:%S"):
    dt = datetime.datetime.strptime(s, fmt)
    return int(dt.timestamp() * 1000)


def his_log(deviceid, from_time, to_time, base_url, token, session_obj=None):
    sess = session_obj or requests.Session()
    history_data = []
    total = None
    headers = {
        "accept": "application/json",
        "Authorization": token,
    }
    history_url = f"{base_url.rstrip('/')}/v1/devices/history"
    for current in range(1, 1000):
        params = {
            "current": current,
            "pageSize": PAGE_SIZE,
            "devEui": deviceid,
            "from": from_time,
            "to": to_time,
        }
        resp = sess.get(history_url, headers=headers, params=params, timeout=REQUEST_TIMEOUT)
        if resp.status_code == 200:
            data = resp.json()
            if data.get("total") is not None:
                total = data.get("total")
            page = data.get("data") or []
            if not page:
                break
            history_data.append(page)
            if isinstance(total, int) and total > 0 and current * params["pageSize"] >= total:
                break
        else:
            print(f"\u8bf7\u6c42\u5931\u8d25\uff0c\u72b6\u6001\u7801: {resp.status_code}")
            break
    return [history_data, total or 0]


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


def iter_records(groups):
    for group in groups:
        for record in group:
            yield record


def payload_value(record, *names):
    wanted = {name.lower() for name in names}
    for item in record.get("payload", []) or []:
        name = str(item.get("name", "")).lower()
        if name in wanted:
            return item.get("value")
    return None


def payload_contains_value(record, keyword):
    keyword = keyword.lower()
    for item in record.get("payload", []) or []:
        name = str(item.get("name", "")).lower()
        if keyword in name:
            return item.get("value")
    return None


def first_value(*values):
    for value in values:
        if value is not None:
            return value
    return ""


def metadata_value(metadata, *names):
    for name in names:
        if name in metadata:
            return metadata.get(name)
    lower_map = {str(key).lower(): value for key, value in metadata.items()}
    for name in names:
        value = lower_map.get(name.lower())
        if value is not None:
            return value
    return None


def rx_info_value(metadata, name):
    rx_info = metadata.get("rxInfo") or metadata.get("rxinfo") or []
    if not isinstance(rx_info, list):
        return None
    for item in rx_info:
        if isinstance(item, dict) and item.get(name) is not None:
            return item.get(name)
    return None


def compact_time(value):
    if value is None:
        return ""
    return str(value).replace("+00:00", "Z")


def format_device_log(device, from_str, to_str, groups):
    lines = [
        f"[{device}] {from_str} \u5230",
        f"{to_str}",
        "created_at                 fCnt  dr rssi snr",
        "battery Coordinates",
        "-----------------------------------------------",
    ]
    seen_fcnt = set()
    row_count = 0
    for record in iter_records(groups):
        if not (record.get("source") == "chirpstack" and isinstance(record.get("metadata"), dict)):
            continue
        metadata = record.get("metadata") or {}
        fcnt = metadata_value(metadata, "fCnt", "fcnt")
        if fcnt is None or fcnt in seen_fcnt:
            continue
        seen_fcnt.add(fcnt)
        row_count += 1

        created_at = compact_time(first_value(
            record.get("created_at"),
            record.get("createdAt"),
            record.get("time"),
        ))
        dr = first_value(metadata_value(metadata, "dr"), record.get("dr"))
        rssi = first_value(metadata_value(metadata, "rssi"), rx_info_value(metadata, "rssi"), record.get("rssi"))
        snr = first_value(metadata_value(metadata, "snr"), rx_info_value(metadata, "snr"), record.get("snr"))
        battery = first_value(
            payload_value(record, "battery"),
            payload_contains_value(record, "battery"),
        )
        coordinates = first_value(
            payload_value(record, "Coordinates"),
            payload_contains_value(record, "coordinate"),
            payload_value(record, "GPS Position Status"),
        )
        lines.append(f"{created_at:<25} {str(fcnt):>4} {str(dr):>3} {str(rssi):>4} {str(snr):>4}")
        lines.append(f"{str(battery):<8} {coordinates}")
    if row_count == 0:
        lines.append("(\u65e0 LoRa \u660e\u7ec6\u6570\u636e)")
    return lines


def run_query(devices_csv: str, from_str: str, to_str: str, base_url: str, token: str) -> str:
    from_ms = to_timestamp_ms(from_str)
    to_ms = to_timestamp_ms(to_str)
    devices = [d.strip() for d in re.split(r"[\s,]+", devices_csv) if d.strip()]

    def query_device(dev):
        out_lines = []
        with requests.Session() as sess:
            his = his_log(dev, from_ms, to_ms, base_url, token, session_obj=sess)
            stats = analyze_gps_data(his[0])
            ble_count = stats[2]
            lora_unique = stats[1]
            out_lines.extend(format_device_log(dev, from_str, to_str, his[0]))
            out_lines.append(f"\u6c47\u603b: lora(\u53bb\u91cd)>{lora_unique}<, ble>{ble_count}<, \u7a7a\u5305>{stats[0]}<")
            out_lines.append("")
        return out_lines

    out_lines = []
    worker_count = min(MAX_WORKERS, max(1, len(devices)))
    with ThreadPoolExecutor(max_workers=worker_count) as executor:
        for lines in executor.map(query_device, devices):
            out_lines.extend(lines)
    return "\n".join(out_lines).rstrip() + "\n"
