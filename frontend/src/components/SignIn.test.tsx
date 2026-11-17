import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import userEvent from '@testing-library/user-event';
import axios from 'axios';
import SignIn from './SignIn';

jest.mock('axios', () => ({
  __esModule: true,
  default: { post: jest.fn(), isAxiosError: jest.fn() },
}));
const api = axios as jest.Mocked<typeof axios>;

test('관리자 권한이 목록 첫 값이 아니어도 관리자 화면으로 간다', async () => {
  api.post.mockResolvedValue({ data: { accessToken: 'a', refreshToken: 'r', roles: ['ROLE_USER', 'ROLE_ADMIN'] } } as any);
  render(
    <MemoryRouter initialEntries={['/signin']}>
      <Routes>
        <Route path="/signin" element={<SignIn />} />
        <Route path="/admin" element={<p>관리자 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
  await userEvent.type(screen.getByLabelText('아이디'), 'admin');
  await userEvent.type(screen.getByLabelText('비밀번호'), 'pw');
  await userEvent.click(screen.getByRole('button', { name: '로그인' }));

  expect(await screen.findByText('관리자 화면')).toBeInTheDocument();
  expect(localStorage.getItem('role')).toBe('ROLE_ADMIN');
});
