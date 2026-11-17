# 프론트 빌드 결과를 Caddy가 서빙하고, /api 등은 백엔드로 넘긴다
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json .npmrc ./
RUN npm ci
COPY . .
# 운영은 같은 도메인이라 API 주소는 빈 값. 지도 키는 빌드할 때 박힌다(JavaScript 키, 공개값)
ARG REACT_APP_KAKAO_API_KEY
ENV REACT_APP_API_URL="" REACT_APP_KAKAO_API_KEY=$REACT_APP_KAKAO_API_KEY CI=false GENERATE_SOURCEMAP=false
RUN npm run build

FROM caddy:2-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /src/build /srv
