import json
import unittest

import query_market_versions as qmv


class FakeResponse:
    def __init__(self, data):
        self.text = data if isinstance(data, str) else json.dumps(data, ensure_ascii=False)


class FakeSession:
    def __init__(self):
        self.requests = []

    def get(self, url, headers=None, params=None, timeout=None):
        self.requests.append(("GET", url, headers or {}, params or {}))
        if url.endswith("/appid-list"):
            return FakeResponse({"ret": {"code": 0, "msg": "ok"}, "appids": [{"value": "10001"}]})
        if url.endswith("/app-info"):
            return FakeResponse(
                {
                    "ret": {"code": 0, "msg": "ok"},
                    "appInfo": {
                        "releaseState": 5,
                        "versionNumber": "1.1.0",
                        "onShelfVersionNumber": "1.0.0",
                    },
                    "auditInfo": {"auditOpinion": "<p>等待审核</p>"},
                }
            )
        if "sj.qq.com" in url:
            return FakeResponse("<html>版本号：6.6.3</html>")
        raise AssertionError(f"unexpected GET {url}")

    def post(self, url, json=None, data=None, timeout=None):
        self.requests.append(("POST", url, {}, json or data or {}))
        if url.endswith("/oauth2/v1/token"):
            return FakeResponse({"access_token": "token"})
        if url == qmv.VIVO_BASE:
            return FakeResponse(
                {
                    "code": "0",
                    "msg": "success",
                    "data": {
                        "versionName": "6.6.4",
                        "status": 2,
                        "saleStatus": 1,
                    },
                }
            )
        if url.endswith("/dev/query"):
            return FakeResponse({"result": 0, "packageInfo": {"versionName": "6.6.3"}})
        raise AssertionError(f"unexpected POST {url}")


class MarketVersionQueryTest(unittest.TestCase):
    def test_huawei_api_client_success(self):
        exported = {
            "app": {"name": "App", "packageName": "cn.example"},
            "channels": [
                {
                    "name": "Huawei",
                    "marketType": "Huawei",
                    "marketAppId": None,
                    "credentials": {"clientId": "cid", "clientSecret": "secret"},
                    "extraFields": {},
                }
            ],
        }

        result = qmv.MarketVersionQuery(FakeSession()).query_export(exported)

        self.assertEqual(result["results"][0]["success"], True)
        self.assertEqual(result["results"][0]["onlineVersion"], "1.0.0")
        self.assertEqual(result["results"][0]["reviewingVersion"], "1.1.0")
        self.assertEqual(result["results"][0]["auditStatus"], "升级审核中，等待审核")

    def test_partial_failure_keeps_results(self):
        exported = {
            "app": {"name": "App", "packageName": "cn.example"},
            "channels": [
                {
                    "name": "vivo",
                    "marketType": "Vivo",
                    "marketAppId": None,
                    "credentials": {"accessKey": "ak", "accessSecret": "secret"},
                    "extraFields": {},
                },
                {
                    "name": "Huawei Service Account",
                    "marketType": "Huawei",
                    "marketAppId": "10001",
                    "credentials": {"huaweiAuthMode": "serviceAccount"},
                    "extraFields": {},
                },
            ],
        }

        result = qmv.MarketVersionQuery(FakeSession()).query_export(exported)

        self.assertEqual(result["results"][0]["success"], True)
        self.assertEqual(result["results"][0]["auditStatus"], "待审核")
        self.assertEqual(result["results"][0]["releaseStatus"], "已上架")
        self.assertEqual(result["results"][1]["success"], False)
        self.assertIn("仅支持 API 客户端", result["results"][1]["error"])

    def test_xiaomi_outputs_unsupported_audit_status(self):
        original_encrypt = qmv.rsa_public_encrypt_hex
        qmv.rsa_public_encrypt_hex = lambda public_key, plain: "sig"
        try:
            exported = {
                "app": {"name": "App", "packageName": "cn.example"},
                "channels": [
                    {
                        "name": "xiaomi",
                        "marketType": "Xiaomi",
                        "marketAppId": None,
                        "credentials": {
                            "userName": "user",
                            "password": "password",
                            "publicKey": "public",
                        },
                        "extraFields": {},
                    }
                ],
            }

            result = qmv.MarketVersionQuery(FakeSession()).query_export(exported)

            self.assertEqual(result["results"][0]["success"], True)
            self.assertEqual(result["results"][0]["onlineVersion"], "6.6.3")
            self.assertIsNone(result["results"][0]["reviewingVersion"])
            self.assertEqual(result["results"][0]["auditStatus"], "小米暂不支持获取审核状态")
        finally:
            qmv.rsa_public_encrypt_hex = original_encrypt

    def test_invalid_input_requires_package_name(self):
        with self.assertRaisesRegex(ValueError, "app.packageName is required"):
            qmv.MarketVersionQuery(FakeSession()).query_export({"app": {"name": "App"}, "channels": []})


if __name__ == "__main__":
    unittest.main()
