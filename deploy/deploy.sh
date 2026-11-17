#!/usr/bin/env bash
# 서버의 main을 최신으로 당겨 컨테이너를 다시 띄우고 smoke.sh로 확인한다.
# 사용: deploy/deploy.sh [app|web|all]   (AWS 자격 증명 필요: 로컬은 AWS_PROFILE=teamhaeyo)
# 서버 접속은 SSH 없이 SSM만 쓴다. web(프론트) 빌드는 10분쯤 걸린다.
set -euo pipefail
cd "$(dirname "$0")"

case ${1:-all} in
  app) SVC=app ;; web) SVC=web ;; all) SVC="app web" ;;
  *) echo "사용: $0 [app|web|all]" >&2; exit 2 ;;
esac
INSTANCE=${INSTANCE_ID:-i-0371f2947b329f191}
BASE=${BASE_URL:-https://teamhaeyo.kro.kr}

REMOTE="set -e; cd /opt/teamhaeyo && git pull -q && echo deployed \$(git rev-parse --short HEAD) && cd deploy && docker compose up -d --build $SVC 2>&1 | tail -3"
CID=$(aws ssm send-command --instance-ids "$INSTANCE" --document-name AWS-RunShellScript \
  --parameters "$(jq -n --arg c "$REMOTE" '{commands:[$c], executionTimeout:["1800"]}')" \
  --query Command.CommandId --output text)
echo "SSM $CID ($SVC)"

# aws ssm wait는 100초에서 끝나 web 빌드를 못 기다린다
while :; do
  STATUS=$(aws ssm get-command-invocation --command-id "$CID" --instance-id "$INSTANCE" --query Status --output text 2>/dev/null || echo Pending)
  case $STATUS in Pending|InProgress|Delayed) sleep 15 ;; *) break ;; esac
done
aws ssm get-command-invocation --command-id "$CID" --instance-id "$INSTANCE" \
  --query '[StandardOutputContent,StandardErrorContent]' --output text
[ "$STATUS" = Success ] || { echo "배포 실패: $STATUS" >&2; exit 1; }

# 앱이 뜨는 동안(30초 안팎) 기다린 뒤 smoke
for _ in $(seq 1 24); do ./smoke.sh "$BASE" >/dev/null 2>&1 && break; sleep 5; done
./smoke.sh "$BASE"
