/**
 * 中国法定节假日与调休上班日数据（独立文件，NFR-6）
 * 已内置：2024 - 2027 年国务院办公厅放假调休安排
 *
 * 【后续年度追加方法】
 * 每年底国务院办公厅发布下年度节假日安排通知后，只需在 YEAR_PLANS 中新增对应年份键值：
 *   2028: {
 *     holidays: { '2028-01-01': '元旦', ... }, // 或用 expandRanges 生成后填入
 *     workdays: { '2028-01-22': '春节调休上班', ... }
 *   }
 * 同时更新 coveredRange.maxYear 即可，无需改动任何业务逻辑。
 */
(function (root) {
  'use strict';

  var WorkCal = (root.WorkCal = root.WorkCal || {});

  function expandRange(startStr, endStr, name, target) {
    var sParts = startStr.split('-');
    var eParts = endStr.split('-');
    var cur = new Date(Date.UTC(+sParts[0], +sParts[1] - 1, +sParts[2]));
    var end = new Date(Date.UTC(+eParts[0], +eParts[1] - 1, +eParts[2]));
    while (cur.getTime() <= end.getTime()) {
      var y = cur.getUTCFullYear();
      var m = String(cur.getUTCMonth() + 1).padStart(2, '0');
      var d = String(cur.getUTCDate()).padStart(2, '0');
      target[y + '-' + m + '-' + d] = name;
      cur.setUTCDate(cur.getUTCDate() + 1);
    }
  }

  function buildYearData(holidayRanges, workdayEntries) {
    var holidays = {};
    var workdays = {};
    for (var i = 0; i < holidayRanges.length; i++) {
      var r = holidayRanges[i];
      expandRange(r[0], r[1], r[2], holidays);
    }
    for (var j = 0; j < workdayEntries.length; j++) {
      var w = workdayEntries[j];
      workdays[w[0]] = w[1];
    }
    return { holidays: holidays, workdays: workdays };
  }

  var YEAR_PLANS = {
    // 国办发明电〔2023〕7号
    2024: buildYearData(
      [
        ['2024-01-01', '2024-01-01', '元旦'],
        ['2024-02-10', '2024-02-17', '春节'],
        ['2024-04-04', '2024-04-06', '清明节'],
        ['2024-05-01', '2024-05-05', '劳动节'],
        ['2024-06-08', '2024-06-10', '端午节'],
        ['2024-09-15', '2024-09-17', '中秋节'],
        ['2024-10-01', '2024-10-07', '国庆节']
      ],
      [
        ['2024-02-04', '春节调休上班'],
        ['2024-02-18', '春节调休上班'],
        ['2024-04-07', '清明节调休上班'],
        ['2024-04-28', '劳动节调休上班'],
        ['2024-05-11', '劳动节调休上班'],
        ['2024-09-14', '中秋节调休上班'],
        ['2024-09-29', '国庆节调休上班'],
        ['2024-10-12', '国庆节调休上班']
      ]
    ),

    // 国办发明电〔2024〕12号
    2025: buildYearData(
      [
        ['2025-01-01', '2025-01-01', '元旦'],
        ['2025-01-28', '2025-02-04', '春节'],
        ['2025-04-04', '2025-04-06', '清明节'],
        ['2025-05-01', '2025-05-05', '劳动节'],
        ['2025-05-31', '2025-06-02', '端午节'],
        ['2025-10-01', '2025-10-08', '国庆节/中秋节']
      ],
      [
        ['2025-01-26', '春节调休上班'],
        ['2025-02-08', '春节调休上班'],
        ['2025-04-27', '劳动节调休上班'],
        ['2025-09-28', '国庆节调休上班'],
        ['2025-10-11', '国庆节调休上班']
      ]
    ),

    // 国办发明电〔2025〕7号
    2026: buildYearData(
      [
        ['2026-01-01', '2026-01-03', '元旦'],
        ['2026-02-15', '2026-02-23', '春节'],
        ['2026-04-04', '2026-04-06', '清明节'],
        ['2026-05-01', '2026-05-05', '劳动节'],
        ['2026-06-19', '2026-06-21', '端午节'],
        ['2026-09-25', '2026-09-27', '中秋节'],
        ['2026-10-01', '2026-10-07', '国庆节']
      ],
      [
        ['2026-01-04', '元旦调休上班'],
        ['2026-02-14', '春节调休上班'],
        ['2026-02-28', '春节调休上班'],
        ['2026-05-09', '劳动节调休上班'],
        ['2026-09-20', '国庆节调休上班'],
        ['2026-10-10', '国庆节调休上班']
      ]
    ),

    // 国办发明电〔2026〕10号
    2027: buildYearData(
      [
        ['2027-01-01', '2027-01-03', '元旦'],
        ['2027-02-05', '2027-02-13', '春节'],
        ['2027-04-03', '2027-04-05', '清明节'],
        ['2027-05-01', '2027-05-05', '劳动节'],
        ['2027-06-09', '2027-06-09', '端午节'],
        ['2027-09-15', '2027-09-15', '中秋节'],
        ['2027-10-01', '2027-10-07', '国庆节']
      ],
      [
        ['2027-01-31', '春节调休上班'],
        ['2027-02-14', '春节调休上班'],
        ['2027-05-08', '劳动节调休上班'],
        ['2027-09-26', '国庆节调休上班'],
        ['2027-10-09', '国庆节调休上班']
      ]
    )
  };

  var ALL_HOLIDAYS = {};
  var ALL_WORKDAYS = {};
  var COVERED_YEARS = {};

  function rebuildIndexes() {
    ALL_HOLIDAYS = {};
    ALL_WORKDAYS = {};
    COVERED_YEARS = {};
    var years = Object.keys(YEAR_PLANS)
      .map(function (y) {
        return parseInt(y, 10);
      })
      .filter(function (y) {
        return !isNaN(y);
      })
      .sort(function (a, b) {
        return a - b;
      });

    years.forEach(function (yr) {
      var plan = YEAR_PLANS[yr];
      COVERED_YEARS[yr] = true;
      if (plan && plan.holidays) Object.assign(ALL_HOLIDAYS, plan.holidays);
      if (plan && plan.workdays) Object.assign(ALL_WORKDAYS, plan.workdays);
    });

    if (years.length) {
      Holidays.coveredRange = {
        minYear: years[0],
        maxYear: years[years.length - 1]
      };
    }
  }

  var Holidays = {
    YEAR_PLANS: YEAR_PLANS,
    coveredRange: { minYear: 2024, maxYear: 2027 },

    isYearCovered: function (year) {
      return Boolean(COVERED_YEARS[year]);
    },

    getHoliday: function (dateStr) {
      return ALL_HOLIDAYS[dateStr] || null;
    },

    getMakeupWorkday: function (dateStr) {
      return ALL_WORKDAYS[dateStr] || null;
    },

    isMakeupWorkday: function (dateStr) {
      return Boolean(ALL_WORKDAYS[dateStr]);
    },

    /**
     * 使用服务端数据库下发的节假日数据刷新前端索引（保留内置数据兜底）
     * payload: { years: number[], entries: Array<{ date, year, type, name }> }
     */
    loadFromServer: function (payload) {
      if (!payload || !Array.isArray(payload.entries)) return;
      var serverYears = {};
      if (Array.isArray(payload.years)) {
        payload.years.forEach(function (y) {
          var yn = parseInt(y, 10);
          if (!isNaN(yn)) {
            serverYears[yn] = { holidays: {}, workdays: {} };
          }
        });
      }
      payload.entries.forEach(function (item) {
        if (!item || !item.date) return;
        var yn = parseInt(item.year || item.date.slice(0, 4), 10);
        if (isNaN(yn)) return;
        if (!serverYears[yn]) {
          serverYears[yn] = { holidays: {}, workdays: {} };
        }
        if (item.type === 'holiday') {
          serverYears[yn].holidays[item.date] = item.name || '法定节假日';
        } else if (item.type === 'workday') {
          serverYears[yn].workdays[item.date] = item.name || '调休上班';
        }
      });

      Object.keys(serverYears).forEach(function (yr) {
        YEAR_PLANS[yr] = serverYears[yr];
      });
      rebuildIndexes();
    }
  };

  rebuildIndexes();

  WorkCal.Holidays = Holidays;

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = Holidays;
  }
})(typeof window !== 'undefined' ? window : globalThis);
