import React, { useEffect, useState } from "react";
import axios from "axios";

const ApplyMemberModal = ({ isOpen, onClose, postId }) => {
  const [memberData, setMemberData] = useState([]);

  useEffect(() => {
    if (isOpen && postId) {
      const fetchData = async () => {
        const response = await axios.get(`${process.env.REACT_APP_API_URL}/api/post/postMember/${postId}`);
        setMemberData(response.data);
      };
      fetchData();
    }
  }, [isOpen, postId]);

  if (!isOpen) return null; // 열리지 않을 땐 완전히 DOM에서 제거됨

  return (
    <div className="modal-overlay">
      <div className="modal-content">
        <button className="modal-close" onClick={onClose}>
          ✖
        </button>
        <h2>신청자 목록</h2>
        {memberData.length > 0 ? (
          memberData.map((m, idx) => <p key={idx}>{m.username}</p>)
        ) : (
          <p>데이터 없음</p>
        )}
      </div>
    </div>
  );
};

export default ApplyMemberModal;
