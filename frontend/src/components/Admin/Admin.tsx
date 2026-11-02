import { useState } from "react";
import Dashboard from "./Dashboard";
import User from "./User";
import Post from "./Post";
import { FaBars, FaTimes } from "react-icons/fa";
function Admin() {
  const [selectedMenu, setSelectedMenu] = useState("dashboard"); 
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);

  const toggleSidebar = () => { 
    setIsSidebarOpen((prev) => !prev); 
  };

  return (
    <div className="admin-container">
      {/* 햄버거 버튼 */}
      <button className="sidebar-toggle" onClick={toggleSidebar}>
        {isSidebarOpen ? <FaTimes size={24} /> : <FaBars size={24} />}
      </button>

      <div className="admin-inner">
        {/* 사이드바 */}
        <div className={`admin-sideBar ${isSidebarOpen ? "open" : ""}`}>
          <h2>메뉴</h2>
          <ul>
            <li>
              <a 
                className={selectedMenu === "dashboard" ? "active" : ""} 
                onClick={() => setSelectedMenu("dashboard")}
              >
                🏠 대시보드
              </a>
            </li>
            <li>
              <a 
                className={selectedMenu === "user" ? "active" : ""} 
                onClick={() => setSelectedMenu("user")}
              >
                👤 회원 관리
              </a>
            </li>
            <li>
              <a 
                className={selectedMenu === "posts" ? "active" : ""} 
                onClick={() => setSelectedMenu("posts")}
              >
                📕 게시글 관리
              </a>
            </li>
          </ul>
        </div>

        {/* 콘텐츠 영역 */}
          <div className={`admin-content ${isSidebarOpen ? "hidden" : ""}`}>
          {selectedMenu === "dashboard" && <Dashboard />}
          {selectedMenu === "user" && <User />}
          {selectedMenu === "posts" && <Post />}
        </div>
         {isSidebarOpen && <div className="overlay" onClick={toggleSidebar}></div>}
      </div>
    </div>
  );
}

export default Admin;
