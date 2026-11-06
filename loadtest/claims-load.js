// k6 load test: staff browsing the queue + customers checking their claims, through nginx.
// Run: docker run --rm -i --network host grafana/k6 run -e BASE=http://localhost - < loadtest/claims-load.js
import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE = __ENV.BASE || 'http://localhost'

export const options = {
  scenarios: {
    staff: { executor: 'constant-vus', vus: 15, duration: '45s', exec: 'staff' },
    customers: { executor: 'constant-vus', vus: 15, duration: '45s', exec: 'customer' },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{kind:list}': ['p(95)<500'],
    'http_req_duration{kind:detail}': ['p(95)<400'],
    'http_req_duration{kind:analytics}': ['p(95)<300'],
  },
}

function login(email) {
  const res = http.post(`${BASE}/api/auth/login`, JSON.stringify({ email, password: 'Demo@1234' }), {
    headers: { 'Content-Type': 'application/json' },
  })
  return res.json('token')
}

export function setup() {
  return { manager: login('manager@claimflow.dev'), customer: login('customer@claimflow.dev') }
}

const h = (token) => ({ headers: { Authorization: `Bearer ${token}` } })

export function staff(data) {
  const list = http.get(`${BASE}/api/claims?size=15&sort=slaDueAt&dir=asc`, { ...h(data.manager), tags: { kind: 'list' } })
  check(list, { 'queue 200': (r) => r.status === 200 })
  const ids = list.json('content.#.id') || []
  if (ids.length) {
    const id = ids[Math.floor(Math.random() * ids.length)]
    check(http.get(`${BASE}/api/claims/${id}`, { ...h(data.manager), tags: { kind: 'detail' } }), { 'detail 200': (r) => r.status === 200 })
  }
  check(http.get(`${BASE}/api/analytics/summary`, { ...h(data.manager), tags: { kind: 'analytics' } }), { 'analytics 200': (r) => r.status === 200 })
  sleep(0.5)
}

export function customer(data) {
  const list = http.get(`${BASE}/api/claims?size=10`, { ...h(data.customer), tags: { kind: 'list' } })
  check(list, { 'my claims 200': (r) => r.status === 200 })
  check(http.get(`${BASE}/api/notifications`, h(data.customer)), { 'notifications 200': (r) => r.status === 200 })
  sleep(0.5)
}
