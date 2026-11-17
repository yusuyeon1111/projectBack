import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import '../css/Sign.css';
import axios from "axios";
import { JwtToken } from "../types";

import { useForm } from 'react-hook-form'
import kakao from '../assets/kakaoLogin.png';
import naver from '../assets/naverLogin.png';
import { FiEye, FiEyeOff } from 'react-icons/fi';
import { toast } from './Toast';

interface SignInForm {
  username: string;
  password: string;
}
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
  const [showPw, setShowPw] = useState(false);
  const {
      register,
      handleSubmit,
      watch,
      formState:{errors},
    }= useForm<SignInForm>();

  const onsubmit = async (data: SignInForm) => {
    const username = watch("username");
    try {
        const response = await axios.post<JwtToken>(`${process.env.REACT_APP_API_URL}/api/member/signin`, data, {
          headers: { "Content-Type": "application/json" }, 
        });
        localStorage.setItem("accessToken", response.data.accessToken);
        localStorage.setItem("refreshToken", response.data.refreshToken);
        localStorage.setItem("username",username)
        // 권한 목록은 순서가 정해져 있지 않아 첫 값이 아니라 포함 여부로 본다
        const isAdmin = response.data.roles.includes('ROLE_ADMIN');
        localStorage.setItem("role", isAdmin ? 'ROLE_ADMIN' : response.data.roles[0]);
        toast("로그인이 완료되었습니다!");
        if(isAdmin) {
          nav("/admin");
        } else {
          nav("/"); 
        }
      } catch (error) {
          if (axios.isAxiosError(error) && error.response) {
              if (error.response.status === 401) {
                  alert("아이디 또는 비밀번호가 잘못되었습니다.");
              } else if (error.response.status === 403 || error.response.status === 429) {
                  alert(error.response.data.error); // 계정 비활성, 시도 횟수 초과
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
        <div className="pw-field">
          <input
            id="password"
            type={showPw ? "text" : "password"}
            {...register('password', { required: '비밀번호를 입력해주세요' })}
          />
          <button
            type="button"
            className="pw-toggle"
            onClick={() => setShowPw(v => !v)}
            aria-label={showPw ? "비밀번호 숨기기" : "비밀번호 보기"}
          >
            {showPw ? <FiEyeOff size={18} /> : <FiEye size={18} />}
          </button>
        </div>
        {errors.password && <p className="error">{errors.password.message}</p>}

        <button type="submit">로그인</button>
      </form>

      <div className="auth-links">
        <a href="/membersignup">회원가입</a>
      </div>

      <div className="oauth-buttons">
        <img src={kakao} className='kakao-login' onClick={()=>{window.location.href = `${process.env.REACT_APP_API_URL}/oauth2/authorization/kakao`;}}/>
        <img src={naver} className='naver-login' onClick={()=>{window.location.href = `${process.env.REACT_APP_API_URL}/oauth2/authorization/naver`;}}/>
      </div>
    </div>

  );
}

export default Signin;
