import { useNavigate } from "react-router-dom";
import '../css/Modal.css'
const LoginRequiredModal = ({ isOpen, onClose }) => {
  const navigate = useNavigate();

  if (!isOpen) return null;

  return (
    <div className='requiremodal-container'>
      <div>
        <p>로그인이 필요한 기능입니다</p>
        <p>로그인을 하시겠습니까?</p>
        <div className="button-container">
        <button onClick={onClose} id="cancleBtn">닫기</button>
        <button onClick={() => navigate("/signin")} id="submitBtn">로그인</button>
        </div>
      </div>
    </div>
  )
}
 export default LoginRequiredModal;
