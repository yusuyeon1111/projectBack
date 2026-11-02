import React, { useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import '../css/Sign.css';
import axios from 'axios';
import { useForm } from 'react-hook-form'
import kakao from '../assets/kakaoLogin.png';
import naver from '../assets/naverLogin.png';
function Signin() {
   useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const errorMessage = params.get("error");
    if (errorMessage) {
      alert(errorMessage); // 비활성화 사용자 등 알림
      window.history.replaceState(null, "", "/"); // URL 정리
    }
  }, []);
  const nav = useNavigate();
  const {
      register,
      handleSubmit,
      watch,
      formState:{errors},
    }= useForm();

  const onsubmit = async (data) => {
    const username = watch("username");
    try {
        const response = await axios.post(`${process.env.REACT_APP_API_URL}/api/member/signin`, data, {
          headers: { "Content-Type": "application/json" }, 
        });
        localStorage.setItem("accessToken", response.data.accessToken);
        localStorage.setItem("refreshToken", response.data.refreshToken);
        localStorage.setItem("username",username)
        localStorage.setItem("role", response.data.roles[0]);
        alert("로그인이 완료되었습니다!");
        if(response.data.roles[0] === 'ROLE_ADMIN') {
          nav("/admin");
        } else {
          nav("/"); 
        }
      } catch (error) {
          if (error.response) {
              if (error.response.status === 401) {
                  alert("아이디 또는 비밀번호가 잘못되었습니다.");
              } else if (error.response.status === 403) {
                  alert(error.response.data.error); // 계정 비활성 등
              } else {
                  alert("로그인 실패");
              }
          }
      }
  }

  return (
     <div className="sign-container">
      <h2>로그인</h2>
      <form className="auth-form" onSubmit={handleSubmit(onsubmit)} noValidate>
        <label htmlFor="username">아이디</label>
        <input
          id="username"
          type="text"
          {...register('username', { required: '아이디를 입력해주세요' })}
        />
        {errors.username && <p className="error">{errors.username.message}</p>}

        <label htmlFor="password">비밀번호</label>
        <input
          id="password"
          type="password"
          {...register('password', { required: '비밀번호를 입력해주세요' })}
        />
        {errors.password && <p className="error">{errors.password.message}</p>}

        <button type="submit">로그인</button>
      </form>

      <div className="auth-links">
        <a href="/membersignup">회원가입</a>
        <a href="/find-id">아이디 찾기</a>
        <a href="/find-pw">비밀번호 찾기</a>
      </div>

      <div className="oauth-buttons">
        <img src={kakao} className='kakao-login' onClick={()=>{window.location.href = "http://localhost:8080/oauth2/authorization/kakao";}}/>
        <img src={naver} className='naver-login' onClick={()=>{window.location.href = "http://localhost:8080/oauth2/authorization/naver";}}/>
      </div>
    </div>

  );
}

export default Signin;
