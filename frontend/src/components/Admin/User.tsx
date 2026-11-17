import axios from 'axios';
import React, { useEffect, useState } from "react";
import { AdminMember, PageResponse } from "../../types";
import { FiRefreshCw } from "react-icons/fi";
import { MdDelete } from "react-icons/md";
import { errorMessage } from '../../auth';
function User() {
const [members, setMembers] = useState<AdminMember[]>([]);
  const [filters, setFilters] = useState({ page: 0, size: 10 }); // 현재 페이지와 페이지 크기
  const [totalPages, setTotalPages] = useState(0);

  const fetchMembers = async () => {
  try {
    const res = await axios.get<PageResponse<AdminMember>>(`${process.env.REACT_APP_API_URL}/api/admin/memberAll?page=${filters.page}&size=${filters.size}`, {
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

const acceptUser = async (memberId: number) => {
  try {
    const res = await axios.post(`${process.env.REACT_APP_API_URL}/api/admin/acceptUser`, { memberId }, {
      headers: {
        Authorization: `Bearer ${localStorage.getItem("accessToken")}`
      },
    });
    alert(res.data);
    fetchMembers(); // 성공 시 리스트 갱신
  } catch (error: any) {
    alert(errorMessage(error, "요청에 실패했습니다."));
  }
}

const banUser = async (member: AdminMember) => {
  if (!window.confirm(`${member.nickname}(${member.email}) 계정을 비활성화할까요? 바로 로그아웃되고 로그인할 수 없습니다.`)) return;
  const memberId = member.id;
  try {
    const res = await axios.post(`${process.env.REACT_APP_API_URL}/api/admin/banUser`, { memberId }, {
      headers: {
        Authorization: `Bearer ${localStorage.getItem("accessToken")}`
      },
    });
    alert(res.data);
    fetchMembers(); // 성공 시 리스트 갱신
  } catch (error: any) {
    alert(errorMessage(error, "요청에 실패했습니다."));
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
                            ) : member.username !== localStorage.getItem('username') && (
                              // 자기 계정은 막지 못한다(서버도 400)
                              <button className="delete-btn" onClick={() => banUser(member)}>
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