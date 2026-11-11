import axios from 'axios';

const AUTH_KEYS = ['accessToken', 'refreshToken', 'username', 'role'];

export const clearAuth = () => AUTH_KEYS.forEach(k => localStorage.removeItem(k));

// 모든 axios 요청에 토큰을 붙인다. 백엔드는 요청값이 아닌 토큰으로 사용자를 식별한다.
axios.interceptors.request.use(config => {
  const token = localStorage.getItem('accessToken');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// ponytail: refresh token 재발급 API가 없어서 만료(2시간) 시 재로그인. 재발급 엔드포인트가 생기면 여기서 갱신.
axios.interceptors.response.use(undefined, error => {
  if (error.response?.status === 401 && localStorage.getItem('accessToken')) {
    clearAuth();
    alert('로그인이 만료되었습니다. 다시 로그인해주세요.');
    window.location.href = '/signin';
  }
  return Promise.reject(error);
});

// 서버 에러 본문은 문자열(MemberController, 신청 API)이거나 {error: 메시지}(GlobalExceptionHandler)다
export const errorMessage = (error: any, fallback: string): string => {
  const data = error?.response?.data;
  return data?.error ?? (typeof data === 'string' && data ? data : fallback);
};
