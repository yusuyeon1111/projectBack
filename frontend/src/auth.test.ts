import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import './auth';

// jest 27이 axios의 ESM 진입점을 못 읽어서 CJS 빌드를 가리킨다
jest.mock('axios', () => jest.requireActual('axios/dist/browser/axios.cjs'));

// 서버 대신 응답을 정하는 어댑터. 만료된 토큰(old)이면 401, 재발급은 new 토큰을 준다
const calls: string[] = [];
let reissueStatus = 200;
const reply = (config: InternalAxiosRequestConfig, status: number, data: any) => {
  const response = { status, data, config, headers: {}, statusText: '' };
  return status < 400 ? Promise.resolve(response)
    : Promise.reject(new AxiosError('fail', String(status), config, null, response as any));
};

beforeEach(() => {
  calls.length = 0;
  reissueStatus = 200;
  localStorage.setItem('accessToken', 'old');
  localStorage.setItem('refreshToken', 'rt-old');
  window.alert = jest.fn();
  axios.defaults.adapter = (config) => {
    calls.push(`${config.url} ${config.headers.Authorization ?? ''}`.trim());
    if (config.url?.endsWith('/api/member/reissue')) {
      return reply(config, reissueStatus, { accessToken: 'new', refreshToken: 'rt-new' });
    }
    return config.headers.Authorization === 'Bearer new' ? reply(config, 200, 'ok') : reply(config, 401, {});
  };
});

test('동시에 401이 나도 재발급은 한 번만 하고 모든 요청을 새 토큰으로 다시 보낸다', async () => {
  const results = await Promise.all([axios.get('/a'), axios.get('/b'), axios.get('/c')]);

  expect(results.map(r => r.data)).toEqual(['ok', 'ok', 'ok']);
  expect(calls.filter(c => c.includes('reissue'))).toHaveLength(1);
  expect(localStorage.getItem('refreshToken')).toBe('rt-new');
});

test('재발급이 거절되면 로그아웃한다', async () => {
  reissueStatus = 401;

  await expect(axios.get('/a')).rejects.toBeTruthy();
  expect(localStorage.getItem('accessToken')).toBeNull();
  expect(window.alert).toHaveBeenCalledTimes(1);
});
