import { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { Box, Button, Divider, IconButton, InputBase, Stack, Typography } from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutline';
import TeamAvatar from './TeamAvatar';
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
  const listRef = useRef<HTMLDivElement>(null);
  const newest = messages.length ? messages[messages.length - 1].id : 0;
  useEffect(() => {
    const el = listRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [newest]);
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

  const mine = (m: ChatMessage) => m.senderMemberId === team.myMemberId;

  return (
    <>
      <Box ref={listRef} sx={{ flex: 1, minHeight: 0, overflowY: 'auto', px: 2.5, py: 2, bgcolor: '#fff' }}>
        {hasOlder && (
          <Box sx={{ textAlign: 'center', mb: 1 }}>
            <Button size="small" onClick={() => loadOlder().catch(() => {})}>이전 대화 보기</Button>
          </Box>
        )}
        {messages.length === 0 && (
          <Stack alignItems="center" justifyContent="center" spacing={1} sx={{ height: '100%', color: 'text.secondary' }}>
            <ChatBubbleOutlineIcon sx={{ fontSize: 40, opacity: 0.4 }} />
            <Typography variant="body2">아직 대화가 없습니다.</Typography>
            {open && <Typography variant="caption">첫 메시지로 인사를 건네 보세요.</Typography>}
          </Stack>
        )}
        {messages.map((m, i) => {
          const prev = messages[i - 1];
          const newDay = !prev || prev.createdAt.slice(0, 10) !== m.createdAt.slice(0, 10);
          // 같은 사람이 이어 보낸 메시지는 이름·얼굴을 한 번만 보인다
          const first = newDay || prev.senderMemberId !== m.senderMemberId;
          const me = mine(m);
          return (
            <Box key={m.id}>
              {newDay && <Divider sx={{ my: 1.5 }}><Typography variant="caption" color="text.secondary">{m.createdAt.slice(0, 10).replace(/-/g, '. ')}</Typography></Divider>}
              <Stack direction={me ? 'row-reverse' : 'row'} spacing={1} alignItems="flex-end" sx={{ mt: first ? 1.25 : 0.4 }}>
                {!me && <Box sx={{ width: 32, flexShrink: 0, alignSelf: 'flex-start' }}>{first && <TeamAvatar memberId={m.senderMemberId} name={m.senderName} size={32} />}</Box>}
                <Box sx={{ maxWidth: '70%' }}>
                  {!me && first && <Typography variant="caption" fontWeight={600} sx={{ display: 'block', mb: 0.25 }}>{m.senderName}</Typography>}
                  <Box sx={{ px: 1.5, py: 1, borderRadius: 2.5, whiteSpace: 'pre-wrap', wordBreak: 'break-word', fontSize: 14, lineHeight: 1.5,
                    ...(me ? { bgcolor: '#007bff', color: '#fff', borderBottomRightRadius: 6 } : { bgcolor: '#f1f3f5', borderTopLeftRadius: first ? 6 : undefined }) }}>
                    {m.content}
                  </Box>
                </Box>
                <Typography variant="caption" color="text.disabled" sx={{ flexShrink: 0, fontSize: 11 }}>{m.createdAt.slice(11, 16)}</Typography>
              </Stack>
            </Box>
          );
        })}
      </Box>
      {open && (
        <Stack direction="row" spacing={1} alignItems="center" sx={{ p: 1.5, borderTop: 1, borderColor: 'divider', bgcolor: '#F9FAFB' }}>
          <InputBase placeholder="메시지를 입력하세요" value={content} onChange={e => setContent(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter' && !e.nativeEvent.isComposing) send(); }}
            inputProps={{ maxLength: 1000, 'aria-label': '메시지' }}
            sx={{ flex: 1, bgcolor: '#fff', border: 1, borderColor: 'divider', borderRadius: 5, px: 2, py: 0.75, fontSize: 14 }} />
          <IconButton aria-label="보내기" disabled={!content.trim()} onClick={send}
            sx={{ bgcolor: '#007bff', color: '#fff', '&:hover': { bgcolor: '#0056b3' }, '&.Mui-disabled': { bgcolor: '#e5e7eb', color: '#fff' } }}>
            <SendIcon fontSize="small" />
          </IconButton>
        </Stack>
      )}
    </>
  );
}
