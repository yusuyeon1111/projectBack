import axios from 'axios';
import React, { useEffect, useState } from 'react'
import { FiRefreshCw } from "react-icons/fi";
import { MdDelete } from "react-icons/md";
function User() {
const [members, setMembers] = useState([]);
  const [filters, setFilters] = useState({ page: 0, size: 10 }); // 현재 페이지와 페이지 크기
  const [totalPages, setTotalPages] = useState(0);

  const fetchMembers = async () => {
  try {
    const res = await axios.get(`${process.env.REACT_APP_API_URL}/api/admin/memberAll?page=${filters.page}&size=${filters.size}`, {
      headers: {
        Authorization: `Bearer ${localStorage.getItem("accessToken")}`
      }
    });
    setMembers(res.data.content);
    setTotalPages(res.data.totalPages);
  } catch (error) {
    console.error(error);
  }
};

useEffect(() => {
  fetchMembers();
}, [filters.page, filters.size]);

const acceptUser = async (memberId) => {
  try {
    const res = await axios.post(`${process.env.REACT_APP_API_URL}/api/admin/acceptUser`, { memberId }, {
      headers: {
        Authorization: `Bearer ${localStorage.getItem("accessToken")}`
      },
    });
    alert(res.data);
    fetchMembers(); // 성공 시 리스트 갱신
  } catch (error) {
    alert(error.data);
  }
}

const banUser = async (memberId) => {
  try {
    const res = await axios.post(`${process.env.REACT_APP_API_URL}/api/admin/banUser`, { memberId }, {
      headers: {
        Authorization: `Bearer ${localStorage.getItem("accessToken")}`
      },
    });
    alert(res.data);
    fetchMembers(); // 성공 시 리스트 갱신
  } catch (error) {
    alert(error.data);
  }
}

  
  return (
        <div className='adminUser-container'>
       <p className='userP'>회원 목록</p>
            <table>
                    <thead>
                    <tr>
                        <th>ID</th>
                        <th>이메일</th>
                        <th>이름</th>
                        <th>닉네임</th>
                        <th>가입일</th>
                        <th>계정 관리</th>
                    </tr>
                    </thead>
                    <tbody>
                      {members.map((member, index) => (
                        <tr key={member.id}>
                          <td data-label="NO">{members.length - index}</td>
                          <td data-label="이메일">{member.email}</td>
                          <td data-label="이름">{member.name}</td>
                          <td data-label="닉네임">{member.nickname}</td>
                          <td data-label="가입일">{new Date(member.createdAt).toLocaleString()}</td>
                          <td data-label="계정 관리">
                            {member.delYn === 'Y' ? (
                              <button onClick={() => acceptUser(member.id)}>
                                <FiRefreshCw /> 계정 활성화
                              </button>
                            ) : (
                              <button className="delete-btn" onClick={() => banUser(member.id)}>
                                <MdDelete /> 계정 비활성화
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>



      </table>

      <div className="paginationDark">
        <button
          onClick={() => setFilters(prev => ({ ...prev, page: Math.max(prev.page - 1, 0) }))}
          disabled={filters.page === 0}
        >
          이전
        </button>

        {Array.from({ length: totalPages }, (_, i) => (
          <button
            key={i}
            className={filters.page === i ? "active" : ""}
            onClick={() => setFilters(prev => ({ ...prev, page: i }))}
          >
            {i + 1}
          </button>
        ))}

        <button
          onClick={() => setFilters(prev => ({ ...prev, page: Math.min(prev.page + 1, totalPages - 1) }))}
          disabled={filters.page === totalPages - 1}
        >
          다음
        </button>
      </div>
    </div>
  )
}

export default User