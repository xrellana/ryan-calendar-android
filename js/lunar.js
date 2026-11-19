/**
 * 零依赖中国农历、二十四节气与传统节日计算（1900 - 2100）
 * 严格使用 UTC 与北京时间（UTC+8），消除跨时区/夏令时日期偏移
 */
(function (root) {
  'use strict';

  var WorkCal = (root.WorkCal = root.WorkCal || {});

  // 1900 - 2100 年农历压缩数据表（201 项）
  var LUNAR_INFO = [
    0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
    0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
    0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
    0x06566, 0x0d4a0, 0x0ea50, 0x16a95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
    0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
    0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0,
    0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
    0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6,
    0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
    0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0,
    0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
    0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
    0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
    0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
    0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
    0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0,
    0x092e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4,
    0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0,
    0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160,
    0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252,
    0x0d520
  ];

  var SOLAR_TERM_NAMES = [
    '小寒', '大寒', '立春', '雨水', '惊蛰', '春分',
    '清明', '谷雨', '立夏', '小满', '芒种', '夏至',
    '小暑', '大暑', '立秋', '处暑', '白露', '秋分',
    '寒露', '霜降', '立冬', '小雪', '大雪', '冬至'
  ];

  var S_TERM_INFO = [
    0, 21208, 42467, 63836, 85337, 107014,
    128867, 150921, 173149, 195551, 218072, 240693,
    263343, 285989, 308563, 331033, 353350, 375494,
    397447, 419210, 440795, 462224, 483532, 504758
  ];

  var MONTH_CN = ['', '正', '二', '三', '四', '五', '六', '七', '八', '九', '十', '冬', '腊'];
  var DAY_PREFIX = ['初', '十', '廿', '三'];
  var DAY_NUM = ['日', '一', '二', '三', '四', '五', '六', '七', '八', '九', '十'];

  var LUNAR_FESTIVALS = {
    '1-1': '春节',
    '1-15': '元宵',
    '5-5': '端午',
    '7-7': '七夕',
    '8-15': '中秋',
    '9-9': '重阳',
    '12-8': '腊八',
    '12-23': '小年'
  };

  var SOLAR_FESTIVALS = {
    '1-1': '元旦',
    '5-1': '劳动节',
    '10-1': '国庆节'
  };

  function leapMonth(y) {
    return LUNAR_INFO[y - 1900] & 0xf;
  }

  function leapDays(y) {
    if (leapMonth(y)) {
      return (LUNAR_INFO[y - 1900] & 0x10000) ? 30 : 29;
    }
    return 0;
  }

  function monthDays(y, m) {
    return (LUNAR_INFO[y - 1900] & (0x10000 >> m)) ? 30 : 29;
  }

  function lYearDays(y) {
    var sum = 348;
    var info = LUNAR_INFO[y - 1900];
    for (var i = 0x8000; i > 0x8; i >>= 1) {
      sum += (info & i) ? 1 : 0;
    }
    return sum + leapDays(y);
  }

  function solarTermDay(y, n) {
    // 1900-01-06 02:05:00 UTC -> 北京时间 +8h
    var baseUtc = Date.UTC(1900, 0, 6, 2, 5);
    var ms = 31556925974.7 * (y - 1900) + S_TERM_INFO[n] * 60000;
    var dt = new Date(baseUtc + ms + 8 * 3600000);
    return {
      month: dt.getUTCMonth() + 1,
      day: dt.getUTCDate(),
      name: SOLAR_TERM_NAMES[n]
    };
  }

  var solarTermCache = {};
  function getSolarTermMap(y) {
    if (solarTermCache[y]) return solarTermCache[y];
    var map = {};
    for (var i = 0; i < 24; i++) {
      var st = solarTermDay(y, i);
      map[st.month + '-' + st.day] = st.name;
    }
    solarTermCache[y] = map;
    return map;
  }

  function formatLunarDay(d) {
    if (d === 10) return '初十';
    if (d === 20) return '二十';
    if (d === 30) return '三十';
    return DAY_PREFIX[Math.floor(d / 10)] + DAY_NUM[d % 10];
  }

  function solarToLunarRaw(y, m, d) {
    if (y < 1900 || y > 2100) {
      return null;
    }
    var offset = Math.floor((Date.UTC(y, m - 1, d) - Date.UTC(1900, 0, 31)) / 86400000);
    if (offset < 0) return null;

    var year = 1900;
    var temp = 0;
    for (; year < 2101 && offset > 0; year++) {
      temp = lYearDays(year);
      offset -= temp;
    }
    if (offset < 0) {
      offset += temp;
      year--;
    }

    var leap = leapMonth(year);
    var isLeap = false;
    var month = 1;
    for (; month < 13 && offset > 0; month++) {
      if (leap > 0 && month === leap + 1 && !isLeap) {
        --month;
        isLeap = true;
        temp = leapDays(year);
      } else {
        temp = monthDays(year, month);
      }
      if (isLeap && month === leap + 1) {
        isLeap = false;
      }
      offset -= temp;
    }

    if (offset === 0 && leap > 0 && month === leap + 1) {
      if (isLeap) {
        isLeap = false;
      } else {
        isLeap = true;
        --month;
      }
    }
    if (offset < 0) {
      offset += temp;
      --month;
    }

    return {
      lunarYear: year,
      lunarMonth: month,
      lunarDay: offset + 1,
      isLeap: isLeap
    };
  }

  function getLunarInfo(year, month, day) {
    var raw = solarToLunarRaw(year, month, day);
    if (!raw) {
      return {
        lunarMonthName: '',
        lunarDayName: '',
        solarTerm: '',
        festival: '',
        label: '',
        isHighlight: false
      };
    }

    var monthName = (raw.isLeap ? '闰' : '') + MONTH_CN[raw.lunarMonth] + '月';
    var dayName = formatLunarDay(raw.lunarDay);
    var termMap = getSolarTermMap(year);
    var solarTerm = termMap[month + '-' + day] || '';

    // 判断是否为除夕：次日为正月初一
    var festival = '';
    var nextUtc = new Date(Date.UTC(year, month - 1, day + 1));
    var nextRaw = solarToLunarRaw(
      nextUtc.getUTCFullYear(),
      nextUtc.getUTCMonth() + 1,
      nextUtc.getUTCDate()
    );
    if (nextRaw && !nextRaw.isLeap && nextRaw.lunarMonth === 1 && nextRaw.lunarDay === 1) {
      festival = '除夕';
    } else if (!raw.isLeap) {
      festival = LUNAR_FESTIVALS[raw.lunarMonth + '-' + raw.lunarDay] || '';
    }

    var solarFest = SOLAR_FESTIVALS[month + '-' + day] || '';
    // 春节、除夕、端午、中秋优先级高于公历同日，其余正常显示
    var primaryFest = festival || solarFest;

    // 格子内优先展示：传统/法定节日 > 节气（含清明）> 农历月首 > 农历日
    var label = '';
    var isHighlight = false;
    if (primaryFest) {
      label = primaryFest;
      isHighlight = true;
    } else if (solarTerm) {
      label = solarTerm;
      isHighlight = true;
    } else if (raw.lunarDay === 1) {
      label = monthName;
    } else {
      label = dayName;
    }

    return {
      lunarYear: raw.lunarYear,
      lunarMonth: raw.lunarMonth,
      lunarDay: raw.lunarDay,
      isLeap: raw.isLeap,
      lunarMonthName: monthName,
      lunarDayName: dayName,
      solarTerm: solarTerm,
      festival: primaryFest,
      label: label,
      isHighlight: isHighlight
    };
  }

  WorkCal.Lunar = {
    getLunarInfo: getLunarInfo
  };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = WorkCal.Lunar;
  }
})(typeof window !== 'undefined' ? window : globalThis);
