# 팀해요
프로젝트와 스터디 팀원을 모집하는 프로젝트 입니다.

## 🚀 1. 프로젝트 소개 (About)

이 프로젝트는 **Spring Boot + React + DB** 를 활용하여  
풀스택 개발 전 과정을 경험하기 위해 진행한 개인 프로젝트입니다.

## 1-1. 프로세스 흐름도
<img width="800" height="500" alt="제목 없는 다이어그램 drawio" src="https://github.com/user-attachments/assets/a73a3a53-1e1d-4593-a4b4-7ed8804a54a2" />

## 1-2. ERD
<img width="800" height="500" alt="project@localhost" src="https://github.com/user-attachments/assets/5f531f3d-9519-4b3a-8944-74e5e43520a5" />

## 1-2. 🔐 **회원 관련 기능**
  - 회원 ERD
  <img width="600" height="300" alt="project" src="https://github.com/user-attachments/assets/afe117b1-97a4-4386-9d9c-6a8cf36027eb" />

  - 회원 가입
    -  member 테이블에 이메일, 닉네임, 비밀번호 등 기본 정보 저장
  - 마이페이지
    - member_profile 테이블: 선호 포지션(preferred_position)과 자기소개(introduce) 작성 가능
    - member_stack 테이블: 프로필과 연결된 기술 스택 저장 (1명의 프로필이 여러 기술 스택 보유 가능)
   - 모집 신청
     - 사용자가 모집 글의 포지션에 신청하면 post_position_member 테이블에 기록
   - 좋아요 기능
     - post_like 테이블: 사용자가 게시글을 좋아요 한 기록 관리 (member_id와 post_id 연결)

 
## 1-3. 📌 **모집/참여 기능**
 - 게시글 ERD
<img width="600" height="300" alt="post" src="https://github.com/user-attachments/assets/9be2438b-87bc-4dae-96c7-a11dbf708999" />

 -  **post** 테이블 : 게시글 작성 시 작성자, 제목, 내용 등 기본 정보를 저장합니다.
 -  **post_stack** 테이블 : 프로젝트에서 사용되는 기술 스택을 저장합니다. 하나의 게시글에 여러 기술 스택을 연결할 수 있습니다.
 -  **post_position** 테이블 : 게시글별로 모집하는 포지션 정보를 저장하며, 한 게시글에 여러 포지션을 등록할 수 있습니다.
 -  **post_position** 테이블 : 포지션별로 신청한 사용자 정보를 관리합니다. 각 사용자는 member_id로 식별되며, position_id로 특정 포지션에 연결됩니다.
 -   **post_like** 테이블 : 게시글에 대한 좋아요 정보를 저장합니다. post_id와 member_id를 기준으로 사용자가 한 게시글에 한 번만 좋아요를 누를 수 있도록 관리합니다.
  
  - 로그인한 사용자는 프로젝트/스터디 모집글 작성 가능
  - 로그인하지 않아도 모집글 조회 가능
  - 사용자는 프로젝트/스터디에 **참여 신청** 가능
  - 프로젝트/스터디장은 신청을 **수락/거절** 가능
 
## 1-4. 📌 **관리자 기능**
  - 대시보드 기능
  - 게시글 관리
  - 사용자 관리
  
## 🛠 2. 기술 스택 (Tech Stack)
- **Frontend:** React, MUI, JavaScript, HTML/CSS
- **Backend:** Spring Boot, Spring Security, JPA
- **DB:** MariaDB, Redis
- **Deployment / DevOps:** Docker
- **기타:** Axios, JWT

## 📖 3. 기능 소개
### 1. 회원 관리
- 회원가입 / 로그인 / 로그아웃 기능
- 비밀번호 암호화(Bcrypt)로 안전하게 저장
- 소셜 로그인(Naver / Kakao) 기능
- JWT 인증을 통한 세션 관리
  - 로그인 시 JWT 발급하여 로그아웃, 만료 관리를 용이하기 위해 Redis에 토큰 저장할 수 있는 로직을 구현함.

#### 1-1. 회원관리 JWT 인증 흐름도
<img width="700" height="600" alt="diagram" src="https://github.com/user-attachments/assets/a908e37e-8336-4f0d-913b-f0ad69a4e8d2" />

#### 1-2. 회원가입 / 로그인 시연 영상
 [![회원가입/로그인 시연 영상](https://img.youtube.com/vi/a6qJKUhoMys/0.jpg)](https://www.youtube.com/watch?v=a6qJKUhoMys)

### 2. 프로젝트/스터디 모집 흐름도


### 3. 마이 페이지
- 작성글 / 찜한 글 / 신청 내역 / 참여 중인 프로젝트 조회 기능
- 회원 정보 수정
  - 자기소개글, 선호하는 포지션, 사용가능한 스택

#### 3-1. 작성글 조회 기능
- 사용자가 작성한 모집글 목록 확인
- 신청자 수락 / 거절 가능

#### 3-2. 신청 내역 조회 기능
- 사용자가 신청한 모집글 목록 확인
- 각 신청 상태 확인 가능

#### 3-3. 참여 중인 프로젝트 조회 기능
- 사용자의 신청이 승인되어 참여 중인 프로젝트 목록 확인

#### 3-4. 마이페이지 시연 영상
 [![마이페이지 시연 영상](https://img.youtube.com/vi/kaPWQzD3w8M/0.jpg)](https://www.youtube.com/watch?v=kaPWQzD3w8M)
 
### 4. 모집글 작성/조회/수정/삭제
#### 4-1. 게시글 erd


 [![프로젝트 시연 영상](https://img.youtube.com/vi/ahoGU-vfmzc/0.jpg)](https://www.youtube.com/watch?v=ahoGU-vfmzc)

# 👤 팀해요
이 프로젝트는 **Spring Boot + React + DB** 를 활용하여  
풀스택 개발 전 과정을 경험하기 위해 진행한 개인 프로젝트입니다.
## 🚀 1. 프로젝트 소개 (About)
혼자서 프로젝트하거나 공부할 때 누군가와 같이 하고싶다고 생각해본적이 있나요? 그럴 때 필용한 서비스입니다!
## 🛠️ 사용 언어 / 기술 스택 (Languages & Tech Stack)
| 분야 | 기술 |
|------|------|
| **Backend** | ![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white) |
| **Frontend** | ![React](https://img.shields.io/badge/React-20232A?style=for-the-badge&logo=react&logoColor=61DAFB) ![HTML5](https://img.shields.io/badge/HTML5-E34F26?style=for-the-badge&logo=html5&logoColor=white) ![CSS3](https://img.shields.io/badge/CSS3-1572B6?style=for-the-badge&logo=css3&logoColor=white) ![JavaScript](https://img.shields.io/badge/JavaScript-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black) |
| **Database / Cache** | ![MariaDB](https://img.shields.io/badge/MariaDB-003545?style=for-the-badge&logo=mariadb&logoColor=white) ![Redis](https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white) |
| **DevOps / Infra** | ![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white) ![AWS](https://img.shields.io/badge/AWS-232F3E?style=for-the-badge&logo=amazon-aws&logoColor=white) |
| **Tools** | ![GitHub](https://img.shields.io/badge/GitHub-181717?style=for-the-badge&logo=github&logoColor=white) |

## 3. 프로세스 흐름도
<img width="800" height="500" alt="제목 없는 다이어그램 drawio" src="https://github.com/user-attachments/assets/a73a3a53-1e1d-4593-a4b4-7ed8804a54a2" />

## 4. ERD
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
