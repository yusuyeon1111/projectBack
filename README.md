# 팀해요
프로젝트와 스터디 팀원을 모집하는 프로젝트 입니다.

## 🚀 프로젝트 소개 (About)

이 프로젝트는 **Spring Boot + React + DB** 를 활용하여  
풀스택 개발 전 과정을 경험하기 위해 진행한 개인 프로젝트입니다.

### 주요 기능
- 🔐 **회원 관리**
  - 로그인한 사용자는 프로젝트/스터디 모집글 작성 가능
  - 로그인하지 않아도 모집글 조회 가능
- 📌 **모집/참여 기능**
  
  <img width="700" height="600" alt="diagram" src="https://github.com/user-attachments/assets/bbf3f5d7-3681-4a3c-85a0-137871968751" />
  
  - 사용자는 프로젝트/스터디에 **참여 신청** 가능
  - 프로젝트/스터디장은 신청을 **수락/거절** 가능
- 📌 **관리자 기능**
  - 대시보드 기능
  - 게시글 관리
  - 사용자 관리
  
## 🛠 기술 스택 (Tech Stack)
- **Frontend:** React, MUI, JavaScript, HTML/CSS
- **Backend:** Spring Boot, Spring Security, JPA
- **DB:** MariaDB, Redis
- **Deployment / DevOps:** Docker
- **기타:** Axios, JWT

## 📖 기능 소개
### 1. 회원 관리
- 회원가입 / 로그인 / 로그아웃 기능
- 비밀번호 암호화(Bcrypt)로 안전하게 저장
- 소셜 로그인(Naver / Kakao) 기능
- JWT 인증을 통한 세션 관리
  - 로그인 시 JWT 발급하여 로그아웃, 만료 관리를 용이하기 위해 Redis에 토큰 저장할 수 있는 로직을 구현함.

#### 1-1. 회원관리 JWT 인증 흐름도
<img width="700" height="600" alt="diagram" src="https://github.com/user-attachments/assets/a908e37e-8336-4f0d-913b-f0ad69a4e8d2" />

#### 1-2. 회원 erd
<img width="700" height="600" alt="project" src="https://github.com/user-attachments/assets/afe117b1-97a4-4386-9d9c-6a8cf36027eb" />

#### 1-3. 회원가입 / 로그인 시연 영상
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
<img width="700" height="600" alt="post" src="https://github.com/user-attachments/assets/9be2438b-87bc-4dae-96c7-a11dbf708999" />

 [![프로젝트 시연 영상](https://img.youtube.com/vi/ahoGU-vfmzc/0.jpg)](https://www.youtube.com/watch?v=ahoGU-vfmzc)
