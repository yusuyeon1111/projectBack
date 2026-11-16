import { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { NotificationItem } from '../types';

const API = `${process.env.REACT_APP_API_URL}/api/notifications`;
const MAX_DELAY = 30000;

// 알림 목록·안 읽은 개수를 불러오고 SSE로 새 알림을 받는다. 로그인 상태에서만 호출한다.
export default function useNotifications() {
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const seen = useRef(new Set<number>());

  useEffect(() => {
    let es: EventSource | null = null;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let delay = 1000;
    let lastId: number | undefined;
    let stopped = false;

    const add = (n: NotificationItem) => {
      lastId = Math.max(lastId ?? 0, n.id);
      if (seen.current.has(n.id)) return;
      seen.current.add(n.id);
      setItems(prev => [n, ...prev]);
      if (!n.read) setUnreadCount(c => c + 1);
    };

    const retry = () => {
      timer = setTimeout(connect, delay);
      delay = Math.min(delay * 2, MAX_DELAY);
    };

    // 티켓은 1회용이라 EventSource 자동 재연결(같은 URL 재사용)은 실패한다.
    // 그래서 에러 시 직접 닫고, 새 티켓과 마지막으로 받은 id로 다시 붙어 놓친 알림을 재전송받는다.
    const connect = async () => {
      if (stopped || !localStorage.getItem('accessToken')) return;
      try {
        const { data } = await axios.post<{ ticket: string }>(`${API}/ticket`);
        if (stopped) return;
        const params = new URLSearchParams({ ticket: data.ticket });
        if (lastId !== undefined) params.set('lastEventId', String(lastId));
        es = new EventSource(`${API}/subscribe?${params}`);
        es.addEventListener('connected', () => { delay = 1000; });
        es.addEventListener('notification', e => add(JSON.parse((e as MessageEvent).data)));
        es.addEventListener('error', () => {
          es?.close();
          retry();
        });
      } catch (error) {
        console.error('알림 구독 실패', error);
        retry();
      }
    };

    // 목록을 먼저 받고, 그 최신 id부터 구독해야 사이에 생긴 알림을 놓치지 않는다
    Promise.all([
      axios.get<NotificationItem[]>(API),
      axios.get<{ count: number }>(`${API}/unread-count`),
    ]).then(([list, count]) => {
      if (stopped) return;
      list.data.forEach(n => seen.current.add(n.id));
      // 목록이 비어 있어도 0을 기준으로 삼아야 조회와 구독 사이에 생긴 알림을 재전송받는다
      lastId = list.data[0]?.id ?? 0;
      setItems(list.data);
      setUnreadCount(count.data.count);
    }).catch(error => console.error('알림 조회 실패', error))
      .finally(connect);

    return () => {
      stopped = true;
      clearTimeout(timer);
      es?.close();
    };
  }, []);

  const markRead = async (n: NotificationItem) => {
    if (n.read) return;
    await axios.patch(`${API}/${n.id}/read`);
    setItems(prev => prev.map(p => (p.id === n.id ? { ...p, read: true } : p)));
    setUnreadCount(c => Math.max(0, c - 1));
  };

  const markAllRead = async () => {
    await axios.patch(`${API}/read-all`);
    setItems(prev => prev.map(p => ({ ...p, read: true })));
    setUnreadCount(0);
  };

  return { items, unreadCount, markRead, markAllRead };
}
