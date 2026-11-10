import React, { useEffect, useState, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { clearAuth } from "../auth";
import axios from 'axios';
import { IoBookmark } from "react-icons/io5";
import { FaUserCircle } from "react-icons/fa";
import { MdEdit } from "react-icons/md";
import NotificationBell from "./NotificationBell";
import '../css/Header.css';

function Header() {
  const navigate = useNavigate();
  const location = useLocation();
  const [showMenu, setShowMenu] = useState(false);
  const [showEditMenu, setShowEditMenu] = useState(false);
  const [showNotification, setShowNotification] = useState(false);
  const [accessToken, setAccessToken] = useState<string | null>("");

  const menuRef = useRef<HTMLDivElement>(null); // 메뉴 영역 ref

  // 토큰 불러오기
  useEffect(() => {
    setAccessToken(localStorage.getItem("accessToken"));
  }, []);

  // 경로 변경 시 메뉴 닫기
  useEffect(() => {
    setShowMenu(false);
    setShowEditMenu(false);
    setShowNotification(false);
  }, [location.pathname]);

  // 외부 클릭 시 메뉴 닫기
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setShowMenu(false);
        setShowEditMenu(false);
        setShowNotification(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const toggleMenu = () => {
    setShowMenu(prev => {
      if (!prev) { setShowEditMenu(false); setShowNotification(false); }
      return !prev;
    });
  };

  const editMenu = () => {
    setShowEditMenu(prev => {
      if (!prev) { setShowMenu(false); setShowNotification(false); }
      return !prev;
    });
  };

  const toggleNotification = () => {
    setShowNotification(prev => {
      if (!prev) { setShowMenu(false); setShowEditMenu(false); }
      return !prev;
    });
  };

  const goToLogout = async () => {
    try {
      await axios.post(`${process.env.REACT_APP_API_URL}/api/member/logout`, null, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      clearAuth();
      alert("로그아웃 되었습니다.");
      navigate("/");
      window.location.reload();
    } catch (error) {
      console.error("로그아웃 실패", error);
      alert("로그아웃 중 오류가 발생했습니다.");
    }
  };

  return (
    <div className='header-container' ref={menuRef}>
      <div className='logo-container'>
        <IoBookmark id='bookmark'/>
        <span id='logo' onClick={()=>navigate("/")}>팀해요</span>
      </div>

      <div className='member-container'>
        {accessToken && (
          <>
            <NotificationBell open={showNotification} onToggle={toggleNotification}/>
            <MdEdit className='memberIcon' onClick={editMenu}/>
            {showEditMenu && (
              <div className='menu-container'>
                <p onClick={() => navigate("/postform")}>프로젝트 모집글 작성</p>
                <p onClick={() => navigate("/studyform")}>스터디 모집글 작성</p>
              </div>
            )}
            <span id='line'>|</span>
          </>
        )}

        <FaUserCircle onClick={toggleMenu} id='userIcon'/>

        {showMenu && (
          <div className='menu-container'>
            {!accessToken ? (
              <>
                <p onClick={() => navigate("/signin")}>로그인</p>
                <p onClick={() => navigate("/membersignup")}>회원가입</p>
              </>
            ) : (
              <>
                <p onClick={() => navigate("/myPage")}>마이페이지</p>
                <p onClick={goToLogout}>로그아웃</p>
              </>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

export default Header;
