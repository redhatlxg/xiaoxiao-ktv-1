#!/usr/bin/env python3
"""晓晓呀 KTV Windows 图形注册机。私钥只保存在本机 tools/offline-license 目录。"""
import base64
import json
import secrets
import tkinter as tk
from tkinter import messagebox, ttk
from datetime import datetime, timezone
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

ROOT = Path(__file__).resolve().parent
PRIVATE = ROOT / "private_key.pem"
PUBLIC = ROOT / "public_key.b64"

def b64u(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("ascii").rstrip("=")

class IssuerApp:
    def __init__(self, root):
        self.root = root
        root.title("晓晓呀 KTV · 离线授权生成器")
        root.geometry("720x500")
        root.minsize(620, 430)
        root.configure(padx=18, pady=16)

        ttk.Label(root, text="晓晓呀 KTV · 离线授权生成器",
                  font=("Microsoft YaHei UI", 17, "bold")).pack(anchor="w")
        ttk.Label(root, text="永久授权 · 一机一码 · 无需联网",
                  font=("Microsoft YaHei UI", 10)).pack(anchor="w", pady=(2, 14))

        key_state = "已存在授权密钥" if PRIVATE.exists() and PUBLIC.exists() else "尚未生成授权密钥"
        self.key_label = ttk.Label(root, text=key_state)
        self.key_label.pack(anchor="w", pady=(0, 10))

        ttk.Button(root, text="首次生成密钥对", command=self.init_keys).pack(anchor="w", pady=(0, 14))

        ttk.Label(root, text="用户设备机器码（从电视激活页面复制）：").pack(anchor="w")
        self.machine = tk.Text(root, height=3, wrap="word", font=("Consolas", 11))
        self.machine.pack(fill="x", pady=(5, 12))

        ttk.Button(root, text="生成永久授权码", command=self.issue).pack(anchor="w")
        ttk.Label(root, text="生成的授权码（复制后发给对应用户）：").pack(anchor="w", pady=(14, 5))
        self.output = tk.Text(root, height=6, wrap="word", font=("Consolas", 9))
        self.output.pack(fill="both", expand=True)

        actions = ttk.Frame(root)
        actions.pack(fill="x", pady=(10, 0))
        ttk.Button(actions, text="复制授权码", command=self.copy).pack(side="left")
        ttk.Label(root, text="安全提示：private_key.pem 是核心签名私钥，绝不能发给客户或上传 GitHub；请离线加密备份。",
                  foreground="#a33", wraplength=680).pack(anchor="w", pady=(12, 0))

    def init_keys(self):
        if PRIVATE.exists() or PUBLIC.exists():
            messagebox.showwarning("密钥已存在", "检测到已有密钥文件。为避免已售授权失效，禁止覆盖。")
            return
        try:
            key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
            private_bytes = key.private_bytes(
                serialization.Encoding.PEM,
                serialization.PrivateFormat.PKCS8,
                serialization.NoEncryption()
            )
            public_der = key.public_key().public_bytes(
                serialization.Encoding.DER,
                serialization.PublicFormat.SubjectPublicKeyInfo
            )
            PRIVATE.write_bytes(private_bytes)
            PUBLIC.write_text(base64.b64encode(public_der).decode("ascii") + "\n", encoding="ascii")
            try:
                PRIVATE.chmod(0o600)
            except OSError:
                pass
            self.key_label.config(text="密钥已生成 · 请立即备份 private_key.pem 和 public_key.b64")
            messagebox.showinfo("生成成功", "密钥对已生成。\n\n1. 备份 private_key.pem（绝不能外传）\n2. 将 public_key.b64 内容设置为 GitHub Secret：KTV_LICENSE_PUBLIC_KEY_B64\n3. 之后不可随意重新生成密钥。")
        except Exception as exc:
            messagebox.showerror("生成失败", str(exc))

    def issue(self):
        if not PRIVATE.exists():
            messagebox.showerror("缺少私钥", "请先点击“首次生成密钥对”。")
            return
        machine = "".join(self.machine.get("1.0", "end").split()).replace("-", "").lower()
        if not machine or len(machine) > 128 or any(ch not in "0123456789abcdef" for ch in machine):
            messagebox.showwarning("机器码格式不正确", "请粘贴软件激活页显示的机器码（十六进制字符）。")
            return
        try:
            key = serialization.load_pem_private_key(PRIVATE.read_bytes(), password=None)
            payload = json.dumps({
                "product": "xiaoxiao-ktv",
                "deviceId": machine,
                "perpetual": True,
                "issuedAt": datetime.now(timezone.utc).isoformat(),
                "licenseId": secrets.token_hex(8)
            }, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
            signature = key.sign(payload, padding.PKCS1v15(), hashes.SHA256())
            token = b64u(payload) + "." + b64u(signature)
            self.output.delete("1.0", "end")
            self.output.insert("1.0", token)
        except Exception as exc:
            messagebox.showerror("签发失败", str(exc))

    def copy(self):
        token = self.output.get("1.0", "end").strip()
        if not token:
            messagebox.showwarning("没有授权码", "请先生成授权码。")
            return
        self.root.clipboard_clear()
        self.root.clipboard_append(token)
        self.root.update()
        messagebox.showinfo("已复制", "授权码已复制到剪贴板。")

if __name__ == "__main__":
    window = tk.Tk()
    try:
        ttk.Style().theme_use("vista")
    except tk.TclError:
        pass
    IssuerApp(window)
    window.mainloop()
