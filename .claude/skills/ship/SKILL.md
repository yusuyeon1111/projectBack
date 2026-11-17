---
name: ship
description: 작업한 변경을 브랜치 → 커밋 → PR → CI 통과까지 올리고, 사용자 승인 뒤 병합·배포·확인까지 마친다. "PR 올려줘", "올리고 배포까지", "ship" 같은 요청에 쓴다.
---

# 변경 올리기

규칙의 원본은 CLAUDE.md의 "커밋 메시지 규칙"과 "브랜치와 PR"이다. 여기서는 순서와 멈추는 지점만 정한다.

## 1. 병합 전 (혼자 진행)

1. **브랜치를 확인한다.** main이면 `<type>/<작업>` 브랜치를 딴다.
2. **로컬에서 검증한다.** 바꾼 쪽만 돌린다.
   - 백엔드: `./gradlew compileJava`, 그리고 관련 테스트(로컬 DB가 있을 때)
   - 프론트: `cd frontend && npx tsc --noEmit && CI=true npm test -- --watchAll=false <관련 파일>`
   - 화면 동작을 바꿨으면 로컬에서 실제로 띄워 확인한다.
3. **커밋한다.**
   - 의도 하나에 커밋 하나. 작업 중 발견한 버그는 별도의 `fix:` 커밋으로 나눈다.
   - 판단이 들어간 변경은 본문에 문제 → 원인 → 해결을 적는다.
4. **push하고 PR을 연다.**
   - push: `git -c credential.helper= -c credential.helper='!gh auth git-credential' push -u origin <브랜치>`
   - PR 본문은 `.github/pull_request_template.md` 형식을 따르고, "확인"에 실제로 한 것만 체크한다.
5. **CI를 기다린다.** `gh pr checks <번호> --watch`
   - 실패하면 원인을 고쳐 다시 push한다. 같은 실패가 두 번 나면 멈추고 사용자에게 보고한다.

## 2. 멈춤: 사용자 승인

다음을 보고하고 병합 여부를 묻는다. **승인 없이 병합하지 않는다.**
- PR 링크
- 무엇을 왜 바꿨는지
- CI 결과
- 배포 필요 여부(app/web/없음)와 예상 시간

이전 PR에서 받은 승인은 다음 PR에 쓰지 않는다.

## 3. 병합 뒤 (승인을 받은 경우만)

1. `gh pr merge <번호> --merge --delete-branch`
   - Squash는 쓰지 않는다. 분리해 둔 `fix:` 커밋이 묻히기 때문이다.
   - 스택 PR이면 위 PR의 base를 먼저 main으로 바꾼다.
2. `git checkout main && git pull`, 로컬 브랜치를 지운다.
3. 배포가 필요하면 deploy 스킬로 반영하고, smoke 결과까지 보고한다.
