import random
import time


def next_id() -> int:
    millis = int(time.time() * 1000)
    return int(f"{millis}{random.randint(100000, 999999)}")
