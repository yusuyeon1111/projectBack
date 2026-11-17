import { Avatar } from '@mui/material';

const colors = ['#007bff', '#16a34a', '#f59e0b', '#db2777', '#7c3aed', '#0891b2', '#ea580c'];

// 팀원 이니셜 아바타. 같은 사람은 어디서나 같은 색이 되도록 회원 id로 색을 고른다
export default function TeamAvatar({ memberId, name, size = 28 }: { memberId: number; name: string | null; size?: number }) {
  return (
    <Avatar sx={{ width: size, height: size, fontSize: size * 0.45, fontWeight: 700, bgcolor: colors[memberId % colors.length] }}>
      {(name ?? '?').slice(0, 1)}
    </Avatar>
  );
}
