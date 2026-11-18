#!/usr/bin/env bash
# 두 커밋 사이에 바뀐 파일로 다시 띄울 서비스를 정한다: app | web | all | none
# 사용: deploy/target.sh <이전 커밋> <새 커밋>
set -euo pipefail
files=$(git diff --name-only "$1" "$2" | grep -v '\.md$' || true)   # 문서만 바뀌면 배포하지 않는다

has() { echo "$files" | grep -qE "$1"; }
if has '^deploy/docker-compose\.yml$'; then echo all; exit; fi
app=0; web=0
has '^(src/main/|build\.gradle$|settings\.gradle$|gradle/|Dockerfile$)' && app=1
has '^frontend/' && web=1
case $app$web in 11) echo all ;; 10) echo app ;; 01) echo web ;; *) echo none ;; esac
