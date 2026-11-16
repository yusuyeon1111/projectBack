import axios from 'axios';

const AUTH_KEYS = ['accessToken', 'refreshToken', 'username', 'role'];

export const clearAuth = () => AUTH_KEYS.forEach(k => localStorage.removeItem(k));

// 모든 axios 요청에 토큰을 붙인다. 백엔드는 요청값이 아닌 토큰으로 사용자를 식별한다.
axios.interceptors.request.use(config => {
  const token = localStorage.getItem('accessToken');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

const expire = () => {
  clearAuth();
  alert('로그인이 만료되었습니다. 다시 로그인해주세요.');
  window.location.href = '/signin';
};

const isReissue = (url?: string) => !!url?.endsWith('/api/member/reissue');

// 동시에 401이 여러 개 나도 재발급 요청은 하나만 보내고 모두 그 결과를 기다린다.
// 서버는 refresh token을 한 번 쓰면 바꾸므로, 각자 보내면 뒤에 간 요청이 "이미 쓴 토큰"이 된다.
let refreshing: Promise<void> | null = null;
const refreshTokens = () => {
  refreshing ??= axios
    .post(`${process.env.REACT_APP_API_URL}/api/member/reissue`, { refreshToken: localStorage.getItem('refreshToken') })
    .then(({ data }) => {
      localStorage.setItem('accessToken', data.accessToken);
      localStorage.setItem('refreshToken', data.refreshToken);
    })
    .finally(() => { refreshing = null; });
  return refreshing;
};

// access token이 만료되면(401) refresh token으로 새로 받아 원래 요청을 한 번 다시 보낸다
axios.interceptors.response.use(undefined, async error => {
  const config = error.config;
  if (error.response?.status !== 401 || !localStorage.getItem('accessToken') || !config || isReissue(config.url)) {
    return Promise.reject(error);
  }
  if (config._retried) { // 새 토큰으로도 401이면 더 할 수 있는 것이 없다
    expire();
    return Promise.reject(error);
  }
  config._retried = true;
  try {
    // 다른 요청(다른 탭 포함)이 이미 토큰을 바꿨으면 재발급 없이 새 토큰으로만 다시 보낸다
    if (config.headers?.Authorization === `Bearer ${localStorage.getItem('accessToken')}`) await refreshTokens();
    return await axios(config); // 요청 인터셉터가 새 토큰을 붙인다
  } catch (e: any) {
    if (isReissue(e?.config?.url) && e.response?.status === 401) expire();
    return Promise.reject(e);
  }
});

// 서버 에러 본문은 문자열(MemberController, 신청 API)이거나 {error: 메시지}(GlobalExceptionHandler)다
export const errorMessage = (error: any, fallback: string): string => {
  const data = error?.response?.data;
  return data?.error ?? (typeof data === 'string' && data ? data : fallback);
};
