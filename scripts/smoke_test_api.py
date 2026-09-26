"""端到端接口冒烟测试：真实调用 FastAPI + Milvus + LLM + LangGraph。

用法：
    .venv\\Scripts\\python.exe scripts\\smoke_test_api.py --base-url http://127.0.0.1:8010
    .venv\\Scripts\\python.exe scripts\\smoke_test_api.py --json-out smoke_report.json

用例矩阵见 TEST_PLAN.md。退出码 0 表示 P0 用例全绿。
注意：会真实调用 DeepSeek 并触发 CPU Reranker，单次检索 15～35 秒。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import httpx

CLARIFY_QUERY = "东风天龙仪表"
MANY_CLARIFY_QUERY = "4HK1发动机电脑板针脚定义"
DIRECT_QUERY = "东风天龙仪表针脚定义"
FALSE_NEGATIVE_QUERY = "徐工仪表线路图"
OPTION_COUNT_PATTERN = re.compile(r"（(\d+)条）")


@dataclass
class Case:
    case_id: str
    name: str
    expectation: str
    severity: str
    status: str = "SKIP"
    detail: str = ""
    latency: float = 0.0


@dataclass
class Report:
    cases: list[Case] = field(default_factory=list)
    raw: dict[str, Any] = field(default_factory=dict)

    def add(
        self,
        case_id: str,
        name: str,
        expectation: str,
        severity: str,
        passed: bool,
        detail: str = "",
        latency: float = 0.0,
    ) -> Case:
        case = Case(
            case_id=case_id,
            name=name,
            expectation=expectation,
            severity=severity,
            status="PASS" if passed else ("WARN" if severity == "P2" else "FAIL"),
            detail=detail,
            latency=latency,
        )
        self.cases.append(case)
        icon = {"PASS": "PASS", "WARN": "WARN", "FAIL": "FAIL"}[case.status]
        print(f"[{icon}] {case_id} {name} ({latency:.1f}s) {detail}", flush=True)
        return case


class Api:
    def __init__(self, base_url: str, timeout: float = 300.0) -> None:
        self.base_url = base_url.rstrip("/")
        self.client = httpx.Client(timeout=timeout)

    def request(self, method: str, path: str, **kwargs: Any) -> tuple[int, Any, float]:
        started = time.perf_counter()
        response = self.client.request(method, f"{self.base_url}{path}", **kwargs)
        elapsed = time.perf_counter() - started
        try:
            payload = response.json()
        except ValueError:
            payload = response.text[:200]
        return response.status_code, payload, elapsed

    def chat(self, session_id: str, message: str) -> tuple[int, Any, float]:
        return self.request(
            "POST",
            "/api/chat",
            json={"sessionId": session_id, "message": message},
        )

    def select(
        self,
        session_id: str,
        option_id: Any,
        option_value: str,
    ) -> tuple[int, Any, float]:
        return self.request(
            "POST",
            "/api/select",
            json={
                "sessionId": session_id,
                "optionId": option_id,
                "optionValue": option_value,
            },
        )


def data_of(payload: Any) -> dict:
    if isinstance(payload, dict) and isinstance(payload.get("data"), dict):
        return payload["data"]
    return {}


def doc_ids(payload: Any) -> list[int]:
    docs = data_of(payload).get("documents") or []
    return [int(doc["id"]) for doc in docs]


def option_expected_count(option: dict) -> int | None:
    match = OPTION_COUNT_PATTERN.search(str(option.get("text", "")))
    return int(match.group(1)) if match else None


def clarify_session(
    api: Api,
    session_id: str,
    report: Report,
) -> tuple[dict, float]:
    """建立一次澄清会话，返回 (payload, latency)。"""
    status, payload, elapsed = api.chat(session_id, CLARIFY_QUERY)
    report.raw.setdefault("clarify", {})[session_id] = {
        "status": status,
        "payload": payload,
    }
    return payload, elapsed


def main() -> int:
    parser = argparse.ArgumentParser(description="Smoke test the running FastAPI service.")
    parser.add_argument("--base-url", default="http://127.0.0.1:8010")
    parser.add_argument("--json-out", type=Path, default=None)
    parser.add_argument(
        "--skip-clarify",
        action="store_true",
        help="只跑 L0/L1/L2，跳过耗时的澄清用例。",
    )
    args = parser.parse_args()

    api = Api(args.base_url)
    report = Report()

    # ---------- L0 服务与静态资源 ----------
    print("\n== L0 服务与静态资源 ==", flush=True)
    status, payload, elapsed = api.request("GET", "/api/health")
    ok = status == 200 and payload.get("status") == "ok" and payload.get("version") == "0.3.0"
    report.add("T01", "健康检查", "200 + status=ok + version=0.3.0", "P0", ok, f"{payload}", elapsed)
    if not ok:
        print("\n服务不可用，终止测试。", flush=True)
        return 1

    status, payload, elapsed = api.request("GET", "/")
    report.add(
        "T02",
        "首页 HTML",
        "200 + HTML",
        "P0",
        status == 200 and "<html" in str(payload).lower(),
        f"HTTP {status}",
        elapsed,
    )

    static_ok = True
    static_detail = []
    for path in ("/css/style.css", "/js/chat.js"):
        status, payload, elapsed = api.request("GET", path)
        static_ok = static_ok and status == 200
        static_detail.append(f"{path}={status}")
    report.add(
        "T03",
        "静态资源挂载",
        "200",
        "P0",
        static_ok,
        ", ".join(static_detail),
        elapsed,
    )

    # ---------- L1 请求校验 ----------
    print("\n== L1 请求校验 ==", flush=True)
    status, payload, elapsed = api.chat("t04", "")
    report.add(
        "T04",
        "空消息被 Schema 拒绝",
        "422",
        "P0",
        status == 422,
        f"HTTP {status}",
        elapsed,
    )

    status, payload, elapsed = api.chat("t05", "   ")
    ok = status == 200 and payload.get("code") == 0 and "不能为空" in str(payload.get("msg"))
    report.add("T05", "纯空白消息提示", "code=0 + 消息内容不能为空", "P0", ok, f"{payload.get('msg')!r}", elapsed)

    # ---------- L2 分流与检索直答 ----------
    print("\n== L2 分流与检索直答 ==", flush=True)
    status, payload, elapsed = api.chat("t06-greeting", "你好")
    data = data_of(payload)
    ok = (
        status == 200
        and payload.get("code") == 1
        and data.get("type") == "text"
        and bool(str(data.get("content", "")).strip())
    )
    report.add(
        "T06",
        "普通问候走 chat 分支",
        "code=1 + type=text（不检索）",
        "P0",
        ok,
        f"{str(data.get('content'))[:40]!r}",
        elapsed,
    )

    status, payload, elapsed = api.chat("t07-direct", DIRECT_QUERY)
    data = data_of(payload)
    ids = doc_ids(payload)
    ok = (
        status == 200
        and payload.get("code") == 1
        and data.get("type") == "result"
        and 1 <= len(ids) <= 5
        and all(
            set(doc) >= {"id", "fileName", "hierarchyPath"}
            for doc in (data.get("documents") or [])
        )
    )
    report.add(
        "T07",
        f"检索直答（{DIRECT_QUERY}）",
        "code=1 + type=result + 1~5 条 + 字段完整",
        "P0",
        ok,
        f"docs={ids}",
        elapsed,
    )

    if args.skip_clarify:
        return summarize(report, args)

    # ---------- L3 澄清多轮 ----------
    print("\n== L3 澄清多轮 ==", flush=True)
    status, payload, elapsed = api.chat("t08-clarify", CLARIFY_QUERY)
    data = data_of(payload)
    options = data.get("options") or []
    ok = (
        status == 200
        and payload.get("code") == 1
        and data.get("type") == "options"
        and 2 <= len(options) <= 5
        and all({"id", "text", "value"} <= set(option) for option in options)
    )
    for option in options:
        print(f"       {option['id']}. {option['text']}  value={option['value']}", flush=True)
    report.add(
        "T08",
        f"触发澄清 interrupt（{CLARIFY_QUERY}）",
        "code=1 + type=options + 2~5 项",
        "P0",
        ok,
        f"options={len(options)}",
        elapsed,
    )

    # T14 选项标签必须可解释（不能是"相关资料第 N 组"这类无信息标签）
    option_text = " ".join(str(option["text"]) for option in options)
    meaningful = bool(option_text.strip()) and not any(
        marker in option_text
        for marker in ("相关资料第", "第 1 组", "第1组", "第 2 组", "第2组")
    )
    report.add(
        "T14",
        "澄清选项标签可解释",
        "选项标签来自真实资料类型或标题，不含“第 N 组”",
        "P1",
        meaningful,
        f"labels={[option['text'] for option in options]}",
        0.0,
    )

    # T09 选项独立性：三个独立会话，各只点一个选项
    print("\n== T09 选项独立性（独立会话分别点击）==", flush=True)
    independent: dict[int, list[int]] = {}
    expected_counts: dict[int, int | None] = {}
    for index in range(min(3, len(options))):
        session_id = f"t09-option-{index}"
        payload, _ = clarify_session(api, session_id, report)
        session_options = data_of(payload).get("options") or []
        if index >= len(session_options):
            break
        option = session_options[index]
        expected_counts[index] = option_expected_count(option)
        status, payload, elapsed = api.select(session_id, option["id"], option["value"])
        independent[index] = doc_ids(payload)
        print(
            f"       选项{index + 1}: 期望{expected_counts[index]}条 -> 实际 {independent[index]}",
            flush=True,
        )

    sets = [set(ids) for ids in independent.values()]
    pairwise_distinct = all(
        sets[i] != sets[j]
        for i in range(len(sets))
        for j in range(i + 1, len(sets))
    )
    counts_match = all(
        len(independent[index]) == expected_counts[index]
        for index in independent
        if expected_counts.get(index) is not None
    )
    report.add(
        "T09",
        "不同选项 → 不同结果集",
        "三组结果互不相同且条数符合选项标注",
        "P1",
        pairwise_distinct and counts_match,
        f"sets={[sorted(s) for s in sets]} 条数符合={counts_match}",
        elapsed,
    )

    # T10 同一会话改选另一个选项（后端通过重建 interrupt 支持改选）
    print("\n== T10 同一会话改选 ==", flush=True)
    session_id = "t10-repeat"
    payload, _ = clarify_session(api, session_id, report)
    session_options = data_of(payload).get("options") or []
    sequential: list[dict[str, Any]] = []
    for option in session_options[:3]:
        status, payload, elapsed = api.select(session_id, option["id"], option["value"])
        record = {
            "option": option["id"],
            "code": payload.get("code"),
            "msg": payload.get("msg"),
            "type": data_of(payload).get("type"),
            "ids": doc_ids(payload),
        }
        sequential.append(record)
        print(
            f"       点击 {option['id']} -> HTTP {status} code={record['code']} "
            f"type={record['type']} docs={record['ids']} msg={record['msg']!r}",
            flush=True,
        )

    first_ids = sequential[0]["ids"]
    silently_repeated = [
        item for item in sequential[1:]
        if item["code"] == 1 and item["ids"] == first_ids
    ]
    switched_ok = all(
        item["code"] == 1 and item["ids"] and item["ids"] != first_ids
        for item in sequential[1:]
    )
    report.add(
        "T10",
        "同一会话改选另一个选项",
        "改选后返回该选项自己的结果集（既不静默重复，也不报错）",
        "P1",
        switched_ok and not silently_repeated,
        f"click1={first_ids} 后续={[(i['code'], i['ids']) for i in sequential[1:]]}",
        elapsed,
    )

    # T11 非法选项值
    print("\n== T11 非法选项值 ==", flush=True)
    session_id = "t11-invalid"
    clarify_session(api, session_id, report)
    status, payload, elapsed = api.select(session_id, 999, "not_a_real_option:9")
    ok = status == 200 and payload.get("code") == 0 and "无效" in str(payload.get("msg"))
    report.add(
        "T11",
        "非法 optionValue 被拒绝",
        "code=0 + 无效的选择项",
        "P0",
        ok,
        f"{payload.get('msg')!r}",
        elapsed,
    )

    # T12 无 pending interrupt 时 select
    print("\n== T12 无 interrupt/已结束会话直接 select ==", flush=True)
    status, payload, elapsed = api.select("t12-no-interrupt", 1, "path_level_0:1")
    msg = str(payload.get("msg"))
    ok = payload.get("code") == 0 and "没有可用的选择项" in msg
    report.add(
        "T12",
        "已结束会话的 select",
        "code=0 + 明确提示（无待处理的选择/已失效）",
        "P1",
        ok,
        f"code={payload.get('code')} msg={msg!r}",
        elapsed,
    )

    # T15 多轮澄清与"返回上一步"
    print("\n== T15 多轮澄清 + 返回上一步 ==", flush=True)
    session_id = "t15-back"
    status, payload, elapsed = api.chat(session_id, MANY_CLARIFY_QUERY)
    round1 = data_of(payload).get("options") or []
    print(f"       第 1 轮: {[option['text'] for option in round1]}", flush=True)

    round2: list[dict] = []
    if round1:
        biggest = max(
            round1,
            key=lambda option: option_expected_count(option) or 0,
        )
        status, payload, elapsed = api.select(
            session_id,
            biggest["id"],
            biggest["value"],
        )
        round2 = data_of(payload).get("options") or []
        print(
            f"       选择「{biggest['text']}」-> type={data_of(payload).get('type')} "
            f"options={[option['text'] for option in round2]}",
            flush=True,
        )

    has_back = bool(round2) and round2[-1]["value"] == "__back__"
    back_restored = False
    if has_back:
        status, payload, elapsed = api.select(session_id, round2[-1]["id"], "__back__")
        back_options = data_of(payload).get("options") or []
        print(f"       返回上一步 -> {[option['text'] for option in back_options]}", flush=True)
        back_restored = [option["text"] for option in back_options] == [
            option["text"] for option in round1
        ]

    report.add(
        "T15",
        "多轮澄清后返回上一步",
        "第二轮提供“返回上一步”，点击后回到第一轮选项",
        "P1",
        has_back and back_restored,
        f"round1={[o['text'] for o in round1]} round2={[o['text'] for o in round2]} 回退一致={back_restored}",
        elapsed,
    )

    # T16 跨轮次改选：第二轮出结果后，回头点第一轮的另一个选项
    print("\n== T16 跨轮次改选 ==", flush=True)
    session_id = "t16-cross-round"
    status, payload, elapsed = api.chat(session_id, MANY_CLARIFY_QUERY)
    cross_round1 = data_of(payload).get("options") or []
    cross_round2: list[dict] = []

    if cross_round1:
        biggest = max(
            cross_round1,
            key=lambda option: option_expected_count(option) or 0,
        )
        status, payload, elapsed = api.select(session_id, biggest["id"], biggest["value"])
        cross_round2 = [
            option
            for option in (data_of(payload).get("options") or [])
            if option["value"] != "__back__"
        ]
        print(f"       第 1 轮选「{biggest['text']}」-> 第 2 轮 {[o['text'] for o in cross_round2]}", flush=True)

    if cross_round2:
        status, payload, elapsed = api.select(
            session_id,
            cross_round2[0]["id"],
            cross_round2[0]["value"],
        )
        print(f"       第 2 轮选「{cross_round2[0]['text']}」-> docs={doc_ids(payload)}", flush=True)

    cross_target = cross_round1[1] if len(cross_round1) > 1 else None
    cross_ids: list[int] = []
    if cross_target:
        status, payload, elapsed = api.select(
            session_id,
            cross_target["id"],
            cross_target["value"],
        )
        cross_ids = doc_ids(payload)
        print(
            f"       回头改选第 1 轮「{cross_target['text']}」-> "
            f"code={payload.get('code')} docs={cross_ids}",
            flush=True,
        )

    expected_cross = option_expected_count(cross_target) if cross_target else None
    report.add(
        "T16",
        "跨轮次改选第 1 轮选项",
        "回到第 1 轮点另一个选项，返回该选项自己的结果集",
        "P1",
        bool(cross_target)
        and payload.get("code") == 1
        and len(cross_ids) == expected_cross,
        f"目标={cross_target['text'] if cross_target else None} "
        f"期望{expected_cross}条 实际{cross_ids}",
        elapsed,
    )

    # ---------- L4 已知缺陷回归 ----------
    print("\n== L4 已知缺陷回归 ==", flush=True)
    status, payload, elapsed = api.chat("t13-false-negative", FALSE_NEGATIVE_QUERY)
    data = data_of(payload)
    is_no_results = "没有找到" in str(data.get("content", "")) or data.get("type") == "text"
    report.add(
        "T13",
        f"硬过滤假阴性（{FALSE_NEGATIVE_QUERY}）",
        "理想应返回结果；当前落到无结果即为缺陷复现（TEST_PLAN §4-1）",
        "P2",
        not is_no_results,
        f"type={data.get('type')} content={str(data.get('content'))[:40]!r}",
        elapsed,
    )

    return summarize(report, args)


def summarize(report: Report, args: argparse.Namespace) -> int:
    print("\n== 汇总 ==", flush=True)
    print(f"{'ID':<5}{'级别':<5}{'状态':<6}{'耗时':>8}  用例", flush=True)
    for case in report.cases:
        print(
            f"{case.case_id:<5}{case.severity:<5}{case.status:<6}{case.latency:>7.1f}s  {case.name}",
            flush=True,
        )

    failed_p0 = [c for c in report.cases if c.severity == "P0" and c.status == "FAIL"]
    failed_p1 = [c for c in report.cases if c.severity == "P1" and c.status == "FAIL"]
    warned = [c for c in report.cases if c.status == "WARN"]

    print(
        f"\nP0 失败 {len(failed_p0)} / P1 失败 {len(failed_p1)} / 已知缺陷复现 {len(warned)}",
        flush=True,
    )
    for case in failed_p0 + failed_p1 + warned:
        print(f"  - {case.case_id} {case.name}: {case.detail}", flush=True)

    if args.json_out:
        args.json_out.write_text(
            json.dumps(
                {
                    "cases": [vars(case) for case in report.cases],
                    "raw": report.raw,
                },
                ensure_ascii=False,
                indent=2,
                default=str,
            ),
            encoding="utf-8",
        )
        print(f"\n原始响应已写入 {args.json_out}", flush=True)

    return 1 if failed_p0 else 0


if __name__ == "__main__":
    sys.exit(main())
