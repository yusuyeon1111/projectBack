import { useEffect, useState } from 'react';
import { Snackbar } from '@mui/material';

const TOAST = 'app-toast';

// 화면을 막지 않는 짧은 안내. Toaster가 라우트 바깥에 하나 있어서 띄운 뒤 페이지를 옮겨도 보인다
export const toast = (message: string) => window.dispatchEvent(new CustomEvent<string>(TOAST, { detail: message }));

export function Toaster() {
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    const show = (e: Event) => setMessage((e as CustomEvent<string>).detail);
    window.addEventListener(TOAST, show);
    return () => window.removeEventListener(TOAST, show);
  }, []);

  return (
    <Snackbar open={message !== null} message={message} autoHideDuration={2500} onClose={() => setMessage(null)}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }} />
  );
}
