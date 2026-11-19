/**
 * 云端数据存储适配层：内存缓存 + 异步 API 同步 + 搜索与类别筛选 + 备份恢复与旧本地数据迁移
 */
(function (root) {
  'use strict';

  var WorkCal = (root.WorkCal = root.WorkCal || {});

  function getConfig() {
    return WorkCal.Config;
  }

  function getDateUtils() {
    return WorkCal.DateUtils;
  }

  function clone(obj) {
    return JSON.parse(JSON.stringify(obj));
  }

  function normalizeCategory(cat) {
    var Color = WorkCal.Color;
    var name = String((cat && cat.name) || '').trim().slice(0, 20) || '未命名类别';
    var color = Color
      ? Color.normalizeHex(cat && cat.color)
      : String((cat && cat.color) || '#2B6CD4').toUpperCase();
    return {
      id: cat && cat.id ? String(cat.id) : 'cat-' + Date.now().toString(36),
      name: name,
      color: color
    };
  }

  function normalizeEvent(ev) {
    var title = String((ev && ev.title) || '').trim().slice(0, 60);
    var startDate = String((ev && ev.startDate) || '').trim();
    var endDate = String((ev && ev.endDate) || '').trim();
    if (!endDate || endDate <= startDate) {
      endDate = '';
    }
    var allDay = ev && ev.allDay === false ? false : true;
    var startTime = allDay ? '' : String((ev && ev.startTime) || '09:00').slice(0, 5);
    var endTime = allDay ? '' : String((ev && ev.endTime) || '18:00').slice(0, 5);
    var notes = String((ev && ev.notes) || '').slice(0, 500);
    var source = ev && ev.source === 'ledger' ? 'ledger' : 'manual';

    var out = {
      id: ev && ev.id ? String(ev.id) : 'evt-' + Date.now().toString(36),
      title: title,
      categoryId: String((ev && ev.categoryId) || ''),
      startDate: startDate,
      endDate: endDate,
      allDay: allDay,
      startTime: startTime,
      endTime: endTime,
      notes: notes,
      source: source,
      createdAt: (ev && ev.createdAt) || new Date().toISOString(),
      updatedAt: (ev && ev.updatedAt) || new Date().toISOString()
    };
    if (source === 'ledger') {
      out.ledgerKey = String((ev && ev.ledgerKey) || '');
      if (ev && ev.ledgerMeta) {
        out.ledgerMeta = {
          projectName: String(ev.ledgerMeta.projectName || ''),
          stage: String(ev.ledgerMeta.stage || ''),
          level: String(ev.ledgerMeta.level || ''),
          role: String(ev.ledgerMeta.role || '')
        };
      }
    }
    return out;
  }

  function createStore(apiClient) {
    var api = apiClient || WorkCal.Api;
    var state = {
      version: 1,
      categories: clone(getConfig().defaultCategories),
      events: [],
      updatedAt: ''
    };

    var filter = {
      query: '',
      categoryIds: []
    };

    function applyServerState(payload) {
      if (!payload) return state;
      state = {
        version: 1,
        categories: Array.isArray(payload.categories)
          ? payload.categories.map(normalizeCategory)
          : [],
        events: Array.isArray(payload.events)
          ? payload.events.map(normalizeEvent).filter(function (e) {
              return Boolean(e.title && e.startDate);
            })
          : [],
        updatedAt: payload.updatedAt || new Date().toISOString()
      };
      // 清理筛选中已不存在的类别 ID
      if (filter.categoryIds.length) {
        var validSet = {};
        state.categories.forEach(function (c) {
          validSet[c.id] = true;
        });
        filter.categoryIds = filter.categoryIds.filter(function (id) {
          return Boolean(validSet[id]);
        });
      }
      return state;
    }

    function resetMemoryState() {
      state = {
        version: 1,
        categories: clone(getConfig().defaultCategories),
        events: [],
        updatedAt: ''
      };
      filter = { query: '', categoryIds: [] };
    }

    function loadFromServer() {
      return api.getState().then(function (data) {
        return applyServerState(data);
      });
    }

    function refreshIfChanged() {
      var prevUpdated = state.updatedAt;
      var prevCount = state.events.length;
      var prevCatCount = state.categories.length;
      return api.getState().then(function (data) {
        var changed =
          data.updatedAt !== prevUpdated ||
          (data.events && data.events.length !== prevCount) ||
          (data.categories && data.categories.length !== prevCatCount);
        applyServerState(data);
        return changed;
      });
    }

    function getState() {
      return state;
    }

    function getCategories() {
      return state.categories;
    }

    function getCategory(id) {
      for (var i = 0; i < state.categories.length; i++) {
        if (state.categories[i].id === id) return state.categories[i];
      }
      return getConfig().fallbackCategory;
    }

    function getCategoryPriorityMap() {
      var map = {};
      for (var i = 0; i < state.categories.length; i++) {
        map[state.categories[i].id] = i;
      }
      return map;
    }

    function countEventsByCategory(categoryId) {
      var count = 0;
      for (var i = 0; i < state.events.length; i++) {
        if (state.events[i].categoryId === categoryId) count++;
      }
      return count;
    }

    // -----------------------------------------------------------------------
    // 筛选与搜索
    // -----------------------------------------------------------------------
    function getFilter() {
      return {
        query: filter.query,
        categoryIds: filter.categoryIds.slice()
      };
    }

    function hasActiveFilter() {
      return Boolean(filter.query.trim() || filter.categoryIds.length > 0);
    }

    function setFilterQuery(q) {
      filter.query = String(q || '');
    }

    function toggleFilterCategory(catId) {
      var idx = filter.categoryIds.indexOf(catId);
      if (idx === -1) filter.categoryIds.push(catId);
      else filter.categoryIds.splice(idx, 1);
    }

    function clearFilters() {
      filter.query = '';
      filter.categoryIds = [];
    }

    function eventMatchesFilter(ev, customQuery, customCatIds) {
      var q = (customQuery !== undefined ? customQuery : filter.query).trim().toLowerCase();
      var catIds = customCatIds !== undefined ? customCatIds : filter.categoryIds;

      if (catIds && catIds.length > 0) {
        if (catIds.indexOf(ev.categoryId) === -1) return false;
      }

      if (q) {
        var cat = getCategory(ev.categoryId);
        var haystack = [
          ev.title,
          ev.notes,
          ev.startDate,
          ev.endDate,
          cat ? cat.name : '',
          ev.ledgerMeta ? ev.ledgerMeta.projectName : '',
          ev.ledgerMeta ? ev.ledgerMeta.stage : '',
          ev.ledgerMeta ? ev.ledgerMeta.level : '',
          ev.ledgerMeta ? ev.ledgerMeta.role : ''
        ]
          .join(' ')
          .toLowerCase();
        var terms = q.split(/\s+/).filter(Boolean);
        for (var i = 0; i < terms.length; i++) {
          if (haystack.indexOf(terms[i]) === -1) return false;
        }
      }
      return true;
    }

    function getEvents() {
      return state.events;
    }

    function getFilteredEvents() {
      if (!hasActiveFilter()) return state.events;
      return state.events.filter(function (ev) {
        return eventMatchesFilter(ev);
      });
    }

    function searchEvents(query, categoryIds) {
      var priorityMap = getCategoryPriorityMap();
      var matched = state.events.filter(function (ev) {
        return eventMatchesFilter(ev, query, categoryIds);
      });
      matched.sort(function (a, b) {
        if (a.startDate !== b.startDate) return b.startDate.localeCompare(a.startDate);
        if (WorkCal.Layout) return WorkCal.Layout.compareEvents(a, b, priorityMap);
        return 0;
      });
      return matched;
    }

    function getEventById(id) {
      for (var i = 0; i < state.events.length; i++) {
        if (state.events[i].id === id) return state.events[i];
      }
      return null;
    }

    function getEventsOnDate(dateStr, opts) {
      var ignoreFilter = Boolean(opts && opts.ignoreFilter);
      var sourceList = ignoreFilter ? state.events : getFilteredEvents();
      var priorityMap = getCategoryPriorityMap();
      var list = sourceList.filter(function (ev) {
        var end = ev.endDate && ev.endDate >= ev.startDate ? ev.endDate : ev.startDate;
        return ev.startDate <= dateStr && end >= dateStr;
      });
      list.sort(function (a, b) {
        if (WorkCal.Layout) {
          return WorkCal.Layout.compareEvents(a, b, priorityMap);
        }
        return 0;
      });
      return list;
    }

    function getTopCategoryOnDate(dateStr) {
      var list = getEventsOnDate(dateStr);
      if (!list.length) return null;
      return getCategory(list[0].categoryId);
    }

    // -----------------------------------------------------------------------
    // 类别异步 CRUD
    // -----------------------------------------------------------------------
    function addCategory(name, color) {
      var cleanName = String(name || '').trim().slice(0, 20);
      if (!cleanName) return Promise.reject(new Error('类别名称不能为空'));
      return api.addCategory(cleanName, color || '#2B6CD4').then(function (res) {
        if (res && Array.isArray(res.categories)) {
          state.categories = res.categories.map(normalizeCategory);
        } else if (res && res.category) {
          state.categories.push(normalizeCategory(res.category));
        }
        state.updatedAt = new Date().toISOString();
        return res.category ? normalizeCategory(res.category) : state.categories[state.categories.length - 1];
      });
    }

    function updateCategory(id, patch) {
      return api.updateCategory(id, patch).then(function (res) {
        if (res && Array.isArray(res.categories)) {
          state.categories = res.categories.map(normalizeCategory);
        }
        state.updatedAt = new Date().toISOString();
        return res && res.category ? normalizeCategory(res.category) : getCategory(id);
      });
    }

    function deleteCategory(id) {
      return api.deleteCategory(id).then(function (res) {
        if (res && Array.isArray(res.categories)) {
          state.categories = res.categories.map(normalizeCategory);
        } else {
          state.categories = state.categories.filter(function (c) {
            return c.id !== id;
          });
        }
        filter.categoryIds = filter.categoryIds.filter(function (cid) {
          return cid !== id;
        });
        state.updatedAt = new Date().toISOString();
        return true;
      });
    }

    function moveCategory(id, direction) {
      var idx = -1;
      for (var i = 0; i < state.categories.length; i++) {
        if (state.categories[i].id === id) {
          idx = i;
          break;
        }
      }
      if (idx === -1) return Promise.resolve(false);
      var target = direction === 'up' ? idx - 1 : idx + 1;
      if (target < 0 || target >= state.categories.length) return Promise.resolve(false);

      var ordered = state.categories.map(function (c) {
        return c.id;
      });
      var tmp = ordered[idx];
      ordered[idx] = ordered[target];
      ordered[target] = tmp;

      return api.reorderCategories(ordered).then(function (res) {
        if (res && Array.isArray(res.categories)) {
          state.categories = res.categories.map(normalizeCategory);
        }
        state.updatedAt = new Date().toISOString();
        return true;
      });
    }

    // -----------------------------------------------------------------------
    // 日程异步 CRUD
    // -----------------------------------------------------------------------
    function addEvent(data) {
      return api.addEvent(data).then(function (res) {
        var created = normalizeEvent(res.event);
        state.events.push(created);
        state.updatedAt = created.updatedAt || new Date().toISOString();
        return created;
      });
    }

    function updateEvent(id, patch) {
      return api.updateEvent(id, patch).then(function (res) {
        var updated = normalizeEvent(res.event);
        var replaced = false;
        for (var i = 0; i < state.events.length; i++) {
          if (state.events[i].id === id) {
            state.events[i] = updated;
            replaced = true;
            break;
          }
        }
        if (!replaced) state.events.push(updated);
        state.updatedAt = updated.updatedAt || new Date().toISOString();
        return updated;
      });
    }

    function deleteEvent(id) {
      return api.deleteEvent(id).then(function () {
        state.events = state.events.filter(function (e) {
          return e.id !== id;
        });
        state.updatedAt = new Date().toISOString();
        return true;
      });
    }

    function clearAllEvents() {
      return api.clearAllEvents().then(function (res) {
        var deleted = (res && res.deleted) || state.events.length;
        state.events = [];
        state.updatedAt = new Date().toISOString();
        return deleted;
      });
    }

    function applyLedgerEntries(entries) {
      return api.importLedger(entries).then(function (res) {
        if (res && res.state) applyServerState(res.state);
        return {
          added: (res && res.added) || 0,
          updated: (res && res.updated) || 0
        };
      });
    }

    function loadSampleData(year, month) {
      return api.loadSampleData(year, month).then(function (res) {
        if (res && res.state) applyServerState(res.state);
        return (res && res.count) || 0;
      });
    }

    // -----------------------------------------------------------------------
    // JSON 备份导出/解析/云端恢复 & 旧版 localStorage 检测
    // -----------------------------------------------------------------------
    function exportBackupJSON() {
      return JSON.stringify(
        {
          app: 'work-calendar',
          version: 1,
          exportedAt: new Date().toISOString(),
          categories: state.categories,
          events: state.events
        },
        null,
        2
      );
    }

    function parseBackupJSON(text) {
      var parsed;
      try {
        parsed = JSON.parse(text);
      } catch (e) {
        throw new Error('JSON 格式不正确，无法解析');
      }
      if (!parsed || !Array.isArray(parsed.categories) || !Array.isArray(parsed.events)) {
        throw new Error('备份文件缺少必要的 categories 或 events 数据');
      }
      var categories = parsed.categories.map(normalizeCategory);
      var events = parsed.events
        .map(normalizeEvent)
        .filter(function (e) {
          return Boolean(e.title && e.startDate);
        });
      return { categories: categories, events: events };
    }

    function restoreBackup(data, mode) {
      return api.restoreBackup(data, mode || 'replace').then(function (res) {
        if (res && res.state) applyServerState(res.state);
        return {
          categories: (res && res.categories) || state.categories.length,
          events: (res && res.events) || state.events.length
        };
      });
    }

    function detectLegacyLocalData() {
      if (typeof localStorage === 'undefined') return null;
      try {
        var raw = localStorage.getItem(getConfig().legacyStorageKey || 'work_calendar_v1');
        if (!raw) return null;
        var parsed = parseBackupJSON(raw);
        if (!parsed.events.length) return null;
        return parsed;
      } catch (err) {
        return null;
      }
    }

    function clearLegacyLocalData() {
      if (typeof localStorage === 'undefined') return;
      try {
        localStorage.removeItem(getConfig().legacyStorageKey || 'work_calendar_v1');
      } catch (err) {
        // ignore
      }
    }

    // -----------------------------------------------------------------------
    // ICS 导出（本地即时下载）
    // -----------------------------------------------------------------------
    function escapeICSText(str) {
      return String(str || '')
        .replace(/\\/g, '\\\\')
        .replace(/\r?\n/g, '\\n')
        .replace(/;/g, '\\;')
        .replace(/,/g, '\\,');
    }

    function exportICS() {
      var DU = getDateUtils();
      var lines = [
        'BEGIN:VCALENDAR',
        'VERSION:2.0',
        'PRODID:-//WorkCalendar//Cloud Schedule//CN',
        'CALSCALE:GREGORIAN',
        'METHOD:PUBLISH',
        'X-WR-CALNAME:工作日程',
        'X-WR-TIMEZONE:Asia/Shanghai'
      ];

      var stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d+Z$/, 'Z');

      for (var i = 0; i < state.events.length; i++) {
        var ev = state.events[i];
        var cat = getCategory(ev.categoryId);
        var endDate = ev.endDate && ev.endDate >= ev.startDate ? ev.endDate : ev.startDate;
        var summary = '[' + cat.name + '] ' + ev.title;
        var descParts = [];
        if (ev.ledgerMeta && ev.ledgerMeta.stage) {
          descParts.push('评估阶段：' + ev.ledgerMeta.stage);
        }
        if (ev.ledgerMeta && ev.ledgerMeta.level) {
          descParts.push('等级：' + ev.ledgerMeta.level);
        }
        if (ev.ledgerMeta && ev.ledgerMeta.role) {
          descParts.push('角色：' + ev.ledgerMeta.role);
        }
        if (ev.notes) {
          descParts.push(ev.notes);
        }

        lines.push('BEGIN:VEVENT');
        lines.push('UID:' + escapeICSText(ev.id) + '@work-calendar');
        lines.push('DTSTAMP:' + stamp);

        if (ev.allDay !== false) {
          var exclusiveEnd = DU.addDays(endDate, 1);
          lines.push('DTSTART;VALUE=DATE:' + ev.startDate.replace(/-/g, ''));
          lines.push('DTEND;VALUE=DATE:' + exclusiveEnd.replace(/-/g, ''));
        } else {
          var sTime = (ev.startTime || '09:00').replace(':', '') + '00';
          var eTime = (ev.endTime || '18:00').replace(':', '') + '00';
          lines.push('DTSTART;TZID=Asia/Shanghai:' + ev.startDate.replace(/-/g, '') + 'T' + sTime);
          lines.push('DTEND;TZID=Asia/Shanghai:' + endDate.replace(/-/g, '') + 'T' + eTime);
        }

        lines.push('SUMMARY:' + escapeICSText(summary));
        lines.push('CATEGORIES:' + escapeICSText(cat.name));
        if (descParts.length) {
          lines.push('DESCRIPTION:' + escapeICSText(descParts.join('\n')));
        }
        lines.push('END:VEVENT');
      }

      lines.push('END:VCALENDAR');
      return lines.join('\r\n');
    }

    return {
      loadFromServer: loadFromServer,
      refreshIfChanged: refreshIfChanged,
      resetMemoryState: resetMemoryState,
      getState: getState,
      getCategories: getCategories,
      getCategory: getCategory,
      getCategoryPriorityMap: getCategoryPriorityMap,
      countEventsByCategory: countEventsByCategory,
      addCategory: addCategory,
      updateCategory: updateCategory,
      deleteCategory: deleteCategory,
      moveCategory: moveCategory,
      getEvents: getEvents,
      getFilteredEvents: getFilteredEvents,
      searchEvents: searchEvents,
      getEventById: getEventById,
      addEvent: addEvent,
      updateEvent: updateEvent,
      deleteEvent: deleteEvent,
      clearAllEvents: clearAllEvents,
      getEventsOnDate: getEventsOnDate,
      getTopCategoryOnDate: getTopCategoryOnDate,
      getFilter: getFilter,
      hasActiveFilter: hasActiveFilter,
      setFilterQuery: setFilterQuery,
      toggleFilterCategory: toggleFilterCategory,
      clearFilters: clearFilters,
      applyLedgerEntries: applyLedgerEntries,
      exportBackupJSON: exportBackupJSON,
      parseBackupJSON: parseBackupJSON,
      restoreBackup: restoreBackup,
      detectLegacyLocalData: detectLegacyLocalData,
      clearLegacyLocalData: clearLegacyLocalData,
      exportICS: exportICS,
      loadSampleData: loadSampleData
    };
  }

  WorkCal.Storage = {
    createStore: createStore
  };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = WorkCal.Storage;
  }
})(typeof window !== 'undefined' ? window : globalThis);
