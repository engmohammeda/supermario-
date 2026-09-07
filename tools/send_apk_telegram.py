#!/usr/bin/env python3
"""
Telegram APK Dispatcher Tool
----------------------------
Sends the release APK to a Telegram chat/bot with progress reporting,
hash verification, and clean error handling without external dependencies.

Secrets come from the environment (CI secrets), never from this file:
    BOT_TOKEN  -- the Telegram bot token
    CHAT_ID    -- the chat / User ID (default 5926222376)

Usage:
    python3 send_apk_telegram.py <apk> [--version v0.2.0] [--caption "extra"]
"""

import os
import sys
import json
import uuid
import hashlib
import argparse
from urllib import request, error


def get_env_or_default(key: str, default: str = "") -> str:
    val = os.getenv(key)
    return val.strip() if val and val.strip() else default


def calculate_sha256(file_path: str) -> str:
    sha = hashlib.sha256()
    with open(file_path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            sha.update(chunk)
    return sha.hexdigest()


def send_telegram_document(token: str, chat_id: str, file_path: str, caption: str = "") -> dict:
    url = f"https://api.telegram.org/bot{token}/sendDocument"
    boundary = f"----WebKitFormBoundary{uuid.uuid4().hex}"

    filename = os.path.basename(file_path)
    file_size = os.path.getsize(file_path)

    print(f"[*] Preparing document: {filename} ({file_size / (1024 * 1024):.2f} MB)")

    with open(file_path, "rb") as f:
        file_bytes = f.read()

    body = bytearray()

    def field(name: str, value: str):
        body.extend(f"--{boundary}\r\n".encode("utf-8"))
        body.extend(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode("utf-8"))
        body.extend(f"{value}\r\n".encode("utf-8"))

    field("chat_id", chat_id)
    field("parse_mode", "HTML")
    if caption:
        field("caption", caption)

    body.extend(f"--{boundary}\r\n".encode("utf-8"))
    body.extend(
        f'Content-Disposition: form-data; name="document"; filename="{filename}"\r\n'.encode("utf-8")
    )
    body.extend(b"Content-Type: application/vnd.android.package-archive\r\n\r\n")
    body.extend(file_bytes)
    body.extend(b"\r\n")
    body.extend(f"--{boundary}--\r\n".encode("utf-8"))

    req = request.Request(url, data=bytes(body))
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    req.add_header("User-Agent", "MarioBox-Telegram-Uploader/2.0")

    print("[*] Uploading to Telegram API...")
    try:
        with request.urlopen(req, timeout=180) as resp:
            resp_data = resp.read().decode("utf-8")
            return json.loads(resp_data)
    except error.HTTPError as e:
        err_msg = e.read().decode("utf-8")
        raise RuntimeError(f"HTTP {e.code}: {err_msg}")
    except Exception as e:
        raise RuntimeError(f"Network error: {e}")


def main():
    parser = argparse.ArgumentParser(description="Send an APK to Telegram")
    parser.add_argument("apk", nargs="?", default=None, help="path to the APK")
    parser.add_argument("--version", default="", help="version label shown in the message")
    parser.add_argument("--caption", default="", help="extra caption text")
    args = parser.parse_args()

    bot_token = get_env_or_default("BOT_TOKEN")
    if not bot_token:
        print("[!] Error: BOT_TOKEN environment variable is not set")
        sys.exit(1)
    chat_id = get_env_or_default("CHAT_ID", "5926222376")

    apk_path = args.apk
    if not apk_path or not os.path.exists(apk_path):
        candidates = [
            "app/build/outputs/apk/release/app-release-unsigned.apk",
            "app/build/outputs/apk/release/app-release.apk",
            "app/build/outputs/apk/debug/app-debug.apk",
        ]
        for c in candidates:
            if os.path.exists(c):
                apk_path = c
                break
    if not apk_path or not os.path.exists(apk_path):
        print(f"[!] Error: File not found at '{apk_path}'")
        sys.exit(1)

    file_size_mb = os.path.getsize(apk_path) / (1024 * 1024)
    file_sha256 = calculate_sha256(apk_path)
    label = args.version or os.path.basename(apk_path)

    caption = (
        f"🎮 <b>MarioBox - {label}</b>\n\n"
        f"📦 <b>File:</b> <code>{os.path.basename(apk_path)}</code>\n"
        f"⚡ <b>Size:</b> <code>{file_size_mb:.2f} MB</code> (R8 + ProGuard)\n"
        f"🔒 <b>SHA-256:</b>\n<code>{file_sha256[:32]}...</code>\n\n"
        f"✅ <i>Ready for direct installation on Android.</i>"
    )
    if args.caption:
        caption += "\n\n" + args.caption

    try:
        result = send_telegram_document(bot_token, chat_id, apk_path, caption)
        if result.get("ok"):
            msg_id = result.get("result", {}).get("message_id")
            print(f"[+] Success! APK sent to Chat ID: {chat_id} (Message ID: {msg_id})")
        else:
            print(f"[!] Telegram API returned error: {result}")
            sys.exit(1)
    except Exception as ex:
        print(f"[!] Upload failed: {ex}")
        sys.exit(1)


if __name__ == "__main__":
    main()
