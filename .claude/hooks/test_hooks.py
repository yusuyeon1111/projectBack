#!/usr/bin/env python3
"""hook 회귀 테스트. 실행: python3 .claude/hooks/test_hooks.py"""
import json, os, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from guard_git import check  # noqa: E402

HEREDOC = "git commit -q -F - <<'EOF'\n{}\n\n본문\nEOF"
SUBST = 'git commit -m "$(cat <<\'EOF\'\n{}\n\n본문\nEOF\n)"'
BLOCK = [
    ("main", 'git commit -m "fix(back): 고침"'),                 # main 직접 커밋
    ("feat/x", 'git commit -m "Update SecurityConfig.java"'),    # 형식 아님
    ("feat/x", 'git commit -am "fix(api): 고침"'),               # 없는 scope
    ("feat/x", HEREDOC.format("고침")),                          # heredoc 제목도 검사
    ("feat/x", SUBST.format("고침")),                            # -m "$(cat <<EOF)" 제목도 검사
    ("feat/x", "gh pr merge 3 --squash --delete-branch"),
    ("feat/x", "gh pr merge 3 -s"),
    ("feat/x", "git push --force origin feat/x"),
    ("feat/x", "git -c credential.helper= push -f origin feat/x"),
]
PASS = [
    ("feat/x", 'git commit -m "fix(back): 로그아웃 토큰 무효화"'),
    ("feat/x", 'git commit -m "docs: README 정리"'),
    ("feat/x", HEREDOC.format("chore: 절차 고정")),
    ("feat/x", SUBST.format("feat(front): 알림 추가")),
    ("main", 'git checkout -q -b fix/y && git commit -m "fix: 고침"'),  # 같은 명령에서 브랜치를 딴 경우
    ("feat/x", 'git commit -m "Revert \\"fix(back): 고침\\""'),
    ("feat/x", "gh pr merge 3 --merge --delete-branch"),
    ("feat/x", "git -c credential.helper= -c credential.helper='!gh auth git-credential' push -q -u origin feat/x"),
    ("main", "git pull -q && git log --oneline -3"),
    ("feat/x", "git commit -F msg.txt"),  # 제목을 알 수 없으면 막지 않는다
    ("feat/x", 'git commit -q -m "$msg"'),
    # 명령이 아니라 데이터 안의 글자는 막지 않는다
    ("main", "cat > t.py <<'EOF'\nrun('gh pr merge 3 --squash')\nrun('git commit -m \"x\"')\nEOF"),
    ("feat/x", 'gh pr create --title "chore: x" --body "git push --force 금지 규칙 추가"'),
]
fails = [f"막아야 함: {c!r}" for b, c in BLOCK if not check(c, b)]
fails += [f"통과해야 함: {c!r} → {check(c, b)}" for b, c in PASS if check(c, b)]


def run(script, payload):
    return subprocess.run([sys.executable, os.path.join(HERE, script)], input=json.dumps(payload),
                          capture_output=True, text=True)


# 실제 git 저장소로 main 판정과 flyway 알림을 확인한다
with tempfile.TemporaryDirectory() as repo:
    subprocess.run(["git", "init", "-q", "-b", "main", repo], check=True)
    r = run("guard_git.py", {"cwd": repo, "tool_input": {"command": 'git commit -m "fix: x"'}})
    if r.returncode != 2 or "main" not in r.stderr:
        fails.append(f"main 커밋이 exit 2가 아님: {r.returncode} {r.stderr}")

    entity = {"cwd": repo, "tool_input": {"file_path": f"{repo}/src/main/java/a/entity/Post.java"}}
    if "Flyway" not in run("flyway_reminder.py", entity).stdout:
        fails.append("마이그레이션 없이 엔티티 수정 시 알림이 없음")
    os.makedirs(f"{repo}/src/main/resources/db/migration")
    open(f"{repo}/src/main/resources/db/migration/V9__x.sql", "w").close()
    if run("flyway_reminder.py", entity).stdout:
        fails.append("새 마이그레이션이 있는데 알림이 나옴")
    if run("flyway_reminder.py", {"cwd": repo, "tool_input": {"file_path": f"{repo}/src/main/java/a/service/S.java"}}).stdout:
        fails.append("엔티티가 아닌 파일에 알림이 나옴")

print("\n".join(fails) or f"ok ({len(BLOCK)} block, {len(PASS)} pass, git 4)")
sys.exit(1 if fails else 0)
