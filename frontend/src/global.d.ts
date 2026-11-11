// ponytail: Kakao Maps SDK는 public/index.html의 <script>로 로드되는 전역이라 any 처리. 타입이 필요해지면 kakao.maps.d.ts 패키지 추가.
interface Window {
  kakao: any;
}
