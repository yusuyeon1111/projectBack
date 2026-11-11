import axios from 'axios';
import React, { useState, useEffect } from "react";
import { Post as PostType, PageResponse } from "../../types";
import { FiRefreshCw } from "react-icons/fi";
import { MdDelete } from "react-icons/md";
import { errorMessage } from '../../auth';
function Post() {
  const [posts, setPosts] = useState<PostType[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [filters, setFilters] = useState({
        page: 0,
        size: 10,
        sortType: "latest",
    });

    const fetchPosts = async () => {
        try {
        const res = await axios.post<PageResponse<PostType>>(`${process.env.REACT_APP_API_URL}/api/post/list`, filters);
        setPosts(res.data.content);
        setTotalPages(res.data.totalPages);
        console.log(res.data.content)
        } catch (error) {
        console.error(error);
        }
    };

    useEffect(() => {
    fetchPosts();
    }, [filters.page, filters.size]);

 const changePostStatus = async (postId: number, delYn: "Y" | "N") => {
  try {
    const res = await axios.post(`${process.env.REACT_APP_API_URL}/api/admin/changePostStatus`, {id: postId, delYn: delYn }, {
      headers: {
        Authorization: `Bearer ${localStorage.getItem("accessToken")}`
      },
    });
    alert(res.data);
    fetchPosts(); // 성공 시 리스트 갱신
  } catch (error: any) {
    alert(errorMessage(error, "요청에 실패했습니다."));
  }
}
  return (
      <div className='adminUser-container'>
           <p className='userP'>게시글 목록</p>
                <table>
                        <thead>
                        <tr>
                            <th>ID</th>
                            <th>작성자</th>
                            <th>제목</th>
                            <th>좋아요 수</th>
                            <th>조회수</th>
                            <th>작성일자</th>
                            <th>수정일자</th>
                            <th>게시글 관리</th>
                        </tr>
                        </thead>
                        <tbody>
                {posts.map((post, index) => (
                    <tr key={post.id}>
                    <td data-label="NO">{posts.length - index}</td>
                    <td data-label="작성자">{post.author}</td>
                    <td data-label="제목">{post.title}</td>
                    <td data-label="좋아요 수">{post.likeCount}</td>
                    <td data-label="조회수">{post.viewCount}</td>
                    <td data-label="작성일자">{new Date(post.created).toLocaleString()}</td>
                    <td data-label="수정일자">{post.updated && new Date(post.updated).toLocaleString()}</td>
                    <td data-label="게시글 관리">{post.delYn === 'N' ?
                        <button onClick={() => changePostStatus(post.id, 'Y')}  className="delete-btn">
                            <MdDelete /> 게시글 비공개
                        </button>
                    :
                        <button onClick={() => changePostStatus(post.id, 'N')}>
                            <FiRefreshCw /> 게시글 공개
                        </button>
                    }</td>
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

export default Post