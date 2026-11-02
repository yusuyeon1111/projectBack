import React, { useEffect, useRef } from "react";
import useLocation from "../hooks/useLocation";
import { MdGpsFixed } from "react-icons/md";
import "../css/Map.css"; // CSS 파일 불러오기
import homePin from '../assets/homePin.png'
function KakaoMap() {
  const mapRef = useRef(null);
  const markerRef = useRef(null);
  const location = useLocation();

  useEffect(() => {
    if (location && window.kakao) {
      const { kakao } = window;

      const container = document.getElementById("map");
      const options = {
        center: new kakao.maps.LatLng(location.latitude, location.longitude),
        level: 4,
      };
      const map = new kakao.maps.Map(container, options);
      mapRef.current = map;

      const markerPosition = new kakao.maps.LatLng(location.latitude, location.longitude);
      const markerImage = new kakao.maps.MarkerImage(
        homePin,
        new kakao.maps.Size(30, 30),
        {offset: new kakao.maps.Point(20, 40)}
      )
      const marker = new kakao.maps.Marker({
        position: markerPosition,
        image:markerImage
        });
      marker.setMap(map);
      markerRef.current = marker;

      map.setCenter(markerPosition);
    }
  }, [location]);

  return (
    <div className="kakaomap-wrapper">
      <div className="kakaomap-container">
        <button
          className="gps-button"
          onClick={() => {
            if (mapRef.current && location) {
              const { kakao } = window;
              const newCenter = new kakao.maps.LatLng(location.latitude, location.longitude);
              mapRef.current.setCenter(newCenter);
            }
          }}
        >
          <MdGpsFixed size={24} color="black" />
        </button>

        <div id="map" style={{ width: "100%", height: "100%" }} />
      </div>
    </div>
  );
}

export default KakaoMap;
