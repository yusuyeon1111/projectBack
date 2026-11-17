import { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { Box, Button, Stack, TextField, Typography } from '@mui/material';
import { errorMessage } from '../auth';
import { ChatMessage, SSE_CONNECTED, TEAM_CHAT, TeamDetail } from '../types';

const PAGE = 50;
const CATCH_UP = 100;

// SSE로 받은 것과 조회로 받은 것이 겹칠 수 있어 id로 합치고 오름차순으로 둔다
const merge = (prev: ChatMessage[], incoming: ChatMessage[]) => {
  const byId = new Map(prev.map(m => [m.id, m]));
  incoming.forEach(m => byId.set(m.id, m));
  return Array.from(byId.values()).sort((a, b) => a.id - b.id);
};

// 팀 채팅. 실시간 메시지는 useNotifications가 넘겨 주고, 끊겼던 동안의 메시지는
// 방을 열 때 · SSE 재연결 때 · 탭이 다시 보일 때 마지막 id 이후를 조회해 메운다(설계 §6.3)
export default function TeamChat({ team, open }: { team: TeamDetail; open: boolean }) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [hasOlder, setHasOlder] = useState(false);
  const [content, setContent] = useState('');
  const lastId = useRef(0);
  const url = `${process.env.REACT_APP_API_URL}/api/teams/${team.teamId}/messages`;

  const add = (incoming: ChatMessage[]) => {
    if (incoming.length === 0) return;
    lastId.current = Math.max(lastId.current, ...incoming.map(m => m.id));
    setMessages(prev => merge(prev, incoming));
  };

  useEffect(() => {
    let stopped = false;
    lastId.current = 0;
    setMessages([]);

    // 응답이 꽉 차면 더 있을 수 있어 이어서 받는다
    const catchUp = async () => {
      for (;;) {
        const res = await axios.get<ChatMessage[]>(`${url}?after=${lastId.current}&limit=${CATCH_UP}`);
        if (stopped) return;
        add(res.data);
        if (res.data.length < CATCH_UP) return;
      }
    };

    const onChat = (e: Event) => {
      const m = (e as CustomEvent<ChatMessage>).detail;
      if (m.teamId === team.teamId) add([m]);
    };
    const onReconnect = () => { catchUp().catch(() => {}); };
    const onVisible = () => { if (document.visibilityState === 'visible') onReconnect(); };

    // 최신 대화부터 연다. 받은 마지막 id 이후는 위의 세 시점에 메운다
    axios.get<ChatMessage[]>(`${url}?limit=${PAGE}`)
      .then(res => {
        if (stopped) return;
        add(res.data);
        setHasOlder(res.data.length === PAGE);
      })
      .catch(e => alert(errorMessage(e, '채팅을 불러오지 못했습니다.')));

    window.addEventListener(TEAM_CHAT, onChat);
    window.addEventListener(SSE_CONNECTED, onReconnect);
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      stopped = true;
      window.removeEventListener(TEAM_CHAT, onChat);
      window.removeEventListener(SSE_CONNECTED, onReconnect);
      document.removeEventListener('visibilitychange', onVisible);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [team.teamId]);

  const loadOlder = async () => {
    const res = await axios.get<ChatMessage[]>(`${url}?before=${messages[0].id}&limit=${PAGE}`);
    setMessages(prev => merge(prev, res.data));
    setHasOlder(res.data.length === PAGE);
  };

  const send = async () => {
    if (!content.trim()) return;
    try {
      const res = await axios.post<ChatMessage>(url, { content });
      add([res.data]);
      setContent('');
    } catch (e) {
      alert(errorMessage(e, '메시지를 보내지 못했습니다.'));
    }
  };

  return (
    <Box sx={{ mt: 2 }}>
      <Typography variant="subtitle1">채팅</Typography>
      <Box sx={{ maxHeight: 240, overflowY: 'auto', border: 1, borderColor: 'divider', borderRadius: 1, p: 1 }}>
        {hasOlder && <Button size="small" onClick={() => loadOlder().catch(() => {})}>이전 대화</Button>}
        {messages.length === 0 && <Typography variant="body2" color="text.secondary">아직 대화가 없습니다.</Typography>}
        {messages.map(m => (
          <Typography key={m.id} variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
            <b>{m.senderName}</b> {m.content}{' '}
            <Typography component="span" variant="caption" color="text.secondary">{m.createdAt.slice(11, 16)}</Typography>
          </Typography>
        ))}
      </Box>
      {open && (
        <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
          <TextField size="small" label="메시지" value={content} onChange={e => setContent(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter' && !e.nativeEvent.isComposing) send(); }}
            inputProps={{ maxLength: 1000 }} sx={{ flex: 1 }} />
          <Button variant="contained" size="small" disabled={!content.trim()} onClick={send}>보내기</Button>
        </Stack>
      )}
    </Box>
  );
}
