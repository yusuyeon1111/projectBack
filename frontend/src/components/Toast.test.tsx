import { act, render, screen } from '@testing-library/react';
import { Toaster, toast } from './Toast';

test('toast로 띄운 문구를 보여 주고 잠시 뒤 닫는다', () => {
  jest.useFakeTimers();
  render(<Toaster />);

  act(() => { toast('로그인이 완료되었습니다!'); });
  expect(screen.getByText('로그인이 완료되었습니다!')).toBeInTheDocument();

  act(() => { jest.advanceTimersByTime(2500); }); // autoHideDuration
  act(() => { jest.advanceTimersByTime(1000); }); // 닫히는 애니메이션
  expect(screen.queryByText('로그인이 완료되었습니다!')).not.toBeInTheDocument();
  jest.useRealTimers();
});
