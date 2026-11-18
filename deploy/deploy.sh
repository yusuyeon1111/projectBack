#!/usr/bin/env bash
# 서버를 지정한 커밋으로 맞춰 컨테이너를 다시 띄우고 smoke.sh로 확인한다.
# smoke가 실패하면 직전에 배포돼 있던 커밋으로 되돌리고 exit 1(롤백 성공 여부와 무관하게 실패로 끝낸다).
#
# 사용: deploy/deploy.sh [app|web|all]
#   DEPLOY_REF  배포할 커밋(기본 origin/main). CI는 테스트를 통과한 커밋 sha를 넘긴다
#   NO_ROLLBACK=1  실패해도 되돌리지 않는다(롤백 자신이 쓴다)
# AWS 자격 증명 필요: 로컬은 AWS_PROFILE=teamhaeyo, CI는 OIDC 역할 teamhaeyo-github-deploy.
# 서버 접속은 SSH 없이 SSM만 쓴다. web(프론트) 빌드는 10분쯤 걸린다.
# ponytail: 롤백은 코드만 되돌린다. 새 Flyway 마이그레이션이 컬럼을 지웠다면 이전 앱이 validate에서 멈출 수 있다
set -euo pipefail
cd "$(dirname "$0")"

TARGET=${1:-all}
case $TARGET in
  app) SVC=app ;; web) SVC=web ;; all) SVC="app web" ;;
  *) echo "사용: $0 [app|web|all]" >&2; exit 2 ;;
esac
REF=${DEPLOY_REF:-origin/main}
INSTANCE=${INSTANCE_ID:-i-0371f2947b329f191}
BASE=${BASE_URL:-https://teamhaeyo.kro.kr}

REMOTE="set -e; cd /opt/teamhaeyo
echo previous \$(git rev-parse HEAD)
git fetch -q origin && git checkout -q --detach $REF
echo deployed \$(git rev-parse HEAD)
cd deploy && docker compose up -d --build $SVC 2>&1 | tail -3"
CID=$(aws ssm send-command --instance-ids "$INSTANCE" --document-name AWS-RunShellScript \
  --parameters "$(jq -n --arg c "$REMOTE" '{commands:[$c], executionTimeout:["1800"]}')" \
  --query Command.CommandId --output text)
echo "SSM $CID ($SVC ← $REF)"

# aws ssm wait는 100초에서 끝나 web 빌드를 못 기다린다
while :; do
  STATUS=$(aws ssm get-command-invocation --command-id "$CID" --instance-id "$INSTANCE" --query Status --output text 2>/dev/null || echo Pending)
  case $STATUS in Pending|InProgress|Delayed) sleep 15 ;; *) break ;; esac
done
OUT=$(aws ssm get-command-invocation --command-id "$CID" --instance-id "$INSTANCE" \
  --query '[StandardOutputContent,StandardErrorContent]' --output text)
echo "$OUT"
PREV=$(echo "$OUT" | awk '/^previous /{print $2; exit}')

ok=0
if [ "$STATUS" = Success ]; then
  # 앱이 뜨는 동안(30초 안팎) 기다린 뒤 smoke
  for _ in $(seq 1 24); do ./smoke.sh "$BASE" >/dev/null 2>&1 && break; sleep 5; done
  ./smoke.sh "$BASE" && ok=1
else
  echo "배포 명령 실패: $STATUS" >&2
fi
[ $ok = 1 ] && exit 0

if [ -z "${NO_ROLLBACK:-}" ] && [ -n "$PREV" ]; then
  echo "::warning::배포 실패. 이전 커밋 $PREV 로 되돌린다" >&2
  # 위에서 deploy/로 cd했으므로 상대 경로 $0은 못 쓴다
  DEPLOY_REF=$PREV NO_ROLLBACK=1 ./deploy.sh "$TARGET" || echo "::error::롤백도 실패했다. 직접 확인이 필요하다" >&2
fi
exit 1
