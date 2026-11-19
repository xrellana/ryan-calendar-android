'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');
const express = require('express');
const {
  generateId,
  generateToken,
  hashPassword,
  destroyAllUserSessions,
  sanitizeUser
} = require('../auth');
const {
  withTransaction,
  seedDefaultCategories,
  getAllHolidaysPayload,
  createBackupSnapshot,
  listBackups
} = require('../db');
const { syncHolidayYear } = require('../holiday-sync');

const USERNAME_RE = /^[a-zA-Z0-9._-]{2,32}$/;
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
const BACKUP_FILE_RE = /^calendar_\d{8}_\d{6}\.db$/;

function createAdminRouter(db, authMw, { dbPath, backupDir }) {
  const router = express.Router();
  router.use(authMw.requireAdmin);

  // =========================================================================
  // 1. 用户管理
  // =========================================================================
  router.get('/users', (req, res) => {
    const rows = db
      .prepare(
        `SELECT u.id, u.username, u.display_name, u.role, u.is_active, u.ics_token,
                u.created_at, u.updated_at, u.last_login_at,
                (SELECT COUNT(*) FROM events e WHERE e.user_id = u.id) AS event_count,
                (SELECT COUNT(*) FROM categories c WHERE c.user_id = u.id) AS category_count
         FROM users u
         ORDER BY CASE WHEN u.role = 'admin' THEN 0 ELSE 1 END, u.created_at ASC`
      )
      .all();

    res.json({
      users: rows.map((r) => ({
        ...sanitizeUser(r),
        eventCount: r.event_count,
        categoryCount: r.category_count
      }))
    });
  });

  router.post('/users', (req, res) => {
    const username = String((req.body && req.body.username) || '').trim();
    const displayName = String((req.body && req.body.displayName) || '').trim().slice(0, 32) || username;
    let password = String((req.body && req.body.password) || '');
    const role = req.body && req.body.role === 'admin' ? 'admin' : 'user';

    if (!USERNAME_RE.test(username)) {
      return res.status(400).json({ error: '账号 ID 需为 2–32 位字母、数字或 . _ - 字符' });
    }
    if (!password) {
      password = crypto.randomBytes(6).toString('base64url');
    } else if (password.length < 6 || password.length > 100) {
      return res.status(400).json({ error: '密码长度需在 6 到 100 位之间' });
    }

    const exists = db
      .prepare('SELECT id FROM users WHERE username = ? COLLATE NOCASE')
      .get(username);
    if (exists) {
      return res.status(409).json({ error: `账号 "${username}" 已存在` });
    }

    const userId = generateId('usr');
    const icsToken = generateToken(32);
    const now = new Date().toISOString();

    withTransaction(db, () => {
      db.prepare(
        `INSERT INTO users (id, username, display_name, password_hash, role, is_active, ics_token, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?)`
      ).run(userId, username, displayName, hashPassword(password), role, icsToken, now, now);
      seedDefaultCategories(db, userId);
    });

    const created = db.prepare('SELECT * FROM users WHERE id = ?').get(userId);
    res.status(201).json({
      user: { ...sanitizeUser(created), eventCount: 0, categoryCount: 4 },
      initialPassword: password
    });
  });

  router.patch('/users/:id', (req, res) => {
    const uid = String(req.params.id || '');
    const target = db.prepare('SELECT * FROM users WHERE id = ?').get(uid);
    if (!target) {
      return res.status(404).json({ error: '账号不存在' });
    }

    let nextDisplayName = target.display_name;
    let nextRole = target.role;
    let nextActive = target.is_active;

    if (req.body && req.body.displayName !== undefined) {
      const dn = String(req.body.displayName).trim().slice(0, 32);
      if (!dn) return res.status(400).json({ error: '显示名称不能为空' });
      nextDisplayName = dn;
    }

    if (req.body && req.body.role !== undefined) {
      nextRole = req.body.role === 'admin' ? 'admin' : 'user';
    }

    if (req.body && req.body.isActive !== undefined) {
      nextActive = req.body.isActive ? 1 : 0;
    }

    if (uid === req.user.id && (!nextActive || nextRole !== 'admin')) {
      return res.status(400).json({ error: '不能禁用自己或取消自己的管理员权限' });
    }

    if (target.role === 'admin' && (nextRole !== 'admin' || !nextActive)) {
      const adminCnt = db
        .prepare("SELECT COUNT(*) AS cnt FROM users WHERE role = 'admin' AND is_active = 1 AND id != ?")
        .get(uid);
      if (!adminCnt || adminCnt.cnt < 1) {
        return res.status(400).json({ error: '系统至少需要保留一位启用中的管理员' });
      }
    }

    const now = new Date().toISOString();
    withTransaction(db, () => {
      db.prepare(
        'UPDATE users SET display_name = ?, role = ?, is_active = ?, updated_at = ? WHERE id = ?'
      ).run(nextDisplayName, nextRole, nextActive, now, uid);
      if (!nextActive) {
        destroyAllUserSessions(db, uid);
      }
    });

    const updated = db.prepare('SELECT * FROM users WHERE id = ?').get(uid);
    res.json({ user: sanitizeUser(updated) });
  });

  router.post('/users/:id/reset-password', (req, res) => {
    const uid = String(req.params.id || '');
    const target = db.prepare('SELECT id, username FROM users WHERE id = ?').get(uid);
    if (!target) {
      return res.status(404).json({ error: '账号不存在' });
    }

    let newPassword = String((req.body && req.body.password) || '').trim();
    if (!newPassword) {
      newPassword = crypto.randomBytes(6).toString('base64url');
    } else if (newPassword.length < 6 || newPassword.length > 100) {
      return res.status(400).json({ error: '密码长度需在 6 到 100 位之间' });
    }

    const now = new Date().toISOString();
    withTransaction(db, () => {
      db.prepare('UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?').run(
        hashPassword(newPassword),
        now,
        uid
      );
      destroyAllUserSessions(db, uid);
    });

    res.json({ ok: true, username: target.username, password: newPassword });
  });

  router.delete('/users/:id', (req, res) => {
    const uid = String(req.params.id || '');
    if (uid === req.user.id) {
      return res.status(400).json({ error: '不能删除当前登录的管理员账号' });
    }
    const target = db.prepare('SELECT id, role FROM users WHERE id = ?').get(uid);
    if (!target) {
      return res.status(404).json({ error: '账号不存在' });
    }
    if (target.role === 'admin') {
      const otherAdmins = db
        .prepare("SELECT COUNT(*) AS cnt FROM users WHERE role = 'admin' AND is_active = 1 AND id != ?")
        .get(uid);
      if (!otherAdmins || otherAdmins.cnt < 1) {
        return res.status(400).json({ error: '不能删除最后一个管理员账号' });
      }
    }

    db.prepare('DELETE FROM users WHERE id = ?').run(uid);
    res.json({ ok: true });
  });

  // =========================================================================
  // 2. 节假日管理（公开数据源同步 + 管理员手工微调）
  // =========================================================================
  router.get('/holidays', (req, res) => {
    res.json(getAllHolidaysPayload(db));
  });

  router.post('/holidays/sync', async (req, res) => {
    const year = parseInt((req.body && req.body.year) || '', 10);
    const overwriteManual = Boolean(req.body && req.body.overwriteManual);
    if (!Number.isFinite(year) || year < 2007 || year > 2100) {
      return res.status(400).json({ error: '请输入有效年份（2007–2100）' });
    }

    try {
      const summary = await syncHolidayYear(db, year, { overwriteManual });
      res.json({
        summary,
        holidays: getAllHolidaysPayload(db)
      });
    } catch (err) {
      res.status(502).json({ error: err.message || '同步节假日失败' });
    }
  });

  router.put('/holidays/:date', (req, res) => {
    const dateStr = String(req.params.date || '').trim();
    if (!DATE_RE.test(dateStr)) {
      return res.status(400).json({ error: '日期格式无效（应为 YYYY-MM-DD）' });
    }
    const type = req.body && req.body.type === 'workday' ? 'workday' : 'holiday';
    const name = String((req.body && req.body.name) || '')
      .trim()
      .slice(0, 40);
    if (!name) {
      return res.status(400).json({ error: '请填写节假日或调休名称' });
    }
    const year = parseInt(dateStr.slice(0, 4), 10);
    const now = new Date().toISOString();

    withTransaction(db, () => {
      db.prepare(
        `INSERT INTO holidays (date, year, type, name, source, updated_at)
         VALUES (?, ?, ?, ?, 'manual', ?)
         ON CONFLICT(date) DO UPDATE SET
           type = excluded.type,
           name = excluded.name,
           source = 'manual',
           updated_at = excluded.updated_at`
      ).run(dateStr, year, type, name, now);

      db.prepare(
        `INSERT OR IGNORE INTO holiday_years (year, source, synced_at)
         VALUES (?, 'manual', ?)`
      ).run(year, now);
    });

    res.json({ holidays: getAllHolidaysPayload(db) });
  });

  router.delete('/holidays/:date', (req, res) => {
    const dateStr = String(req.params.date || '').trim();
    if (!DATE_RE.test(dateStr)) {
      return res.status(400).json({ error: '日期格式无效' });
    }
    db.prepare('DELETE FROM holidays WHERE date = ?').run(dateStr);
    res.json({ holidays: getAllHolidaysPayload(db) });
  });

  // =========================================================================
  // 3. 数据库快照与自动备份管理
  // =========================================================================
  router.get('/backups', (req, res) => {
    let dbSize = 0;
    try {
      dbSize = fs.statSync(dbPath).size;
    } catch {
      // ignore
    }
    res.json({
      dbSize,
      backups: listBackups(backupDir)
    });
  });

  router.post('/backups', (req, res) => {
    try {
      const created = createBackupSnapshot(db, backupDir);
      res.status(201).json({
        backup: {
          filename: created.filename,
          size: created.size,
          createdAt: created.createdAt
        },
        backups: listBackups(backupDir)
      });
    } catch (err) {
      res.status(500).json({ error: err.message || '创建数据库备份失败' });
    }
  });

  router.get('/backups/download', (req, res) => {
    const requestedFile = req.query && req.query.file ? String(req.query.file) : '';
    if (requestedFile) {
      if (!BACKUP_FILE_RE.test(requestedFile)) {
        return res.status(400).json({ error: '无效的备份文件名' });
      }
      const fullPath = path.join(backupDir, requestedFile);
      if (!fs.existsSync(fullPath)) {
        return res.status(404).json({ error: '备份文件不存在' });
      }
      return res.download(fullPath, requestedFile);
    }

    // 实时生成一致的 WAL-safe 快照并下载
    const tmpName = `calendar_snapshot_${Date.now()}.db`;
    const tmpPath = path.join(os.tmpdir(), tmpName);
    try {
      if (fs.existsSync(tmpPath)) fs.unlinkSync(tmpPath);
      db.prepare('VACUUM INTO ?').run(tmpPath);
    } catch (err) {
      return res.status(500).json({ error: err.message || '生成快照失败' });
    }

    res.download(tmpPath, tmpName, () => {
      try {
        fs.unlinkSync(tmpPath);
      } catch {
        // ignore
      }
    });
  });

  return router;
}

module.exports = createAdminRouter;
