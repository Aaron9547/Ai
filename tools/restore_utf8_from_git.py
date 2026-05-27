#!/usr/bin/env python3
"""Restore text files from git HEAD as UTF-8 (no BOM). Optional --fix-truncations."""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# Common HEAD truncations: ? before "); where last CJK char was lost
TRUNCATION_FIXES: list[tuple[str, str]] = [
    ('可查看已退出成?);', '可查看已退出成员");'),
    ('可按租户筛选成?);', '可按租户筛选成员");'),
    ('可指定目标租?);', '可指定目标租户");'),
    ('缺少租户上下?);', '缺少租户上下文");'),
    ('或所有者角?);', '或所有者角色");'),
    ('限流规?);', '限流规则");'),
    ('创建限流规?);', '创建限流规则");'),
    ('定时任?);', '定时任务");'),
    ('持?poller ?);', '持有 poller 锁");'),
    ('邀?恢复', '邀请/恢复'),
    ('改角色?', '改角色。'),
    ('创始人可?', '创始人可传'),
    ('不一致?', '不一致。'),
    ('成员?', '成员。'),
    ('可?', '可用'),
    ('登录?', '登录名'),
    ('用户?', '用户。'),
    ('词不?为?', '词不能为空");'),  # may need care
]

DEFAULT_PATHS = [
    "modules/ai-identity/src/main/java/com/aaron/cloud/identity/tenant/TenantMemberRoleApplicationService.java",
    "modules/ai-common/src/main/java/com/aaron/cloud/common/profile/UserProfileApplicationService.java",
    "modules/ai-common/src/main/java/com/aaron/cloud/common/profile/UserMemoryApplicationService.java",
    "modules/ai-chat/src/main/java/com/aaron/cloud/chat/intent/TravelReimbursementIntentRunner.java",
    "modules/ai-chat/src/main/java/com/aaron/cloud/chat/GuardrailSensitiveTermAdminService.java",
    "modules/ai-chat/src/main/java/com/aaron/cloud/chat/intent/ChatIntentStreamRouter.java",
    "modules/ai-gateway/src/main/java/com/aaron/cloud/gateway/GatewayRateLimitApplicationService.java",
    "modules/ai-job/src/main/java/com/aaron/cloud/scheduled/TenantScheduledTaskPoller.java",
    "modules/ai-job/src/main/java/com/aaron/cloud/scheduled/run/TenantScheduledRunOrchestrator.java",
]


def git_show(rel: str) -> bytes:
    proc = subprocess.run(
        ["git", "show", f"HEAD:{rel}"],
        cwd=ROOT,
        capture_output=True,
        check=True,
    )
    return proc.stdout


def restore_one(rel: str, fix_trunc: bool) -> None:
    data = git_show(rel)
    if data.startswith(b"\xef\xbb\xbf"):
        data = data[3:]
    text = data.decode("utf-8", errors="strict")
    if fix_trunc:
        for old, new in TRUNCATION_FIXES:
            text = text.replace(old, new)
        # Generic: AccessDeniedException("....?); -> ");
        text = re.sub(
            r'(throw new AccessDeniedException\("[^"]*)\?\);',
            r'\1");',
            text,
        )
        text = re.sub(
            r'(throw new ResponseStatusException\([^,]+,\s*"[^"]*)\?\);',
            r'\1");',
            text,
        )
        text = re.sub(
            r'(log\.debug\("[^"]*)\?\);',
            r'\1");',
            text,
        )
        text = re.sub(
            r'(failRun\(run,\s*"[^"]*)\?\);',
            r'\1");',
            text,
        )
        # Split merged comment + code (identity listMembers)
        text = text.replace(
            "默认行为）。        UserAccountStatus statusFilter",
            "默认行为）。\n        UserAccountStatus statusFilter",
        )
    dest = ROOT / rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(text, encoding="utf-8", newline="\n")
    print("restored", rel)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("paths", nargs="*", help="Repo-relative paths")
    parser.add_argument("--fix-truncations", action="store_true", default=True)
    parser.add_argument("--no-fix-truncations", action="store_false", dest="fix_truncations")
    args = parser.parse_args()
    paths = args.paths or DEFAULT_PATHS
    for rel in paths:
        try:
            restore_one(rel, args.fix_truncations)
        except subprocess.CalledProcessError as e:
            print("skip (not in HEAD):", rel, e, file=sys.stderr)
        except FileNotFoundError:
            print("skip:", rel, file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
