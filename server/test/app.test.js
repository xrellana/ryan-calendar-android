'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { createApp } = require('../index');

function createClient(baseUrl) {
  let cookie = '';

  async function req(method, urlPath, body, opts = {}) {
    const headers = {
      Accept: 'application/json',
      ...(opts.omitCsrf ? {} : { 'X-Requested-With': 'WorkCal' }),
      ...(opts.headers || {})
    };
    if (cookie) headers.Cookie = cookie;
    let payload;
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
      payload = JSON.stringify(body);
    }

    const resp = await fetch(baseUrl + urlPath, {
      method,
      headers,
      body: payload,
      redirect: 'manual'
    });

    const setCookie = resp.headers.get('set-cookie');
    if (setCookie) {
      const m = setCookie.match(/workcal_sid=([^;]*)/);
      if (m) {
        cookie = m[1] ? `workcal_sid=${m[1]}` : '';
      }
    }

    const text = await resp.text();
    let json = null;
    try {
      json = text ? JSON.parse(text) : null;
    } catch {
      json = null;
    }
    return { status: resp.status, headers: resp.headers, text, json };
  }

  return {
    req,
    getCookie: () => cookie,
    clearCookie: () => {
      cookie = '';
    }
  };
}

test('full end-to-end backend, auth, multi-tenant isolation, admin, holidays, ICS, and backups', async (t) => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'ryan-cal-test-'));
  const dbPath = path.join(tmpDir, 'calendar.db');
  const backupDir = path.join(tmpDir, 'backups');

  process.env.ADMIN_USERNAME = 'admin';
  process.env.ADMIN_PASSWORD = 'AdminPassword123!';
  process.env.COOKIE_SECURE = 'auto';

  const { app, db, bootstrapInfo } = createApp({ dbPath, backupDir });
  assert.ok(bootstrapInfo);
  assert.equal(bootstrapInfo.username, 'admin');

  const server = await new Promise((resolve) => {
    const s = app.listen(0, '127.0.0.1', () => resolve(s));
  });
  const baseUrl = `http://127.0.0.1:${server.address().port}`;

  t.after(async () => {
    await new Promise((resolve) => server.close(resolve));
    db.close();
    fs.rmSync(tmpDir, { recursive: true, force: true });
  });

  const admin = createClient(baseUrl);
  const alice = createClient(baseUrl);
  const bob = createClient(baseUrl);

  // 1. Healthcheck & static whitelist security
  const health = await admin.req('GET', '/healthz');
  assert.equal(health.status, 200);
  assert.equal(health.json.ok, true);

  const indexHtml = await admin.req('GET', '/');
  assert.equal(indexHtml.status, 200);
  assert.ok(indexHtml.text.includes('id="login-screen"'));

  const blockedServerFile = await admin.req('GET', '/server/index.js');
  assert.equal(blockedServerFile.status, 404);

  const blockedPkg = await admin.req('GET', '/package.json');
  assert.equal(blockedPkg.status, 404);

  // 2. Unauthenticated access rejected on API, CSRF enforced
  const unauthState = await admin.req('GET', '/api/state');
  assert.equal(unauthState.status, 401);
  assert.match(unauthState.headers.get('cache-control') || '', /no-store/);

  const csrfRejected = await admin.req(
    'POST',
    '/api/auth/login',
    { username: 'admin', password: 'AdminPassword123!' },
    { omitCsrf: true }
  );
  assert.equal(csrfRejected.status, 403);

  // 3. Admin login & /me
  const loginRes = await admin.req('POST', '/api/auth/login', {
    username: 'admin',
    password: 'AdminPassword123!'
  });
  assert.equal(loginRes.status, 200);
  assert.equal(loginRes.json.user.role, 'admin');
  assert.ok(admin.getCookie().startsWith('workcal_sid='));

  const meRes = await admin.req('GET', '/api/auth/me');
  assert.equal(meRes.status, 200);
  assert.equal(meRes.json.user.username, 'admin');

  // 4. Admin creates two regular users (alice and bob)
  const createAlice = await admin.req('POST', '/api/admin/users', {
    username: 'alice',
    displayName: '爱丽丝',
    password: 'AlicePassword123',
    role: 'user'
  });
  assert.equal(createAlice.status, 201);
  const aliceId = createAlice.json.user.id;

  const createBob = await admin.req('POST', '/api/admin/users', {
    username: 'bob',
    displayName: '鲍勃',
    password: 'BobPassword123',
    role: 'user'
  });
  assert.equal(createBob.status, 201);

  // 5. Regular users login and cannot access admin routes
  assert.equal(
    (await alice.req('POST', '/api/auth/login', { username: 'alice', password: 'AlicePassword123' })).status,
    200
  );
  assert.equal(
    (await bob.req('POST', '/api/auth/login', { username: 'bob', password: 'BobPassword123' })).status,
    200
  );
  assert.equal((await alice.req('GET', '/api/admin/users')).status, 403);

  // 6. Strict multi-tenant isolation on categories & events
  const aliceState0 = await alice.req('GET', '/api/state');
  assert.equal(aliceState0.status, 200);
  assert.equal(aliceState0.json.categories.length, 4);
  assert.equal(aliceState0.json.events.length, 0);

  const aliceEvt = await alice.req('POST', '/api/events', {
    title: '爱丽丝的私密出差',
    categoryId: 'cat-assessment',
    startDate: '2026-11-20',
    endDate: '2026-11-22',
    allDay: true,
    notes: '仅爱丽丝可见'
  });
  assert.equal(aliceEvt.status, 201);
  const aliceEvtId = aliceEvt.json.event.id;

  // Bob sees 0 events and cannot update or delete Alice's event
  const bobState = await bob.req('GET', '/api/state');
  assert.equal(bobState.json.events.length, 0);
  assert.equal((await bob.req('PATCH', `/api/events/${aliceEvtId}`, { title: 'hacked' })).status, 404);
  assert.equal((await bob.req('DELETE', `/api/events/${aliceEvtId}`)).status, 404);

  // 7. Category add, reorder, update, delete
  const newCat = await alice.req('POST', '/api/categories', { name: '内部评审', color: '#7c3aed' });
  assert.equal(newCat.status, 201);
  assert.equal(newCat.json.category.color, '#7C3AED');
  const newCatId = newCat.json.category.id;

  const reordered = await alice.req('PUT', '/api/categories/reorder', {
    orderedIds: [newCatId, 'cat-deadline', 'cat-assessment', 'cat-consulting', 'cat-rest']
  });
  assert.equal(reordered.status, 200);
  assert.equal(reordered.json.categories[0].id, newCatId);

  // 8. Ledger import deduplicates and never overwrites manual events
  const imp1 = await alice.req('POST', '/api/events/import-ledger', {
    entries: [
      {
        title: '项目A（预评估）',
        categoryId: 'cat-assessment',
        startDate: '2026-12-01',
        endDate: '2026-12-03',
        notes: '第一版',
        ledgerKey: 'proj-a|pre',
        ledgerMeta: { projectName: '项目A', stage: '预评估', level: '一级', role: '组长' }
      }
    ]
  });
  assert.equal(imp1.status, 200);
  assert.equal(imp1.json.added, 1);
  assert.equal(imp1.json.updated, 0);

  const imp2 = await alice.req('POST', '/api/events/import-ledger', {
    entries: [
      {
        title: '项目A（预评估-改期）',
        categoryId: 'cat-assessment',
        startDate: '2026-12-05',
        endDate: '2026-12-06',
        notes: '第二版',
        ledgerKey: 'proj-a|pre',
        ledgerMeta: { projectName: '项目A', stage: '预评估', level: '一级', role: '组长' }
      }
    ]
  });
  assert.equal(imp2.status, 200);
  assert.equal(imp2.json.added, 0);
  assert.equal(imp2.json.updated, 1);
  assert.equal(imp2.json.state.events.length, 2); // manual + 1 ledger

  // 9. Private ICS subscription & token reset
  const aliceMe = await alice.req('GET', '/api/auth/me');
  const oldIcsToken = aliceMe.json.user.icsToken;
  const icsRes = await fetch(`${baseUrl}/ics/${oldIcsToken}.ics`);
  assert.equal(icsRes.status, 200);
  assert.match(icsRes.headers.get('content-type') || '', /text\/calendar/);
  assert.match(icsRes.headers.get('cache-control') || '', /no-store/);
  const icsText = await icsRes.text();
  assert.ok(icsText.includes('BEGIN:VCALENDAR'));
  assert.ok(icsText.includes('爱丽丝的私密出差'));

  const resetIcs = await alice.req('POST', '/api/auth/ics-token/reset', {});
  assert.equal(resetIcs.status, 200);
  const newIcsToken = resetIcs.json.icsToken;
  assert.notEqual(newIcsToken, oldIcsToken);
  assert.equal((await fetch(`${baseUrl}/ics/${oldIcsToken}.ics`)).status, 404);
  assert.equal((await fetch(`${baseUrl}/ics/${newIcsToken}.ics`)).status, 200);

  // 10. Admin holiday manual add/delete & DB backup snapshot
  const addHol = await admin.req('PUT', '/api/admin/holidays/2028-01-01', {
    type: 'holiday',
    name: '2028元旦测试'
  });
  assert.equal(addHol.status, 200);
  assert.ok(addHol.json.holidays.years.includes(2028));
  assert.ok(addHol.json.holidays.entries.some((e) => e.date === '2028-01-01' && e.name === '2028元旦测试'));

  const delHol = await admin.req('DELETE', '/api/admin/holidays/2028-01-01');
  assert.equal(delHol.status, 200);
  assert.ok(!delHol.json.holidays.entries.some((e) => e.date === '2028-01-01'));

  const snapRes = await admin.req('POST', '/api/admin/backups', {});
  assert.equal(snapRes.status, 201);
  assert.ok(snapRes.json.backup.size > 0);

  const dlRes = await admin.req('GET', '/api/admin/backups/download');
  assert.equal(dlRes.status, 200);

  // 11. Admin disables Alice -> Alice immediately logged out & ICS disabled
  const disableAlice = await admin.req('PATCH', `/api/admin/users/${aliceId}`, { isActive: false });
  assert.equal(disableAlice.status, 200);
  assert.equal((await alice.req('GET', '/api/state')).status, 401);
  assert.equal((await fetch(`${baseUrl}/ics/${newIcsToken}.ics`)).status, 404);

  // 12. Admin resets Alice password & re-enables -> Alice logs in with new password
  await admin.req('PATCH', `/api/admin/users/${aliceId}`, { isActive: true });
  const resetPwd = await admin.req('POST', `/api/admin/users/${aliceId}/reset-password`, {});
  assert.equal(resetPwd.status, 200);
  const newPwd = resetPwd.json.password;
  assert.ok(newPwd.length >= 6);

  assert.equal(
    (await alice.req('POST', '/api/auth/login', { username: 'alice', password: 'AlicePassword123' })).status,
    401
  );
  assert.equal(
    (await alice.req('POST', '/api/auth/login', { username: 'alice', password: newPwd })).status,
    200
  );

  // 13. JSON backup restore (merge & replace)
  const restoreMerge = await bob.req('POST', '/api/backup/restore', {
    mode: 'merge',
    categories: [{ id: 'cat-custom', name: '自定义', color: '#112233' }],
    events: [
      {
        id: 'evt-migrated-1',
        title: '旧版迁移日程',
        categoryId: 'cat-custom',
        startDate: '2026-11-18',
        allDay: true
      }
    ]
  });
  assert.equal(restoreMerge.status, 200);
  assert.equal(restoreMerge.json.events, 1);
  assert.equal(restoreMerge.json.state.events.length, 1);
});

test('Cloudflare IP rate limiting, admin self-protection, password change session revocation, and Holidays.loadFromServer', async (t) => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'ryan-cal-sec-'));
  const dbPath = path.join(tmpDir, 'calendar.db');
  const backupDir = path.join(tmpDir, 'backups');

  process.env.ADMIN_USERNAME = 'rootadmin';
  process.env.ADMIN_PASSWORD = 'RootPassword123!';

  const { app, db } = createApp({ dbPath, backupDir });
  const server = await new Promise((resolve) => {
    const s = app.listen(0, '127.0.0.1', () => resolve(s));
  });
  const baseUrl = `http://127.0.0.1:${server.address().port}`;

  t.after(async () => {
    await new Promise((resolve) => server.close(resolve));
    db.close();
    fs.rmSync(tmpDir, { recursive: true, force: true });
  });

  // 1. CF-Connecting-IP rate limiting isolates attacker IP from legitimate IP
  const attacker = createClient(baseUrl);
  for (let i = 0; i < 10; i++) {
    const r = await attacker.req(
      'POST',
      '/api/auth/login',
      { username: 'unknown_user_' + i, password: 'wrong' },
      { headers: { 'CF-Connecting-IP': '198.51.100.77' } }
    );
    assert.equal(r.status, 401);
  }
  const blocked = await attacker.req(
    'POST',
    '/api/auth/login',
    { username: 'rootadmin', password: 'RootPassword123!' },
    { headers: { 'CF-Connecting-IP': '198.51.100.77' } }
  );
  assert.equal(blocked.status, 429);

  // Legitimate user from another CF IP can still login
  const dev1 = createClient(baseUrl);
  const dev2 = createClient(baseUrl);
  const ok1 = await dev1.req(
    'POST',
    '/api/auth/login',
    { username: 'rootadmin', password: 'RootPassword123!' },
    { headers: { 'CF-Connecting-IP': '203.0.113.10' } }
  );
  assert.equal(ok1.status, 200);
  const adminId = ok1.json.user.id;

  const ok2 = await dev2.req(
    'POST',
    '/api/auth/login',
    { username: 'rootadmin', password: 'RootPassword123!' },
    { headers: { 'CF-Connecting-IP': '203.0.113.11' } }
  );
  assert.equal(ok2.status, 200);

  // 2. Admin cannot disable or delete self
  assert.equal((await dev1.req('PATCH', `/api/admin/users/${adminId}`, { isActive: false })).status, 400);
  assert.equal((await dev1.req('DELETE', `/api/admin/users/${adminId}`)).status, 400);

  // 3. Changing password revokes other sessions (dev2) while keeping dev1 alive
  const chPwd = await dev1.req('PUT', '/api/auth/password', {
    currentPassword: 'RootPassword123!',
    newPassword: 'NewRootPassword456!'
  });
  assert.equal(chPwd.status, 200);
  assert.equal((await dev1.req('GET', '/api/state')).status, 200);
  assert.equal((await dev2.req('GET', '/api/state')).status, 401);

  // 4. Frontend Holidays.loadFromServer updates index & coveredRange
  const Holidays = require('../../js/holidays');
  Holidays.loadFromServer({
    years: [2028],
    entries: [
      { date: '2028-01-01', year: 2028, type: 'holiday', name: '元旦' },
      { date: '2028-01-22', year: 2028, type: 'workday', name: '春节调休上班' }
    ]
  });
  assert.equal(Holidays.isYearCovered(2028), true);
  assert.equal(Holidays.getHoliday('2028-01-01'), '元旦');
  assert.equal(Holidays.getMakeupWorkday('2028-01-22'), '春节调休上班');
  assert.equal(Holidays.coveredRange.maxYear >= 2028, true);
});
