#!/usr/bin/env python3
"""Query Android market versions from an Andpub exported settings JSON.

Dependencies:
  python3 -m pip install requests cryptography
"""

from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import re
import sys
import time
from dataclasses import dataclass
from typing import Any

try:
    import requests
except ImportError:  # pragma: no cover - exercised by real CLI environments.
    requests = None


HUAWEI_AUTH_BASE = "https://connect-api.cloud.huawei.com/api"
HUAWEI_PUBLISH_BASE = f"{HUAWEI_AUTH_BASE}/publish/v2"
HONOR_AUTH_BASE = "https://iam.developer.honor.com"
HONOR_PUBLISH_BASE = "https://appmarket-openapi-drcn.cloud.honor.com/openapi/v1/publish"
OPPO_BASE = "https://oop-openapi-cn.heytapmobi.com"
VIVO_BASE = "https://developer-api.vivo.com.cn/router/rest"
TENCENT_BASE = "https://p.open.qq.com/open_file/developer_api"
XIAOMI_BASE = "https://api.developer.xiaomi.com/devupload"


@dataclass
class QueryResult:
    marketType: str
    name: str
    success: bool
    onlineVersion: str | None = None
    reviewingVersion: str | None = None
    auditStatus: str | None = None
    releaseStatus: str | None = None
    error: str | None = None

    def to_json(self) -> dict[str, Any]:
        return {
            "marketType": self.marketType,
            "name": self.name,
            "success": self.success,
            "onlineVersion": self.onlineVersion,
            "reviewingVersion": self.reviewingVersion,
            "auditStatus": self.auditStatus,
            "releaseStatus": self.releaseStatus,
            "error": self.error,
        }


class MarketVersionQuery:
    def __init__(self, session: Any) -> None:
        self.session = session

    def query_export(self, exported: dict[str, Any]) -> dict[str, Any]:
        app = require_dict(exported.get("app"), "app")
        package_name = require_str(app.get("packageName"), "app.packageName")
        channels = exported.get("channels")
        if not isinstance(channels, list):
            raise ValueError("channels must be an array")

        results = []
        for channel in channels:
            channel_dict = require_dict(channel, "channel")
            market_type = require_str(channel_dict.get("marketType"), "channel.marketType")
            name = channel_dict.get("name") or market_type
            try:
                info = self.query_channel(app, channel_dict)
                results.append(
                    QueryResult(
                        marketType=market_type,
                        name=name,
                        success=True,
                        onlineVersion=info.get("onlineVersion"),
                        reviewingVersion=info.get("reviewingVersion"),
                        auditStatus=info.get("auditStatus"),
                        releaseStatus=info.get("releaseStatus"),
                    ).to_json()
                )
            except Exception as exc:  # noqa: BLE001 - per-channel failure is output data.
                results.append(
                    QueryResult(
                        marketType=market_type,
                        name=name,
                        success=False,
                        error=str(exc),
                    ).to_json()
                )

        return {
            "app": {
                "name": app.get("name") or "",
                "packageName": package_name,
            },
            "results": results,
        }

    def query_channel(
        self,
        app: dict[str, Any],
        channel: dict[str, Any],
    ) -> dict[str, str | None]:
        market_type = require_str(channel.get("marketType"), "channel.marketType")
        package_name = require_str(app.get("packageName"), "app.packageName")
        credentials = require_dict(channel.get("credentials"), f"{market_type}.credentials")

        handlers = {
            "Huawei": self.query_huawei,
            "Honor": self.query_honor,
            "Vivo": self.query_vivo,
            "Oppo": self.query_oppo,
            "Tencent": self.query_tencent,
            "Xiaomi": self.query_xiaomi,
        }
        handler = handlers.get(market_type)
        if handler is None:
            raise ValueError(f"unsupported marketType: {market_type}")
        return handler(package_name, channel, credentials)

    def query_huawei(
        self,
        package_name: str,
        channel: dict[str, Any],
        credentials: dict[str, Any],
    ) -> dict[str, str | None]:
        auth_mode = credentials.get("huaweiAuthMode") or credentials.get("authMode") or "apiClient"
        if auth_mode not in ("apiClient", "ApiClient", ""):
            raise ValueError("华为巡检脚本仅支持 API 客户端")

        client_id = require_str(credentials.get("clientId"), "Huawei.clientId")
        client_secret = require_str(credentials.get("clientSecret"), "Huawei.clientSecret")
        token_response = self.post_json(
            f"{HUAWEI_AUTH_BASE}/oauth2/v1/token",
            {
                "grant_type": "client_credentials",
                "client_id": client_id,
                "client_secret": client_secret,
            },
            "华为Token",
        )
        require_huawei_success(token_response, "华为Token")
        access_token = token_response.get("access_token")
        if not access_token:
            raise ValueError("华为 Token 接口未返回 access_token")
        headers = {
            "client_id": client_id,
            "Authorization": f"Bearer {access_token}",
            "Accept": "application/json",
        }

        app_id = optional_str(channel.get("marketAppId"))
        if not app_id:
            app_ids = self.get_json(
                f"{HUAWEI_PUBLISH_BASE}/appid-list",
                "华为应用ID列表",
                headers=headers,
                params={"packageName": package_name},
            )
            require_huawei_success(app_ids, "华为应用ID列表")
            entries = app_ids.get("appids") or []
            if not entries:
                raise ValueError("华为未返回 appId")
            app_id = optional_str(entries[0].get("value"))
            if not app_id:
                raise ValueError("华为 appId 为空")

        app_info_response = self.get_json(
            f"{HUAWEI_PUBLISH_BASE}/app-info",
            "华为应用信息",
            headers=headers,
            params={"appId": app_id},
        )
        require_huawei_success(app_info_response, "华为应用信息")
        app_info = app_info_response.get("appInfo") or {}
        audit_info = app_info_response.get("auditInfo") or {}
        release_state = app_info.get("releaseState")
        return {
            "onlineVersion": app_info.get("onShelfVersionNumber") or app_info.get("versionNumber"),
            "reviewingVersion": app_info.get("versionNumber"),
            "auditStatus": huawei_audit_status(release_state, audit_info),
            "releaseStatus": huawei_release_status(release_state, (app_info_response.get("phasedReleaseInfo") or {}).get("state")),
        }

    def query_honor(
        self,
        package_name: str,
        channel: dict[str, Any],
        credentials: dict[str, Any],
    ) -> dict[str, str | None]:
        token_response = self.post_form(
            f"{HONOR_AUTH_BASE}/auth/token",
            "荣耀Token",
            {
                "grant_type": "client_credentials",
                "client_id": require_str(credentials.get("clientId"), "Honor.clientId"),
                "client_secret": require_str(credentials.get("clientSecret"), "Honor.clientSecret"),
            },
        )
        token = require_str(token_response.get("access_token"), "Honor.access_token")
        headers = {"Authorization": f"Bearer {token}"}
        app_id = optional_str(channel.get("marketAppId"))
        if not app_id:
            app_id_response = self.get_json(
                f"{HONOR_PUBLISH_BASE}/get-app-id",
                "荣耀APPID",
                headers=headers,
                params={"pkgName": package_name},
            )
            require_code_success(app_id_response, "荣耀APPID", "code", "msg")
            apps = app_id_response.get("data") or []
            if not apps:
                raise ValueError("荣耀未返回 APPID")
            app_id = optional_str(apps[0].get("id") or apps[0].get("appId"))
            if not app_id:
                raise ValueError("荣耀 APPID 为空")

        detail_response = self.get_json(
            f"{HONOR_PUBLISH_BASE}/get-app-detail",
            "荣耀应用详情",
            headers=headers,
            params={"appId": app_id},
        )
        require_code_success(detail_response, "荣耀应用详情", "code", "msg")
        detail = detail_response.get("data") or {}
        release_response = self.get_json(
            f"{HONOR_PUBLISH_BASE}/get-app-current-release",
            "荣耀最新版本状态",
            headers=headers,
            params={"appId": app_id},
        )
        require_code_success(release_response, "荣耀最新版本状态", "code", "msg")
        current = release_response.get("data") or {}
        detail_release = detail.get("releaseInfo") or detail.get("currentRelease") or {}
        detail_version = detail_release.get("versionName") or detail.get("versionName")
        return {
            "onlineVersion": current.get("versionName") or detail_version,
            "reviewingVersion": current.get("versionName"),
            "auditStatus": honor_audit_status(current.get("auditResult")) or current.get("reviewStatus"),
            "releaseStatus": current.get("publishStatus") or ("已上架" if detail_version else None),
        }

    def query_vivo(
        self,
        package_name: str,
        channel: dict[str, Any],
        credentials: dict[str, Any],
    ) -> dict[str, str | None]:
        data = self.vivo_call(
            "app.query.details",
            {
                "access_key": require_str(credentials.get("accessKey"), "Vivo.accessKey"),
                "access_secret": require_str(credentials.get("accessSecret"), "Vivo.accessSecret"),
                "packageName": package_name,
            },
        )
        sale_status = data.get("saleStatus", data.get("onlineStatus"))
        return {
            "onlineVersion": data.get("versionName"),
            "reviewingVersion": data.get("versionName"),
            "auditStatus": vivo_audit_status(data.get("status"), data.get("unPassReason")),
            "releaseStatus": vivo_release_status(sale_status, data.get("onlineType")),
        }

    def query_oppo(
        self,
        package_name: str,
        channel: dict[str, Any],
        credentials: dict[str, Any],
    ) -> dict[str, str | None]:
        client_id = require_str(credentials.get("clientId"), "Oppo.clientId")
        client_secret = require_str(credentials.get("clientSecret"), "Oppo.clientSecret")
        token_response = self.get_json(
            f"{OPPO_BASE}/developer/v1/token",
            "OPPO Token",
            params={"client_id": client_id, "client_secret": client_secret},
        )
        require_code_success(token_response, "OPPO Token", "errno", "errmsg")
        data = token_response.get("data") or {}
        access_token = require_str(data.get("access_token"), "Oppo.access_token")
        params = {
            "access_token": access_token,
            "timestamp": current_seconds(),
            "pkg_name": package_name,
        }
        params["api_sign"] = hmac_sha256_hex(client_secret, sign_plain(params))
        info_response = self.get_json(
            f"{OPPO_BASE}/resource/v1/app/info",
            "OPPO应用详情",
            params=params,
        )
        require_code_success(info_response, "OPPO应用详情", "errno", "errmsg")
        info = info_response.get("data") or {}
        audit_status = info.get("audit_status")
        release_status = info.get("release_status")
        return {
            "onlineVersion": info.get("version_name"),
            "reviewingVersion": info.get("version_name"),
            "auditStatus": info.get("audit_status_name") or oppo_status_text(audit_status),
            "releaseStatus": oppo_status_text(release_status) or oppo_status_text(audit_status),
        }

    def query_tencent(
        self,
        package_name: str,
        channel: dict[str, Any],
        credentials: dict[str, Any],
    ) -> dict[str, str | None]:
        app_id = require_str(channel.get("marketAppId"), "Tencent.marketAppId")
        user_id = require_str(credentials.get("userId"), "Tencent.userId")
        access_secret = require_str(credentials.get("accessSecret"), "Tencent.accessSecret")
        detail = self.tencent_signed_post(
            "query_app_detail",
            {
                "user_id": user_id,
                "timestamp": current_seconds(),
                "pkg_name": package_name,
                "app_id": app_id,
            },
            access_secret,
            "腾讯应用详情",
        )
        update_status = self.tencent_signed_post(
            "query_app_update_status",
            {
                "user_id": user_id,
                "timestamp": current_seconds(),
                "pkg_name": package_name,
                "app_id": app_id,
            },
            access_secret,
            "腾讯审核状态",
            allow_not_found=True,
        )
        audit_status = update_status.get("audit_status") if update_status else None
        online_version = detail.get("version_name") or self.fetch_tencent_store_version(package_name)
        return {
            "onlineVersion": online_version,
            "reviewingVersion": "腾讯接口暂不支持获取审核中版本",
            "auditStatus": tencent_audit_status(audit_status),
            "releaseStatus": tencent_release_status(audit_status),
        }

    def query_xiaomi(
        self,
        package_name: str,
        channel: dict[str, Any],
        credentials: dict[str, Any],
    ) -> dict[str, str | None]:
        user_name = require_str(credentials.get("userName"), "Xiaomi.userName")
        password = require_str(credentials.get("password"), "Xiaomi.password")
        public_key = require_str(credentials.get("publicKey"), "Xiaomi.publicKey")
        request_data = json.dumps(
            {"packageName": package_name, "userName": user_name},
            ensure_ascii=False,
            separators=(",", ":"),
        )
        response = self.xiaomi_signed_post(
            f"{XIAOMI_BASE}/dev/query",
            request_data,
            password,
            public_key,
            "小米查询应用",
        )
        package_info = response.get("packageInfo") or {}
        return {
            "onlineVersion": package_info.get("versionName"),
            "reviewingVersion": "小米接口暂不支持获取审核中版本",
            "auditStatus": "小米暂不支持获取审核状态",
            "releaseStatus": xiaomi_release_status(package_info or None, response.get("create")),
        }

    def vivo_call(self, method: str, values: dict[str, str]) -> dict[str, Any]:
        access_secret = values.pop("access_secret")
        params = {
            "method": method,
            "access_key": values.pop("access_key"),
            "timestamp": current_millis(),
            "format": "json",
            "v": "1.0",
            "sign_method": "HMAC-SHA256",
            "target_app_key": "developer",
            **values,
        }
        params["sign"] = hmac_sha256_hex(access_secret, sign_plain(params))
        response = self.post_form(VIVO_BASE, "vivo应用详情", params)
        if response.get("code") == "10018":
            raise ValueError("vivo 当前接入信息没有 app.query.details 访问权限")
        if response.get("code") not in (None, 0, "0"):
            raise ValueError(f"vivo应用详情 code={response.get('code')}: {response.get('msg') or response.get('subMsg') or ''}")
        data = response.get("data")
        if not isinstance(data, dict):
            raise ValueError("vivo 未返回应用详情")
        return data

    def tencent_signed_post(
        self,
        path: str,
        params: dict[str, str],
        access_secret: str,
        market_name: str,
        allow_not_found: bool = False,
    ) -> dict[str, Any] | None:
        params["sign"] = hmac_sha256_hex(access_secret, sign_plain(params))
        response = self.post_form(f"{TENCENT_BASE}/{path}", market_name, params)
        ret = response.get("ret")
        if allow_not_found and ret == 5000002:
            return None
        if ret not in (None, 0, "0"):
            raise ValueError(f"{market_name} ret={ret}: {response.get('msg') or ''}")
        return response

    def xiaomi_signed_post(
        self,
        url: str,
        request_data: str,
        password: str,
        public_key: str,
        market_name: str,
    ) -> dict[str, Any]:
        sig_payload = json.dumps(
            {
                "password": password,
                "sig": [{"name": "RequestData", "hash": hashlib.md5(request_data.encode()).hexdigest()}],
            },
            ensure_ascii=False,
            separators=(",", ":"),
        )
        response = self.post_form(
            url,
            market_name,
            {
                "RequestData": request_data,
                "SIG": rsa_public_encrypt_hex(public_key, sig_payload),
            },
        )
        result = response.get("result")
        if result not in (None, 0, "0"):
            raise ValueError(f"{market_name} result={result}: {response.get('message') or ''}")
        return response

    def fetch_tencent_store_version(self, package_name: str) -> str | None:
        response = self.session.get(f"https://sj.qq.com/appdetail/{package_name}", timeout=30)
        text = response.text
        text = re.sub(r"(?is)<script.*?</script>", " ", text)
        text = re.sub(r"(?is)<style.*?</style>", " ", text)
        text = re.sub(r"<[^>]+>", "\n", text).replace("&nbsp;", " ").replace("&amp;", "&")
        lines = "\n".join(line.strip() for line in text.splitlines() if line.strip())
        version_pattern = r"[0-9]+(?:[._-][0-9A-Za-z]+)+"
        match = re.search(rf"版本号\s*[:：]?\s*({version_pattern})", lines)
        return match.group(1) if match else None

    def get_json(
        self,
        url: str,
        market_name: str,
        headers: dict[str, str] | None = None,
        params: dict[str, str] | None = None,
    ) -> dict[str, Any]:
        response = self.session.get(url, headers=headers, params=params, timeout=30)
        return response_json(response, market_name)

    def post_json(
        self,
        url: str,
        body: dict[str, Any],
        market_name: str,
    ) -> dict[str, Any]:
        response = self.session.post(url, json=body, timeout=30)
        return response_json(response, market_name)

    def post_form(
        self,
        url: str,
        market_name: str,
        data: dict[str, str],
    ) -> dict[str, Any]:
        response = self.session.post(url, data=data, timeout=30)
        return response_json(response, market_name)


def response_json(response: Any, market_name: str) -> dict[str, Any]:
    text = response.text.strip()
    if not text:
        raise ValueError(f"{market_name} 返回了空响应")
    if text.startswith("<"):
        raise ValueError(f"{market_name} 返回了非 JSON 响应：{text[:300]}")
    data = json.loads(text)
    if not isinstance(data, dict):
        raise ValueError(f"{market_name} 响应不是 JSON 对象")
    return data


def require_dict(value: Any, name: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ValueError(f"{name} must be an object")
    return value


def require_str(value: Any, name: str) -> str:
    text = optional_str(value)
    if not text:
        raise ValueError(f"{name} is required")
    return text


def optional_str(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def sign_plain(params: dict[str, Any]) -> str:
    return "&".join(f"{key}={value}" for key, value in sorted(params.items()) if str(value) != "")


def hmac_sha256_hex(secret: str, plain: str) -> str:
    return hmac.new(secret.encode(), plain.encode(), hashlib.sha256).hexdigest()


def current_seconds() -> str:
    return str(int(time.time()))


def current_millis() -> str:
    return str(int(time.time() * 1000))


def require_code_success(
    response: dict[str, Any],
    market_name: str,
    code_key: str,
    message_key: str,
) -> None:
    code = response.get(code_key)
    if code not in (None, 0, "0"):
        raise ValueError(f"{market_name} {code_key}={code}: {response.get(message_key) or ''}")


def require_huawei_success(response: dict[str, Any], market_name: str) -> None:
    ret = response.get("ret")
    if not isinstance(ret, dict):
        return
    code = ret.get("code")
    if code not in (None, 0, "0"):
        raise ValueError(f"{market_name} code={code}: {ret.get('msg') or ''}")


def huawei_audit_status(release_state: Any, audit_info: dict[str, Any]) -> str | None:
    base = {
        0: "已上架",
        1: "上架审核不通过",
        2: "已下架",
        3: "待上架，预约上架",
        4: "审核中",
        5: "升级审核中",
        6: "申请下架",
        7: "草稿",
        8: "升级审核不通过",
        9: "下架审核不通过",
        10: "开发者下架",
        11: "撤销上架",
        12: "预审中",
        13: "预审不通过",
    }.get(as_int(release_state), f"未知审核状态({release_state})" if release_state is not None else None)
    extras = []
    opinion = audit_info.get("auditOpinion")
    if opinion:
        extras.append(strip_html(str(opinion)))
    return "，".join([item for item in [base, *extras] if item])


def huawei_release_status(release_state: Any, phased_state: Any) -> str | None:
    state = as_int(release_state)
    text = "已上架" if state == 0 else "未上架" if state is not None else None
    if phased_state == "RELEASE":
        return "，".join([item for item in [text, "分阶段发布中"] if item])
    return text


def honor_audit_status(status: Any) -> str | None:
    return {
        0: "审核中",
        1: "审核通过",
        2: "审核不通过",
        3: "其他非审核状态",
        4: "编辑中，未提交审核",
    }.get(as_int(status))


def vivo_audit_status(status: Any, un_pass_reason: Any) -> str | None:
    value = as_int(status)
    text = {
        1: "草稿",
        2: "待审核",
        3: "审核通过",
        4: "审核不通过",
        5: "撤销审核",
    }.get(value, f"未知审核状态({status})" if status is not None else None)
    reason = optional_str(un_pass_reason) if value == 4 else None
    return "：".join([item for item in [text, reason] if item])


def vivo_release_status(sale_status: Any, online_type: Any) -> str | None:
    sale = {
        0: "待上架",
        1: "已上架",
        2: "已下架",
    }.get(as_int(sale_status), f"未知上架状态({sale_status})" if sale_status is not None else None)
    online = {
        1: "实时上架",
        2: "定时上架",
    }.get(as_int(online_type), f"未知上架类型({online_type})" if online_type is not None else None)
    return "，".join([item for item in [sale, online] if item])


def oppo_status_text(status: Any) -> str | None:
    if status is None or str(status) == "":
        return None
    return {
        "0": "未发布",
        "1": "审核中",
        "2": "审核通过",
        "3": "测试不通过",
        "4": "运营审核中",
        "5": "运营打回",
        "6": "运营通过",
        "7": "定时发布",
        "00": "资质审核中",
        "11": "资质审核通过",
        "-11": "资质审核不通过",
        "-22": "报备提交成功",
        "22": "已冻结",
        "111": "上线",
        "222": "下线",
        "444": "审核不通过",
    }.get(str(status), str(status))


def tencent_audit_status(status: Any) -> str | None:
    return {
        1: "审核中",
        2: "审核驳回",
        3: "审核通过",
        8: "开发者主动撤销",
    }.get(as_int(status), f"未知审核状态({status})" if status is not None else None)


def tencent_release_status(status: Any) -> str:
    return {
        1: "已上架，更新审核中",
        2: "已上架，更新审核驳回",
        3: "已上架，更新审核通过",
        8: "已上架，更新已撤销",
    }.get(as_int(status), "已上架")


def xiaomi_release_status(package_info: dict[str, Any] | None, create: Any) -> str:
    if package_info:
        return "已存在"
    if create is True:
        return "未创建，可新增"
    if create is False:
        return "未创建，不可新增"
    return "未返回上架状态"


def strip_html(value: str) -> str:
    return re.sub(r"<[^>]+>", "", value).strip()


def as_int(value: Any) -> int | None:
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def rsa_public_encrypt_hex(public_key: str, plain: str) -> str:
    try:
        from cryptography import x509
        from cryptography.hazmat.primitives import serialization
        from cryptography.hazmat.primitives.asymmetric import padding
    except ImportError as exc:  # pragma: no cover - dependency guard.
        raise RuntimeError("Xiaomi requires cryptography: python3 -m pip install cryptography") from exc

    key_bytes = public_key.encode()
    if "BEGIN CERTIFICATE" in public_key:
        key = x509.load_pem_x509_certificate(key_bytes).public_key()
    else:
        key = serialization.load_pem_public_key(key_bytes)
    key_size = key.key_size // 8
    block_size = key_size - 11
    encrypted = bytearray()
    data = plain.encode()
    for offset in range(0, len(data), block_size):
        encrypted.extend(key.encrypt(data[offset : offset + block_size], padding.PKCS1v15()))
    return encrypted.hex()


def load_export(path: str) -> dict[str, Any]:
    with open(path, "r", encoding="utf-8") as file:
        data = json.load(file)
    if not isinstance(data, dict):
        raise ValueError("export JSON must be an object")
    require_dict(data.get("app"), "app")
    require_str(data["app"].get("packageName"), "app.packageName")
    return data


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Query Android market versions from Andpub settings JSON.")
    parser.add_argument("export_json", help="Path to exported Andpub app settings JSON")
    args = parser.parse_args(argv)

    if requests is None:
        print("requests is required: python3 -m pip install requests cryptography", file=sys.stderr)
        return 2

    try:
        exported = load_export(args.export_json)
        result = MarketVersionQuery(requests.Session()).query_export(exported)
    except Exception as exc:  # noqa: BLE001 - CLI should report invalid input clearly.
        print(str(exc), file=sys.stderr)
        return 1

    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
