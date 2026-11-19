'use strict';

const express = require('express');
const {
  hashPassword,
  verifyPassword,
  generateToken,
  hashToken,
  setSessionCookie,
  clearSessionCookie,
  getClientIp,
  createLoginRateLimiter,
  createSession,
  destroySessionByToken,
  sanitizeUser
} = require('../auth');

function createAuthRouter(db, authMw) {
  const router = express.Router();
  const limiter = createLoginRateLimiter({ windowMs: 15 * 60 * 1000, maxAttempts: 10 });

  router.get('/me', (req, res) => {
    res.json({ user: req.user || null });
  });

  router.post('/login', (req, res) => {
    const username = String((req.body && req.body.username) || '').trim();
    const password = String((req.body && req.body.password) || '');

    if (!username || !password) {
      return res.status(400).json({ error: '请输入账号和密码' });
    }

    const ip = getClientIp(req);
    const limitKeys = [`ip:${ip}`, `user:${username.toLowerCase()}`];
    const check = limiter.isLimited(limitKeys);
    if (check.limited) {
      return res.status(429).json({
        error: `登录失败次数过多，请 ${Math.ceil(check.retryAfterSec / 60)} 分钟后再试`
      });
    }

    const row = db
      .prepare(
        `SELECT id, username, display_name, password_hash, role, is_active, ics_token,
                created_at, updated_at, last_login_at
         FROM users WHERE username = ? COLLATE NOCASE`
      )
      .get(username);

    if (!row || !verifyPassword(password, row.password_hash)) {
      limiter.recordFailure(limitKeys);
      return res.status(401).json({ error: '账号或密码错误' });
    }

    if (!row.is_active) {
      return res.status(403).json({ error: '该账号已被禁用，请联系管理员' });
    }

    limiter.clear(limitKeys);

    const now = new Date().toISOString();
    db.prepare('UPDATE users SET last_login_at = ? WHERE id = ?').run(now, row.id);
    row.last_login_at = now;

    const session = createSession(db, row.id, req);
    setSessionCookie(req, res, session.rawToken);

    res.json({ user: sanitizeUser(row) });
  });

  router.post('/logout', (req, res) => {
    if (req.sessionToken) {
      destroySessionByToken(db, req.sessionToken);
    }
    clearSessionCookie(req, res);
    res.json({ ok: true });
  });

  router.put('/profile', authMw.requireAuth, (req, res) => {
    const displayName = String((req.body && req.body.displayName) || '').trim().slice(0, 32);
    if (!displayName) {
      return res.status(400).json({ error: '显示名称不能为空' });
    }
    const now = new Date().toISOString();
    db.prepare('UPDATE users SET display_name = ?, updated_at = ? WHERE id = ?').run(
      displayName,
      now,
      req.user.id
    );
    const updated = db.prepare('SELECT * FROM users WHERE id = ?').get(req.user.id);
    res.json({ user: sanitizeUser(updated) });
  });

  router.put('/password', authMw.requireAuth, (req, res) => {
    const currentPassword = String((req.body && req.body.currentPassword) || '');
    const newPassword = String((req.body && req.body.newPassword) || '');

    if (!currentPassword || !newPassword) {
      return res.status(400).json({ error: '请填写当前密码和新密码' });
    }
    if (newPassword.length < 6 || newPassword.length > 100) {
      return res.status(400).json({ error: '新密码长度需在 6 到 100 位之间' });
    }

    const row = db.prepare('SELECT password_hash FROM users WHERE id = ?').get(req.user.id);
    if (!row || !verifyPassword(currentPassword, row.password_hash)) {
      return res.status(400).json({ error: '当前密码不正确' });
    }

    const now = new Date().toISOString();
    db.prepare('UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?').run(
      hashPassword(newPassword),
      now,
      req.user.id
    );

    // 踢掉其他设备的旧会话，保留当前设备登录态
    if (req.sessionToken) {
      db.prepare('DELETE FROM sessions WHERE user_id = ? AND token_hash != ?').run(
        req.user.id,
        hashToken(req.sessionToken)
      );
    }

    res.json({ ok: true });
  });

  router.post('/ics-token/reset', authMw.requireAuth, (req, res) => {
    const nextToken = generateToken(32);
    const now = new Date().toISOString();
    db.prepare('UPDATE users SET ics_token = ?, updated_at = ? WHERE id = ?').run(
      nextToken,
      now,
      req.user.id
    );
    res.json({ icsToken: nextToken });
  });

  return router;
}

module.exports = createAuthRouter;
