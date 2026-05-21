#!/usr/bin/env python3
"""Check market versions against expected version and alert via WeCom webhook.

Usage:
    python3 check_market_version.py <export_json> <expected_version> <webhook_url>

If any market's online version != expected_version, sends an alert and @mentions.
"""

import argparse
import sys

try:
    import requests
except ImportError:
    print("requests is required: python3 -m pip install requests", file=sys.stderr)
    sys.exit(2)

from query_market_versions import MarketVersionQuery, load_export

MENTION_LIST = [
    {"userid": "luoyaling", "name": "哑零"},
    {"userid": "longshuai", "name": "马骝"},
]


def build_reason(r: dict, expected_version: str) -> str:
    online = r["onlineVersion"] or "未知"
    audit = (r["auditStatus"] or "").strip()
    release = (r["releaseStatus"] or "").strip()

    if "审核中" in audit or "审核中" in release:
        return f"v{online} → v{expected_version} 审核中"
    if "审核不通过" in audit or "驳回" in audit:
        return f"v{online} 审核被驳回"
    if "审核通过" in audit and online != expected_version:
        return f"v{expected_version} 审核通过，待上架"
    if online != expected_version and not audit:
        return f"v{online}，未提审 v{expected_version}"
    return f"v{online}（{audit or release}）"


def check_and_alert(export_path: str, expected_version: str, webhook_url: str) -> int:
    exported = load_export(export_path)
    result = MarketVersionQuery(requests.Session()).query_export(exported)

    mismatched = []
    for r in result["results"]:
        if not r["success"]:
            mismatched.append({"name": r["name"], "reason": f"查询失败: {r['error']}"})
        elif r["onlineVersion"] != expected_version:
            mismatched.append({
                "name": r["name"],
                "reason": build_reason(r, expected_version),
            })

    if not mismatched:
        print(f"All markets on {expected_version}. No alert.")
        return 0

    market_names_highlighted = "、".join(
        f'<font color="warning">{m["name"]}</font>' for m in mismatched
    )
    reason_lines = "\n".join(
        f'{i+1}. **{m["name"]}**：<font color="comment">{m["reason"]}</font>' for i, m in enumerate(mismatched)
    )

    content = (
        f"# Android 应用未上架警报\n\n"
        f"**应用市场**：{market_names_highlighted}\n"
        f'**期望版本**：<font color="info">{expected_version}</font>\n'
        f"**未上架原因**：\n{reason_lines}"
    )

    payload = {"msgtype": "markdown", "markdown": {"content": content}}
    resp = requests.post(webhook_url, json=payload, timeout=30)
    resp.raise_for_status()
    resp_data = resp.json()
    if resp_data.get("errcode") != 0:
        print(f"Webhook error: {resp_data}", file=sys.stderr)
        return 1

    market_names = "、".join(m["name"] for m in mismatched)
    print(f"Alert sent: {market_names}")
    return 0


def main():
    parser = argparse.ArgumentParser(description="Check market versions and send alert if mismatched.")
    parser.add_argument("export_json", help="Path to Andpub exported settings JSON")
    parser.add_argument("expected_version", help="Expected online version (e.g. 6.6.4)")
    parser.add_argument("webhook_url", help="WeCom webhook URL")
    args = parser.parse_args()
    sys.exit(check_and_alert(args.export_json, args.expected_version, args.webhook_url))


if __name__ == "__main__":
    main()
