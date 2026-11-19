'use strict';

const express = require('express');
const { generateId } = require('../auth');
const {
  withTransaction,
  listCategoriesForUser,
  listEventsForUser,
  rowToEvent,
  getUserStateUpdatedAt,
  getAllHolidaysPayload
} = require('../db');

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
const TIME_RE = /^\d{2}:\d{2}$/;

function normalizeHex(hex) {
  let s = String(hex || '').trim();
  if (!s.startsWith('#')) s = `#${s}`;
  if (/^#[0-9a-fA-F]{3}$/.test(s)) {
    s = `#${s[1]}${s[1]}${s[2]}${s[2]}${s[3]}${s[3]}`;
  }
  if (/^#[0-9a-fA-F]{6}$/.test(s)) {
    return s.toUpperCase();
  }
  return '#2B6CD4';
}

function normalizeEventPayload(raw, existing = null) {
  const title = String(raw.title !== undefined ? raw.title : (existing ? existing.title : ''))
    .trim()
    .slice(0, 60);
  if (!title) {
    throw new Error('日程标题不能为空');
  }

  const startDate = String(
    raw.startDate !== undefined ? raw.startDate : (existing ? existing.start_date : '')
  ).trim();
  if (!DATE_RE.test(startDate)) {
    throw new Error('开始日期格式无效（应为 YYYY-MM-DD）');
  }

  let endDate = String(
    raw.endDate !== undefined ? raw.endDate : (existing ? existing.end_date : '')
  ).trim();
  if (endDate) {
    if (!DATE_RE.test(endDate)) {
      throw new Error('结束日期格式无效（应为 YYYY-MM-DD）');
    }
    if (endDate < startDate) {
      throw new Error('结束日期不能早于开始日期');
    }
    if (endDate === startDate) {
      endDate = '';
    }
  }

  const allDay =
    raw.allDay !== undefined
      ? raw.allDay !== false
      : existing
        ? existing.all_day !== 0
        : true;

  let startTime = '';
  let endTime = '';
  if (!allDay) {
    const rawS = String(
      raw.startTime !== undefined ? raw.startTime : (existing ? existing.start_time : '09:00')
    )
      .trim()
      .slice(0, 5);
    const rawE = String(
      raw.endTime !== undefined ? raw.endTime : (existing ? existing.end_time : '18:00')
    )
      .trim()
      .slice(0, 5);
    startTime = TIME_RE.test(rawS) ? rawS : '09:00';
    endTime = TIME_RE.test(rawE) ? rawE : '18:00';
  }

  const categoryId = String(
    raw.categoryId !== undefined ? raw.categoryId : (existing ? existing.category_id : '')
  ).trim();

  const notes = String(
    raw.notes !== undefined ? raw.notes : (existing ? existing.notes : '')
  ).slice(0, 500);

  const source =
    raw.source !== undefined
      ? raw.source === 'ledger'
        ? 'ledger'
        : 'manual'
      : existing
        ? existing.source
        : 'manual';

  let ledgerKey = '';
  let ledgerMetaJson = null;
  if (source === 'ledger') {
    ledgerKey = String(
      raw.ledgerKey !== undefined ? raw.ledgerKey : (existing ? existing.ledger_key : '')
    ).slice(0, 200);
    if (raw.ledgerMeta !== undefined) {
      if (raw.ledgerMeta && typeof raw.ledgerMeta === 'object') {
        ledgerMetaJson = JSON.stringify({
          projectName: String(raw.ledgerMeta.projectName || '').slice(0, 100),
          stage: String(raw.ledgerMeta.stage || '').slice(0, 40),
          level: String(raw.ledgerMeta.level || '').slice(0, 40),
          role: String(raw.ledgerMeta.role || '').slice(0, 40)
        });
      } else {
        ledgerMetaJson = null;
      }
    } else if (existing) {
      ledgerMetaJson = existing.ledger_meta || null;
    }
  }

  return {
    title,
    categoryId,
    startDate,
    endDate,
    allDay: allDay ? 1 : 0,
    startTime,
    endTime,
    notes,
    source,
    ledgerKey,
    ledgerMetaJson
  };
}

function buildStateResponse(db, userId) {
  return {
    version: 1,
    categories: listCategoriesForUser(db, userId),
    events: listEventsForUser(db, userId),
    updatedAt: getUserStateUpdatedAt(db, userId) || new Date().toISOString()
  };
}

function createCalendarRouter(db, authMw) {
  const router = express.Router();
  router.use(authMw.requireAuth);

  // 获取当前用户完整日历数据
  router.get('/state', (req, res) => {
    res.json(buildStateResponse(db, req.user.id));
  });

  // 获取节假日数据（全站共享）
  router.get('/holidays', (req, res) => {
    res.json(getAllHolidaysPayload(db));
  });

  // -------------------------------------------------------------------------
  // 类别 CRUD 与排序
  // -------------------------------------------------------------------------
  router.post('/categories', (req, res) => {
    const name = String((req.body && req.body.name) || '').trim().slice(0, 20);
    if (!name) {
      return res.status(400).json({ error: '类别名称不能为空' });
    }
    const color = normalizeHex(req.body && req.body.color);
    const id = generateId('cat');
    const now = new Date().toISOString();

    const maxRow = db
      .prepare('SELECT COALESCE(MAX(sort_order), -1) AS m FROM categories WHERE user_id = ?')
      .get(req.user.id);
    const sortOrder = (maxRow ? maxRow.m : -1) + 1;

    db.prepare(
      `INSERT INTO categories (id, user_id, name, color, sort_order, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?)`
    ).run(id, req.user.id, name, color, sortOrder, now, now);

    res.status(201).json({
      category: { id, name, color },
      categories: listCategoriesForUser(db, req.user.id)
    });
  });

  router.put('/categories/reorder', (req, res) => {
    const orderedIds = req.body && req.body.orderedIds;
    if (!Array.isArray(orderedIds) || !orderedIds.length) {
      return res.status(400).json({ error: '请提供有效的排序列表' });
    }

    const now = new Date().toISOString();
    withTransaction(db, () => {
      const stmt = db.prepare(
        'UPDATE categories SET sort_order = ?, updated_at = ? WHERE user_id = ? AND id = ?'
      );
      orderedIds.forEach((cid, idx) => {
        stmt.run(idx, now, req.user.id, String(cid));
      });
    });

    res.json({ categories: listCategoriesForUser(db, req.user.id) });
  });

  router.patch('/categories/:id', (req, res) => {
    const cid = String(req.params.id || '');
    const existing = db
      .prepare('SELECT id, name, color FROM categories WHERE user_id = ? AND id = ?')
      .get(req.user.id, cid);
    if (!existing) {
      return res.status(404).json({ error: '类别不存在' });
    }

    let nextName = existing.name;
    let nextColor = existing.color;
    if (req.body && req.body.name !== undefined) {
      const cleaned = String(req.body.name).trim().slice(0, 20);
      if (!cleaned) {
        return res.status(400).json({ error: '类别名称不能为空' });
      }
      nextName = cleaned;
    }
    if (req.body && req.body.color !== undefined) {
      nextColor = normalizeHex(req.body.color);
    }

    const now = new Date().toISOString();
    db.prepare(
      'UPDATE categories SET name = ?, color = ?, updated_at = ? WHERE user_id = ? AND id = ?'
    ).run(nextName, nextColor, now, req.user.id, cid);

    res.json({
      category: { id: cid, name: nextName, color: nextColor },
      categories: listCategoriesForUser(db, req.user.id)
    });
  });

  router.delete('/categories/:id', (req, res) => {
    const cid = String(req.params.id || '');
    const info = db
      .prepare('DELETE FROM categories WHERE user_id = ? AND id = ?')
      .run(req.user.id, cid);
    if (info.changes === 0) {
      return res.status(404).json({ error: '类别不存在' });
    }
    res.json({ ok: true, categories: listCategoriesForUser(db, req.user.id) });
  });

  // -------------------------------------------------------------------------
  // 日程 CRUD
  // -------------------------------------------------------------------------
  router.post('/events', (req, res) => {
    let data;
    try {
      data = normalizeEventPayload(req.body || {});
    } catch (err) {
      return res.status(400).json({ error: err.message });
    }

    const id = generateId('evt');
    const now = new Date().toISOString();

    db.prepare(
      `INSERT INTO events (
         id, user_id, title, category_id, start_date, end_date, all_day,
         start_time, end_time, notes, source, ledger_key, ledger_meta, created_at, updated_at
       ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
    ).run(
      id,
      req.user.id,
      data.title,
      data.categoryId,
      data.startDate,
      data.endDate,
      data.allDay,
      data.startTime,
      data.endTime,
      data.notes,
      data.source,
      data.ledgerKey,
      data.ledgerMetaJson,
      now,
      now
    );

    const created = db
      .prepare('SELECT * FROM events WHERE user_id = ? AND id = ?')
      .get(req.user.id, id);
    res.status(201).json({ event: rowToEvent(created) });
  });

  router.patch('/events/:id', (req, res) => {
    const eid = String(req.params.id || '');
    const existing = db
      .prepare('SELECT * FROM events WHERE user_id = ? AND id = ?')
      .get(req.user.id, eid);
    if (!existing) {
      return res.status(404).json({ error: '日程不存在' });
    }

    let data;
    try {
      data = normalizeEventPayload(req.body || {}, existing);
    } catch (err) {
      return res.status(400).json({ error: err.message });
    }

    const now = new Date().toISOString();
    db.prepare(
      `UPDATE events
       SET title = ?, category_id = ?, start_date = ?, end_date = ?, all_day = ?,
           start_time = ?, end_time = ?, notes = ?, updated_at = ?
       WHERE user_id = ? AND id = ?`
    ).run(
      data.title,
      data.categoryId,
      data.startDate,
      data.endDate,
      data.allDay,
      data.startTime,
      data.endTime,
      data.notes,
      now,
      req.user.id,
      eid
    );

    const updated = db
      .prepare('SELECT * FROM events WHERE user_id = ? AND id = ?')
      .get(req.user.id, eid);
    res.json({ event: rowToEvent(updated) });
  });

  router.delete('/events/:id', (req, res) => {
    const eid = String(req.params.id || '');
    const info = db
      .prepare('DELETE FROM events WHERE user_id = ? AND id = ?')
      .run(req.user.id, eid);
    if (info.changes === 0) {
      return res.status(404).json({ error: '日程不存在' });
    }
    res.json({ ok: true });
  });

  router.delete('/events', (req, res) => {
    const info = db.prepare('DELETE FROM events WHERE user_id = ?').run(req.user.id);
    res.json({ ok: true, deleted: info.changes });
  });

  // -------------------------------------------------------------------------
  // 台账批量导入（仅更新 source='ledger' 且 ledger_key 匹配项，绝不覆盖手填）
  // -------------------------------------------------------------------------
  router.post('/events/import-ledger', (req, res) => {
    const entries = req.body && req.body.entries;
    if (!Array.isArray(entries)) {
      return res.status(400).json({ error: '导入数据格式无效' });
    }
    if (entries.length > 2000) {
      return res.status(400).json({ error: '单次最多导入 2000 条记录' });
    }

    const now = new Date().toISOString();
    let added = 0;
    let updated = 0;

    try {
      withTransaction(db, () => {
        const existingRows = db
          .prepare("SELECT id, ledger_key FROM events WHERE user_id = ? AND source = 'ledger' AND ledger_key != ''")
          .all(req.user.id);
        const keyToId = new Map();
        for (const r of existingRows) {
          keyToId.set(r.ledger_key, r.id);
        }

        const updateStmt = db.prepare(
          `UPDATE events
           SET title = ?, category_id = ?, start_date = ?, end_date = ?, all_day = 1,
               start_time = '', end_time = '', notes = ?, ledger_meta = ?, updated_at = ?
           WHERE user_id = ? AND id = ?`
        );
        const insertStmt = db.prepare(
          `INSERT INTO events (
             id, user_id, title, category_id, start_date, end_date, all_day,
             start_time, end_time, notes, source, ledger_key, ledger_meta, created_at, updated_at
           ) VALUES (?, ?, ?, ?, ?, ?, 1, '', '', ?, 'ledger', ?, ?, ?, ?)`
        );

        for (const item of entries) {
          const data = normalizeEventPayload({
            title: item.title,
            categoryId: item.categoryId,
            startDate: item.startDate,
            endDate: item.endDate || '',
            allDay: true,
            notes: item.notes || '',
            source: 'ledger',
            ledgerKey: item.ledgerKey || '',
            ledgerMeta: item.ledgerMeta || null
          });

          const existingId = data.ledgerKey ? keyToId.get(data.ledgerKey) : null;
          if (existingId) {
            updateStmt.run(
              data.title,
              data.categoryId,
              data.startDate,
              data.endDate,
              data.notes,
              data.ledgerMetaJson,
              now,
              req.user.id,
              existingId
            );
            updated++;
          } else {
            const newId = generateId('evt');
            insertStmt.run(
              newId,
              req.user.id,
              data.title,
              data.categoryId,
              data.startDate,
              data.endDate,
              data.notes,
              data.ledgerKey,
              data.ledgerMetaJson,
              now,
              now
            );
            if (data.ledgerKey) keyToId.set(data.ledgerKey, newId);
            added++;
          }
        }
      });
    } catch (err) {
      return res.status(400).json({ error: err.message || '台账导入失败' });
    }

    res.json({
      added,
      updated,
      state: buildStateResponse(db, req.user.id)
    });
  });

  // -------------------------------------------------------------------------
  // 加载示例日程
  // -------------------------------------------------------------------------
  router.post('/events/sample', (req, res) => {
    const now = new Date();
    const year = parseInt((req.body && req.body.year) || now.getFullYear(), 10);
    const month = parseInt((req.body && req.body.month) || now.getMonth() + 1, 10);
    if (!Number.isFinite(year) || !Number.isFinite(month) || month < 1 || month > 12) {
      return res.status(400).json({ error: '无效的年月参数' });
    }

    const ym = `${year}-${String(month).padStart(2, '0')}`;
    const samples = [
      {
        title: '华东新能源装备厂现场评估',
        categoryId: 'cat-assessment',
        startDate: `${ym}-03`,
        endDate: `${ym}-05`,
        allDay: true,
        notes: '联系人：李工 13800001111；需携带评估表与安全检查清单'
      },
      {
        title: '智能制造专项咨询出差（跨周）',
        categoryId: 'cat-consulting',
        startDate: `${ym}-11`,
        endDate: `${ym}-16`,
        allDay: true,
        notes: '高铁往返，周一上午与甲方项目组对齐评估范围'
      },
      {
        title: '某化工园区预评估（并行档期）',
        categoryId: 'cat-assessment',
        startDate: `${ym}-13`,
        endDate: `${ym}-15`,
        allDay: true,
        notes: '协助复核工艺安全资料'
      },
      {
        title: '华东项目初稿报告提交截止',
        categoryId: 'cat-deadline',
        startDate: `${ym}-19`,
        endDate: '',
        allDay: false,
        startTime: '17:00',
        endTime: '18:00',
        notes: '17:00 前发甲方邮箱并抄送内审组'
      },
      {
        title: '内部评估专家复核会',
        categoryId: 'cat-consulting',
        startDate: `${ym}-19`,
        endDate: '',
        allDay: false,
        startTime: '09:30',
        endTime: '11:30',
        notes: '三楼小会议室'
      },
      {
        title: '整理现场照片与归档台账',
        categoryId: 'cat-rest',
        startDate: `${ym}-19`,
        endDate: '',
        allDay: true,
        notes: '下午调休半天'
      }
    ];

    const ts = new Date().toISOString();
    withTransaction(db, () => {
      const stmt = db.prepare(
        `INSERT INTO events (
           id, user_id, title, category_id, start_date, end_date, all_day,
           start_time, end_time, notes, source, ledger_key, ledger_meta, created_at, updated_at
         ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'manual', '', NULL, ?, ?)`
      );
      for (const s of samples) {
        const d = normalizeEventPayload(s);
        stmt.run(
          generateId('evt'),
          req.user.id,
          d.title,
          d.categoryId,
          d.startDate,
          d.endDate,
          d.allDay,
          d.startTime,
          d.endTime,
          d.notes,
          ts,
          ts
        );
      }
    });

    res.json({
      count: samples.length,
      state: buildStateResponse(db, req.user.id)
    });
  });

  // -------------------------------------------------------------------------
  // JSON 备份恢复 / 本地旧数据一键云端迁移
  // -------------------------------------------------------------------------
  router.post('/backup/restore', (req, res) => {
    const mode = req.body && req.body.mode === 'merge' ? 'merge' : 'replace';
    const rawCategories = req.body && req.body.categories;
    const rawEvents = req.body && req.body.events;

    if (!Array.isArray(rawCategories) || !Array.isArray(rawEvents)) {
      return res.status(400).json({ error: '备份数据缺少 categories 或 events 数组' });
    }
    if (rawCategories.length > 200 || rawEvents.length > 10000) {
      return res.status(400).json({ error: '单次恢复数据量超出上限' });
    }

    const now = new Date().toISOString();
    let importedEvents = 0;

    try {
      withTransaction(db, () => {
        if (mode === 'replace') {
          db.prepare('DELETE FROM categories WHERE user_id = ?').run(req.user.id);
          db.prepare('DELETE FROM events WHERE user_id = ?').run(req.user.id);
        }

        const existingCatIds = new Set(
          db
            .prepare('SELECT id FROM categories WHERE user_id = ?')
            .all(req.user.id)
            .map((r) => r.id)
        );
        const maxOrderRow = db
          .prepare('SELECT COALESCE(MAX(sort_order), -1) AS m FROM categories WHERE user_id = ?')
          .get(req.user.id);
        let nextOrder = (maxOrderRow ? maxOrderRow.m : -1) + 1;

        const insertCat = db.prepare(
          `INSERT OR IGNORE INTO categories (id, user_id, name, color, sort_order, created_at, updated_at)
           VALUES (?, ?, ?, ?, ?, ?, ?)`
        );

        rawCategories.forEach((c) => {
          const cid = String((c && c.id) || generateId('cat')).slice(0, 80);
          if (existingCatIds.has(cid)) return;
          const name = String((c && c.name) || '未命名类别').trim().slice(0, 20) || '未命名类别';
          const color = normalizeHex(c && c.color);
          insertCat.run(cid, req.user.id, name, color, nextOrder++, now, now);
          existingCatIds.add(cid);
        });

        const existingEvtIds = new Set(
          db
            .prepare('SELECT id FROM events WHERE user_id = ?')
            .all(req.user.id)
            .map((r) => r.id)
        );

        const insertEvt = db.prepare(
          `INSERT OR IGNORE INTO events (
             id, user_id, title, category_id, start_date, end_date, all_day,
             start_time, end_time, notes, source, ledger_key, ledger_meta, created_at, updated_at
           ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
        );

        rawEvents.forEach((ev) => {
          if (!ev || !ev.title || !ev.startDate) return;
          let d;
          try {
            d = normalizeEventPayload(ev);
          } catch {
            return;
          }
          const eid = String(ev.id || generateId('evt')).slice(0, 80);
          if (existingEvtIds.has(eid)) return;
          const createdAt = typeof ev.createdAt === 'string' && ev.createdAt ? ev.createdAt : now;
          const updatedAt = typeof ev.updatedAt === 'string' && ev.updatedAt ? ev.updatedAt : now;
          insertEvt.run(
            eid,
            req.user.id,
            d.title,
            d.categoryId,
            d.startDate,
            d.endDate,
            d.allDay,
            d.startTime,
            d.endTime,
            d.notes,
            d.source,
            d.ledgerKey,
            d.ledgerMetaJson,
            createdAt,
            updatedAt
          );
          existingEvtIds.add(eid);
          importedEvents++;
        });
      });
    } catch (err) {
      return res.status(400).json({ error: err.message || '恢复数据失败' });
    }

    const state = buildStateResponse(db, req.user.id);
    res.json({
      categories: state.categories.length,
      events: mode === 'merge' ? importedEvents : state.events.length,
      state
    });
  });

  return router;
}

module.exports = createCalendarRouter;
