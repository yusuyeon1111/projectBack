import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import './App.css'; // 공용 버튼·폰트. App.tsx는 렌더되지 않아 거기서의 import는 번들에 안 들어간다
import './auth';
import App from './App';
import SignIn from './components/SignIn';
import OAuth2Success from './components/OAuth2Success';
import {BrowserRouter, Route, Routes} from "react-router-dom"
import reportWebVitals from './reportWebVitals';
import MemberSignUp from './components/MemberSignUp';
import MyPage from './components/MyPage';
import Post from './components/Post';
import Layout from './components/Layout';
import Main from './components/Main';
import PostForm from './components/PostForm';
import StudyForm from './components/StudyForm';
import PostView from './components/PostView';
import StudyView from './components/StudyView';
import LoginRequiredModal from './components/LoginRequiredModal';
import Admin from './components/Admin/Admin';
import User from './components/Admin/User';
import AdminLayout from './components/Admin/Layout';
import { Toaster } from './components/Toast';

// 날짜 칸은 달력 아이콘을 눌러야만 달력이 열려서, 칸 어디를 눌러도 열리게 한다(글 작성·수정의 모든 날짜 칸)
document.addEventListener('click', e => {
  const input = e.target as HTMLInputElement;
  if (input instanceof HTMLInputElement && input.type === 'date' && !input.disabled && !input.readOnly) {
    try { input.showPicker(); } catch { /* 지원하지 않는 브라우저는 원래대로 */ }
  }
});

const root = ReactDOM.createRoot(document.getElementById('root') as HTMLElement);
root.render(
  <BrowserRouter basename = {process.env.PUBLIC_URL}>
    <Routes>
      <Route element={<Layout/>}>
        <Route path="/" element={<Main/>}/>
        <Route path="/myPage" element={<MyPage/>}/>
        <Route path='/post' element={<Post/>}/>
        <Route path='/postform' element={<PostForm/>}/>
        <Route path="/post/:postId" element={<PostView/>}/>
        <Route path="/study/:postId" element={<StudyView/>}/>
        <Route path='studyform' element={<StudyForm/>}/>
      </Route>
      <Route path="/signin" element={<SignIn/>}/>
      <Route path="/membersignup" element={<MemberSignUp/>}/>
      <Route path="/oauth2/success" element={<OAuth2Success/>}/>
      <Route path="/user" element={<User/>}/>
      <Route element={<AdminLayout/>}>
        <Route path="/admin" element={<Admin/>}/>
      </Route>
    </Routes>
    <Toaster/>
  </BrowserRouter>
);

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
