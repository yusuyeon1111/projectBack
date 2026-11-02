const { createProxyMiddleware } = require('http-proxy-middleware');

// 개발 서버(npm start) 전용. REACT_APP_API_URL을 빈 값으로 두면 /api 요청이 여기로 온다.
// 백엔드가 같은 저장소에 있으므로 기본값은 로컬 백엔드, 원격 서버를 쓰려면 PROXY_TARGET 지정.
module.exports = function (app) {
  app.use(
    '/api',
    createProxyMiddleware({
      target: `${process.env.PROXY_TARGET || 'http://localhost:8080'}/api`,
      changeOrigin: true,
      on: {
        // 브라우저의 Origin(localhost:3000)이 그대로 넘어가면 백엔드 CORS 설정이 403으로 막는다.
        // 프록시 뒤에서는 같은 출처 요청이므로 Origin을 지운다.
        proxyReq: (proxyReq) => proxyReq.removeHeader('origin'),
      },
    })
  );
};
