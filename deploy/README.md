# 배포 (EC2 한 대)

운영 구성: EC2 t4g.small(Ubuntu 24.04 arm64) 한 대에 Docker Compose로 앱·MySQL·Redis·Caddy를 띄운다.
RDS·ElastiCache는 비용 때문에 쓰지 않는다. 알림·채팅 SSE 연결을 서버 메모리에 두므로 앱은 한 대여야 한다.

| 파일 | 하는 일 |
|---|---|
| `../Dockerfile` | 백엔드 이미지(Gradle 빌드 → JRE) |
| `../frontend/Dockerfile`, `../frontend/Caddyfile` | 프론트 빌드 결과를 Caddy가 서빙, `/api`·`/oauth2`·`/login/oauth2`는 앱으로. 인증서는 Caddy가 자동 발급·갱신 |
| `docker-compose.yml` | 네 컨테이너와 데이터 볼륨 |
| `env.example` | 서버에서 만들 `.env`의 항목 |
| `ec2-user-data.sh` | 첫 부팅 때 스왑·Docker 설치, 저장소 clone |

AWS 쪽에 미리 만들어 둔 것: S3 버킷 `teamhaeyo-files-830283279722`, 인스턴스 역할 `teamhaeyo-ec2`(그 버킷 + SSM), 보안 그룹 `teamhaeyo-web`(80·443만).
S3 키는 어디에도 넣지 않는다. 앱이 인스턴스 역할로 접근한다.

## 처음 한 번

1. 인스턴스 생성. 컨테이너 안에서 인스턴스 역할(IMDSv2)을 읽으려면 hop limit이 2여야 한다.
   ```bash
   export AWS_PROFILE=teamhaeyo
   AMI=$(aws ssm get-parameter --name /aws/service/canonical/ubuntu/server/24.04/stable/current/arm64/hvm/ebs-gp3/ami-id --query Parameter.Value --output text)
   aws ec2 run-instances --image-id $AMI --instance-type t4g.small \
     --iam-instance-profile Name=teamhaeyo-ec2 --security-group-ids <teamhaeyo-web SG id> \
     --block-device-mappings 'DeviceName=/dev/sda1,Ebs={VolumeSize=20,VolumeType=gp3}' \
     --metadata-options HttpTokens=required,HttpPutResponseHopLimit=2 \
     --user-data file://deploy/ec2-user-data.sh \
     --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=teamhaeyo},{Key=project,Value=teamhaeyo}]'
   ```
2. 탄력적 IP를 만들어 인스턴스에 연결하고, `teamhaeyo.kro.kr`의 A 레코드를 그 IP로 바꾼다. DNS가 이 서버를 가리켜야 Caddy가 인증서를 받는다.
3. SSM으로 접속(`aws ssm start-session --target <instance-id>`, 또는 콘솔 → EC2 → 연결 → Session Manager)해서 `.env`를 만든다.
   ```bash
   cd /opt/teamhaeyo/deploy && sudo cp env.example .env && sudo chmod 600 .env && sudo nano .env
   ```
4. 띄운다(첫 빌드는 10분 안팎).
   ```bash
   cd /opt/teamhaeyo/deploy && sudo docker compose up -d --build
   ```

## 업데이트

```bash
cd /opt/teamhaeyo && sudo git pull && cd deploy && sudo docker compose up -d --build
```
스키마 변경은 앱이 뜰 때 Flyway가 적용한다.

## 확인·운영

- 상태: `sudo docker compose ps`, 로그: `sudo docker compose logs -f app`
- DB 백업: `sudo docker compose exec mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" project' > backup.sql`
- `kro.kr`은 공유 도메인이라 Let's Encrypt가 주간 한도(상위 도메인당 50장)로 거절하는 일이 잦다. `ACME_EMAIL`이 있으면 Caddy가 ZeroSSL로 넘어가 받는다. ZeroSSL 웹사이트 가입·유료 요금제는 필요 없다.
- 인증서는 `caddy-data` 볼륨에 있다. 지우면 다시 발급받는데, Let's Encrypt는 발급 횟수 제한이 있다.
- `docker compose down -v`는 DB까지 지운다. 내릴 때는 `-v` 없이.

## 로컬 리허설

`DOMAIN=localhost`를 주면 Caddy가 자체 서명 인증서로 `https://localhost`를 연다. `.env` 대신 환경변수로 값을 넘기면 된다.
```bash
cd deploy && MYSQL_ROOT_PASSWORD=x DB_PASSWORD=y JWT_SECRET=$(openssl rand -base64 32) KAKAO_CLIENT_ID=x \
  REACT_APP_KAKAO_API_KEY=x ACME_EMAIL=x@example.com AWS_S3_BUCKET_NAME=teamhaeyo-files-830283279722 DOMAIN=localhost \
  docker compose -p teamhaeyo-rehearsal up -d --build
# 끝나면: docker compose -p teamhaeyo-rehearsal down -v
```
