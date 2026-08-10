#!/usr/bin/env python3
"""
Production-energy optimization entrypoint.

Usage:
    python generate_plan.py input.csv output.json
    python generate_plan.py input.csv output.json orders.csv

This script mirrors the JSON contract of the MATLAB prototype while using only
the Python standard library, so the backend can be deployed without MATLAB.
"""
from __future__ import annotations

import csv
import json
import math
import random
import sys
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path
from typing import Iterable, List, Sequence


BASE_ELEC_COEFF = 14.00
DEMAND_ELEC_TO_TON = 0.035
MIN_PRODUCTION = 0.15
HEAT_EXTRA_CAPACITY = 0.80
MIN_HEAT_HOURS = 8
MAX_HEAT_HOURS = 12
HISTORY_DAYS = 7
POINTS_PER_DAY_15MIN = 24 * 4
POINTS_PER_DAY_1MIN = 24 * 60


class AlgorithmError(Exception):
    def __init__(self, code: int, message: str, **extra):
        super().__init__(message)
        self.code = code
        self.message = message
        self.extra = extra


@dataclass
class EnergyPoint:
    timestamp: datetime
    elec: float
    steam: float


@dataclass
class ProductionOrder:
    order_no: str
    planned_quantity: float
    due_time: datetime
    priority: int


def parse_timestamp(value: str) -> datetime:
    text = (value or "").strip()
    formats = (
        "%d/%m/%Y %H:%M",
        "%d/%m/%Y %H:%M:%S",
        "%m/%d/%Y %H:%M",
        "%m/%d/%Y %H:%M:%S",
        "%Y-%m-%d %H:%M:%S",
        "%Y-%m-%d %H:%M",
        "%Y/%m/%d %H:%M:%S",
        "%Y/%m/%d %H:%M",
    )
    for fmt in formats:
        try:
            return datetime.strptime(text, fmt)
        except ValueError:
            pass
    try:
        return datetime.fromisoformat(text)
    except ValueError as exc:
        raise AlgorithmError(
            415,
            "timestamp parse failed; expected formats like yyyy-MM-dd HH:mm:ss or MM/dd/yyyy HH:mm",
        ) from exc


def parse_float(value: str | None) -> float | None:
    if value is None:
        return None
    text = str(value).strip()
    if not text:
        return None
    try:
        return float(text)
    except ValueError as exc:
        raise AlgorithmError(415, f"numeric field parse failed: {text}") from exc


def normalize_header(name: str) -> str:
    key = (name or "").strip().lower()
    if key in {"date", "timestamp", "datetime"}:
        return "timestamp"
    if key in {"usage_kwh", "power", "power_kw", "elec"}:
        return "elec"
    if key == "steam":
        return "steam"
    return key


def normalize_order_header(name: str) -> str:
    key = (name or "").strip().replace("_", "").replace("-", "").lower()
    aliases = {
        "orderno": "order_no",
        "orderid": "order_no",
        "plannedquantity": "planned_quantity",
        "quantity": "planned_quantity",
        "demandton": "planned_quantity",
        "weight": "planned_quantity",
        "duetime": "due_time",
        "deadline": "due_time",
        "deliverytime": "due_time",
        "priority": "priority",
        "status": "status",
    }
    return aliases.get(key, key)


def linear_fill(values: Sequence[float | None]) -> List[float]:
    result: List[float | None] = list(values)
    known = [i for i, value in enumerate(result) if value is not None]
    if not known:
        raise AlgorithmError(415, "elec column has no numeric values")

    first = known[0]
    for i in range(first):
        result[i] = result[first]

    for left, right in zip(known, known[1:]):
        left_value = result[left]
        right_value = result[right]
        assert left_value is not None and right_value is not None
        span = right - left
        for i in range(left + 1, right):
            ratio = (i - left) / span
            result[i] = left_value + (right_value - left_value) * ratio

    last = known[-1]
    for i in range(last + 1, len(result)):
        result[i] = result[last]

    return [float(value) for value in result if value is not None]


def read_energy_csv(path: Path) -> List[EnergyPoint]:
    if not path.exists():
        raise AlgorithmError(404, f"input file not found: {path}")

    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if not reader.fieldnames:
            raise AlgorithmError(400, "CSV file is empty")

        field_map = {name: normalize_header(name) for name in reader.fieldnames}
        normalized_fields = set(field_map.values())
        if "timestamp" not in normalized_fields or "elec" not in normalized_fields:
            raise AlgorithmError(
                415,
                "input format error; CSV must contain timestamp/date and elec/Usage_kWh columns",
            )

        timestamps: List[datetime] = []
        elec_raw: List[float | None] = []
        steam_raw: List[float | None] = []

        for row in reader:
            normalized = {field_map[key]: value for key, value in row.items() if key in field_map}
            timestamps.append(parse_timestamp(normalized.get("timestamp", "")))
            elec_raw.append(parse_float(normalized.get("elec")))
            steam_raw.append(parse_float(normalized.get("steam")))

    elec_values = linear_fill(elec_raw)
    steam_values = linear_fill(steam_raw) if any(value is not None for value in steam_raw) else [
        value * 0.005 + 0.5 for value in elec_values
    ]

    points = [
        EnergyPoint(timestamp=ts, elec=elec, steam=steam)
        for ts, elec, steam in zip(timestamps, elec_values, steam_values)
        if elec > 0.01
    ]
    points.sort(key=lambda item: item.timestamp)

    if len(points) < 2:
        raise AlgorithmError(
            400,
            "insufficient input data; at least two valid rows are required",
            received_rows=len(points),
            required_rows=2,
        )

    for previous, current in zip(points, points[1:]):
        if current.timestamp <= previous.timestamp:
            raise AlgorithmError(400, "timestamp column must be strictly increasing without duplicates")

    return points


def read_orders(path: Path) -> tuple[List[ProductionOrder], List[float], datetime | None]:
    if not path.exists():
        raise AlgorithmError(404, f"orders file not found: {path}")

    orders: List[ProductionOrder] = []
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if not reader.fieldnames:
            return orders, [0.0] * 24, None

        field_map = {name: normalize_order_header(name) for name in reader.fieldnames}
        normalized_fields = set(field_map.values())
        required = {"order_no", "planned_quantity", "due_time"}
        if not required.issubset(normalized_fields):
            missing = sorted(required - normalized_fields)
            raise AlgorithmError(415, f"orders CSV missing fields: {', '.join(missing)}")

        for row in reader:
            normalized = {field_map[key]: value for key, value in row.items() if key in field_map}
            order_no = (normalized.get("order_no") or "").strip()
            quantity = parse_float(normalized.get("planned_quantity"))
            due_time = parse_timestamp(normalized.get("due_time") or "")
            priority_value = normalized.get("priority")
            try:
                priority = int(priority_value) if priority_value not in (None, "") else 1
            except ValueError:
                priority = 1

            if not order_no or quantity is None or quantity <= 0:
                continue
            orders.append(ProductionOrder(order_no, quantity, due_time, priority))

    if not orders:
        return orders, [0.0] * 24, None

    plan_date = min(order.due_time.date() for order in orders)
    demand = [0.0] * 24
    for order in orders:
        if order.due_time.date() != plan_date:
            continue
        due_hour = min(max(order.due_time.hour, 0), 23)
        demand[due_hour] += order.planned_quantity

    return orders, demand, datetime.combine(plan_date, datetime.min.time())


def minutes_since_epoch(value: datetime) -> float:
    return (value - datetime(1970, 1, 1)).total_seconds() / 60.0


def pchip_slopes(x: Sequence[float], y: Sequence[float]) -> List[float]:
    n = len(x)
    if n == 2:
        slope = (y[1] - y[0]) / (x[1] - x[0])
        return [slope, slope]

    h = [x[i + 1] - x[i] for i in range(n - 1)]
    delta = [(y[i + 1] - y[i]) / h[i] for i in range(n - 1)]
    d = [0.0] * n

    for i in range(1, n - 1):
        if delta[i - 1] == 0 or delta[i] == 0 or (delta[i - 1] > 0) != (delta[i] > 0):
            d[i] = 0.0
        else:
            w1 = 2 * h[i] + h[i - 1]
            w2 = h[i] + 2 * h[i - 1]
            d[i] = (w1 + w2) / (w1 / delta[i - 1] + w2 / delta[i])

    d[0] = endpoint_slope(h[0], h[1], delta[0], delta[1])
    d[-1] = endpoint_slope(h[-1], h[-2], delta[-1], delta[-2])
    return d


def endpoint_slope(h0: float, h1: float, delta0: float, delta1: float) -> float:
    d = ((2 * h0 + h1) * delta0 - h0 * delta1) / (h0 + h1)
    if (d > 0) != (delta0 > 0):
        return 0.0
    if (delta0 > 0) != (delta1 > 0) and abs(d) > abs(3 * delta0):
        return 3 * delta0
    return d


def pchip_interpolate(x: Sequence[float], y: Sequence[float], x_new: Iterable[float]) -> List[float]:
    slopes = pchip_slopes(x, y)
    values: List[float] = []
    interval = 0
    last_interval = len(x) - 2

    for target in x_new:
        while interval < last_interval and target > x[interval + 1]:
            interval += 1
        h = x[interval + 1] - x[interval]
        t = (target - x[interval]) / h
        h00 = (2 * t**3) - (3 * t**2) + 1
        h10 = (t**3) - (2 * t**2) + t
        h01 = (-2 * t**3) + (3 * t**2)
        h11 = (t**3) - (t**2)
        values.append(h00 * y[interval] + h10 * h * slopes[interval] + h01 * y[interval + 1] + h11 * h * slopes[interval + 1])

    return values


def prepare_data(raw_points: List[EnergyPoint]) -> tuple[List[EnergyPoint], str]:
    steps = [
        (current.timestamp - previous.timestamp).total_seconds() / 60.0
        for previous, current in zip(raw_points, raw_points[1:])
    ]
    step_minutes = median(steps)
    print(f"raw rows: {len(raw_points)}, estimated granularity: {step_minutes:.2f} minutes")

    if step_minutes <= 2:
        required_points = HISTORY_DAYS * POINTS_PER_DAY_1MIN
        if len(raw_points) < required_points:
            raise AlgorithmError(
                400,
                f"insufficient input data; at least {HISTORY_DAYS} days / {required_points} rows of 1-minute data are required",
                received_rows=len(raw_points),
                required_rows=required_points,
            )
        print(f"detected 1-minute data, direct use: {len(raw_points)} rows")
        recent = raw_points[-required_points:]
        return recent, "1 minute"

    if 10 <= step_minutes <= 20:
        required_points = HISTORY_DAYS * POINTS_PER_DAY_15MIN
        if len(raw_points) < required_points:
            raise AlgorithmError(
                400,
                f"insufficient input data; at least {HISTORY_DAYS} days / {required_points} rows of 15-minute data are required",
                received_rows=len(raw_points),
                required_rows=required_points,
            )
        recent = raw_points[-required_points:]
        x = [minutes_since_epoch(point.timestamp) for point in recent]
        y = [point.elec for point in recent]
        expected_points = HISTORY_DAYS * POINTS_PER_DAY_1MIN
        count = int(round(x[-1] - x[0])) + 1
        if count > expected_points * 2:
            raise AlgorithmError(
                400,
                "timestamp range is abnormal; check the timestamp date format",
            )
        x_new = [x[0] + i for i in range(count)]
        y_new = pchip_interpolate(x, y, x_new)
        start = recent[0].timestamp
        data = [
            EnergyPoint(start + timedelta(minutes=i), elec=value, steam=value * 0.005 + 0.5)
            for i, value in enumerate(y_new)
        ]
        print(f"PCHIP interpolation completed: {len(data)} rows")
        return data, "1 minute"

    raise AlgorithmError(
        415,
        f"unsupported input granularity: {step_minutes:.2f} minutes; only 1-minute or 15-minute data is supported",
    )


def median(values: Sequence[float]) -> float:
    ordered = sorted(values)
    mid = len(ordered) // 2
    if len(ordered) % 2:
        return ordered[mid]
    return (ordered[mid - 1] + ordered[mid]) / 2


def aggregate_hourly(points: Sequence[EnergyPoint]) -> List[float]:
    buckets: dict[datetime, List[float]] = {}
    for point in points:
        hour = point.timestamp.replace(minute=0, second=0, microsecond=0)
        buckets.setdefault(hour, []).append(point.elec)
    return [sum(values) / len(values) for _, values in sorted(buckets.items())]


def generate_demand(elec_hourly: Sequence[float]) -> List[float]:
    recent = elec_hourly[-min(HISTORY_DAYS * 24, len(elec_hourly)) :]
    base_demand = (sum(recent) / len(recent)) * DEMAND_ELEC_TO_TON
    rng = random.Random(42)
    return [max(base_demand * (0.7 + 0.6 * rng.random()), base_demand * 0.4) for _ in range(24)]


def solve_schedule(demand: Sequence[float]) -> tuple[List[float], List[int], str]:
    total = sum(demand)
    min_total = 24 * MIN_PRODUCTION
    max_total = min_total + MAX_HEAT_HOURS * HEAT_EXTRA_CAPACITY

    if total < min_total or total > max_total:
        heat_hours = choose_heat_hours(demand, MIN_HEAT_HOURS)
        return list(demand), [1 if i in heat_hours else 0 for i in range(24)], "fallback"

    extra_needed = total - min_total
    heat_count = max(MIN_HEAT_HOURS, min(MAX_HEAT_HOURS, math.ceil(extra_needed / HEAT_EXTRA_CAPACITY)))
    heat_hours = choose_heat_hours(demand, heat_count)
    production = [MIN_PRODUCTION] * 24
    remaining = extra_needed
    active = set(heat_hours)

    while remaining > 1e-9 and active:
        weight_sum = sum(max(demand[i], 0.01) for i in active)
        changed = False
        for hour in list(active):
            available = MIN_PRODUCTION + HEAT_EXTRA_CAPACITY - production[hour]
            share = remaining * max(demand[hour], 0.01) / weight_sum
            addition = min(available, share)
            if addition > 1e-12:
                production[hour] += addition
                remaining -= addition
                changed = True
            if production[hour] >= MIN_PRODUCTION + HEAT_EXTRA_CAPACITY - 1e-9:
                active.remove(hour)
        if not changed:
            break

    if abs(sum(production) - total) > 1e-6 and active:
        production[next(iter(active))] += total - sum(production)

    heat_state = [1 if i in heat_hours else 0 for i in range(24)]
    return production, heat_state, "feasible_heuristic"


def choose_heat_hours(demand: Sequence[float], count: int) -> set[int]:
    ranked = sorted(range(24), key=lambda i: demand[i], reverse=True)
    return set(ranked[:count])


def optimize_process_parameters(total_production: float) -> tuple[int, float, float, float]:
    t_range = [1140, 1145, 1150, 1155, 1160]
    v_range = [9, 9.5, 10, 10.5, 11]
    t_ref = 1150
    v_ref = 10
    temp_coeff = 0.00222
    speed_coeff = 0.03
    best = (1150, 10.0, float("inf"), BASE_ELEC_COEFF)

    for temperature in t_range:
        for speed in v_range:
            temp_factor = 1 + temp_coeff * (temperature - t_ref)
            speed_factor = 1 - speed_coeff * (speed - v_ref)
            coeff = BASE_ELEC_COEFF * temp_factor * speed_factor
            energy = total_production * coeff
            if energy < best[2]:
                best = (temperature, speed, energy, coeff)

    return best[0], best[1], best[2], best[3]


def generate_realtime_control(points: Sequence[EnergyPoint],
                              production: Sequence[float],
                              control_date: datetime) -> dict:
    sim_data = list(points[-min(24 * 60, len(points)) :])
    elec_ref_by_hour = [value * BASE_ELEC_COEFF for value in production]
    elec_ref_minutely: List[float] = []
    for value in elec_ref_by_hour:
        elec_ref_minutely.extend([value] * 60)

    n_steps = min(120, len(sim_data))
    if n_steps == 0:
        raise AlgorithmError(400, "no data available for realtime control")

    boiler_min, boiler_max = 20.0, 80.0
    turbine_min, turbine_max = 5.0, 30.0
    ramp_rate = 5.0
    w_elec = 2.0
    boiler_state = 30.0
    turbine_state = 10.0
    boiler_values: List[float] = []
    turbine_values: List[float] = []
    grid_values: List[float] = []

    for point in sim_data[:n_steps]:
        minute_of_day = min(point.timestamp.hour * 60 + point.timestamp.minute, 1439)
        plan_elec = elec_ref_minutely[minute_of_day]
        elec_error = point.elec - plan_elec
        hour_idx = point.timestamp.hour % 24
        base_boiler = 25 + production[hour_idx] * 2
        base_turbine = 8 + production[hour_idx] * 0.5
        correction = elec_error * 0.25 * w_elec

        boiler_setpoint = clamp(base_boiler - correction, boiler_min, boiler_max)
        if boiler_values:
            boiler_setpoint = boiler_state + clamp(boiler_setpoint - boiler_state, -ramp_rate, ramp_rate)
        boiler_state = boiler_setpoint

        turbine_setpoint = clamp(base_turbine + correction * 0.15, turbine_min, turbine_max)
        if turbine_values:
            turbine_setpoint = turbine_state + clamp(turbine_setpoint - turbine_state, -2.0, 2.0)
        turbine_state = turbine_setpoint

        boiler_values.append(boiler_setpoint)
        turbine_values.append(turbine_setpoint)
        grid_values.append(max(0.0, point.elec - turbine_setpoint * 0.5))

    executable = 0
    for index, value in enumerate(boiler_values):
        in_range = boiler_min <= value <= boiler_max
        ramp_ok = index == 0 or abs(value - boiler_values[index - 1]) <= ramp_rate
        if in_range and ramp_ok:
            executable += 1
    er = executable / len(boiler_values) * 100

    control_timestamp = datetime.combine(
        control_date.date(),
        sim_data[n_steps - 1].timestamp.time(),
    )

    return {
        "timestamp": control_timestamp.strftime("%Y-%m-%d %H:%M:%S"),
        "control_date": control_timestamp.strftime("%Y-%m-%d"),
        "control": {
            "boiler_load": boiler_values[-1],
            "turbine_output": turbine_values[-1],
            "grid_purchase": grid_values[-1],
            "power_factor_target": 0.95,
        },
        "forecast": {
            "elec_next_5min": (sum(boiler_values) / len(boiler_values)) * 0.15,
            "steam_next_5min": (sum(boiler_values) / len(boiler_values)) * 0.002,
        },
        "performance": {
            "ER": er,
        },
    }


def generate_energy_plan(production: Sequence[float],
                         optimized_coeff: float,
                         plan_start: datetime) -> dict:
    details = []
    electricity_cost = 0.0
    steam_cost = 0.0
    for hour, production_value in enumerate(production):
        timestamp = plan_start + timedelta(hours=hour)
        electricity = production_value * optimized_coeff
        boiler_output = clamp(20.0 + production_value * 60.0, 20.0, 80.0)
        steam = boiler_output * 0.012 + production_value * 0.18
        carbon = electricity * 0.00057
        electricity_price = price_for_hour(hour)
        hour_electricity_cost = electricity * electricity_price
        hour_steam_cost = steam * 180.0
        electricity_cost += hour_electricity_cost
        steam_cost += hour_steam_cost
        details.append({
            "hour": hour,
            "timestamp": timestamp.strftime("%Y-%m-%d %H:%M:%S"),
            "equipment_id": 1,
            "boiler_output": boiler_output,
            "turbine_output": clamp(8.0 + production_value * 0.5, 5.0, 30.0),
            "grid_purchase": max(0.0, electricity - boiler_output * 0.35),
            "electricity_consumption": electricity,
            "steam_consumption": steam,
            "carbon_emission_tco2": carbon,
            "energy_cost": hour_electricity_cost + hour_steam_cost,
        })

    return {
        "timestamp": plan_start.strftime("%Y-%m-%d %H:%M:%S"),
        "plan_date": plan_start.strftime("%Y-%m-%d"),
        "source": "algorithm_milp_energy_plan",
        "objective": "cost_energy_carbon_executability",
        "electric_price_mode": "PEAK_VALLEY",
        "time_interval": 60,
        "electricity_cost": electricity_cost,
        "steam_cost": steam_cost,
        "total_energy_cost": electricity_cost + steam_cost,
        "details": details,
    }


def price_for_hour(hour: int) -> float:
    if 0 <= hour < 8 or 22 <= hour < 24:
        return 0.35
    if 8 <= hour < 22:
        return 1.05
    return 0.65


def clamp(value: float, lower: float, upper: float) -> float:
    return max(lower, min(upper, value))


def build_result(input_file: Path, orders_file: Path | None = None) -> dict:
    raw_points = read_energy_csv(input_file)
    points, granularity = prepare_data(raw_points)
    elec_hourly = aggregate_hourly(points)
    print(f"aggregated hours: {len(elec_hourly)}")

    orders: List[ProductionOrder] = []
    plan_start: datetime | None = None
    demand_source = "energy_estimation"
    if orders_file is not None:
        orders, order_demand, plan_start = read_orders(orders_file)
        if sum(order_demand) > 0:
            demand = order_demand
            demand_source = "production_orders"
            print(f"orders loaded: {len(orders)}, order demand: {sum(demand):.1f} tons")
        else:
            demand = generate_demand(elec_hourly)
            print("orders file has no valid demand, fallback to energy estimation")
    else:
        demand = generate_demand(elec_hourly)
    production, heat_state, solver_status = solve_schedule(demand)
    total_production = sum(production)
    temperature, speed, _, optimized_coeff = optimize_process_parameters(total_production)
    reduction_rate = (BASE_ELEC_COEFF - optimized_coeff) / BASE_ELEC_COEFF * 100
    total_energy = total_production * optimized_coeff

    print(f"total demand: {sum(demand):.1f} tons")
    print(f"heat furnace running hours: {sum(heat_state)}")
    print(f"EC reduction: {reduction_rate:.2f}%")

    generated_at = plan_start or datetime.now()
    daily_plan = {
        "timestamp": generated_at.strftime("%Y-%m-%d %H:%M:%S"),
        "plan_horizon": 24,
        "unit": "hour",
        "data_granularity": granularity,
        "demand_source": demand_source,
        "order_count": len(orders),
        "EC_baseline": BASE_ELEC_COEFF,
        "EC_optimized": optimized_coeff,
        "EC_reduction": reduction_rate,
        "total_demand": sum(demand),
        "total_production": total_production,
        "total_energy": total_energy,
        "optimal_temperature": temperature,
        "optimal_speed": speed,
        "solver_status": solver_status,
        "solver_exitflag": 1 if solver_status != "fallback" else 0,
        "algorithm_runtime": "python",
        "schedule": [
            {
                "hour": hour,
                "demand": demand[hour],
                "production": production[hour],
            }
            for hour in range(24)
        ],
    }
    energy_plan = generate_energy_plan(production, optimized_coeff, generated_at)
    realtime_control = generate_realtime_control(points, production, generated_at)
    print(f"ER: {realtime_control['performance']['ER']:.2f}%")

    return {
        "daily_plan": daily_plan,
        "energy_plan": energy_plan,
        "realtime_control": realtime_control,
    }


def write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, separators=(",", ":"))


def write_error_json(path: Path, error: Exception, input_file: Path | None = None) -> None:
    if isinstance(error, AlgorithmError):
        payload = {
            "status": "error",
            "code": error.code,
            "message": error.message,
        }
        payload.update(error.extra)
    else:
        payload = {
            "status": "error",
            "code": 500,
            "message": str(error),
        }
    if input_file and input_file.exists() and "received_rows" not in payload:
        try:
            with input_file.open("r", encoding="utf-8-sig", newline="") as handle:
                payload["received_rows"] = max(sum(1 for _ in handle) - 1, 0)
        except OSError:
            pass
    write_json(path, payload)


def main(argv: Sequence[str]) -> int:
    input_file = Path(argv[1]) if len(argv) > 1 else Path("steel_data_cleaned.csv")
    output_file = Path(argv[2]) if len(argv) > 2 else Path("output_sample.json")
    orders_file = Path(argv[3]) if len(argv) > 3 else None

    print("========================================")
    print("  Production-energy optimization v1.0 (Python)")
    print("========================================")
    print(f"input: {input_file}")
    print(f"output: {output_file}")
    if orders_file is not None:
        print(f"orders: {orders_file}")

    try:
        result = build_result(input_file, orders_file)
        write_json(output_file, result)
        print("optimization completed")
        return 0
    except Exception as exc:
        write_error_json(output_file, exc, input_file)
        print(f"optimization failed: {exc}")
        return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
