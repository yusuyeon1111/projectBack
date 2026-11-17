---
name: deploy
description: 팀해요 운영 서버(EC2)에 main을 반영하고 smoke test로 확인한다. "배포해줘", "운영에 반영", "서버에 올려" 같은 요청, 또는 ship 스킬의 병합 뒤 단계에서 쓴다.
---

# 운영 배포

운영은 EC2 한 대의 Docker Compose(app·mysql·redis·web)다. 서버 접속은 SSM만 쓴다. 구성은 `deploy/README.md`.

1. **무엇을 다시 띄울지 정한다.** 병합된 변경 파일로 판단한다.
   - `src/`·`build.gradle`·`application.yml` → `app`
   - `frontend/` → `web` (빌드 10분쯤)
   - 둘 다 → `all`
   - `deploy/docker-compose.yml`이나 `Caddyfile`이 바뀌었으면 해당 서비스. 문서만 바뀌었으면 배포하지 않는다.
2. **실행한다.**
   ```bash
   export AWS_PROFILE=teamhaeyo
   deploy/deploy.sh app|web|all
   ```
   - 오래 걸리므로 백그라운드로 돌리고 끝나면 결과를 읽는다.
   - 자격 증명이 만료됐다는 오류가 나면, 사용자에게 `! aws login --profile teamhaeyo`를 부탁한다.
3. **결과를 판단한다.**
   - 스크립트가 끝에 `smoke.sh`를 돌린다. 모두 `ok`이고 exit 0일 때만 "반영 완료"라고 보고한다.
   - 바뀐 동작이 smoke 항목에 없으면 그 동작을 따로 한 번 확인하고, 무엇을 확인했는지 적는다.
   - 실패하면 고치지 말고 출력을 그대로 사용자에게 보여 준다. `auth-required`가 503이면 Redis 장애다.

## 하지 않는 것

- 서버 `.env`를 읽거나 고치지 않는다. 비밀값은 사용자가 직접 넣는다.
- AWS 리소스를 만들거나 지우지 않는다. 필요하면 무엇을 왜 하는지와 비용을 먼저 말하고 승인을 받는다.
- 운영 DB에 쓰는 SQL은 사용자가 승인한 것만 실행한다.
