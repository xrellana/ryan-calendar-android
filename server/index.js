'use strict';

const fs = require('fs');
const path = require('path');
const express = require('express');
const { initDatabase, createBackupSnapshot, listBackups } = require('./db');
const { createAuthMiddleware, cleanupExpiredSessions } = require('./auth');
const { autoSyncUpcomingYearIfNeeded } = require('./holiday-sync');
const createAuthRouter = require('./routes/auth');
const createCalendarRouter = require('./routes/calendar');
const createAdminRouter = require('./routes/admin');
const createIcsRouter = require('./routes/ics');

function createApp(options = {}) {
  const rootDir = path.resolve(__dirname, '..');
  const { db, dbPath, backupDir, bootstrapInfo } = initDatabase({
    dbPath: options.dbPath,
    backupDir: options.backupDir
  });

  const app = express();
  app.disable('x-powered-by');

  const trustProxy = String(process.env.TRUST_PROXY || 'true').toLowerCase() !== 'false';
  if (trustProxy) {
    app.set('trust proxy', 1);
  }

  // 安全响应头
  app.use((req, res, next) => {
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-Frame-Options', 'SAMEORIGIN');
    res.setHeader('Referrer-Policy', 'same-origin');
    next();
  });

  // 健康检查（供 Docker / Cloudflare 探活）
  app.get('/healthz', (req, res) => {
    res.setHeader('Cache-Control', 'no-store');
    try {
      db.prepare('SELECT 1').get();
      res.json({ ok: true });
    } catch {
      res.status(503).json({ ok: false });
    }
  });

  // 私有 .ics 日历订阅（不走 Cookie 会话，仅凭高熵私有 token 访问）
  app.use('/ics', createIcsRouter(db));

  // API 中间件与路由（禁止 Cloudflare 或浏览器缓存私有数据）
  const authMw = createAuthMiddleware(db);
  app.use('/api', (req, res, next) => {
    res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, private');
    next();
  });
  app.use('/api', express.json({ limit: '5mb' }));
  app.use('/api', authMw.requireCsrfHeader);
  app.use('/api', authMw.attachSession);

  app.use('/api/auth', createAuthRouter(db, authMw));
  app.use('/api/admin', createAdminRouter(db, authMw, { dbPath, backupDir }));
  app.use('/api', createCalendarRouter(db, authMw));

  // API 404 与统一错误处理
  app.use('/api', (req, res) => {
    res.status(404).json({ error: '接口不存在' });
  });
  app.use((err, req, res, next) => {
    if (err && err.type === 'entity.parse.failed') {
      return res.status(400).json({ error: '请求 JSON 格式无效' });
    }
    if (err && err.type === 'entity.too.large') {
      return res.status(413).json({ error: '请求体过大（上限 5MB）' });
    }
    console.error('[server error]', err);
    if (res.headersSent) return next(err);
    res.status(500).json({ error: '服务器内部错误，请稍后重试' });
  });

  // 静态资源白名单托管（严禁暴露 server/、data/、.env、package.json 等）
  const staticOpts = {
    etag: true,
    setHeaders(res, filePath) {
      if (filePath.endsWith('.html')) {
        res.setHeader('Cache-Control', 'no-cache');
      } else {
        res.setHeader('Cache-Control', 'public, max-age=300, must-revalidate');
      }
    }
  };

  app.use('/css', express.static(path.join(rootDir, 'css'), staticOpts));
  app.use('/js', express.static(path.join(rootDir, 'js'), staticOpts));
  app.use('/icons', express.static(path.join(rootDir, 'icons'), staticOpts));

  app.get('/manifest.json', (req, res) => {
    res.setHeader('Cache-Control', 'public, max-age=300');
    res.sendFile(path.join(rootDir, 'manifest.json'));
  });

  app.get(['/', '/index.html'], (req, res) => {
    res.setHeader('Cache-Control', 'no-cache');
    res.sendFile(path.join(rootDir, 'index.html'));
  });

  return { app, db, dbPath, backupDir, bootstrapInfo };
}

function startBackgroundJobs(db, backupDir) {
  const timers = [];

  // 1. 定期清理过期会话
  const sessionTimer = setInterval(() => {
    try {
      cleanupExpiredSessions(db);
    } catch (err) {
      console.error('[session cleanup error]', err.message);
    }
  }, 6 * 3600 * 1000);
  sessionTimer.unref();
  timers.push(sessionTimer);

  // 2. 每日 SQLite 自动备份
  if (String(process.env.ENABLE_AUTO_BACKUP || 'true').toLowerCase() !== 'false') {
    const maybeBackup = () => {
      try {
        const existing = listBackups(backupDir);
        const last = existing[0];
        const ageMs = last ? Date.now() - Date.parse(last.createdAt) : Infinity;
        if (ageMs >= 23 * 3600 * 1000) {
          const snap = createBackupSnapshot(db, backupDir);
          console.log(`[backup] Created snapshot ${snap.filename} (${snap.size} bytes)`);
        }
      } catch (err) {
        console.error('[backup error]', err.message);
      }
    };
    const initTimer = setTimeout(maybeBackup, 5000);
    initTimer.unref();
    timers.push(initTimer);

    const dailyTimer = setInterval(maybeBackup, 24 * 3600 * 1000);
    dailyTimer.unref();
    timers.push(dailyTimer);
  }

  // 3. 每年 11–12 月自动检查同步次年节假日
  if (String(process.env.ENABLE_AUTO_HOLIDAY_SYNC || 'true').toLowerCase() !== 'false') {
    const trySync = () => {
      autoSyncUpcomingYearIfNeeded(db)
        .then((res) => {
          if (res) {
            console.log(
              `[holidays] Auto-synced ${res.year} (${res.holidayCount} holidays, ${res.workdayCount} workdays)`
            );
          }
        })
        .catch(() => {});
    };
    const bootTimer = setTimeout(trySync, 15000);
    bootTimer.unref();
    timers.push(bootTimer);

    const dailyHolidayTimer = setInterval(trySync, 24 * 3600 * 1000);
    dailyHolidayTimer.unref();
    timers.push(dailyHolidayTimer);
  }

  return timers;
}

if (require.main === module) {
  const port = parseInt(process.env.PORT || '3000', 10) || 3000;
  const host = process.env.HOST || '0.0.0.0';
  const { app, db, dbPath, backupDir, bootstrapInfo } = createApp();

  if (bootstrapInfo) {
    console.log('============================================================');
    console.log(' 已初始化默认管理员账号（请尽快登录后修改密码）');
    console.log(` 账号 ID : ${bootstrapInfo.username}`);
    console.log(
      ` 初始密码: ${bootstrapInfo.password}${bootstrapInfo.generated ? ' （自动生成）' : ' （来自环境变量）'}`
    );
    console.log('============================================================');
  }

  startBackgroundJobs(db, backupDir);

  const server = app.listen(port, host, () => {
    console.log(`[ryan-calendar] Listening on http://${host}:${port} (db: ${dbPath})`);
  });

  const shutdown = (signal) => {
    console.log(`[ryan-calendar] Received ${signal}, shutting down...`);
    server.close(() => {
      try {
        db.close();
      } catch {
        // ignore
      }
      process.exit(0);
    });
  };
  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('SIGTERM', () => shutdown('SIGTERM'));
}

module.exports = { createApp, startBackgroundJobs };
