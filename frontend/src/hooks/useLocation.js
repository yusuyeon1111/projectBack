import { useEffect, useState } from "react";

export default function useLocation() {
  const [location, setLocation] = useState(null);

  useEffect(() => {
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(success, error, {
        enableHighAccuracy: true, 
        timeout: 10000, 
        maximumAge: 0, 
      });
    } else {
      console.log("Geolocation을 지원하지 않는 브라우저입니다.");
    }

    function success(position) {
      const lat = position.coords.latitude;
      const lng = position.coords.longitude;

      console.log("위치 받기 성공:", lat, lng); 

      setLocation({ latitude: lat, longitude: lng });
    }

    function error() {
      console.log("위치 받기 실패, 기본 위치 설정됨");

      setLocation({
        latitude: 37.483034, // 기본값 (서울)
        longitude: 126.902345,
      });
    }
  }, []);

  return location;
}
