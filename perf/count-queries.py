"""시나리오마다 목록 API를 한 번씩 호출해 요청당 SQL 개수와 응답 크기를 잰다.

앱은 Hibernate 통계 로그를 켜고(--spring.jpa.properties.hibernate.generate_statistics=true)
표준 출력을 파일로 남겨 둔 상태여야 한다. 요청 직후 새로 찍힌 'Session Metrics'를 읽어 센다.

    python3 perf/count-queries.py <앱 로그 파일> > perf/results/<이름>-queries.tsv

시나리오는 list-search.k6.js와 같다.
"""
import json
import re
import sys
import time
import urllib.request

BASE_URL = 'http://localhost:8081'
DEFAULT_FILTER = {"category": "", "status": "", "postStatus": "", "region": "", "position": "", "keyword": "",
                  "page": 0, "size": 5, "sortType": "latest", "projectType": "", "stackKeyword": ""}
SCENARIOS = {
    "S1_latest": {},
    "S2_popular": {"sortType": "popular"},
    "S3_category_status": {"category": "project", "status": "ING"},
    "S4_region_position": {"region": "서울특별시", "position": "backend"},
    "S5_keyword_hit": {"keyword": "React"},
    "S6_keyword_miss": {"keyword": "존재하지않는검색어"},
    "S7_stack": {"stackKeyword": "Redis"},
    "S8_deep_page": {"page": 10000},
}
STATEMENTS = re.compile(r'(\d+) nanoseconds spent executing (\d+) JDBC statements')


def main(log_path):
    print("scenario\tstatements\tsql_ms\tresp_ms\tresp_bytes\ttotal_elements")
    for name, override in SCENARIOS.items():
        before = open(log_path).read().count('Session Metrics')
        started = time.time()
        req = urllib.request.Request(f'{BASE_URL}/api/post/list',
                                     data=json.dumps({**DEFAULT_FILTER, **override}).encode(),
                                     headers={'Content-Type': 'application/json'})
        body = urllib.request.urlopen(req, timeout=300).read()
        elapsed_ms = (time.time() - started) * 1000
        time.sleep(0.5)  # 세션 종료 로그가 찍힐 시간
        sessions = open(log_path).read().split('Session Metrics')[before + 1:]
        hits = [STATEMENTS.search(s) for s in sessions]
        statements = sum(int(h.group(2)) for h in hits)
        sql_ms = sum(int(h.group(1)) for h in hits) / 1e6
        total = json.loads(body).get('totalElements')
        print(f"{name}\t{statements}\t{sql_ms:.0f}\t{elapsed_ms:.0f}\t{len(body)}\t{total}", flush=True)


if __name__ == '__main__':
    main(sys.argv[1])
