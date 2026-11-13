"""Append-only synthetic windows. Existing business samples, tasks and reports are never rewritten."""
import datetime as dt
import uuid


def choose_date(used, now=None):
    today = now or dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).date()
    # Historical dates submit immediately. Walk backwards so the pool renews daily.
    candidate = today - dt.timedelta(days=1)
    while candidate >= dt.date(2000, 1, 9):
        if candidate.isoformat() not in used:
            return candidate
        candidate -= dt.timedelta(days=1)
    raise RuntimeError('No unoccupied historical verification date is available.')


def ensure_window(sql, date):
    """Insert only missing quarter-hour timestamps, with fresh IDs and explicit synthetic lineage."""
    start = date - dt.timedelta(days=7)
    existing = set(sql(f"SELECT DATE_FORMAT(timestamp,'%Y-%m-%d %H:%i:%s') FROM energy_realtime_data WHERE timestamp >= '{start}' AND timestamp < '{date}'").splitlines())
    source = 'VERIFICATION_SYNTHETIC'
    # Signed BIGINT, distinct UUID-based range. A collision fails; it never triggers an UPDATE.
    prefix = 7_000_000_000_000_000_000 + (uuid.uuid4().int % 900_000_000_000) * 1000
    rows = []
    for index in range(7 * 96):
        moment = dt.datetime.combine(start, dt.time()) + dt.timedelta(minutes=15 * index)
        stamp = moment.strftime('%Y-%m-%d %H:%M:%S')
        if stamp in existing:
            continue
        rows.append(f"({prefix+index},'{stamp}',{50+(index*17)%35},{.4+(index%7)*.05:.2f},'NORMAL','{source}')")
    for offset in range(0, len(rows), 192):
        sql('INSERT INTO energy_realtime_data(id,timestamp,electricity_consumption,steam_consumption,data_quality,source) VALUES' + ','.join(rows[offset:offset+192]) + ';')
    return len(rows)


def allocate(sql):
    used = set(sql('SELECT schedule_date FROM schedule_generation_guard UNION SELECT schedule_date FROM production_schedule_plan UNION SELECT DATE(due_time) FROM production_order').splitlines())
    date = choose_date(used)
    ensure_window(sql, date)
    return date.isoformat()
