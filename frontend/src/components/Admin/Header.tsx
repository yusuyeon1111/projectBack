import { useRef, useEffect, useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { clearAuth } from "../../auth";
import axios from "axios";
import { IoBookmark } from "react-icons/io5";
import { FaUserCircle } from "react-icons/fa";
import '../../css/admin/Header.css';
function Header() {
  const navigate = useNavigate();
  const location = useLocation();
  const [showMenu, setShowMenu] = useState(false);
  const [showEditMenu, setShowEditMenu] = useState(false);
  const [accessToken, setAccessToken] = useState<string | null>("");

  const menuRef = useRef<HTMLDivElement>(null); // 메뉴 영역 ref

  useEffect(() => {
    setAccessToken(localStorage.getItem("accessToken"));
  }, []);

  // 경로가 바뀌면 메뉴 닫기
  useEffect(() => {
    setShowMenu(false);
    setShowEditMenu(false);
  }, [location.pathname]);

  // 외부 클릭 시 메뉴 닫기
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setShowMenu(false);
        setShowEditMenu(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, []);

  const toggleMenu = () => {
    setShowMenu(prev => {
      if (!prev) setShowEditMenu(false);
      return !prev;
    });
  };

  const editMenu = () => {
    setShowEditMenu(prev => {
      if (!prev) setShowMenu(false);
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
      navigate("/")
      window.location.reload();
    } catch (error) {
      console.error("로그아웃 실패", error);
      alert("로그아웃 중 오류가 발생했습니다.");
    }
  };

  return (
    <div className='adminHeader-container'>
      <div className='adminlogo-container'>
        <IoBookmark id='adminbookmark'/>
        <span id='adminlogo' onClick={()=>navigate("/")}>팀해요 관리자</span>
      </div>
      <div className='adminmember-container' ref={menuRef}>
        <FaUserCircle onClick={toggleMenu} id='adminuserIcon'/>

        {showMenu && (
          <div className='adminmenu-container'>
            {!accessToken ? (
              <>
                <p onClick={() => navigate("/signin")}>로그인</p>
                <p onClick={() => navigate("/membersignup")}>회원가입</p>
              </>
            ) : (
              <>
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
