#!/bin/bash
# EC2(Ubuntu 24.04 arm64) 첫 부팅 때 한 번 실행되는 준비 스크립트. 인스턴스를 만들 때 user-data로 넘긴다.
# 접속은 SSH 대신 SSM(Session Manager)으로 한다. Ubuntu AMI에는 SSM 에이전트가 들어 있다.
set -euxo pipefail

# 2GB 메모리에서 이미지 빌드(Gradle·CRA)가 버티도록 스왑 2GB
if [ ! -f /swapfile ]; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
timedatectl set-timezone Asia/Seoul

# Docker 공식 apt 저장소(빌드에 필요한 buildx·compose 플러그인 포함)
apt-get update
apt-get install -y ca-certificates curl git
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker

# 소스는 공개 저장소에서 받는다. .env는 SSM으로 접속해 따로 만든다(deploy/README.md)
git clone https://github.com/yusuyeon1111/projectBack.git /opt/teamhaeyo
