import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import Layout from './Layout';

jest.mock('./Header', () => () => null);

const renderAdmin = () => render(
  <MemoryRouter initialEntries={['/admin']}>
    <Routes>
      <Route path="/" element={<p>메인</p>} />
      <Route element={<Layout />}><Route path="/admin" element={<p>관리자 화면</p>} /></Route>
    </Routes>
  </MemoryRouter>,
);

afterEach(() => localStorage.clear());

test('관리자가 아니면 /admin에서 메인으로 보낸다', () => {
  localStorage.setItem('role', 'ROLE_USER');
  renderAdmin();
  expect(screen.getByText('메인')).toBeInTheDocument();
});

test('관리자는 /admin을 본다', () => {
  localStorage.setItem('role', 'ROLE_ADMIN');
  renderAdmin();
  expect(screen.getByText('관리자 화면')).toBeInTheDocument();
});
