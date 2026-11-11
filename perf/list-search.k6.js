// 모집글 목록 검색(POST /api/post/list) 성능 측정
//
// 지연 시간: 시나리오별로 1명이 순서대로 호출 (다른 요청과 경합 없는 단건 응답 시간)
//   k6 run perf/list-search.k6.js
// 부하: 여러 명이 시나리오를 섞어 동시에 호출
//   k6 run -e MODE=load -e VUS=20 -e DURATION=60s perf/list-search.k6.js
//
// 요청 본문은 프론트(Main.tsx)가 실제로 보내는 형태를 그대로 쓴다 (빈 필터는 "", 페이지 크기 5).
import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const MODE = __ENV.MODE || 'latency';
const WARMUP = Number(__ENV.WARMUP || 2);
const RUNS = Number(__ENV.RUNS || 10);

const DEFAULT_FILTER = {
  category: '', status: '', postStatus: '', region: '', position: '', keyword: '',
  page: 0, size: 5, sortType: 'latest', projectType: '', stackKeyword: '',
};

export const SCENARIOS = {
  S1_latest: {},                                          // 첫 화면: 필터 없음, 최신순
  S2_popular: { sortType: 'popular' },                    // 인기순
  S3_category_status: { category: 'project', status: 'ING' },
  S4_region_position: { region: '서울특별시', position: 'backend' },
  S5_keyword_hit: { keyword: 'React' },                   // 결과가 많은 키워드
  S6_keyword_miss: { keyword: '존재하지않는검색어' },       // 결과가 없는 키워드 (전체를 훑는 최악 경우)
  S7_stack: { stackKeyword: 'Redis' },
  S8_deep_page: { page: 10000 },                          // 뒤쪽 페이지
};
// ONLY=S5_keyword_hit,S6_keyword_miss 처럼 일부 시나리오만 돌릴 수 있다
const NAMES = __ENV.ONLY ? __ENV.ONLY.split(',') : Object.keys(SCENARIOS);

const responseBytes = new Trend('response_bytes');

// 시나리오별 통계가 요약에 나오도록 태그마다 threshold를 건다 (값 자체는 판정에 쓰지 않음)
const thresholds = {};
for (const name of NAMES) {
  thresholds[`http_req_duration{scenario_name:${name},phase:measure}`] = ['max>=0'];
  thresholds[`response_bytes{scenario_name:${name},phase:measure}`] = ['max>=0'];
}

const summaryTrendStats = ['avg', 'med', 'p(95)', 'max'];
export const options = MODE === 'load'
  ? { vus: Number(__ENV.VUS || 20), duration: __ENV.DURATION || '60s', thresholds, summaryTrendStats }
  // 기본 maxDuration(10분)을 넘기면 남은 요청을 버리므로 넉넉히 둔다
  : { scenarios: { latency: { executor: 'shared-iterations', vus: 1, iterations: NAMES.length * (WARMUP + RUNS), maxDuration: '2h' } },
      thresholds, summaryTrendStats };

export default function () {
  let name;
  let phase = 'measure';
  if (MODE === 'load') {
    name = NAMES[Math.floor(Math.random() * NAMES.length)];
  } else {
    const perScenario = WARMUP + RUNS;
    name = NAMES[Math.floor(__ITER / perScenario)];
    if (__ITER % perScenario < WARMUP) phase = 'warmup';
  }

  const tags = { scenario_name: name, phase };
  const res = http.post(`${BASE_URL}/api/post/list`, JSON.stringify({ ...DEFAULT_FILTER, ...SCENARIOS[name] }), {
    headers: { 'Content-Type': 'application/json' },
    tags,
    timeout: '120s',
    responseType: 'binary', // 응답 크기를 글자 수가 아닌 바이트로 재기 위해
  });
  check(res, { 'status 200': (r) => r.status === 200 }, tags);
  responseBytes.add(res.body ? res.body.byteLength : 0, tags);
}

// 결과를 시나리오별 표로 출력하고 JSON으로도 남긴다
export function handleSummary(data) {
  const pick = (metric, name) => data.metrics[`${metric}{scenario_name:${name},phase:measure}`]?.values;
  const rows = NAMES.map((name) => {
    const d = pick('http_req_duration', name);
    const b = pick('response_bytes', name);
    return d ? { scenario: name, avg: d.avg, med: d.med, p95: d['p(95)'], max: d.max, bytes: b?.avg } : null;
  }).filter(Boolean);

  const ms = (v) => `${v.toFixed(0)}ms`.padStart(9);
  let text = `\nMODE=${MODE}  requests=${data.metrics.http_reqs.values.count}  rps=${data.metrics.http_reqs.values.rate.toFixed(1)}  failed=${(data.metrics.http_req_failed.values.rate * 100).toFixed(1)}%\n`;
  text += 'scenario                  avg      med      p95      max   resp(bytes)\n';
  for (const r of rows) {
    text += `${r.scenario.padEnd(22)}${ms(r.avg)}${ms(r.med)}${ms(r.p95)}${ms(r.max)}${String(Math.round(r.bytes)).padStart(13)}\n`;
  }
  const out = __ENV.OUT || `perf/results/${MODE}-${new Date().toISOString().replace(/[:.]/g, '-')}.json`;
  return { stdout: text, [out]: JSON.stringify({ mode: MODE, rows, rps: data.metrics.http_reqs.values.rate }, null, 2) };
}
