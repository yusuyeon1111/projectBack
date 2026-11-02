import axios from 'axios';
import React, { useEffect, useState } from 'react'
import { Bar, BarChart, CartesianGrid, Cell, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import '../../css/admin/Admin.css'
function Dashboard() {
  const [memberData , setMemberData] = useState([]);
  const [postData , setPostData] = useState([]);
  const [statusData , setStatusData] = useState([]);
  const [positionData, setPositionData] = useState([]);
  const [categoryData, setCategoryData] = useState([]);
  const [chartData, setChartData] = useState([]);
  useEffect(() => {
    fetchPosts();
  },[])

  const fetchPosts = async () => {
    try {
      const res = await axios.get(`${process.env.REACT_APP_API_URL}/api/admin/chartAll`, {
        params: { year: 2025 },
        headers: {
          Authorization: `Bearer ${localStorage.getItem("accessToken")}`
        }
      });
      setStatusData(res.data.postStsStatus)
      const position = await axios.get(`${process.env.REACT_APP_API_URL}/api/admin/chartPosition`, {
        params: { year: 2025 },
        headers: {
          Authorization: `Bearer ${localStorage.getItem("accessToken")}`
        }
      });
      setPositionData(position.data)
      console.log(position.data)

      const post = await axios.get(`${process.env.REACT_APP_API_URL}/api/admin/chartPost`, {
        params: { year: 2025 },
        headers: {
          Authorization: `Bearer ${localStorage.getItem("accessToken")}`
        }
      });
      const chartData = mergeChartData(res.data.postStatus, post.data);
      setChartData(chartData);

      const member = await axios.get(`${process.env.REACT_APP_API_URL}/api/admin/chartPre`, {
        params: { year: 2025 },
        headers: {
          Authorization: `Bearer ${localStorage.getItem("accessToken")}`
        }
      });
      
        const data = memberChartData(res.data.memberStatus, member.data);
        setMemberData(data);
    } catch (error) {
      console.error(error);
    }
  };

  const mergeChartData = (postStatus, CategoryStatus) => {
    const base = {};
    postStatus.forEach(item => {
      base[item.name] = {
        name: item.name,
        total: item.count,
        project: 0,
        study: 0,
      }
    })
    CategoryStatus.forEach(item => {
      if(!base[item.name]) {
        base[item.name] = {name:item.name, total:0, project:0, study:0};
      }
      base[item.name][item.category] = item.count;
    })
    return Object.values(base);
  }

    const memberChartData = (totalStatus, categoryStatus) => {
      const base = {};

      totalStatus.forEach(item => {
        base[item.name] = {
          name: item.name,
          total: item.count,
          backend: 0,
          frontend: 0,
          pm: 0,
          design: 0,
          etc: 0
        };
      });

      categoryStatus.forEach(item => {
        if (!base[item.name]) {
          base[item.name] = {
            name: item.name,
            total: 0, 
            backend: 0,
            frontend: 0,
            pm: 0,
            design: 0,
            etc: 0
          };
        }
        base[item.name][item.category] = item.count;
      });
      return Object.values(base);
    };

  return (
  <div className='status-container'>
      <div>
      <p className='statusP'>사용자 통계</p>
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={memberData} margin={{ bottom: 50 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="name" />
              <YAxis />
              <Tooltip />
              <Legend />
              <Line type="monotone" dataKey="total" stroke="#1976d2" strokeWidth={3}  dot={{ r: 5 }} name='총 가입자 수' />
              <Line type="monotone" dataKey="backend" stroke="#f57c00" strokeWidth={3} dot={{ r: 5 }}  name='backend' />
              <Line type="monotone" dataKey="frontend" stroke="#388e3c" strokeWidth={3}  dot={{ r: 5 }} name='frontend' />
              <Line type="monotone" dataKey="pm" stroke="#d32f2f" strokeWidth={3}  dot={{ r: 5 }} name='pm' />
              <Line type="monotone" dataKey="design" stroke="#7b1fa2" strokeWidth={3}  dot={{ r: 5 }} name='design' />
              <Line type="monotone" dataKey="etc" stroke="#5d4037" strokeWidth={3}  dot={{ r: 5 }} name='etc' />
            </LineChart>
          </ResponsiveContainer>
        </div>

        {/* 게시글 통계 */}
        <div>
          <p className='statusP'>게시글 통계</p>
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={chartData} margin={{ bottom: 50 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="name" />
              <YAxis />
              <Tooltip />
              <Legend />
              <Line type="monotone" dataKey="total" stroke="#e53935" strokeWidth={3}  dot={{ r: 5 }} name='총 게시글 수' />
              <Line type="monotone" dataKey="project" stroke="#1e88e5" strokeWidth={3}  dot={{ r: 5 }} name='프로젝트 수' />
              <Line type="monotone" dataKey="study" stroke="#43a047" strokeWidth={3}  dot={{ r: 5 }} name='스터디 수' />
            </LineChart>
          </ResponsiveContainer>
        </div>

        {/* 진행 상황 통계 */}
        <div>
          <p className='statusP'>진행 상황 통계</p>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={statusData} margin={{ bottom: 50 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis
                dataKey="name"
                tick={{ fill: '#ffffff', fontSize: 14, fontWeight: 600 }}
                tickFormatter={(value) =>
                  value === 'IN_PROGRESS' ? '진행중' :
                  value === 'END' ? '진행종료' :
                  value === 'SCHEDULED' ? '진행예정' :
                  value
                }
              />
              <YAxis />
              <Tooltip
                labelFormatter={(label) =>
                  label === 'IN_PROGRESS' ? '진행중' :
                  label === 'END' ? '진행종료' :
                  label === 'SCHEDULED' ? '진행예정' :
                  label
                }
              />
              <Legend
                payload={[
                  { value: '진행중', type: 'square', color: '#4caf50' },
                  { value: '진행종료', type: 'square', color: '#f44336' },
                  { value: '진행예정', type: 'square', color: '#2196f3' },
                ]}
              />
              <Bar dataKey="count" name="게시글 수" barSize={120}>
                {statusData.map((entry, index) => (
                  <Cell
                    key={`cell-${index}`}
                    fill={
                      entry.name === 'IN_PROGRESS' ? '#4caf50' :
                      entry.name === 'END' ? '#f44336' :
                      entry.name === 'SCHEDULED' ? '#2196f3' :
                      '#8884d8'
                    }
                  />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* 포지션 통계 */}
        <div>
          <p className='statusP'>포지션 통계</p>
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={positionData} margin={{ bottom: 50 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="name" />
              <YAxis />
              <Tooltip />
              <Legend />
              <Line type="monotone" dataKey="acceptCount" stroke="#ff9800" strokeWidth={3} dot={{ r: 5 }} name='매칭 수' />
              <Line type="monotone" dataKey="applyCount" stroke="#2e7d32" strokeWidth={3} dot={{ r: 5 }} name='전체 신청 수' />
              <Line type="monotone" dataKey="rejectCount" stroke="#f44336" strokeWidth={3} dot={{ r: 5 }} name='거절 수' />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>


  )
}
export default Dashboard

