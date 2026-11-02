import React, { useState } from 'react';
import '../css/Sign.css';
import { useForm } from 'react-hook-form';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';
import { Chip, TextField, Stack } from "@mui/material";
import naver from '../assets/naver.png';
import kakao from '../assets/kakao.png';

function MemberSignUp() {
  const navigate = useNavigate();
  const [stackInput, setStackInput] = useState("");
  const [stackList, setStackList] = useState([]);

  // 중복 체크 상태
  const [isIdChecked, setIsIdChecked] = useState(false);
  const [isEmailChecked, setIsEmailChecked] = useState(false);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    getValues,
    formState: { errors },
  } = useForm();

  const password = watch("password");

  // 입력 변화 시 중복 체크 초기화
  const handleUsernameChange = (e) => {
    setIsIdChecked(false);
    setValue("username", e.target.value, { shouldValidate: true });
  };

  const handleEmailChange = (e) => {
    setIsEmailChecked(false);
    setValue("email", e.target.value, { shouldValidate: true });
  };

  // 아이디 중복 체크
  const idCheck = async () => {
    const username = getValues("username"); // 항상 최신값
    if (!username) return alert("아이디를 입력해주세요");

    try {
      const response = await axios.get(`${process.env.REACT_APP_API_URL}/api/member/idCheck`, { params: { username } });
      if (response.data.includes("가능")) {
          setIsIdChecked(true);
          alert(response.data)
        } else {
          setIsIdChecked(false);
          alert(response.data)
        }

    } catch (error) {
      console.error(error);
      setIsIdChecked(false);
      alert("아이디 중복 체크에 실패했습니다.");
    }
  };

  // 이메일 중복 체크
  const emlCheck = async () => {
    const email = getValues("email");
    if (!email) return alert("이메일을 입력해주세요");

    try {
      const response = await axios.get(`${process.env.REACT_APP_API_URL}/api/member/emlCheck`, { params: { email } });
      if (response.data.includes("사용")) {
        setIsEmailChecked(true);
        alert(response.data);
      } else {
        setIsEmailChecked(false);
        alert(response.data);
      }
    } catch (error) {
      console.error(error);
      setIsEmailChecked(false);
      alert("이메일 중복 체크에 실패했습니다.");
    }
  };

  // 회원가입 제출
  const onsubmit = async (data) => {
      if (!isIdChecked) {
          alert("아이디 중복 체크를 완료해주세요.");
          return;
        }
        if (!isEmailChecked) {
          alert("이메일 중복 체크를 완료해주세요.");
          return;
        }

    try {
      const payload = { ...data, preferredTechStacks: stackList };
      await axios.post(`${process.env.REACT_APP_API_URL}/api/member/signup`, payload, { headers: { "Content-Type": "application/json" } });
      alert("회원가입이 완료되었습니다!");
      navigate("/signIn");
    } catch (error) {
      console.error("회원가입 오류:", error);
      alert("회원가입에 실패했습니다. 다시 시도해주세요.");
    }
  };

  // 기술 스택 추가/삭제
  const handleAddStack = () => {
    const trimmed = stackInput.trim();
    if (trimmed && !stackList.includes(trimmed)) {
      setStackList([...stackList, trimmed]);
      setStackInput("");
    }
  };
  const handleDeleteStack = (stackToDelete) => {
    setStackList(stackList.filter((stack) => stack !== stackToDelete));
  };

  const emailRules = {
    required: "이메일을 입력해주세요",
    pattern: { value: /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i, message: "올바른 이메일 형식이 아닙니다." },
    minLength: { value: 6, message: "이메일은 최소 6자 이상이어야 합니다." },
  };

  return (
    <div className='auth-container'>
      <h2>회원가입</h2>
      <div className='oauth-container'>
        <span>소셜로 간편하게 로그인하세요</span>
        <img src={kakao} id='kakaoImg' onClick={()=>{window.location.href = "http://localhost:8080/oauth2/authorization/kakao";}}/>
        <img src={naver} id='naverImg' onClick={()=>{window.location.href = "http://localhost:8080/oauth2/authorization/naver";}}/>
      </div>

      <form className='auth-form' onSubmit={handleSubmit(onsubmit)}>
        <p className='subtitle'>회원 정보를 입력해주세요</p>

        <label htmlFor='name'>이름</label>
        <input {...register("name", { required: "이름을 입력해주세요" })} />
        {errors.name && <p className='error'>{errors.name.message}</p>}

        <label htmlFor='nickname'>닉네임</label>
        <input {...register("nickname", { required: "닉네임을 입력해주세요" })} />
        {errors.nickname && <p className='error'>{errors.nickname.message}</p>}

        <label htmlFor='username'>아이디</label>
        <div className="input-button-group">
          <input {...register("username", { required: "아이디를 입력해주세요" })} onChange={handleUsernameChange} />
          <button type="button" onClick={idCheck} id='idChkBtn'>중복체크</button>
        </div>
        {errors.username && <p className='error'>{errors.username.message}</p>}

        <label htmlFor='email'>이메일</label>
        <div className="input-button-group">
          <input type='email' {...register("email", emailRules)} onChange={handleEmailChange} />
          <button type="button" onClick={emlCheck} id='emkChkBtn'>중복체크</button>
        </div>
        {errors.email && <p className='error'>{errors.email.message}</p>}

        <label htmlFor='password'>비밀번호</label>
        <input
          type="password"
          {...register("password", {
            required: "비밀번호를 입력해주세요",
            minLength: { value: 6, message: "6자 이상 입력해주세요!" },
          })}
        />
        {errors.password && <p className='error'>{errors.password.message}</p>}

        <label htmlFor='pwConfirm'>비밀번호 확인</label>
        <input
          type="password"
          {...register("pwConfirm", {
            required: "비밀번호 확인은 필수입니다",
            validate: (value) => value === password || "비밀번호가 일치하지 않습니다!",
          })}
        />
        {errors.pwConfirm && <p className='error'>{errors.pwConfirm.message}</p>}

        <label htmlFor='preferredPosition'>희망 포지션</label>
        <select id='preferredPosition' {...register("preferredPosition", { required: "희망 포지션을 선택해주세요" })}>
          <option value='backend'>BACKEND</option>
          <option value='frontend'>FRONTEND</option>
          <option value='pm'>기획</option>
          <option value='design'>디자인</option>
          <option value='etc'>기타</option>
        </select>
        {errors.preferredPosition && <p className='error'>{errors.preferredPosition.message}</p>}

        <div className="signStack-container">
          <label>기술스택</label>
          <div className="input-button-group">
            <TextField
              variant="outlined"
              size="small"
              value={stackInput}
              onChange={(e) => setStackInput(e.target.value)}
              onKeyPress={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault();
                  handleAddStack();
                }
              }}
              sx={{
                flex: 1,
                minWidth: 0,
                '& .MuiOutlinedInput-root': {
                  padding: 0,
                  fontSize: '14px',
                  borderRadius: '8px',
                  '& fieldset': { borderColor: '#ccc' },
                },
                '& .MuiOutlinedInput-input': { padding: '12px' },
              }}
            />
            <button type="button" onClick={handleAddStack}>+</button>
          </div>

          <Stack direction="row" spacing={1} flexWrap="wrap" mt={2}>
            {stackList.map((stack, index) => (
              <Chip key={index} label={stack} onDelete={() => handleDeleteStack(stack)} color="primary" sx={{ mb: 1 }} />
            ))}
          </Stack>
        </div>

        <button type='submit'>회원가입</button>
      </form>
    </div>
  );
}

export default MemberSignUp;
