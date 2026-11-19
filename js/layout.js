/**
 * 月视图跨天行程整体切段与泳道排版引擎（FR-06 / BR-5 / AC-5 / AC-6 / AC-7）
 */
(function (root) {
  'use strict';

  var WorkCal = (root.WorkCal = root.WorkCal || {});

  function isMultiDay(ev) {
    return Boolean(ev && ev.endDate && ev.endDate > ev.startDate);
  }

  function getEventEndDate(ev) {
    return ev.endDate && ev.endDate >= ev.startDate ? ev.endDate : ev.startDate;
  }

  function compareEvents(a, b, priorityMap) {
    var pa = Object.prototype.hasOwnProperty.call(priorityMap, a.categoryId)
      ? priorityMap[a.categoryId]
      : 9999;
    var pb = Object.prototype.hasOwnProperty.call(priorityMap, b.categoryId)
      ? priorityMap[b.categoryId]
      : 9999;
    if (pa !== pb) return pa - pb;

    // 全天行程排在非全天前面，其余按开始时间、创建时间
    var aAll = a.allDay !== false ? 0 : 1;
    var bAll = b.allDay !== false ? 0 : 1;
    if (aAll !== bAll) return aAll - bAll;

    if (a.startTime !== b.startTime) {
      return (a.startTime || '').localeCompare(b.startTime || '');
    }
    return String(a.id || '').localeCompare(String(b.id || ''));
  }

  /**
   * 为月视图的某一周（7 天）计算跨天片段、泳道高度与每日单日日程展示配额
   * @param {string[]} weekDates 长度 7 的 YYYY-MM-DD 数组（周一..周日）
   * @param {Array} allEvents 全量日程
   * @param {Object} priorityMap categoryId -> priorityIndex (0 最高)
   * @param {number} activeYear 当前浏览年份
   * @param {number} activeMonth 当前浏览月份 (1..12)
   * @param {boolean} isMobile 是否手机窄屏
   */
  function layoutWeek(weekDates, allEvents, priorityMap, activeYear, activeMonth, isMobile) {
    var weekStart = weekDates[0];
    var weekEnd = weekDates[6];
    var activePrefix =
      String(activeYear) + '-' + (activeMonth < 10 ? '0' + activeMonth : String(activeMonth)) + '-';

    var multiSegments = [];

    for (var i = 0; i < allEvents.length; i++) {
      var ev = allEvents[i];
      if (!isMultiDay(ev)) continue;
      var evEnd = getEventEndDate(ev);
      if (evEnd < weekStart || ev.startDate > weekEnd) continue;

      var segStartDate = ev.startDate > weekStart ? ev.startDate : weekStart;
      var segEndDate = evEnd < weekEnd ? evEnd : weekEnd;

      var startCol = weekDates.indexOf(segStartDate);
      var endCol = weekDates.indexOf(segEndDate);
      if (startCol < 0 || endCol < 0) continue;

      // 整段行程完全不落在当前浏览月份时整体淡化，仅作位置提示（FR-06）
      var hasAnyInMonth = false;
      for (var c = startCol; c <= endCol; c++) {
        if (weekDates[c].indexOf(activePrefix) === 0) {
          hasAnyInMonth = true;
          break;
        }
      }

      multiSegments.push({
        event: ev,
        startCol: startCol,
        endCol: endCol,
        colSpan: endCol - startCol + 1,
        cutLeft: ev.startDate < weekStart,
        cutRight: evEnd > weekEnd,
        isOutOfMonth: !hasAnyInMonth,
        lane: 0
      });
    }

    // 排序：起始列靠前 > 跨度更长 > 类别优先级更高 > ID
    multiSegments.sort(function (a, b) {
      if (a.startCol !== b.startCol) return a.startCol - b.startCol;
      if (a.colSpan !== b.colSpan) return b.colSpan - a.colSpan;
      return compareEvents(a.event, b.event, priorityMap);
    });

    // 贪心分配泳道（互不遮挡，AC-7）
    var laneOccupancy = []; // laneOccupancy[laneIdx] = 数组 [0..6] boolean
    var maxLane = -1;

    for (var s = 0; s < multiSegments.length; s++) {
      var seg = multiSegments[s];
      var assigned = -1;
      for (var l = 0; l < laneOccupancy.length; l++) {
        var conflict = false;
        for (var col = seg.startCol; col <= seg.endCol; col++) {
          if (laneOccupancy[l][col]) {
            conflict = true;
            break;
          }
        }
        if (!conflict) {
          assigned = l;
          break;
        }
      }
      if (assigned === -1) {
        assigned = laneOccupancy.length;
        laneOccupancy.push([false, false, false, false, false, false, false]);
      }
      for (var col2 = seg.startCol; col2 <= seg.endCol; col2++) {
        laneOccupancy[assigned][col2] = true;
      }
      seg.lane = assigned;
      if (assigned > maxLane) maxLane = assigned;
    }

    var laneCount = maxLane + 1;
    var laneHeight;
    var laneGap;
    if (isMobile) {
      if (laneCount <= 3) {
        laneHeight = 18;
        laneGap = 2;
      } else {
        laneHeight = Math.max(13, Math.floor(60 / laneCount));
        laneGap = 1;
      }
    } else {
      if (laneCount <= 3) {
        laneHeight = 24;
        laneGap = 3;
      } else {
        laneHeight = Math.max(16, Math.floor(84 / laneCount));
        laneGap = 2;
      }
    }

    // 每日最高占用的泳道深度（1-based），用于预留空间并扣减下方单日日程可见条数（FR-06）
    var dayReservedLanes = [0, 0, 0, 0, 0, 0, 0];
    for (var k = 0; k < multiSegments.length; k++) {
      var sg = multiSegments[k];
      for (var c2 = sg.startCol; c2 <= sg.endCol; c2++) {
        if (sg.lane + 1 > dayReservedLanes[c2]) {
          dayReservedLanes[c2] = sg.lane + 1;
        }
      }
    }

    var baseMaxSingle = isMobile ? 1 : 2;
    var daySingleEvents = [];
    var dayVisibleSingle = [];
    var dayHiddenCount = [];
    var dayTotalCount = [];

    for (var colIdx = 0; colIdx < 7; colIdx++) {
      var dateStr = weekDates[colIdx];
      var singleList = [];
      var multiCountOnDay = 0;

      for (var eIdx = 0; eIdx < allEvents.length; eIdx++) {
        var item = allEvents[eIdx];
        var itemEnd = getEventEndDate(item);
        if (item.startDate <= dateStr && itemEnd >= dateStr) {
          if (isMultiDay(item)) {
            multiCountOnDay++;
          } else {
            singleList.push(item);
          }
        }
      }

      singleList.sort(function (a, b) {
        return compareEvents(a, b, priorityMap);
      });

      var reserved = dayReservedLanes[colIdx];
      var allowedSingle = Math.max(0, baseMaxSingle - reserved);
      var visible = singleList.slice(0, allowedSingle);
      var hidden = Math.max(0, singleList.length - visible.length);

      daySingleEvents.push(singleList);
      dayVisibleSingle.push(visible);
      dayHiddenCount.push(hidden);
      dayTotalCount.push(multiCountOnDay + singleList.length);
    }

    return {
      segments: multiSegments,
      laneCount: laneCount,
      laneHeight: laneHeight,
      laneGap: laneGap,
      dayReservedLanes: dayReservedLanes,
      daySingleEvents: daySingleEvents,
      dayVisibleSingle: dayVisibleSingle,
      dayHiddenCount: dayHiddenCount,
      dayTotalCount: dayTotalCount
    };
  }

  WorkCal.Layout = {
    isMultiDay: isMultiDay,
    getEventEndDate: getEventEndDate,
    compareEvents: compareEvents,
    layoutWeek: layoutWeek
  };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = WorkCal.Layout;
  }
})(typeof window !== 'undefined' ? window : globalThis);
