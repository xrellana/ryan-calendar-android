'use strict';

const { withTransaction } = require('./db');

const SOURCES = [
  (year) => `https://fastly.jsdelivr.net/gh/NateScarlet/holiday-cn@master/${year}.json`,
  (year) => `https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/${year}.json`,
  (year) => `https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/${year}.json`
];

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

function normalizeWorkdayName(name) {
  const clean = String(name || '调休').trim();
  if (clean.includes('调休') || clean.includes('上班')) return clean;
  return `${clean}调休上班`;
}

async function fetchYearFromPublicSources(year) {
  const yr = parseInt(year, 10);
  if (!Number.isFinite(yr) || yr < 2007 || yr > 2100) {
    throw new Error('年份必须在 2007 到 2100 之间');
  }

  let lastError = null;
  for (const buildUrl of SOURCES) {
    const url = buildUrl(yr);
    try {
      const resp = await fetch(url, {
        headers: {
          Accept: 'application/json',
          'User-Agent': 'ryan-calendar/2.0'
        },
        signal: AbortSignal.timeout(10000)
      });
      if (resp.status === 404) {
        lastError = new Error(`公开数据源暂未发布 ${yr} 年节假日安排（404）`);
        continue;
      }
      if (!resp.ok) {
        lastError = new Error(`数据源响应异常（HTTP ${resp.status}）`);
        continue;
      }
      const data = await resp.json();
      if (!data || !Array.isArray(data.days)) {
        lastError = new Error('数据源返回格式不符合预期');
        continue;
      }

      const entries = [];
      for (const item of data.days) {
        if (!item || typeof item.date !== 'string' || !DATE_RE.test(item.date)) continue;
        const entryYear = parseInt(item.date.slice(0, 4), 10);
        if (entryYear !== yr) continue;
        const isOff = Boolean(item.isOffDay);
        entries.push({
          date: item.date,
          year: yr,
          type: isOff ? 'holiday' : 'workday',
          name: isOff ? String(item.name || '法定节假日').trim() : normalizeWorkdayName(item.name)
        });
      }

      if (!entries.length) {
        lastError = new Error(`${yr} 年数据源未包含有效日期条目`);
        continue;
      }

      return { year: yr, entries, sourceUrl: url };
    } catch (err) {
      lastError = err;
    }
  }

  throw lastError || new Error(`无法拉取 ${yr} 年节假日数据`);
}

async function syncHolidayYear(db, year, { overwriteManual = false } = {}) {
  const fetched = await fetchYearFromPublicSources(year);
  const now = new Date().toISOString();

  withTransaction(db, () => {
    if (overwriteManual) {
      db.prepare('DELETE FROM holidays WHERE year = ?').run(fetched.year);
    } else {
      db.prepare("DELETE FROM holidays WHERE year = ? AND source != 'manual'").run(fetched.year);
    }

    const upsert = db.prepare(
      `INSERT INTO holidays (date, year, type, name, source, updated_at)
       VALUES (?, ?, ?, ?, 'sync', ?)
       ON CONFLICT(date) DO UPDATE SET
         type = excluded.type,
         name = excluded.name,
         source = 'sync',
         updated_at = excluded.updated_at
       WHERE holidays.source != 'manual' OR ? = 1`
    );

    for (const item of fetched.entries) {
      upsert.run(item.date, item.year, item.type, item.name, now, overwriteManual ? 1 : 0);
    }

    db.prepare(
      `INSERT INTO holiday_years (year, source, synced_at)
       VALUES (?, 'sync', ?)
       ON CONFLICT(year) DO UPDATE SET source = 'sync', synced_at = excluded.synced_at`
    ).run(fetched.year, now);
  });

  const holidayCount = fetched.entries.filter((e) => e.type === 'holiday').length;
  const workdayCount = fetched.entries.filter((e) => e.type === 'workday').length;

  return {
    year: fetched.year,
    total: fetched.entries.length,
    holidayCount,
    workdayCount,
    sourceUrl: fetched.sourceUrl,
    syncedAt: now
  };
}

/**
 * 每年 11–12 月自动尝试同步次年节假日（若尚未入库）
 */
async function autoSyncUpcomingYearIfNeeded(db) {
  const now = new Date();
  const nextYear = now.getFullYear() + 1;
  const exists = db.prepare('SELECT year FROM holiday_years WHERE year = ?').get(nextYear);
  if (exists) return null;
  // 11 月起国务院一般陆续发布次年放假安排
  if (now.getMonth() + 1 < 11) return null;
  try {
    return await syncHolidayYear(db, nextYear, { overwriteManual: false });
  } catch {
    return null;
  }
}

module.exports = {
  fetchYearFromPublicSources,
  syncHolidayYear,
  autoSyncUpcomingYearIfNeeded
};
