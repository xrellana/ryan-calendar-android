'use strict';

const crypto = require('crypto');

const SESSION_COOKIE_NAME = 'workcal_sid';

function getSessionTtlDays() {
  const n = parseInt(process.env.SESSION_TTL_DAYS || '30', 10);
  return Number.isFinite(n) && n >= 1 && n <= 365 ? n : 30;
}

function generateId(prefix) {
  return `${prefix}-${Date.now().toString(36)}-${crypto.randomBytes(5).toString('hex')}`;
}

function generateToken(bytes = 32) {
  return crypto.randomBytes(bytes).toString('hex');
}

function hashToken(token) {
  return crypto.createHash('sha256').update(String(token)).digest('hex');
}

function hashPassword(password) {
  const salt = crypto.randomBytes(16).toString('hex');
  const derived = crypto.scryptSync(String(password), salt, 64).toString('hex');
  return `scrypt$${salt}$${derived}`;
}

function verifyPassword(password, storedHash) {
  if (!storedHash || typeof storedHash !== 'string') return false;
  const parts = storedHash.split('$');
  if (parts.length !== 3 || parts[0] !== 'scrypt') return false;
  const [, salt, expectedHex] = parts;
  try {
    const actual = crypto.scryptSync(String(password), salt, 64);
    const expected = Buffer.from(expectedHex, 'hex');
    if (actual.length !== expected.length) return false;
    return crypto.timingSafeEqual(actual, expected);
  } catch {
    return false;
  }
}

function parseCookies(cookieHeader) {
  const out = {};
  if (!cookieHeader || typeof cookieHeader !== 'string') return out;
  const pairs = cookieHeader.split(';');
  for (const pair of pairs) {
    const idx = pair.indexOf('=');
    if (idx <= 0) continue;
    const key = pair.slice(0, idx).trim();
    const val = pair.slice(idx + 1).trim();
    if (!key) continue;
    try {
      out[key] = decodeURIComponent(val);
    } catch {
      out[key] = val;
    }
  }
  return out;
}

function shouldUseSecureCookie(req) {
  const mode = String(process.env.COOKIE_SECURE || 'auto').toLowerCase();
  if (mode === 'true' || mode === '1') return true;
  if (mode === 'false' || mode === '0') return false;
  if (req.secure) return true;
  const xfProto = String(req.get('x-forwarded-proto') || '').split(',')[0].trim().toLowerCase();
  if (xfProto === 'https') return true;
  const cfVisitor = req.get('cf-visitor');
  if (cfVisitor && cfVisitor.includes('"https"')) return true;
  return false;
}

function setSessionCookie(req, res, rawToken) {
  const ttlSec = getSessionTtlDays() * 86400;
  const parts = [
    `${SESSION_COOKIE_NAME}=${encodeURIComponent(rawToken)}`,
    'Path=/',
    'HttpOnly',
    'SameSite=Lax',
    `Max-Age=${ttlSec}`
  ];
  if (shouldUseSecureCookie(req)) {
    parts.push('Secure');
  }
  res.setHeader('Set-Cookie', parts.join('; '));
}

function clearSessionCookie(req, res) {
  const parts = [
    `${SESSION_COOKIE_NAME}=`,
    'Path=/',
    'HttpOnly',
    'SameSite=Lax',
    'Max-Age=0'
  ];
  if (shouldUseSecureCookie(req)) {
    parts.push('Secure');
  }
  res.setHeader('Set-Cookie', parts.join('; '));
}

function getClientIp(req) {
  const trustProxy = String(process.env.TRUST_PROXY || 'true').toLowerCase() !== 'false';
  if (trustProxy) {
    const cfIp = req.get('cf-connecting-ip');
    if (cfIp) return cfIp.trim();
    const xff = req.get('x-forwarded-for');
    if (xff) return xff.split(',')[0].trim();
  }
  return req.ip || (req.socket && req.socket.remoteAddress) || 'unknown';
}

/**
 * 内存滑动窗口登录限流（支持按 IP 与账号分别统计）
 */
function createLoginRateLimiter({ windowMs = 15 * 60 * 1000, maxAttempts = 10 } = {}) {
  const buckets = new Map();

  function prune(now) {
    for (const [key, list] of buckets.entries()) {
      const kept = list.filter((ts) => now - ts < windowMs);
      if (kept.length) buckets.set(key, kept);
      else buckets.delete(key);
    }
  }

  function isLimited(keys) {
    const now = Date.now();
    for (const key of keys) {
      const list = buckets.get(key);
      if (!list) continue;
      const recent = list.filter((ts) => now - ts < windowMs);
      if (recent.length !== list.length) {
        if (recent.length) buckets.set(key, recent);
        else buckets.delete(key);
      }
      if (recent.length >= maxAttempts) {
        const oldest = recent[0];
        const retryAfterSec = Math.max(1, Math.ceil((windowMs - (now - oldest)) / 1000));
        return { limited: true, retryAfterSec };
      }
    }
    return { limited: false, retryAfterSec: 0 };
  }

  function recordFailure(keys) {
    const now = Date.now();
    if (buckets.size > 5000) prune(now);
    for (const key of keys) {
      const list = buckets.get(key) || [];
      list.push(now);
      buckets.set(key, list);
    }
  }

  function clear(keys) {
    for (const key of keys) {
      buckets.delete(key);
    }
  }

  return { isLimited, recordFailure, clear };
}

function createSession(db, userId, req) {
  const rawToken = generateToken(32);
  const tokenHash = hashToken(rawToken);
  const now = new Date();
  const expiresAt = new Date(now.getTime() + getSessionTtlDays() * 86400 * 1000);
  const ua = String(req.get('user-agent') || '').slice(0, 240);
  const ip = getClientIp(req).slice(0, 80);

  db.prepare(
    `INSERT INTO sessions (token_hash, user_id, expires_at, created_at, last_seen_at, user_agent, ip)
     VALUES (?, ?, ?, ?, ?, ?, ?)`
  ).run(tokenHash, userId, expiresAt.toISOString(), now.toISOString(), now.toISOString(), ua, ip);

  return { rawToken, expiresAt: expiresAt.toISOString() };
}

function destroySessionByToken(db, rawToken) {
  if (!rawToken) return;
  db.prepare('DELETE FROM sessions WHERE token_hash = ?').run(hashToken(rawToken));
}

function destroyAllUserSessions(db, userId) {
  db.prepare('DELETE FROM sessions WHERE user_id = ?').run(userId);
}

function cleanupExpiredSessions(db) {
  db.prepare('DELETE FROM sessions WHERE expires_at <= ?').run(new Date().toISOString());
}

function sanitizeUser(row) {
  if (!row) return null;
  return {
    id: row.id,
    username: row.username,
    displayName: row.display_name,
    role: row.role,
    isActive: Boolean(row.is_active),
    icsToken: row.ics_token,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    lastLoginAt: row.last_login_at || null
  };
}

function createAuthMiddleware(db) {
  function attachSession(req, res, next) {
    const cookies = parseCookies(req.headers.cookie);
    const rawToken = cookies[SESSION_COOKIE_NAME];
    if (!rawToken) {
      req.user = null;
      req.sessionToken = null;
      return next();
    }

    const tokenHash = hashToken(rawToken);
    const row = db
      .prepare(
        `SELECT s.token_hash, s.expires_at, s.last_seen_at,
                u.id, u.username, u.display_name, u.role, u.is_active, u.ics_token, u.created_at, u.updated_at, u.last_login_at
         FROM sessions s
         JOIN users u ON u.id = s.user_id
         WHERE s.token_hash = ?`
      )
      .get(tokenHash);

    const nowIso = new Date().toISOString();
    if (!row || row.expires_at <= nowIso || !row.is_active) {
      if (row) {
        db.prepare('DELETE FROM sessions WHERE token_hash = ?').run(tokenHash);
      }
      clearSessionCookie(req, res);
      req.user = null;
      req.sessionToken = null;
      return next();
    }

    // 滑动续期（距上次记录超过 6 小时刷新 last_seen_at 与过期时间）
    const lastSeenMs = Date.parse(row.last_seen_at || '');
    if (!Number.isFinite(lastSeenMs) || Date.now() - lastSeenMs > 6 * 3600 * 1000) {
      const nextExp = new Date(Date.now() + getSessionTtlDays() * 86400 * 1000).toISOString();
      db.prepare('UPDATE sessions SET last_seen_at = ?, expires_at = ? WHERE token_hash = ?').run(
        nowIso,
        nextExp,
        tokenHash
      );
      setSessionCookie(req, res, rawToken);
    }

    req.user = sanitizeUser(row);
    req.sessionToken = rawToken;
    next();
  }

  function requireAuth(req, res, next) {
    if (!req.user) {
      return res.status(401).json({ error: '登录已失效或未登录，请重新登录', code: 'UNAUTHORIZED' });
    }
    next();
  }

  function requireAdmin(req, res, next) {
    if (!req.user) {
      return res.status(401).json({ error: '未登录', code: 'UNAUTHORIZED' });
    }
    if (req.user.role !== 'admin') {
      return res.status(403).json({ error: '仅管理员可执行此操作', code: 'FORBIDDEN' });
    }
    next();
  }

  function requireCsrfHeader(req, res, next) {
    if (req.method === 'GET' || req.method === 'HEAD' || req.method === 'OPTIONS') {
      return next();
    }
    const hdr = req.get('x-requested-with');
    if (hdr !== 'WorkCal') {
      return res.status(403).json({ error: '无效的请求来源', code: 'CSRF_REJECTED' });
    }
    next();
  }

  return {
    attachSession,
    requireAuth,
    requireAdmin,
    requireCsrfHeader
  };
}

module.exports = {
  SESSION_COOKIE_NAME,
  generateId,
  generateToken,
  hashToken,
  hashPassword,
  verifyPassword,
  parseCookies,
  setSessionCookie,
  clearSessionCookie,
  getClientIp,
  createLoginRateLimiter,
  createSession,
  destroySessionByToken,
  destroyAllUserSessions,
  cleanupExpiredSessions,
  sanitizeUser,
  createAuthMiddleware
};
