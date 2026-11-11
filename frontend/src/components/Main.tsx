import React, { useEffect, useState } from 'react'
import KakaoMap from './KakaoMap'
import '../css/Main.css'
import { FiSearch } from "react-icons/fi";
import { FaFilter } from "react-icons/fa";
import { IoMdClose } from "react-icons/io";
import PostList from './PostList'
import axios from 'axios';
import { IoLocationSharp } from "react-icons/io5";
import { useNavigate } from 'react-router-dom';
import { Post, PageResponse, postStatusText, regions, roleTextMap } from "../types";

interface Filters {
  category: string | null;
  status: string | null;
  postStatus: string | null;
  region: string | null;
  position: string | null;
  keyword: string | null;
  page: number;
  size: number;
  sortType: string;
  projectType: string | null;
  stackKeyword: string | null;
}
function Main() {
  const navigate = useNavigate();
  const [projectList, setProjectList] = useState<Post[]>([]);
  const [loadError, setLoadError] = useState(false);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [type, setType] = useState('');
  const [filterClick, setFilterClick] = useState(false);
  const [filters, setFilters] = useState<Filters>({
    category: "",
    status: "",
    postStatus: "",
    region: "",
    position: "",
    keyword: "",
    page: 0,
    size: 5,
    sortType: "latest",
    projectType: "",  
    stackKeyword:""
  });

  const fetchPosts = async () => {
    try {
      const res = await axios.post<PageResponse<Post>>(`${process.env.REACT_APP_API_URL}/api/post/list`, filters);
      setProjectList(res.data.content);
      setTotalPages(res.data.totalPages);
      setLoadError(false);
    } catch (error) {
      console.error(error);
      setLoadError(true);
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setFilters(prev => ({
      ...prev,
      [name]: value === "" ? null : value
    }));
};

    useEffect(() => {
      fetchPosts();
  }, [filters]);

  return (
    <div className='main-container'>
      {!filterClick ? <>
      <div className='main-inner'>
        <p id='main-top-p'>
          팀해요에서 자유롭게 <strong>프로젝트</strong>와 <strong>스터디</strong> 팀원을 구해보세요!
        </p>

        {/* 프로젝트 */}
        <div className='main-post-container'>
         <div className='sortButton'>
          <button
            className={filters.sortType === "latest" ? "active" : ""}
            onClick={() => setFilters(prev => ({ ...prev, sortType: "latest", page: 0 }))}
          >
            최신순
          </button>
          <button
            className={filters.sortType === "popular" ? "active" : ""}
            onClick={() => setFilters(prev => ({ ...prev, sortType: "popular", page: 0 }))}
          >
            좋아요 순
          </button>
        </div>

          <div className='main-post-inner'>
          <div className='searchMenu'>
            <div className='searchItem'>
            <label>검색</label>
            <div className='searchBox'>
              <input
                type="text"
                name="keyword"
                placeholder="제목, 내용, 작성자 검색"
                value={filters.keyword ?? ""}
                onChange={handleChange}
              />
                <FiSearch size={18} className="searchIcon" />
            </div>
             </div>
            <div className='searchItem'>
            <label>진행상황</label>
            <select name="postStatus" onChange={handleChange} value={filters.postStatus ?? ""}>
              <option value="">전체</option>
              <option value="SCHEDULED">진행예정</option>
              <option value="IN_PROGRESS">진행중</option>
              <option value="END">진행완료</option>
            </select>
          </div>
          <div className='searchItem'>
            <label>모집상황</label>
            <select name="status" onChange={handleChange} value={filters.status ?? ""}>
              <option value="">전체</option>
              <option value="ING">모집중</option>
              <option value="END">모집완료</option>
            </select>
          </div>
           <div className='searchItem'>
            <label>목적</label>
            <select name="category" onChange={handleChange} value={filters.category ?? ""}>
              <option value="">전체</option>
              <option value="project">프로젝트</option>
              <option value="study">스터디</option>
            </select>
          </div>
          <div className='searchItem'>
            <label>방식</label>
            <select name="projectType" onChange={handleChange} value={filters.projectType ?? ""}>
              <option value="">전체</option>
              <option value="online">온라인</option>
              <option value="offline">오프라인</option>
              <option value="both">온라인/오프라인</option>
            </select>
          </div>
          {filters.projectType !== "" && filters.projectType !== "online" &&(
          <div className='searchItem'>
            <label>지역</label>
            <select name="region" onChange={handleChange} value={filters.region ?? ""}>
              <option value="">전체</option>
              <option value="nothing">지역무관</option>
              {type !== 'online' && regions.map((r) => <option key={r}>{r}</option>)}
            </select>
          </div>
          )}
          <div className='searchItem'>
            <label>포지션</label>
            <select name="position" onChange={handleChange} value={filters.position ?? ""}>
              <option value="">전체</option>
              <option value="frontend">프론트엔드</option>
              <option value="backend">백엔드</option>
              <option value="designer">디자이너</option>
              <option value="pm">PM</option>
              <option value="etc">기타</option>
            </select>
          </div>
          <div className='searchItem'>
            <label>스택 검색</label>
            <div className='searchBox'>
              <input
                type="text"
                name="stackKeyword"
                placeholder="스택 검색"
                value={filters.stackKeyword ?? ""}
                onChange={handleChange}
              />
                <FiSearch size={18} className="searchIcon" />
            </div>
             </div>
        </div>
        <div className='searchMenuMobile'>
            <div className='searchItem'>
            <label>검색</label>
            <div className='searchBox'>
              <input
                type="text"
                name="keyword"
                placeholder="제목, 내용, 작성자 검색"
                value={filters.keyword ?? ""}
                onChange={handleChange}
              />
                <FiSearch size={18} className="searchIcon" />
            </div>
             </div>
              <div className='searchIcon'>
                <span>필터</span>
                <FaFilter size={18} onClick={()=>setFilterClick(!filterClick)}/>
              </div>
        </div>
          {projectList.length > 0 ?<>
            <div className='itemList'>
            {projectList
            .map((post) => (
              <div key={post.id} className='studyItem'>
                <div className='itemListTop'>
                  {post.delYn === 'Y' ? 
                  <span id='delLabel'>🔒 비공개</span>
                  :<>
                   {post.category === 'project' ? 
                    <span id='projectLabel'>❤️ 프로젝트</span>
                    :
                    <span id='studyLabel'>🩵 스터디</span>
                    }
                  </>}
               
                <span
                  id='postTitle'
                  onClick={() => {
                    if (post.delYn === 'Y') {
                      alert("관리자에 의해 비공개된 게시글입니다.");
                    } else {
                      navigate(post.category === 'project' ? `/post/${post.id}` : `/study/${post.id}`);
                    }
                  }}
                >
                  {post.title}
                </span>
                <span
                  className='status'
                  data-type={
                    post.postStatus === 'END' ? 'end' : post.postStatus === 'IN_PROGRESS' ? 'active' : 'scheduled'}>
                  {postStatusText(post.postStatus)}
                </span>
                </div>
                {post.category === 'project' && (
                <div className='stack-container'>
                  {post.techStacks.map((stack, index) => (
                    <span key={index} className='stack-pill'># {stack}</span>
                  ))}
                </div>
                )}
                <div className='dataInner'>
                  <span className='myDate'>
                    {post.projectType === "online" && '온라인'}
                    {post.projectType === "offline" && '오프라인'}
                    {post.projectType === "both" && '온라인/오프라인'}
                  </span>
                  <span className='line'>|</span>
                  <span><IoLocationSharp id='locationLogo' /></span>
                  {post.region === 'nothing' ? <span className='myDate'>지역무관</span> : <span className='myDate'>{post.region}</span>}
                  <span className='line'>|</span>
                  <span className='myDate'> 기간 : {post.startDate} - {post.endDate}</span><span className='line'>|</span>
                  <span className='myDate'> 조회수 {post.viewCount}</span><span className='line'>|</span>
                  <span className='myDate'> ❤️ {post.likeCount}</span>
                </div>
              <div>
                 {post.status === 'END' ? (
                  <span data-type="end" className='myDate'><strong>(모집완료)</strong></span>
                ) : (
                  <span data-type="active" className='myDate'><strong>(모집중)</strong></span>
                )}
                <span className='myDate' style={{marginLeft:'8px'}}>
                {post.category === 'study'
                  ? `모집 인원: ${post.positions[0]?.count ?? 0}명`
                  : `포지션: ${[...new Set(post.positions.map(pos => roleTextMap[pos.role]))].join(", ")}`}
              </span>
              </div>
              </div>
            ))}
          </div>
          </> :<div className='emptyItem'><p id='emptyMent'>{loadError ? '목록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.' : '데이터 없음.'}</p></div>
          }
          </div>
          {projectList.length > 0 && (
          <div className="pagination">
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
          )}
          </div>
        </div>
        </> :
          <>
            <div className='filterMenuMobile'>
              <div className='filterHeader'>
                <span id='filterTop'>필터 상세</span>
                <IoMdClose size={25} id='closeFilter' onClick={()=>{setFilterClick(false)}}/>
              </div>
              <div className='filterContent'></div>
              <div className='searchItem'>
            <label>검색</label>
            <div className='searchBox'>
              <input
                type="text"
                name="keyword"
                placeholder="제목, 내용, 작성자 검색"
                value={filters.keyword ?? ""}
                onChange={handleChange}
              />
                <FiSearch size={18} className="searchIcon" />
            </div>
             </div>
            <div className='searchItem'>
            <label>진행상황</label>
            <select name="postStatus" onChange={handleChange} value={filters.postStatus ?? ""}>
              <option value="">전체</option>
              <option value="SCHEDULED">진행예정</option>
              <option value="IN_PROGRESS">진행중</option>
              <option value="END">진행완료</option>
            </select>
          </div>
          <div className='searchItem'>
            <label>모집상황</label>
            <select name="status" onChange={handleChange} value={filters.status ?? ""}>
              <option value="">전체</option>
              <option value="ING">모집중</option>
              <option value="END">모집완료</option>
            </select>
          </div>
           <div className='searchItem'>
            <label>목적</label>
            <select name="category" onChange={handleChange} value={filters.category ?? ""}>
              <option value="">전체</option>
              <option value="project">프로젝트</option>
              <option value="study">스터디</option>
            </select>
          </div>
          <div className='searchItem'>
            <label>방식</label>
            <select name="projectType" onChange={handleChange} value={filters.projectType ?? ""}>
              <option value="">전체</option>
              <option value="online">온라인</option>
              <option value="offline">오프라인</option>
              <option value="both">온라인/오프라인</option>
            </select>
          </div>
          {filters.projectType !== "" && filters.projectType !== "online" &&(
          <div className='searchItem'>
            <label>지역</label>
            <select name="region" onChange={handleChange} value={filters.region ?? ""}>
              <option value="">전체</option>
              <option value="nothing">지역무관</option>
              {type !== 'online' && regions.map((r) => <option key={r}>{r}</option>)}
            </select>
          </div>
          )}
          <div className='searchItem'>
            <label>포지션</label>
            <select name="position" onChange={handleChange} value={filters.position ?? ""}>
              <option value="">전체</option>
              <option value="frontend">프론트엔드</option>
              <option value="backend">백엔드</option>
              <option value="designer">디자이너</option>
              <option value="pm">PM</option>
              <option value="etc">기타</option>
            </select>
          </div>
          <div className='searchItem'>
            <label>스택 검색</label>
            <div className='searchBox'>
              <input
                type="text"
                name="stackKeyword"
                placeholder="스택 검색"
                value={filters.stackKeyword ?? ""}
                onChange={handleChange}
              />
                <FiSearch className="searchIcon" />
            </div>
             </div>
            </div>
          </>
        }
    </div>
  )
}

export default Main;
