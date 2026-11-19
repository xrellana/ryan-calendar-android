/**
 * 日期运算、底色判定（BR-4）与多格式日期解析（BR-3）
 */
(function (root) {
  'use strict';

  var WorkCal = (root.WorkCal = root.WorkCal || {});

  var WEEKDAY_NAMES = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];

  function pad2(n) {
    return n < 10 ? '0' + n : String(n);
  }

  function formatDate(y, m, d) {
    return String(y) + '-' + pad2(m) + '-' + pad2(d);
  }

  function parseYMD(dateStr) {
    if (!dateStr || typeof dateStr !== 'string') return null;
    var m = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(dateStr.trim());
    if (!m) return null;
    var y = parseInt(m[1], 10);
    var mo = parseInt(m[2], 10);
    var d = parseInt(m[3], 10);
    if (!isValidCalendarDate(y, mo, d)) return null;
    return { year: y, month: mo, day: d };
  }

  function isValidCalendarDate(y, m, d) {
    if (!Number.isInteger(y) || !Number.isInteger(m) || !Number.isInteger(d)) return false;
    if (y < 1900 || y > 2100 || m < 1 || m > 12 || d < 1 || d > 31) return false;
    var dt = new Date(y, m - 1, d);
    return dt.getFullYear() === y && dt.getMonth() === m - 1 && dt.getDate() === d;
  }

  function toLocalDate(dateStr) {
    var p = parseYMD(dateStr);
    if (!p) return null;
    return new Date(p.year, p.month - 1, p.day);
  }

  function fromLocalDate(dt) {
    return formatDate(dt.getFullYear(), dt.getMonth() + 1, dt.getDate());
  }

  function todayStr() {
    return fromLocalDate(new Date());
  }

  function addDays(dateStr, offset) {
    var p = parseYMD(dateStr);
    if (!p) return dateStr;
    var dt = new Date(Date.UTC(p.year, p.month - 1, p.day + offset));
    return formatDate(dt.getUTCFullYear(), dt.getUTCMonth() + 1, dt.getUTCDate());
  }

  function diffDays(startStr, endStr) {
    var s = parseYMD(startStr);
    var e = parseYMD(endStr);
    if (!s || !e) return 0;
    var t1 = Date.UTC(s.year, s.month - 1, s.day);
    var t2 = Date.UTC(e.year, e.month - 1, e.day);
    return Math.round((t2 - t1) / 86400000);
  }

  // 周一=0 .. 周日=6
  function getWeekdayMon0(dateStr) {
    var p = parseYMD(dateStr);
    if (!p) return 0;
    var day = new Date(Date.UTC(p.year, p.month - 1, p.day)).getUTCDay();
    return (day + 6) % 7;
  }

  function getWeekdayLabel(dateStr) {
    return WEEKDAY_NAMES[getWeekdayMon0(dateStr)];
  }

  /**
   * 获取月视图 6x7=42 天的日期网格（周一为每周首日，FR-02）
   * @param {number} year
   * @param {number} month 1..12
   */
  function getMonthGridDates(year, month) {
    var firstDayStr = formatDate(year, month, 1);
    var startOffset = getWeekdayMon0(firstDayStr);
    var gridStart = addDays(firstDayStr, -startOffset);
    var weeks = [];
    for (var w = 0; w < 6; w++) {
      var row = [];
      for (var c = 0; c < 7; c++) {
        row.push(addDays(gridStart, w * 7 + c));
      }
      weeks.push(row);
    }
    return weeks;
  }

  /**
   * 获取包含指定日期的那一周（周一至周日）
   */
  function getWeekDates(anchorDateStr) {
    var wd = getWeekdayMon0(anchorDateStr);
    var monday = addDays(anchorDateStr, -wd);
    var list = [];
    for (var i = 0; i < 7; i++) {
      list.push(addDays(monday, i));
    }
    return list;
  }

  /**
   * 业务规则 BR-4：日期底色与角标判定顺序（三视图共用，FR-03）
   * 1. 法定节假日 -> 浅灰底 + 「休」
   * 2. 调休上班日 -> 白底 + 「班」
   * 3. 普通周末   -> 浅灰底 + 无角标
   * 4. 普通工作日 -> 白底 + 无角标
   */
  function getDayMeta(dateStr) {
    var Holidays = WorkCal.Holidays;
    var holidayName = Holidays ? Holidays.getHoliday(dateStr) : null;
    if (holidayName) {
      return {
        type: 'holiday',
        isGray: true,
        badge: '休',
        holidayName: holidayName
      };
    }

    var makeupName = Holidays ? Holidays.getMakeupWorkday(dateStr) : null;
    if (makeupName) {
      return {
        type: 'makeup',
        isGray: false,
        badge: '班',
        holidayName: makeupName
      };
    }

    var wd = getWeekdayMon0(dateStr);
    if (wd === 5 || wd === 6) {
      return {
        type: 'weekend',
        isGray: true,
        badge: '',
        holidayName: ''
      };
    }

    return {
      type: 'workday',
      isGray: false,
      badge: '',
      holidayName: ''
    };
  }

  /**
   * 业务规则 BR-3：单日期解析
   * 依次尝试：标准日期格式 -> 中文年月日 -> 8位紧凑数字 -> Excel序列号
   */
  function parseSingleDate(raw, options) {
    if (raw === null || raw === undefined) return null;
    if (raw instanceof Date && !isNaN(raw.getTime())) {
      return fromLocalDate(raw);
    }

    // 数字类型（Excel 日期序列号）
    if (typeof raw === 'number' && isFinite(raw)) {
      return parseExcelSerial(raw, options && options.date1904);
    }

    var str = String(raw).trim();
    if (!str) return null;

    // 1. 标准日期格式: YYYY-MM-DD, YYYY/M/D, YYYY.M.D (允许尾随时间)
    var mStd = /^(\d{4})[-\/.](\d{1,2})[-\/.](\d{1,2})(?:[T\s]+\d{1,2}:\d{2}(?::\d{2})?)?$/.exec(str);
    if (mStd) {
      var y1 = parseInt(mStd[1], 10);
      var mo1 = parseInt(mStd[2], 10);
      var d1 = parseInt(mStd[3], 10);
      return isValidCalendarDate(y1, mo1, d1) ? formatDate(y1, mo1, d1) : null;
    }

    // 2. 中文年月日: 2025年8月6日 / 2025年08月06号
    var mCn = /^(\d{4})\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*[日号]?$/.exec(str);
    if (mCn) {
      var y2 = parseInt(mCn[1], 10);
      var mo2 = parseInt(mCn[2], 10);
      var d2 = parseInt(mCn[3], 10);
      return isValidCalendarDate(y2, mo2, d2) ? formatDate(y2, mo2, d2) : null;
    }

    // 3. 8位紧凑数字: 20250806
    var mCompact = /^(\d{4})(\d{2})(\d{2})$/.exec(str);
    if (mCompact) {
      var y3 = parseInt(mCompact[1], 10);
      var mo3 = parseInt(mCompact[2], 10);
      var d3 = parseInt(mCompact[3], 10);
      return isValidCalendarDate(y3, mo3, d3) ? formatDate(y3, mo3, d3) : null;
    }

    // 4. 字符串形式的 Excel 序列号
    if (/^\d{5}(?:\.\d+)?$/.test(str)) {
      var num = parseFloat(str);
      return parseExcelSerial(num, options && options.date1904);
    }

    return null;
  }

  function parseExcelSerial(serial, is1904) {
    if (serial < 1 || serial > 100000) return null;
    var days = Math.floor(serial);
    var baseMs;
    if (is1904) {
      baseMs = Date.UTC(1904, 0, 1);
      var dt1904 = new Date(baseMs + days * 86400000);
      return formatDate(dt1904.getUTCFullYear(), dt1904.getUTCMonth() + 1, dt1904.getUTCDate());
    }
    // Excel 1900 系统将 1900-02-29 误算为闰日，故 >= 60 需减 2 天；1899-12-31 为 day 1
    var adjusted = days > 59 ? days - 1 : days;
    baseMs = Date.UTC(1899, 11, 31);
    var dt = new Date(baseMs + adjusted * 86400000);
    var y = dt.getUTCFullYear();
    var m = dt.getUTCMonth() + 1;
    var d = dt.getUTCDate();
    return isValidCalendarDate(y, m, d) ? formatDate(y, m, d) : null;
  }

  /**
   * 尝试拆分形如 "2025-8-6至2025-8-8"、"2025.8.6~8.8"、"2025年8月6日-8日" 的区间写法（BR-3 第5步）
   */
  function splitRangeString(raw, options) {
    if (raw === null || raw === undefined) return null;
    var str = String(raw).trim();
    if (!str) return null;

    // 先尝试按明确区间符拆分（不含普通 - 避免误切 YYYY-MM-DD）
    var sepMatch = str.match(/^(.+?)\s*(?:至|到|~|～|—|–)\s*(.+)$/);
    // 若未命中，再匹配 "完整日期 - 完整日期或M.D或D"
    if (!sepMatch) {
      sepMatch = str.match(/^(\d{4}[-\/.年]\d{1,2}[-\/.月]\d{1,2}[日号]?)\s*-\s*(.+)$/);
    }
    if (!sepMatch) return null;

    var leftStr = sepMatch[1].trim();
    var rightStr = sepMatch[2].trim();
    var start = parseSingleDate(leftStr, options);
    if (!start) return null;

    var end = parseSingleDate(rightStr, options);
    if (!end) {
      var sp = parseYMD(start);
      // 简写月日：8.8 / 8/8 / 8-8 / 8月8日
      var mMD = /^(\d{1,2})[-\/.月](\d{1,2})[日号]?$/.exec(rightStr);
      if (mMD) {
        var mo = parseInt(mMD[1], 10);
        var dy = parseInt(mMD[2], 10);
        if (isValidCalendarDate(sp.year, mo, dy)) {
          end = formatDate(sp.year, mo, dy);
        }
      } else {
        // 简写日：8日 / 8号 / 8
        var mD = /^(\d{1,2})[日号]?$/.exec(rightStr);
        if (mD) {
          var dy2 = parseInt(mD[1], 10);
          if (isValidCalendarDate(sp.year, sp.month, dy2)) {
            end = formatDate(sp.year, sp.month, dy2);
          }
        }
      }
    }

    if (!end) return null;
    if (end < start) return { startDate: end, endDate: start };
    return { startDate: start, endDate: end };
  }

  function isEmptyOrInvalidMarker(val) {
    if (val === null || val === undefined) return true;
    var s = String(val).trim();
    if (!s) return true;
    return /^[/\-—–~～]+$/.test(s) || /^(无|暂无|待定|未定|N\/A|null|none)$/i.test(s);
  }

  /**
   * 综合解析起止日期（FR-10 / BR-3）
   */
  function parseDateRange(rawStart, rawEnd, options) {
    // 若开始时间字段本身写了区间（且结束时间为空或为 "/"）
    if (typeof rawStart === 'string') {
      var startAsRange = splitRangeString(rawStart, options);
      if (startAsRange && isEmptyOrInvalidMarker(rawEnd)) {
        return {
          ok: true,
          startDate: startAsRange.startDate,
          endDate: startAsRange.endDate
        };
      }
    }

    if (isEmptyOrInvalidMarker(rawStart)) {
      return { ok: false, reason: '开始时间为空或无效' };
    }
    if (isEmptyOrInvalidMarker(rawEnd)) {
      return { ok: false, reason: '结束时间为空或为“/”' };
    }

    var start = parseSingleDate(rawStart, options);
    if (!start) {
      var sr = splitRangeString(rawStart, options);
      if (sr) start = sr.startDate;
    }
    if (!start) {
      return { ok: false, reason: '开始时间格式无法识别（' + String(rawStart).trim() + '）' };
    }

    var end = parseSingleDate(rawEnd, options);
    if (!end) {
      var er = splitRangeString(rawEnd, options);
      if (er) end = er.endDate;
    }
    if (!end) {
      return { ok: false, reason: '结束时间格式无法识别（' + String(rawEnd).trim() + '）' };
    }

    if (end < start) {
      return {
        ok: false,
        reason: '结束时间（' + end + '）早于开始时间（' + start + '）'
      };
    }

    return { ok: true, startDate: start, endDate: end };
  }

  function formatDisplayDateCN(dateStr) {
    var p = parseYMD(dateStr);
    if (!p) return dateStr;
    return p.year + '年' + p.month + '月' + p.day + '日';
  }

  function formatShortMD(dateStr) {
    var p = parseYMD(dateStr);
    if (!p) return dateStr;
    return p.month + '月' + p.day + '日';
  }

  WorkCal.DateUtils = {
    WEEKDAY_NAMES: WEEKDAY_NAMES,
    pad2: pad2,
    formatDate: formatDate,
    parseYMD: parseYMD,
    isValidCalendarDate: isValidCalendarDate,
    toLocalDate: toLocalDate,
    fromLocalDate: fromLocalDate,
    todayStr: todayStr,
    addDays: addDays,
    diffDays: diffDays,
    getWeekdayMon0: getWeekdayMon0,
    getWeekdayLabel: getWeekdayLabel,
    getMonthGridDates: getMonthGridDates,
    getWeekDates: getWeekDates,
    getDayMeta: getDayMeta,
    parseSingleDate: parseSingleDate,
    splitRangeString: splitRangeString,
    parseDateRange: parseDateRange,
    isEmptyOrInvalidMarker: isEmptyOrInvalidMarker,
    formatDisplayDateCN: formatDisplayDateCN,
    formatShortMD: formatShortMD
  };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = WorkCal.DateUtils;
  }
})(typeof window !== 'undefined' ? window : globalThis);
