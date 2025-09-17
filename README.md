# 👤 팀해요
이 프로젝트는 **Spring Boot + React + DB** 를 활용하여  
풀스택 개발 전 과정을 경험하기 위해 진행한 개인 프로젝트입니다.
팀이 없어도 협업 경험을 흉내 내고, 실제 배포까지 경험하며 백엔드 인증, 프론트 상태 관리, 클라우드 배포까지 구현하였습니다.
## 🚀 1. 프로젝트 소개 (About)
혼자서 프로젝트하거나 공부할 때 누군가와 같이 하고싶다고 생각해본적이 있나요? 그럴 때 필용한 서비스입니다!

## 2. 🔗 실제 사이트
프로젝트를 실제 환경에서 사용해볼 수 있습니다.

[🌐 팀해요 - 실제 서비스 바로가기](https://teamhaeyo.kro.kr/)

사용자 테스트 계정 test/test1234 <br/>
관리자 테스트 계정 admin123/admin123

## 🛠️ 3. 사용 언어 / 기술 스택 (Languages & Tech Stack)
| 분야 | 기술 |
|------|------|
| **Backend** | ![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white) |
| **Frontend** | ![React](https://img.shields.io/badge/React-20232A?style=for-the-badge&logo=react&logoColor=61DAFB) ![HTML5](https://img.shields.io/badge/HTML5-E34F26?style=for-the-badge&logo=html5&logoColor=white) ![CSS3](https://img.shields.io/badge/CSS3-1572B6?style=for-the-badge&logo=css3&logoColor=white) ![JavaScript](https://img.shields.io/badge/JavaScript-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black) |
| **Database / Cache** | ![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white) ![Redis](https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white) |
| **DevOps / Infra** | ![AWS](https://img.shields.io/badge/AWS-232F3E?style=for-the-badge&logo=amazon-aws&logoColor=white) ![EC2](https://img.shields.io/badge/EC2-FF9900?style=for-the-badge&logo=amazon-aws&logoColor=white) ![S3](https://img.shields.io/badge/S3-569A31?style=for-the-badge&logo=amazon-aws&logoColor=white) ![CloudFront](https://img.shields.io/badge/CloudFront-FF9900?style=for-the-badge&logo=amazon-aws&logoColor=white) |
| **Tools** | ![GitHub](https://img.shields.io/badge/GitHub-181717?style=for-the-badge&logo=github&logoColor=white) |

## 4. 아키텍처 
<img width="800" height="500" alt="image" src="https://github.com/user-attachments/assets/f203df5a-1ea7-4c99-aae6-62ed986b8087" />

## 5. 프로세스 흐름도
<img width="800" height="500" alt="제목 없는 다이어그램 drawio" src="https://github.com/user-attachments/assets/a73a3a53-1e1d-4593-a4b4-7ed8804a54a2" />

## 6. ERD
아래 ERD는 스터디/프로젝트 매칭 플랫폼의 데이터베이스 구조를 나타냅니다.
회원(Member), 모집글(Post), 지원(Application) 등을 중심으로 설계되었으며, 각 테이블은 다음과 같은 역할을 합니다.
<img width="800" height="500" alt="project@localhost" src="https://github.com/user-attachments/assets/5f531f3d-9519-4b3a-8944-74e5e43520a5" />
### 주요 테이블 설명
- **member**  
  사용자 계정을 관리하는 테이블입니다.  
  - 이메일, 비밀번호, 닉네임, 소셜 로그인 제공자 등의 정보를 저장합니다.  
  - `member_profile`과 연결되어 사용자의 자기소개 및 선호 포지션을 관리합니다.  
  - `member_roles`를 통해 권한(관리자, 일반 사용자 등)을 부여합니다.  

- **post**  
  프로젝트/스터디 모집 글을 관리합니다.  
  - 작성자(author), 제목, 내용, 모집 분야, 모집 인원, 지역, 모집 상태 등을 저장합니다.  
  - `post_stack`으로 필요한 기술 스택을, `post_position`으로 모집 포지션을 정의합니다.  

- **application**  
  사용자가 모집글에 지원한 내역을 관리합니다.  
  - 지원한 회원, 지원한 모집글, 지원 상태, 지원 날짜를 기록합니다.
    
- **post_position_member**  
  특정 포지션에 지원한 멤버와 그 상태(대기/승인/거절)를 관리합니다.  
- **post_like**  
  게시글에 달린 좋아요 정보를 관리합니다.
  
## 7. 주요 기능 소개
### 7-1. 회원가입/로그인
- 회원가입 / 로그인 / 로그아웃 기능
- 비밀번호 암호화(Bcrypt)로 안전하게 저장
- 소셜 로그인(Kakao) 기능
- JWT 인증을 통한 세션 관리
- 로그인 시 JWT 발급하여 로그아웃, 만료 관리를 용이하기 위해 Redis에 토큰 저장.

[![프로젝트 시연](https://img.youtube.com/vi/ahoGU-vfmzc/0.jpg)](https://www.youtube.com/watch?v=ahoGU-vfmzc)

### 7-2. 프로젝트/스터디원 모집
- 프로젝트 제목, 기간(시작일/마감일) 입력
- 프로젝트 방식 선택: 온라인 / 오프라인 / 혼합
- 지역 선택 (오프라인인 경우)
- 모집 포지션 선택 및 인원 수 지정
- 기술 스택 입력
- 상세 설명 작성
  
[![프로젝트 시연](https://img.youtube.com/vi/P1CJEVxjok4/0.jpg)](https://www.youtube.com/watch?v=P1CJEVxjok4)

## 8. 트러플 슈팅
### 8-1. HTTPS + CloudFront + React 배포 문제
- React 프론트엔드를 S3 + CloudFront로 배포.
- 백엔드(Spring Boot)는 EC2 또는 RDS 연결
- React에서 API 요청을 할 때 HTTPS CloudFront 도메인을 사용하려고 함.
- 문제: HTTPS로 접근 시 CROS 에러 또는 네트워크 에러 발생.
- 원인 분석: CloudFront 배포 시 SSL 인증서를 적용하지 않아 브라우저에서 HTTPS 요청 거부
- 해결 과정: 도메인을 구매해 CloudFront 배포에서 ACM SSL 인증서 연결 → HTTPS 적용.
- 배운 점: 클라우드 배포 시 프론트엔드와 백엔드의 도메인, 프로토콜, CORS 정책을 꼼꼼히 확인해야 함
