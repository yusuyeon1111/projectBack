#!/usr/bin/env python3
"""PreToolUse(Bash): CLAUDE.md의 git 규칙을 어기는 명령을 실행 전에 막는다.

막으면 exit 2 + stderr(이유)라서 Claude가 이유를 보고 고쳐 다시 실행한다.
- main에 직접 커밋 / 형식이 아닌 커밋 제목 / squash·rebase 병합 / force push

heredoc 본문과 따옴표 안 문자열은 명령이 아니라 데이터라서 검사 전에 가린다.
(가리지 않으면 테스트 파일을 쓰는 명령 안의 "gh pr merge --squash" 글자까지 막는다)
"""
import json, re, subprocess, sys

TYPES = "feat|fix|perf|refactor|test|docs|ci|chore|style"
TITLE = re.compile(rf"^({TYPES})(\((back|front)\))?: \S")
COMMIT = re.compile(r"\bgit\b(?:\s+-[cC]\s+\S+)*\s+commit\b")
MARK = re.compile(r"\x00(\d+)\x00")


def mask(cmd):
    """heredoc 본문과 따옴표 문자열을 \\x00번호\\x00로 바꾸고 원문은 saved에 둔다."""
    saved = []

    def keep(text):
        saved.append(text)
        return f"\x00{len(saved) - 1}\x00"

    cmd = re.sub(r"(<<-?\s*(['\"]?)(\w+)\2[^\n]*\n)(.*?)\n\3\b",
                 lambda m: m.group(1) + keep(m.group(4)) + "\n", cmd, flags=re.S)
    cmd = re.sub(r"'[^']*'|\"(?:\\.|[^\"\\])*\"", lambda m: keep(m.group(0)[1:-1]), cmd)
    return cmd, saved


def unmask(text, saved):
    while MARK.search(text):
        text = MARK.sub(lambda m: saved[int(m.group(1))], text)
    return text


def first_line(text):
    lines = [l.strip() for l in text.replace('\\"', '"').splitlines() if l.strip()]
    return lines[0] if lines else ""


def commit_title(code, saved, start):
    """커밋 제목: -m 값(안에 $(cat <<EOF)가 있으면 그 본문) 또는 -F - heredoc의 첫 줄. 모르면 None."""
    rest = code[start:].split("&&")[0]
    m = re.search(r"\s-a?m\s*\x00(\d+)\x00", rest)
    if m:
        value = saved[int(m.group(1))]
        inner = MARK.search(value)
        if value.startswith("$") and not (inner and value.startswith("$(")):
            return None  # "$msg"처럼 변수면 실행 전엔 알 수 없다
        return first_line(unmask(saved[int(inner.group(1))], saved) if inner and value.startswith("$(") else value)
    m = re.search(r"<<[^\n]*\n\x00(\d+)\x00", rest)
    if m:
        return first_line(unmask(saved[int(m.group(1))], saved))
    return None


def check(cmd, branch):
    code, saved = mask(cmd)
    commit = COMMIT.search(code)
    if commit and "--amend" not in code:
        switched = re.search(r"\b(checkout\s+(-q\s+)?-[bB]|switch\s+(-q\s+)?-[cC])\s", code[:commit.start()])
        if branch == "main" and not switched:
            return "main에 직접 커밋하지 않는다(CLAUDE.md 브랜치와 PR). `<type>/<작업>` 브랜치를 먼저 딴다."
        title = commit_title(code, saved, commit.end())
        if title is not None and not title.startswith(("Revert ", "Merge ")) and not TITLE.match(title):
            return (f"커밋 제목 형식이 아니다: {title!r}\n"
                    f"`<type>(<scope>): <제목>` — type은 {TYPES.replace('|', ', ')}, scope는 back·front 또는 생략.")
    for seg in re.split(r"&&|\|\||;|\n", code):
        if re.search(r"\bgh\s+pr\s+merge\b", seg) and re.search(r"\s(--squash|--rebase|-s|-r)\b", seg):
            return "병합은 Merge commit만 쓴다(--merge). squash·rebase는 분리해 둔 fix: 커밋을 묻는다."
        if re.search(r"\bgit\b.*\bpush\b", seg) and re.search(r"\s(--force(-with-lease)?|-f)\b|\s\+\S", seg):
            return "이미 push한 이력은 다시 쓰지 않는다(force push 금지). 새 커밋으로 고친다."
    return None


def main():
    data = json.load(sys.stdin)
    cmd = data.get("tool_input", {}).get("command", "")
    if "git" not in cmd and "gh" not in cmd:
        return 0
    branch = subprocess.run(["git", "-C", data.get("cwd", "."), "branch", "--show-current"],
                            capture_output=True, text=True).stdout.strip()
    reason = check(cmd, branch)
    if reason:
        print(reason, file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
