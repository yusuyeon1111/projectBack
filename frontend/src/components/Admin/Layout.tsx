import React from 'react'
import Header from './Header'
import { Navigate, Outlet } from 'react-router-dom'

// 데이터는 서버가 /api/admin/**으로 막는다. 여기서는 관리자가 아니면 빈 관리자 화면 대신 메인으로 보낸다
function Layout() {
  if (localStorage.getItem('role') !== 'ROLE_ADMIN') return <Navigate to="/" replace />;
  return (
    <>
        <Header/>
        <Outlet/>
    </>
  )
}

export default Layout
