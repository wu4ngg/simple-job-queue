#!/usr/bin/env python3
import json
import os
import sys
import time
import urllib.error
import urllib.request


BASE_URL = os.environ.get("BASE_URL", "http://localhost:8080")
RECEIVER_URL = os.environ.get("RECEIVER_URL", f"{BASE_URL}/test-receiver")
HEADERS = {"Content-Type": "application/json"}
EVENTS_COUNT = int(os.environ.get("EVENTS_COUNT", "10"))
DEADLINE_S = int(os.environ.get("DEADLINE_S", "45"))
POLL_INTERVAL_S = 2
def create_endpoint():
    print("-------> ENDPOINT <-------")
    url = f"{BASE_URL}/endpoints"
    body = {
        "url": RECEIVER_URL
     }
    req = urllib.request.Request(url, data=json.dumps(body).encode(), headers=HEADERS, method="POST")
    with urllib.request.urlopen(req, timeout=5) as r:
        return r.status, json.load(r)

def send_events(endpoint_id: str):
    print("-------> EVENT <-------")
    url = f"{BASE_URL}/endpoints/{endpoint_id}/events"
    delivery_ids = []
    for i in range(1,EVENTS_COUNT + 1):
        print(f"[EVENT] -> sending event {i}")
        body = {
            "n": i
        }
        req = urllib.request.Request(url, data=json.dumps(body).encode(), headers=HEADERS, method="POST")
        with urllib.request.urlopen(req, timeout=5) as r:
            data = json.load(r)
            delivery_ids.append(data.get("deliveryId"))
            print(f"[EVENT] -> event sent: {data}")
    return delivery_ids

def wait_until_settled(delivery_ids):
    print("-------> POLLING <-------")
    deadline = time.monotonic() + DEADLINE_S
    url = f"{BASE_URL}/deliveries"
    wanted = set(delivery_ids)
    list = []
    while time.monotonic() < deadline:
        req = urllib.request.Request(url, headers=HEADERS, method="GET")
        with urllib.request.urlopen(req, timeout=5) as r:
            data = json.load(r)
            list = data.get("items")
            list = [d for d in list if d["id"] in wanted]
            print(f"[POLLING] -> poll: {[i.get("status") for i in list]}")
            all_done = all(i.get("status") == 'succeeded' or i.get("status") == 'dead' for i in list)
            if all_done:
                return list, False
        time.sleep(POLL_INTERVAL_S)
    return list, True

def print_table(deliveries):
    print("-------> DELIVERIES <-------")
    ROW = "{:<10} {:<10} {:>8}  {}"
    print(ROW.format("ID", "STATUS", "ATTEMPTS", "LAST ERROR"))
    print("-"*50)
    for e in deliveries:
        print(ROW.format(e.get("id")[:8], e.get("status"), e.get("attempts"), e.get("lastError")))
        

def check_invariants(deliveries):
    print("-------> INVARIANTS CHECK <-------")
    is_success_or_dead = all(i.get("status") == 'succeeded' or i.get("status") == 'dead' for i in deliveries)
    list_filter_dead = [d for d in deliveries if d.get('status') == 'dead']
    dead_five_attempts = all(i.get("status") == 'dead' and i.get("attempts") == 5 and i.get("lastError") == 'HTTP 500' for i in list_filter_dead)
    list_filter_succeeded = [d for d in deliveries if d.get('status') == 'succeeded']
    success_correct_attempts = all(i.get("status") == 'succeeded' and i.get("attempts") in range(1,6) for i in list_filter_succeeded)
    correct_deliveries_count = len(deliveries) == EVENTS_COUNT
    print(f"[CHECK] -> Correct deliveries count: {correct_deliveries_count}.")
    print(f"[CHECK] -> Success or failed: {is_success_or_dead}.")
    if (not is_success_or_dead):
        list_filter_others = [i for i in deliveries if i.get("status") != 'succeeded' and i.get("status") != 'dead']
        print (f"[CHECK] -> Offending entries")
        print_table(list_filter_others)
    print(f"[CHECK] -> Dead deliveries have 5 attempts and HTTP 500 lastError: {dead_five_attempts}.")
    if (not dead_five_attempts):
        list_filter_others = [i for i in list_filter_dead if i.get("attempts") != 5 or i.get("lastError") != 'HTTP 500']
        print (f"[CHECK] -> Offending entries")
        print_table(list_filter_others)
    print(f"[CHECK] -> Success deliveries have between 1 and 5 attempts: {success_correct_attempts}.")
    if (not success_correct_attempts):
        list_filter_others = [i for i in list_filter_succeeded if i.get("status") == 'succeeded' and i.get("attempts") not in range(1,6)]
        print (f"[CHECK] -> Offending entries")
        print_table(list_filter_others)
    print(f"[CHECK] -> Total attempts == total receiver logs count: Check manually.")
    return is_success_or_dead and dead_five_attempts and success_correct_attempts and correct_deliveries_count


if __name__ == "__main__":
    start = time.monotonic()

    status, res = create_endpoint()

    print(f"[ENDPOINT] -> created endpoint {res.get("id")}")

    events = send_events(res.get("id"))

    print(f"[EVENTS] -> sent events: {events}")

    list, timed_out = wait_until_settled(events)
    if (not timed_out):
        print("[POLLING] -> all done")
    else:
        print("[POLLING] -> timeout")

    end = time.monotonic()
    print_table(list)
    print(f"[FINISHED] -> setteled after: {end - start}s")
    
    passed = check_invariants(list)
    if passed:
        sys.exit(0)
    else:
        sys.exit(1)