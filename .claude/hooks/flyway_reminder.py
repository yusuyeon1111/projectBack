#!/usr/bin/env python3
"""PostToolUse(Edit|Write): 엔티티를 고쳤는데 새 Flyway 마이그레이션이 없으면 알린다.

ddl-auto: validate라 스키마와 엔티티가 어긋나면 기동이 멈춘다. 모든 엔티티 수정이
스키마 변경은 아니므로 막지 않고 Claude에게 확인하라고만 전한다.
"""
import json, subprocess, sys

data = json.load(sys.stdin)
path = data.get("tool_input", {}).get("file_path", "")
if "/src/main/java/" not in path or "/entity/" not in path or not path.endswith(".java"):
    sys.exit(0)

new = subprocess.run(["git", "-C", data.get("cwd", "."), "status", "--porcelain", "--", "src/main/resources/db/migration"],
                     capture_output=True, text=True).stdout.strip()
if not new:
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "PostToolUse",
        "additionalContext": "엔티티를 고쳤는데 새 Flyway 마이그레이션이 없다. 컬럼·테이블·제약이 바뀌었다면 "
                             "src/main/resources/db/migration에 V{다음 번호}__설명.sql을 추가한다(이미 적용된 파일은 고치지 않는다). "
                             "스키마와 무관한 수정이면 무시한다.",
    }}, ensure_ascii=False))
