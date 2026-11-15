#!/usr/bin/env python3
"""晓晓呀 KTV 离线注册机。首次运行生成 RSA 密钥，私钥只留在卖家电脑。"""
import argparse, base64, json, os, secrets, sys
from pathlib import Path
from datetime import datetime, timezone
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa, padding

ROOT = Path(__file__).resolve().parent
PRIVATE = ROOT / "private_key.pem"
PUBLIC = ROOT / "public_key.b64"

def b64u(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode().rstrip("=")

def init_keys():
    if PRIVATE.exists():
        raise SystemExit("密钥已存在；为避免覆盖，请先备份现有密钥。")
    key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    PRIVATE.write_bytes(key.private_bytes(
        serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
        serialization.NoEncryption()))
    os.chmod(PRIVATE, 0o600)
    pub = key.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    PUBLIC.write_text(base64.b64encode(pub).decode() + "\n", encoding="ascii")
    print("密钥创建完成。private_key.pem 是核心资产，必须离线备份，绝不可发送或上传。")
    print("将 public_key.b64 的内容作为 KTV_LICENSE_PUBLIC_KEY_B64 环境变量用于 APK 构建。")

def issue(machine: str):
    if not PRIVATE.exists():
        raise SystemExit("尚未创建密钥，请先运行：python register.py init")
    device = machine.strip().replace("-", "").lower()
    if not device or len(device) > 128:
        raise SystemExit("机器码格式无效")
    key = serialization.load_pem_private_key(PRIVATE.read_bytes(), password=None)
    payload = json.dumps({
        "product": "xiaoxiao-ktv", "deviceId": device,
        "perpetual": True, "issuedAt": datetime.now(timezone.utc).isoformat(),
        "licenseId": secrets.token_hex(8)
    }, separators=(",", ":"), ensure_ascii=False).encode()
    signature = key.sign(payload, padding.PKCS1v15(), hashes.SHA256())
    print("授权码（完整复制给用户）：")
    print(b64u(payload) + "." + b64u(signature))

def main():
    p=argparse.ArgumentParser(description="晓晓呀 KTV 离线注册机")
    sub=p.add_subparsers(dest="cmd",required=True)
    sub.add_parser("init",help="首次生成密钥对")
    i=sub.add_parser("issue",help="根据机器码生成永久授权码")
    i.add_argument("machine_code")
    a=p.parse_args()
    if a.cmd=="init": init_keys()
    else: issue(a.machine_code)

if __name__=="__main__": main()
