import React from 'react';
import { useNavigate } from 'react-router-dom';
import { FaBell } from 'react-icons/fa6';
import { errorMessage } from '../auth';
import useNotifications from '../hooks/useNotifications';
import { NotificationItem, notificationLink, notificationMessage } from '../types';

interface Props {
  open: boolean;
  onToggle: () => void;
}

// 열림 상태는 Header가 들고 있어 다른 헤더 메뉴와 동시에 열리지 않는다
function NotificationBell({ open, onToggle }: Props) {
  const navigate = useNavigate();
  const { items, unreadCount, markRead, markAllRead } = useNotifications();

  const openItem = async (n: NotificationItem) => {
    try {
      await markRead(n);
    } catch (error) {
      console.error('알림 읽음 처리 실패', error);
    }
    navigate(notificationLink(n));
  };

  const readAll = async () => {
    try {
      await markAllRead();
    } catch (error) {
      alert(errorMessage(error, '알림 읽음 처리 중 오류가 발생했습니다.'));
    }
  };

  return (
    <>
      <button type='button' className='notification-button' aria-label='알림' onClick={onToggle}>
        <FaBell className='memberIcon'/>
        {unreadCount > 0 && <span className='notification-badge'>{unreadCount}</span>}
      </button>
      {open && (
        <div className='menu-container notification-menu'>
          <div className='notification-header'>
            <span>알림</span>
            {unreadCount > 0 && <button type='button' onClick={readAll}>모두 읽음</button>}
          </div>
          {items.length === 0 ? (
            <p className='notification-empty'>알림이 없습니다.</p>
          ) : (
            <ul>
              {items.map(n => (
                <li key={n.id} className={n.read ? '' : 'unread'} onClick={() => openItem(n)}>
                  <span>{notificationMessage(n)}</span>
                  <time>{new Date(n.createdAt).toLocaleString()}</time>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </>
  );
}

export default NotificationBell;
