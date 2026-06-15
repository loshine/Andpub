#!/usr/bin/env python3
"""Send market version query results to WeCom group webhook."""

import json
import sys

try:
    import requests
except ImportError:
    print("requests is required: python3 -m pip install requests", file=sys.stderr)
    sys.exit(2)


def format_markdown(data: dict) -> str:
    app_name = data["app"]["name"]
    package_name = data["app"]["packageName"]
    lines = [
        f"## {app_name} 应用市场提审通知",
        f"包名：`{package_name}`\n",
    ]
    for r in data["results"]:
        status = "OK" if r["success"] else "FAIL"
        if r["success"]:
            online = r["onlineVersion"] or "-"
            reviewing = r["reviewingVersion"] or "-"
            audit = r["auditStatus"] or "-"
            lines.append(f"**{r['name']}** [{status}]")
            lines.append(f"> 线上版本: {online}")
            lines.append(f"> 正在审核版本: {reviewing}")
            lines.append(f"> 审核状态: {audit}")
            lines.append("")
        else:
            lines.append(f"**{r['name']}** [{status}]")
            lines.append(f"> 错误: {r['error']}\n")
    return "\n".join(lines)


def send_webhook(webhook_url: str, content: str) -> None:
    payload = {"msgtype": "markdown", "markdown": {"content": content}}
    resp = requests.post(webhook_url, json=payload, timeout=30)
    resp.raise_for_status()
    result = resp.json()
    if result.get("errcode") != 0:
        print(f"Webhook error: {result}", file=sys.stderr)
        sys.exit(1)
    print("Webhook sent successfully.")


def main():
    if len(sys.argv) < 3:
        print("Usage: send_wecom_webhook.py <webhook_url> <results_json_path>", file=sys.stderr)
        sys.exit(1)
    webhook_url = sys.argv[1]
    results_path = sys.argv[2]
    with open(results_path) as f:
        data = json.load(f)
    content = format_markdown(data)
    send_webhook(webhook_url, content)


if __name__ == "__main__":
    main()
