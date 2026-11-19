'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { DatabaseSync } = require('node:sqlite');
const { generateId, generateToken, hashPassword } = require('./auth');
const AppConfig = require('../js/config');
const BuiltinHolidays = require('../js/holidays');

function withTransaction(db, fn) {
  db.exec('BEGIN IMMEDIATE');
  try {
    const result = fn();
    db.exec('COMMIT');
    return result;
  } catch (err) {
    try {
      db.exec('ROLLBACK');
    } catch {
      // ignore rollback errors
    }
    throw err;
  }
}

function initDatabase({ dbPath, backupDir } = {}) {
  const resolvedDbPath = path.resolve(dbPath || process.env.DB_PATH || path.join(__dirname, '..', 'data', 'calendar.db'));
  const resolvedBackupDir = path.resolve(
    backupDir || process.env.BACKUP_DIR || path.join(path.dirname(resolvedDbPath), 'backups')
  );

  fs.mkdirSync(path.dirname(resolvedDbPath), { recursive: true });
  fs.mkdirSync(resolvedBackupDir, { recursive: true });

  const db = new DatabaseSync(resolvedDbPath);
  db.exec('PRAGMA journal_mode = WAL;');
  db.exec('PRAGMA synchronous = NORMAL;');
  db.exec('PRAGMA foreign_keys = ON;');
  db.exec('PRAGMA busy_timeout = 5000;');

  db.exec(`
    CREATE TABLE IF NOT EXISTS users (
      id TEXT PRIMARY KEY,
      username TEXT NOT NULL UNIQUE COLLATE NOCASE,
      display_name TEXT NOT NULL,
      password_hash TEXT NOT NULL,
      role TEXT NOT NULL DEFAULT 'user' CHECK(role IN ('admin', 'user')),
      is_active INTEGER NOT NULL DEFAULT 1,
      ics_token TEXT NOT NULL UNIQUE,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      last_login_at TEXT
    );

    CREATE TABLE IF NOT EXISTS sessions (
      token_hash TEXT PRIMARY KEY,
      user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      expires_at TEXT NOT NULL,
      created_at TEXT NOT NULL,
      last_seen_at TEXT NOT NULL,
      user_agent TEXT,
      ip TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
    CREATE INDEX IF NOT EXISTS idx_sessions_expires ON sessions(expires_at);

    CREATE TABLE IF NOT EXISTS categories (
      id TEXT NOT NULL,
      user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      name TEXT NOT NULL,
      color TEXT NOT NULL,
      sort_order INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      PRIMARY KEY (user_id, id)
    );
    CREATE INDEX IF NOT EXISTS idx_categories_user_order ON categories(user_id, sort_order);

    CREATE TABLE IF NOT EXISTS events (
      id TEXT NOT NULL,
      user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      title TEXT NOT NULL,
      category_id TEXT NOT NULL,
      start_date TEXT NOT NULL,
      end_date TEXT NOT NULL DEFAULT '',
      all_day INTEGER NOT NULL DEFAULT 1,
      start_time TEXT NOT NULL DEFAULT '',
      end_time TEXT NOT NULL DEFAULT '',
      notes TEXT NOT NULL DEFAULT '',
      source TEXT NOT NULL DEFAULT 'manual' CHECK(source IN ('manual', 'ledger')),
      ledger_key TEXT NOT NULL DEFAULT '',
      ledger_meta TEXT,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      PRIMARY KEY (user_id, id)
    );
    CREATE INDEX IF NOT EXISTS idx_events_user_dates ON events(user_id, start_date, end_date);
    CREATE INDEX IF NOT EXISTS idx_events_user_ledger ON events(user_id, source, ledger_key);

    CREATE TABLE IF NOT EXISTS holiday_years (
      year INTEGER PRIMARY KEY,
      source TEXT NOT NULL DEFAULT 'builtin',
      synced_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS holidays (
      date TEXT PRIMARY KEY,
      year INTEGER NOT NULL,
      type TEXT NOT NULL CHECK(type IN ('holiday', 'workday')),
      name TEXT NOT NULL,
      source TEXT NOT NULL DEFAULT 'builtin',
      updated_at TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_holidays_year ON holidays(year);
  `);

  seedBuiltinHolidays(db);
  const bootstrapInfo = ensureInitialAdmin(db);

  return {
    db,
    dbPath: resolvedDbPath,
    backupDir: resolvedBackupDir,
    bootstrapInfo
  };
}

function seedDefaultCategories(db, userId) {
  const now = new Date().toISOString();
  const stmt = db.prepare(
    `INSERT OR IGNORE INTO categories (id, user_id, name, color, sort_order, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?)`
  );
  const defaults = AppConfig.defaultCategories || [];
  defaults.forEach((cat, idx) => {
    stmt.run(cat.id, userId, cat.name, cat.color, idx, now, now);
  });
}

function seedBuiltinHolidays(db) {
  const plans = BuiltinHolidays.YEAR_PLANS || {};
  const checkYear = db.prepare('SELECT year FROM holiday_years WHERE year = ?');
  const insertYear = db.prepare(
    'INSERT OR IGNORE INTO holiday_years (year, source, synced_at) VALUES (?, ?, ?)'
  );
  const insertEntry = db.prepare(
    `INSERT OR IGNORE INTO holidays (date, year, type, name, source, updated_at)
     VALUES (?, ?, ?, ?, ?, ?)`
  );
  const now = new Date().toISOString();

  withTransaction(db, () => {
    Object.keys(plans).forEach((yrKey) => {
      const year = parseInt(yrKey, 10);
      if (!Number.isFinite(year)) return;
      if (checkYear.get(year)) return;
      const plan = plans[yrKey];
      insertYear.run(year, 'builtin', now);
      if (plan && plan.holidays) {
        Object.keys(plan.holidays).forEach((dateStr) => {
          insertEntry.run(dateStr, year, 'holiday', String(plan.holidays[dateStr] || '法定节假日'), 'builtin', now);
        });
      }
      if (plan && plan.workdays) {
        Object.keys(plan.workdays).forEach((dateStr) => {
          insertEntry.run(dateStr, year, 'workday', String(plan.workdays[dateStr] || '调休上班'), 'builtin', now);
        });
      }
    });
  });
}

function ensureInitialAdmin(db) {
  const existingAdmin = db.prepare("SELECT id, username FROM users WHERE role = 'admin' LIMIT 1").get();
  if (existingAdmin) return null;

  const username = String(process.env.ADMIN_USERNAME || 'admin').trim() || 'admin';
  let password = String(process.env.ADMIN_PASSWORD || '').trim();
  let generated = false;

  if (!password) {
    password = crypto.randomBytes(9).toString('base64url');
    generated = true;
  }

  const now = new Date().toISOString();
  const userId = generateId('usr');
  const icsToken = generateToken(32);

  withTransaction(db, () => {
    db.prepare(
      `INSERT INTO users (id, username, display_name, password_hash, role, is_active, ics_token, created_at, updated_at)
       VALUES (?, ?, ?, ?, 'admin', 1, ?, ?, ?)`
    ).run(userId, username, '管理员', hashPassword(password), icsToken, now, now);
    seedDefaultCategories(db, userId);
  });

  return { username, password, generated };
}

function getUserStateUpdatedAt(db, userId) {
  const e = db.prepare('SELECT MAX(updated_at) AS ts FROM events WHERE user_id = ?').get(userId);
  const c = db.prepare('SELECT MAX(updated_at) AS ts FROM categories WHERE user_id = ?').get(userId);
  const tsList = [e && e.ts, c && c.ts].filter(Boolean).sort();
  return tsList.length ? tsList[tsList.length - 1] : null;
}

function listCategoriesForUser(db, userId) {
  const rows = db
    .prepare(
      `SELECT id, name, color, sort_order, created_at, updated_at
       FROM categories
       WHERE user_id = ?
       ORDER BY sort_order ASC, created_at ASC`
    )
    .all(userId);
  return rows.map((r) => ({
    id: r.id,
    name: r.name,
    color: r.color
  }));
}

function rowToEvent(r) {
  const ev = {
    id: r.id,
    title: r.title,
    categoryId: r.category_id,
    startDate: r.start_date,
    endDate: r.end_date || '',
    allDay: r.all_day !== 0,
    startTime: r.start_time || '',
    endTime: r.end_time || '',
    notes: r.notes || '',
    source: r.source === 'ledger' ? 'ledger' : 'manual',
    createdAt: r.created_at,
    updatedAt: r.updated_at
  };
  if (ev.source === 'ledger') {
    ev.ledgerKey = r.ledger_key || '';
    if (r.ledger_meta) {
      try {
        ev.ledgerMeta = JSON.parse(r.ledger_meta);
      } catch {
        // ignore invalid json
      }
    }
  }
  return ev;
}

function listEventsForUser(db, userId) {
  const rows = db
    .prepare(
      `SELECT id, title, category_id, start_date, end_date, all_day, start_time, end_time,
              notes, source, ledger_key, ledger_meta, created_at, updated_at
       FROM events
       WHERE user_id = ?
       ORDER BY start_date ASC, created_at ASC`
    )
    .all(userId);
  return rows.map(rowToEvent);
}

function getAllHolidaysPayload(db) {
  const years = db
    .prepare('SELECT year, source, synced_at FROM holiday_years ORDER BY year ASC')
    .all()
    .map((r) => ({
      year: r.year,
      source: r.source,
      syncedAt: r.synced_at
    }));
  const entries = db
    .prepare('SELECT date, year, type, name, source, updated_at FROM holidays ORDER BY date ASC')
    .all()
    .map((r) => ({
      date: r.date,
      year: r.year,
      type: r.type,
      name: r.name,
      source: r.source,
      updatedAt: r.updated_at
    }));
  return {
    years: years.map((y) => y.year),
    yearMeta: years,
    entries
  };
}

function formatBackupStamp(date = new Date()) {
  const pad = (n) => String(n).padStart(2, '0');
  return (
    date.getFullYear() +
    pad(date.getMonth() + 1) +
    pad(date.getDate()) +
    '_' +
    pad(date.getHours()) +
    pad(date.getMinutes()) +
    pad(date.getSeconds())
  );
}

function createBackupSnapshot(db, backupDir, keepCount) {
  fs.mkdirSync(backupDir, { recursive: true });
  const filename = `calendar_${formatBackupStamp()}.db`;
  const targetPath = path.join(backupDir, filename);
  if (fs.existsSync(targetPath)) {
    fs.unlinkSync(targetPath);
  }
  db.prepare('VACUUM INTO ?').run(targetPath);
  const stat = fs.statSync(targetPath);

  // 清理超出保留数量的旧备份
  const keep = Number.isFinite(keepCount) && keepCount >= 1 ? keepCount : parseInt(process.env.BACKUP_KEEP_DAYS || '14', 10) || 14;
  const files = listBackups(backupDir);
  if (files.length > keep) {
    files.slice(keep).forEach((item) => {
      try {
        fs.unlinkSync(path.join(backupDir, item.filename));
      } catch {
        // ignore
      }
    });
  }

  return {
    filename,
    size: stat.size,
    createdAt: stat.mtime.toISOString(),
    path: targetPath
  };
}

function listBackups(backupDir) {
  if (!fs.existsSync(backupDir)) return [];
  return fs
    .readdirSync(backupDir)
    .filter((f) => /^calendar_\d{8}_\d{6}\.db$/.test(f))
    .map((f) => {
      const st = fs.statSync(path.join(backupDir, f));
      return {
        filename: f,
        size: st.size,
        createdAt: st.mtime.toISOString()
      };
    })
    .sort((a, b) => b.filename.localeCompare(a.filename));
}

module.exports = {
  initDatabase,
  withTransaction,
  seedDefaultCategories,
  getUserStateUpdatedAt,
  listCategoriesForUser,
  listEventsForUser,
  rowToEvent,
  getAllHolidaysPayload,
  createBackupSnapshot,
  listBackups
};
