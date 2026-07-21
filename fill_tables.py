#!/usr/bin/env python3
"""
一键填满 EnergyPlan + JointOptimization 表

前置条件：后端服务已启动，DB 已执行 seed_demo_data.sql + seed_steel_data.sql

用法：
  python3 fill_tables.py [--base-url http://localhost:8080]

等价链：
  1. POST /api/auth/login          → token
  2. POST /api/energy/plan/generate → taskId → 轮询 → planId
  3. POST /api/optimize/joint/generate → taskId → 轮询 → 完成
"""

import requests
import time
import sys
import argparse

# ============================================================
# 配置
# ============================================================
BASE_URL = "http://localhost:8080"
USERNAME = "admin"
PASSWORD = "123456"
PLAN_DATE = "2026-07-17"
SCHEDULE_ID = 4000000000000000101   # seed 里排产方案 ID
POLL_INTERVAL = 2                   # 轮询间隔，秒
MAX_WAIT = 10                       # 最多等几次

HEADERS = {"Content-Type": "application/json"}


def login():
    """登录获取 token."""
    print("[1/5] 登录...", end=" ")
    resp = requests.post(
        f"{BASE_URL}/api/auth/login",
        json={"username": USERNAME, "password": PASSWORD},
        headers=HEADERS,
    )
    body = resp.json()
    if body["code"] != 200 or body["data"] is None:
        print(f"❌ 登录失败: {body}")
        sys.exit(1)
    token = body["data"]["token"]
    print(f"✅ token={token[:30]}...")
    return {"Authorization": f"Bearer {token}", **HEADERS}


def generate_energy_plan(auth_headers):
    """生成能源方案，返回 planId."""
    print("[2/5] 创建能源运行方案任务...", end=" ")
    resp = requests.post(
        f"{BASE_URL}/api/energy/plan/generate",
        json={
            "planDate": PLAN_DATE,
            "timeRange": "24h",
            "electricPriceMode": "PEAK_VALLEY",
            "objective": "MIN_ENERGY_COST",
            "constraints": {
                "steamUnitPrice": "180.00",
                "equipmentId": 1,
            },
        },
        headers=auth_headers,
    )
    body = resp.json()
    if body["code"] != 200 or body["data"] is None:
        print(f"❌ 创建失败: {body}")
        sys.exit(1)
    task_id = body["data"]["taskId"]
    print(f"✅ taskId={task_id}")

    # 轮询直到 SUCCESS
    print(f"[3/5] 轮询任务状态...", end=" ", flush=True)
    for attempt in range(1, MAX_WAIT + 1):
        time.sleep(POLL_INTERVAL)
        resp = requests.get(f"{BASE_URL}/api/tasks/{task_id}", headers=auth_headers)
        task = resp.json()["data"]
        if task is None:
            print(f"❌ 任务不存在")
            sys.exit(1)
        status = task["status"]
        if status == "SUCCESS":
            plan_id = task["resultId"]
            print(f"✅ {status} (planId={plan_id})")
            return plan_id
        if status == "FAILED":
            print(f"❌ {status}: {task.get('errorMessage', '')}")
            sys.exit(1)
        print(f"{status}...", end=" ", flush=True)

    print(f"❌ 超时，当前状态={status}")
    sys.exit(1)


def generate_joint_optimize(auth_headers, plan_id):
    """生成协同优化."""
    print("[4/5] 创建协同优化任务...", end=" ")
    resp = requests.post(
        f"{BASE_URL}/api/optimize/joint/generate",
        json={
            "scheduleId": SCHEDULE_ID,
            "energyPlanId": plan_id,
            "objectiveWeights": {
                "productionEfficiency": 0.3,
                "energyCost": 0.4,
                "carbonEmission": 0.3,
                "maxBoilerLoad": 80.0,
            },
        },
        headers=auth_headers,
    )
    body = resp.json()
    if body["code"] != 200 or body["data"] is None:
        print(f"❌ 创建失败: {body}")
        sys.exit(1)
    task_id = body["data"]["taskId"]
    print(f"✅ taskId={task_id}")

    # 轮询
    print(f"[5/5] 轮询任务状态...", end=" ", flush=True)
    for attempt in range(1, MAX_WAIT + 1):
        time.sleep(POLL_INTERVAL)
        resp = requests.get(f"{BASE_URL}/api/tasks/{task_id}", headers=auth_headers)
        task = resp.json()["data"]
        if task is None:
            print(f"❌ 任务不存在")
            sys.exit(1)
        status = task["status"]
        if status == "SUCCESS":
            opt_id = task["resultId"]
            print(f"✅ {status} (optimizeId={opt_id})")
            return opt_id
        if status == "FAILED":
            print(f"❌ {status}: {task.get('errorMessage', '')}")
            sys.exit(1)
        print(f"{status}...", end=" ", flush=True)

    print(f"❌ 超时，当前状态={status}")
    sys.exit(1)


def main():
    parser = argparse.ArgumentParser(description="填满 EnergyPlan + JointOptimization 表")
    parser.add_argument("--base-url", default="http://localhost:8080", help="后端地址")
    args = parser.parse_args()
    global BASE_URL
    BASE_URL = args.base_url

    print(f"目标: {BASE_URL}")
    print()

    auth_headers = login()
    plan_id = generate_energy_plan(auth_headers)
    opt_id = generate_joint_optimize(auth_headers, plan_id)

    print()
    print("=" * 50)
    print("全部完成！")
    print(f"  energy_plan          → planId = {plan_id}")
    print(f"  energy_plan_detail   → {PLAN_DATE} 24h 明细已生成")
    print(f"  joint_optimization_plan       → optimizeId = {opt_id}")
    print(f"  joint_optimization_timeseries → 24 条时序已生成")
    print(f"  constraint_conflict           → 按规则已生成")
    print()
    print("验证:")
    print(f"  curl -H 'Authorization: Bearer <token>' {BASE_URL}/api/energy/plan/{PLAN_DATE}")
    print(f"  curl -H 'Authorization: Bearer <token>' {BASE_URL}/api/optimize/joint/{opt_id}")


if __name__ == "__main__":
    main()
