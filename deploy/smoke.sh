#!/usr/bin/env bash
# 배포 뒤 핵심 경로가 살아 있는지 본다. 하나라도 어긋나면 exit 1.
# 사용: deploy/smoke.sh [주소]   (기본 https://teamhaeyo.kro.kr)
# 자체 서명 인증서(CI·리허설의 https://localhost)는 SMOKE_INSECURE=1로 인증서 검사를 끈다. 운영에는 쓰지 않는다
set -u
BASE=${1:-https://teamhaeyo.kro.kr}
fail=0

check() { # 이름 기대코드 URL [curl 인자...]
  local name=$1 want=$2 url=$3; shift 3
  local got
  got=$(curl -s ${SMOKE_INSECURE:+-k} -o /dev/null -w '%{http_code}' --max-time 10 "$@" "$url")
  if [ "$got" = "$want" ]; then echo "ok   $name $got"; else echo "FAIL $name $got (기대 $want)"; fail=1; fi
}

check front         200 "$BASE/"
check front-js      200 "$BASE$(curl -s ${SMOKE_INSECURE:+-k} --max-time 10 "$BASE/asset-manifest.json" | jq -r '.files["main.js"]')"
check spa-route     200 "$BASE/oauth2/success"   # 프론트 라우트가 백엔드로 새지 않는지(#39)
check post-list     200 "$BASE/api/post/list" -X POST -H 'Content-Type: application/json' -d '{"page":0,"size":1}'
check auth-required 401 "$BASE/api/member/myPage"  # 401이 아니라 503이면 Redis 장애(fail-closed)
check kakao-start   302 "$BASE/oauth2/authorization/kakao"
exit $fail
