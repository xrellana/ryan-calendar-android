/**
 * 日历三视图（月 / 周 / 年）、图例栏与右侧辅助面板渲染（FR-02 / FR-03 / FR-04 / FR-06 / UI-1 / UI-2）
 */
(function (root) {
  'use strict';

  var WorkCal = (root.WorkCal = root.WorkCal || {});

  function escapeHtml(str) {
    return String(str === undefined || str === null ? '' : str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function isMobileViewport() {
    return typeof window !== 'undefined' && window.innerWidth <= 640;
  }

  function getPeriodTitle(state) {
    var DU = WorkCal.DateUtils;
    if (state.view === 'year') {
      return state.anchorYear + '年';
    }
    if (state.view === 'month') {
      return state.anchorYear + '年' + state.anchorMonth + '月';
    }
    var weekDates = DU.getWeekDates(state.anchorDate);
    var s = DU.parseYMD(weekDates[0]);
    var e = DU.parseYMD(weekDates[6]);
    if (s.year === e.year && s.month === e.month) {
      return s.year + '年' + s.month + '月' + s.day + '日 - ' + e.day + '日';
    }
    if (s.year === e.year) {
      return s.year + '年' + s.month + '月' + s.day + '日 - ' + e.month + '月' + e.day + '日';
    }
    return (
      s.year + '年' + s.month + '月' + s.day + '日 - ' + e.year + '年' + e.month + '月' + e.day + '日'
    );
  }

  function renderHeader(state) {
    var titleText = getPeriodTitle(state);
    var titleEl = document.getElementById('period-title');
    if (titleEl) titleEl.textContent = titleText;

    var printTitleEl = document.getElementById('print-period-title');
    if (printTitleEl) printTitleEl.textContent = titleText;

    var printTsEl = document.getElementById('print-timestamp');
    if (printTsEl) {
      var now = new Date();
      printTsEl.textContent =
        '打印日期：' +
        now.getFullYear() +
        '-' +
        String(now.getMonth() + 1).padStart(2, '0') +
        '-' +
        String(now.getDate()).padStart(2, '0');
    }

    var tabs = document.querySelectorAll('.view-tab');
    for (var i = 0; i < tabs.length; i++) {
      var tab = tabs[i];
      var active = tab.getAttribute('data-view') === state.view;
      tab.classList.toggle('active', active);
      tab.setAttribute('aria-selected', active ? 'true' : 'false');
    }

    // 检查节假日数据覆盖提示（FR-12）
    var banner = document.getElementById('year-coverage-banner');
    if (banner) {
      var checkYear = state.view === 'week'
        ? WorkCal.DateUtils.parseYMD(state.anchorDate).year
        : state.anchorYear;
      if (!WorkCal.Holidays.isYearCovered(checkYear)) {
        var r = WorkCal.Holidays.coveredRange;
        banner.textContent =
          '提示：当前内置法定节假日与调休数据覆盖 ' +
          r.minYear +
          '–' +
          r.maxYear +
          ' 年，' +
          checkYear +
          ' 年日期底色暂仅按周末判定。';
        banner.classList.remove('hidden');
      } else {
        banner.classList.add('hidden');
      }
    }
  }

  function renderLegend(store) {
    var container = document.getElementById('legend-bar');
    if (!container) return;
    var cats = store.getCategories();
    var flt = store.getFilter ? store.getFilter() : { query: '', categoryIds: [] };
    var hasCatFilter = flt.categoryIds && flt.categoryIds.length > 0;

    var catHtml = cats
      .map(function (c) {
        var isSelected = hasCatFilter && flt.categoryIds.indexOf(c.id) !== -1;
        var cls = 'legend-item legend-filter-chip';
        if (isSelected) cls += ' is-active';
        else if (hasCatFilter) cls += ' is-dimmed';
        return (
          '<button type="button" class="' +
          cls +
          '" data-action="toggle-filter-cat" data-cat-id="' +
          escapeHtml(c.id) +
          '" aria-pressed="' +
          (isSelected ? 'true' : 'false') +
          '" title="点击筛选该类别（支持多选）；顺序越靠前同日优先级越高">' +
          '<span class="legend-swatch" style="background:' +
          escapeHtml(c.color) +
          ';"></span>' +
          '<span>' +
          escapeHtml(c.name) +
          '</span>' +
          '</button>'
        );
      })
      .join('');

    var dayTypeHtml =
      '<span class="legend-daytype-group">' +
      '<span class="legend-item"><span class="legend-daybox is-workday"></span>工作日/调休班</span>' +
      '<span class="legend-item"><span class="legend-daybox is-rest"></span>周末/节假日</span>' +
      '</span>';

    container.innerHTML =
      '<div class="legend-items">' +
      (catHtml || '<span class="form-hint">暂无类别，请点击右侧添加</span>') +
      dayTypeHtml +
      '</div>' +
      '<button type="button" class="legend-manage-btn" data-action="open-categories" title="新增、编辑或调整类别优先级">' +
      '⚙ 管理类别' +
      '</button>';
  }

  function renderFilterAndSearch(store) {
    var flt = store.getFilter ? store.getFilter() : { query: '', categoryIds: [] };
    var q = (flt.query || '').trim();
    var catIds = flt.categoryIds || [];
    var hasFilter = Boolean(q || catIds.length);

    var clearSearchBtn = document.getElementById('btn-clear-search');
    if (clearSearchBtn) {
      clearSearchBtn.classList.toggle('hidden', !q);
    }

    var statusEl = document.getElementById('filter-status');
    var statusText = document.getElementById('filter-status-text');
    var filtered = store.getFilteredEvents ? store.getFilteredEvents() : store.getEvents();
    var total = store.getEvents().length;

    if (statusEl && statusText) {
      if (hasFilter) {
        statusText.textContent = '筛选结果：' + filtered.length + ' / ' + total + ' 条';
        statusEl.classList.remove('hidden');
      } else {
        statusEl.classList.add('hidden');
      }
    }

    var panel = document.getElementById('search-results-panel');
    if (!panel) return;
    if (!q) {
      panel.classList.add('hidden');
      panel.innerHTML = '';
      return;
    }

    var DU = WorkCal.DateUtils;
    var Color = WorkCal.Color;
    var matched = store.searchEvents ? store.searchEvents(q, catIds) : filtered;
    var limit = 25;
    var shown = matched.slice(0, limit);

    var bodyHtml;
    if (!shown.length) {
      bodyHtml = '<div class="search-empty">没有找到匹配“' + escapeHtml(q) + '”的日程</div>';
    } else {
      bodyHtml =
        '<div class="search-result-list">' +
        shown
          .map(function (ev) {
            var cat = store.getCategory(ev.categoryId);
            var st = Color.getEventStyles(cat.color);
            var dateLabel =
              ev.endDate && ev.endDate > ev.startDate
                ? ev.startDate + ' ~ ' + ev.endDate
                : ev.startDate + '（' + DU.getWeekdayLabel(ev.startDate) + '）';
            var snippet = ev.notes ? '<span class="search-result-note">' + escapeHtml(ev.notes.slice(0, 60)) + '</span>' : '';
            return (
              '<button type="button" class="search-result-item" data-action="jump-to-event" data-event-id="' +
              escapeHtml(ev.id) +
              '" data-date="' +
              escapeHtml(ev.startDate) +
              '">' +
              '<span class="search-result-badge" style="background:' +
              st.chipBg +
              ';color:' +
              st.textColor +
              ';">' +
              escapeHtml(cat.name) +
              '</span>' +
              '<span class="search-result-main">' +
              '<span class="search-result-title">' +
              escapeHtml(ev.title) +
              '</span>' +
              snippet +
              '</span>' +
              '<span class="search-result-date">' +
              escapeHtml(dateLabel) +
              '</span>' +
              '</button>'
            );
          })
          .join('') +
        '</div>' +
        (matched.length > limit
          ? '<div class="search-more-hint">共匹配 ' + matched.length + ' 条，仅展示最近前 ' + limit + ' 条，可输入更具体关键词缩小范围</div>'
          : '');
    }

    panel.innerHTML =
      '<div class="search-results-head">' +
      '<span>搜索“' +
      escapeHtml(q) +
      '”共匹配 <strong>' +
      matched.length +
      '</strong> 条日程（点击可定位查看）</span>' +
      '<button type="button" class="btn btn-subtle btn-sm" data-action="clear-search">收起</button>' +
      '</div>' +
      bodyHtml;
    panel.classList.remove('hidden');
  }

  function renderMonthView(state, store) {
    var DU = WorkCal.DateUtils;
    var Lunar = WorkCal.Lunar;
    var Color = WorkCal.Color;
    var Layout = WorkCal.Layout;

    var year = state.anchorYear;
    var month = state.anchorMonth;
    var weeks = DU.getMonthGridDates(year, month);
    var today = DU.todayStr();
    var allEvents = store.getFilteredEvents ? store.getFilteredEvents() : store.getEvents();
    var priorityMap = store.getCategoryPriorityMap();
    var isMobile = isMobileViewport();

    var html = [];
    html.push('<div class="month-weekdays">');
    for (var wd = 0; wd < DU.WEEKDAY_NAMES.length; wd++) {
      var cls = wd >= 5 ? 'month-weekday-cell is-weekend' : 'month-weekday-cell';
      html.push('<div class="' + cls + '">' + DU.WEEKDAY_NAMES[wd] + '</div>');
    }
    html.push('</div>');

    html.push('<div class="month-weeks">');

    for (var w = 0; w < weeks.length; w++) {
      var weekDates = weeks[w];
      var weekLayout = Layout.layoutWeek(weekDates, allEvents, priorityMap, year, month, isMobile);

      html.push('<div class="month-week-row">');
      html.push('<div class="week-days-grid">');

      for (var c = 0; c < 7; c++) {
        var dateStr = weekDates[c];
        var parts = DU.parseYMD(dateStr);
        var isCurrentMonth = parts.year === year && parts.month === month;
        var isToday = dateStr === today;
        var meta = DU.getDayMeta(dateStr);
        var lunar = Lunar.getLunarInfo(parts.year, parts.month, parts.day);
        var topCat = store.getTopCategoryOnDate(dateStr);

        var cellClasses = ['day-cell'];
        if (meta.isGray) cellClasses.push('is-rest-bg');
        if (!isCurrentMonth) cellClasses.push('is-other-month');
        if (isToday) cellClasses.push('is-today');

        html.push(
          '<div class="' +
            cellClasses.join(' ') +
            '" data-action="open-day" data-date="' +
            dateStr +
            '" title="' +
            escapeHtml(dateStr + (meta.holidayName ? ' · ' + meta.holidayName : '')) +
            '">'
        );

        // 顶部最高优先级类别指示线（BR-1 / FR-04）
        if (topCat) {
          html.push(
            '<div class="day-top-stripe" style="background:' +
              escapeHtml(topCat.color) +
              ';"></div>'
          );
        }

        // 日期头部：公历 + 农历 + 休/班角标
        html.push('<div class="day-cell-head">');
        html.push('<div class="day-head-left">');
        html.push('<span class="day-num">' + parts.day + '</span>');
        html.push(
          '<span class="day-lunar' +
            (lunar.isHighlight ? ' is-highlight' : '') +
            '">' +
            escapeHtml(lunar.label) +
            '</span>'
        );
        html.push('</div>');
        if (meta.badge) {
          var badgeCls = meta.badge === '休' ? 'day-badge badge-rest' : 'day-badge badge-work';
          html.push('<span class="' + badgeCls + '">' + meta.badge + '</span>');
        }
        html.push('</div>');

        // 跨天泳道精确占位，防止与上方跨天条叠压（FR-06）
        var reserved = weekLayout.dayReservedLanes[c];
        var spacerPx =
          reserved > 0 ? reserved * (weekLayout.laneHeight + weekLayout.laneGap) + 2 : 0;
        if (spacerPx > 0) {
          html.push('<div class="day-span-spacer" style="height:' + spacerPx + 'px;"></div>');
        }

        // 当天单日日程与 +N 折叠
        var visibleSingles = weekLayout.dayVisibleSingle[c];
        var hiddenCount = weekLayout.dayHiddenCount[c];
        if (visibleSingles.length || hiddenCount > 0) {
          html.push('<div class="day-singles">');
          for (var s = 0; s < visibleSingles.length; s++) {
            var ev = visibleSingles[s];
            var cat = store.getCategory(ev.categoryId);
            var st = Color.getEventStyles(cat.color);
            var timeTag =
              ev.allDay === false && ev.startTime
                ? '<span class="single-event-time">' + escapeHtml(ev.startTime) + '</span>'
                : '';
            html.push(
              '<button type="button" class="single-event-chip" data-action="open-event" data-event-id="' +
                escapeHtml(ev.id) +
                '" style="--cat-color:' +
                st.borderColor +
                ';--cat-soft-bg:' +
                st.softBg +
                ';--cat-text:' +
                st.textColor +
                ';" title="' +
                escapeHtml(ev.title) +
                '">' +
                timeTag +
                '<span class="single-event-title">' +
                escapeHtml(ev.title) +
                '</span></button>'
            );
          }
          if (hiddenCount > 0) {
            html.push(
              '<button type="button" class="day-more-btn" data-action="open-day" data-date="' +
                dateStr +
                '">+' +
                hiddenCount +
                ' 项</button>'
            );
          }
          html.push('</div>');
        }

        html.push('</div>'); // .day-cell
      }

      html.push('</div>'); // .week-days-grid

      // 跨天行程整体层（绝对定位横跨多日）
      if (weekLayout.segments.length > 0) {
        var layerHeight =
          weekLayout.laneCount * (weekLayout.laneHeight + weekLayout.laneGap);
        html.push('<div class="week-spans-layer" style="height:' + layerHeight + 'px;">');

        for (var k = 0; k < weekLayout.segments.length; k++) {
          var seg = weekLayout.segments[k];
          var segCat = store.getCategory(seg.event.categoryId);
          var segStyles = Color.getEventStyles(segCat.color);

          var leftInset = seg.cutLeft ? 0 : 3;
          var rightInset = seg.cutRight ? 0 : 3;
          var leftExpr = 'calc(' + seg.startCol + ' * 100% / 7 + ' + leftInset + 'px)';
          var widthExpr =
            'calc(' + seg.colSpan + ' * 100% / 7 - ' + (leftInset + rightInset) + 'px)';
          var topPx = seg.lane * (weekLayout.laneHeight + weekLayout.laneGap);

          var barCls = ['span-bar'];
          if (seg.cutLeft) barCls.push('is-cut-left');
          if (seg.cutRight) barCls.push('is-cut-right');
          if (seg.isOutOfMonth) barCls.push('is-out-month');

          var leftArrow = seg.cutLeft ? '<span class="span-arrow" aria-hidden="true">‹</span>' : '';
          var rightArrow = seg.cutRight ? '<span class="span-arrow" aria-hidden="true">›</span>' : '';

          html.push(
            '<button type="button" class="' +
              barCls.join(' ') +
              '" data-action="open-event" data-event-id="' +
              escapeHtml(seg.event.id) +
              '" style="left:' +
              leftExpr +
              ';width:' +
              widthExpr +
              ';top:' +
              topPx +
              'px;height:' +
              weekLayout.laneHeight +
              'px;--cat-border:' +
              segStyles.borderColor +
              ';--cat-soft-bg:' +
              segStyles.softBg +
              ';--cat-text:' +
              segStyles.textColor +
              ';" title="' +
              escapeHtml(
                seg.event.title +
                  '（' +
                  seg.event.startDate +
                  ' 至 ' +
                  seg.event.endDate +
                  '）'
              ) +
              '">' +
              leftArrow +
              '<span class="span-bar-title">' +
              escapeHtml(seg.event.title) +
              '</span>' +
              rightArrow +
              '</button>'
          );
        }

        html.push('</div>');
      }

      html.push('</div>'); // .month-week-row
    }

    html.push('</div>'); // .month-weeks
    return html.join('');
  }

  function renderWeekView(state, store) {
    var DU = WorkCal.DateUtils;
    var Lunar = WorkCal.Lunar;
    var Color = WorkCal.Color;
    var Layout = WorkCal.Layout;

    var weekDates = DU.getWeekDates(state.anchorDate);
    var today = DU.todayStr();
    var html = ['<div class="week-view-grid">'];

    for (var i = 0; i < weekDates.length; i++) {
      var dateStr = weekDates[i];
      var p = DU.parseYMD(dateStr);
      var meta = DU.getDayMeta(dateStr);
      var lunar = Lunar.getLunarInfo(p.year, p.month, p.day);
      var events = store.getEventsOnDate(dateStr);

      var colCls = ['week-col'];
      if (meta.isGray) colCls.push('is-rest-bg');
      if (dateStr === today) colCls.push('is-today');

      html.push('<div class="' + colCls.join(' ') + '">');
      html.push(
        '<div class="week-col-header" data-action="open-day" data-date="' + dateStr + '">'
      );
      html.push('<div class="week-col-date-wrap">');
      html.push(
        '<div class="week-col-wd">' +
          DU.WEEKDAY_NAMES[i] +
          (dateStr === today ? ' · 今天' : '') +
          '</div>'
      );
      html.push('<div class="week-col-num-row">');
      html.push('<span class="week-col-num">' + p.month + '/' + p.day + '</span>');
      if (meta.badge) {
        var bCls = meta.badge === '休' ? 'day-badge badge-rest' : 'day-badge badge-work';
        html.push('<span class="' + bCls + '">' + meta.badge + '</span>');
      }
      html.push('</div>');
      html.push(
        '<div class="week-col-lunar">' +
          escapeHtml(lunar.lunarMonthName + lunar.lunarDayName) +
          (lunar.festival ? ' · ' + escapeHtml(lunar.festival) : '') +
          (lunar.solarTerm ? ' · ' + escapeHtml(lunar.solarTerm) : '') +
          '</div>'
      );
      html.push('</div>');
      html.push('</div>'); // header

      html.push('<div class="week-col-body">');
      if (!events.length) {
        html.push('<div class="panel-empty">暂无安排</div>');
      } else {
        for (var j = 0; j < events.length; j++) {
          var ev = events[j];
          var cat = store.getCategory(ev.categoryId);
          var st = Color.getEventStyles(cat.color);
          var isMulti = Layout.isMultiDay(ev);
          var spanTag = '';
          if (isMulti) {
            var totalDays = DU.diffDays(ev.startDate, ev.endDate) + 1;
            var curDay = DU.diffDays(ev.startDate, dateStr) + 1;
            spanTag = '<span class="week-event-span-tag">第' + curDay + '/' + totalDays + '天</span>';
          }
          var timeLine =
            ev.allDay === false
              ? escapeHtml((ev.startTime || '09:00') + ' - ' + (ev.endTime || '18:00'))
              : '全天';
          var noteText = ev.notes ? '<div class="week-event-notes">' + escapeHtml(ev.notes) + '</div>' : '';

          html.push(
            '<button type="button" class="week-event-card" data-action="open-event" data-event-id="' +
              escapeHtml(ev.id) +
              '" style="--cat-border:' +
              st.borderColor +
              ';--cat-soft-bg:' +
              st.softBg +
              ';--cat-text:' +
              st.textColor +
              ';">' +
              '<div class="week-event-top"><span class="week-event-cat">' +
              escapeHtml(cat.name) +
              '</span>' +
              spanTag +
              '</div>' +
              '<div class="week-event-title">' +
              escapeHtml(ev.title) +
              '</div>' +
              '<div class="week-event-time">' +
              timeLine +
              '</div>' +
              noteText +
              '</button>'
          );
        }
      }
      html.push(
        '<button type="button" class="week-add-btn" data-action="new-event-date" data-date="' +
          dateStr +
          '">+ 新增日程</button>'
      );
      html.push('</div>');
      html.push('</div>');
    }

    html.push('</div>');
    return html.join('');
  }

  function renderYearView(state, store) {
    var DU = WorkCal.DateUtils;
    var Color = WorkCal.Color;
    var year = state.anchorYear;
    var today = DU.todayStr();
    var shortWds = ['一', '二', '三', '四', '五', '六', '日'];

    var html = ['<div class="year-view-grid">'];

    for (var m = 1; m <= 12; m++) {
      var firstStr = DU.formatDate(year, m, 1);
      var leadEmpty = DU.getWeekdayMon0(firstStr);
      var daysInMonth = new Date(year, m, 0).getDate();
      var monthEventDays = 0;

      var dayBtns = [];
      for (var e = 0; e < leadEmpty; e++) {
        dayBtns.push('<span class="year-mini-day is-empty"></span>');
      }

      for (var d = 1; d <= daysInMonth; d++) {
        var dateStr = DU.formatDate(year, m, d);
        var meta = DU.getDayMeta(dateStr);
        var topCat = store.getTopCategoryOnDate(dateStr);
        var isToday = dateStr === today;

        var cls = ['year-mini-day'];
        if (meta.isGray) cls.push('is-rest-bg');
        if (isToday) cls.push('is-today');

        var styleAttr = '';
        if (topCat) {
          monthEventDays++;
          cls.push('has-event');
          var st = Color.getEventStyles(topCat.color);
          var lum = Color.getRelativeLuminance(st.yearFill);
          var fg = lum > 0.45 ? '#0f172a' : '#ffffff';
          styleAttr = ' style="background:' + st.yearFill + ';color:' + fg + ';"';
        }

        dayBtns.push(
          '<button type="button" class="' +
            cls.join(' ') +
            '"' +
            styleAttr +
            ' data-action="open-day" data-date="' +
            dateStr +
            '" title="' +
            escapeHtml(dateStr + (topCat ? ' · ' + topCat.name : '')) +
            '">' +
            d +
            '</button>'
        );
      }

      html.push('<div class="year-month-card">');
      html.push(
        '<button type="button" class="year-month-title" data-action="jump-month" data-year="' +
          year +
          '" data-month="' +
          m +
          '">' +
          '<span>' +
          m +
          '月</span>' +
          '<span class="year-month-sub">' +
          (monthEventDays ? monthEventDays + '天有安排' : '空闲') +
          '</span>' +
          '</button>'
      );
      html.push('<div class="year-mini-weekdays">');
      for (var w = 0; w < 7; w++) {
        html.push('<span class="year-mini-wd">' + shortWds[w] + '</span>');
      }
      html.push('</div>');
      html.push('<div class="year-mini-days">' + dayBtns.join('') + '</div>');
      html.push('</div>');
    }

    html.push('</div>');
    return html.join('');
  }

  function addOneCalendarMonth(dateStr) {
    var DU = WorkCal.DateUtils;
    var p = DU.parseYMD(dateStr);
    if (!p) return DU.addDays(dateStr, 30);
    var nextYear = p.month === 12 ? p.year + 1 : p.year;
    var nextMonth = p.month === 12 ? 1 : p.month + 1;
    var lastDay = new Date(nextYear, nextMonth, 0).getDate();
    var targetDay = Math.min(p.day, lastDay);
    return DU.formatDate(nextYear, nextMonth, targetDay);
  }

  function renderSidebar(state, store) {
    var container = document.getElementById('sidebar');
    if (!container) return;

    var DU = WorkCal.DateUtils;
    var Layout = WorkCal.Layout;
    var today = DU.todayStr();
    var reminderRange = state.reminderRange === '30d' ? '30d' : '7d';
    var rangeEnd = reminderRange === '30d' ? addOneCalendarMonth(today) : DU.addDays(today, 6);
    var rangeLabel = reminderRange === '30d' ? '今起一个月' : '今起 7 天';

    var rawAllEvents = store.getEvents();
    var allEvents = store.getFilteredEvents ? store.getFilteredEvents() : rawAllEvents;
    var categories = store.getCategories();
    var priorityMap = store.getCategoryPriorityMap();

    // 1. 近期提醒（合并原“近期提醒”与“接下来”，支持切换 今起7天 / 今起一个月）
    var recentEvents = allEvents
      .filter(function (ev) {
        var end = Layout.getEventEndDate(ev);
        return end >= today && ev.startDate <= rangeEnd;
      })
      .sort(function (a, b) {
        if (a.startDate !== b.startDate) return a.startDate.localeCompare(b.startDate);
        return Layout.compareEvents(a, b, priorityMap);
      });

    var recentHtml;
    if (recentEvents.length) {
      recentHtml =
        '<div class="panel-list is-scrollable">' +
        recentEvents
          .map(function (ev) {
            var cat = store.getCategory(ev.categoryId);
            var end = Layout.getEventEndDate(ev);
            var rangeText =
              end > ev.startDate
                ? DU.formatShortMD(ev.startDate) + ' - ' + DU.formatShortMD(end)
                : ev.startDate === today
                  ? '今天'
                  : DU.formatShortMD(ev.startDate) + '（' + DU.getWeekdayLabel(ev.startDate) + '）';
            return (
              '<button type="button" class="panel-item" data-action="open-event" data-event-id="' +
              escapeHtml(ev.id) +
              '" style="--cat-color:' +
              escapeHtml(cat.color) +
              ';">' +
              '<div class="panel-item-title">' +
              escapeHtml(ev.title) +
              '</div>' +
              '<div class="panel-item-meta"><span>' +
              escapeHtml(cat.name) +
              '</span><span>' +
              escapeHtml(rangeText) +
              '</span></div>' +
              '</button>'
            );
          })
          .join('') +
        '</div>' +
        '<div class="panel-foot-meta"><span>范围：' +
        escapeHtml(DU.formatShortMD(today) + ' - ' + DU.formatShortMD(rangeEnd)) +
        '</span><span>共 ' +
        recentEvents.length +
        ' 条</span></div>';
    } else if (rawAllEvents.length === 0) {
      recentHtml =
        '<div class="panel-empty">' +
        '<div>当前暂无日程记录</div>' +
        '<div style="margin-top:8px;">' +
        '<button type="button" class="btn btn-outline btn-sm" data-action="load-sample">加载本月示例日程</button>' +
        '</div>' +
        '</div>';
    } else if (store.hasActiveFilter && store.hasActiveFilter()) {
      recentHtml = '<div class="panel-empty">当前筛选条件下' + rangeLabel + '内暂无日程</div>';
    } else {
      recentHtml = '<div class="panel-empty">' + rangeLabel + '内暂无待办行程</div>';
    }

    // 2. 日程统计（支持自由选择 按月/按年 及具体年份/月份）
    var statsMode = state.statsMode === 'year' ? 'year' : 'month';
    var statsYear = state.statsYear || state.anchorYear;
    var statsMonth = state.statsMonth || state.anchorMonth;

    // 汇总可选年份范围（覆盖当前年份前后 2 年 + 已有日程涉及的所有年份 + 当前选中年份）
    var nowYear = new Date().getFullYear();
    var minYear = Math.min(nowYear - 2, statsYear, state.anchorYear);
    var maxYear = Math.max(nowYear + 2, statsYear, state.anchorYear);
    for (var yIdx = 0; yIdx < rawAllEvents.length; yIdx++) {
      var evItem = rawAllEvents[yIdx];
      var sy = parseInt(String(evItem.startDate || '').slice(0, 4), 10);
      var ey = parseInt(String(evItem.endDate || evItem.startDate || '').slice(0, 4), 10);
      if (!isNaN(sy)) {
        if (sy < minYear) minYear = sy;
        if (sy > maxYear) maxYear = sy;
      }
      if (!isNaN(ey)) {
        if (ey < minYear) minYear = ey;
        if (ey > maxYear) maxYear = ey;
      }
    }

    var yearOptionsHtml = '';
    for (var yr = maxYear; yr >= minYear; yr--) {
      yearOptionsHtml +=
        '<option value="' + yr + '"' + (yr === statsYear ? ' selected' : '') + '>' + yr + '年</option>';
    }

    var monthOptionsHtml = '';
    for (var mo = 1; mo <= 12; mo++) {
      monthOptionsHtml +=
        '<option value="' + mo + '"' + (mo === statsMonth ? ' selected' : '') + '>' + mo + '月</option>';
    }

    var periodStart;
    var periodEnd;
    if (statsMode === 'year') {
      periodStart = statsYear + '-01-01';
      periodEnd = statsYear + '-12-31';
    } else {
      var lastDay = new Date(statsYear, statsMonth, 0).getDate();
      periodStart = DU.formatDate(statsYear, statsMonth, 1);
      periodEnd = DU.formatDate(statsYear, statsMonth, lastDay);
    }

    var catDaySets = {};
    var periodEventCount = 0;
    var activeAllDays = {};

    for (var i = 0; i < allEvents.length; i++) {
      var ev = allEvents[i];
      var evEnd = Layout.getEventEndDate(ev);
      if (evEnd < periodStart || ev.startDate > periodEnd) continue;
      periodEventCount++;
      var s = ev.startDate > periodStart ? ev.startDate : periodStart;
      var e = evEnd < periodEnd ? evEnd : periodEnd;
      var cur = s;
      while (cur <= e) {
        if (!catDaySets[ev.categoryId]) catDaySets[ev.categoryId] = {};
        catDaySets[ev.categoryId][cur] = true;
        activeAllDays[cur] = true;
        cur = DU.addDays(cur, 1);
      }
    }

    var statItems = categories
      .map(function (c, idx) {
        var count = catDaySets[c.id] ? Object.keys(catDaySets[c.id]).length : 0;
        return { id: c.id, name: c.name, color: c.color, days: count, order: idx };
      })
      .filter(function (item) {
        return item.days > 0;
      });

    // 若存在已删除类别的历史日程落在当期，补充统计兜底行
    var catIdSet = {};
    categories.forEach(function (c) {
      catIdSet[c.id] = true;
    });
    var orphanDaySet = {};
    Object.keys(catDaySets).forEach(function (cid) {
      if (!catIdSet[cid]) {
        Object.assign(orphanDaySet, catDaySets[cid]);
      }
    });
    var orphanDays = Object.keys(orphanDaySet).length;
    if (orphanDays > 0) {
      statItems.push({
        id: '__fallback__',
        name: '已删除类别',
        color: WorkCal.Config.fallbackCategory.color,
        days: orphanDays,
        order: categories.length + 1
      });
    }

    // 按天数从多到少排序（天数相同时保持原类别优先级顺序）
    statItems.sort(function (a, b) {
      if (b.days !== a.days) return b.days - a.days;
      return a.order - b.order;
    });

    var maxDays = statItems.length ? statItems[0].days : 0;

    var statsRowsHtml = statItems.length
      ? statItems
          .map(function (item) {
            var pct = maxDays > 0 ? Math.round((item.days / maxDays) * 100) : 0;
            return (
              '<div class="stat-row">' +
              '<div class="stat-row-top">' +
              '<span class="stat-name"><span class="stat-dot" style="background:' +
              escapeHtml(item.color) +
              ';"></span>' +
              escapeHtml(item.name) +
              '</span>' +
              '<span class="stat-value">' +
              item.days +
              ' 天</span>' +
              '</div>' +
              '<div class="stat-track"><div class="stat-fill" style="width:' +
              pct +
              '%;background:' +
              escapeHtml(item.color) +
              ';"></div></div>' +
              '</div>'
            );
          })
          .join('')
      : '<div class="panel-empty">该期间暂无日程安排</div>';

    var statsPickerHtml =
      '<div class="stats-picker-row">' +
      '<div class="stats-select-group">' +
      '<select class="stats-select" data-sidebar-select="stats-year" aria-label="选择统计年份">' +
      yearOptionsHtml +
      '</select>' +
      (statsMode === 'month'
        ? '<select class="stats-select" data-sidebar-select="stats-month" aria-label="选择统计月份">' +
          monthOptionsHtml +
          '</select>'
        : '') +
      '</div>' +
      '<span class="panel-sub">共 ' +
      periodEventCount +
      ' 条</span>' +
      '</div>';

    container.innerHTML =
      '<section class="panel-card">' +
      '<div class="panel-header">' +
      '<h2 class="panel-title">近期提醒</h2>' +
      '<div class="mini-seg" role="tablist" aria-label="提醒范围切换">' +
      '<button type="button" class="mini-seg-btn' +
      (reminderRange === '7d' ? ' is-active' : '') +
      '" data-action="set-reminder-range" data-range="7d" aria-selected="' +
      (reminderRange === '7d' ? 'true' : 'false') +
      '">今起7天</button>' +
      '<button type="button" class="mini-seg-btn' +
      (reminderRange === '30d' ? ' is-active' : '') +
      '" data-action="set-reminder-range" data-range="30d" aria-selected="' +
      (reminderRange === '30d' ? 'true' : 'false') +
      '">今起一个月</button>' +
      '</div>' +
      '</div>' +
      recentHtml +
      '</section>' +
      '<section class="panel-card">' +
      '<div class="panel-header">' +
      '<h2 class="panel-title">日程统计</h2>' +
      '<div class="mini-seg" role="tablist" aria-label="统计维度切换">' +
      '<button type="button" class="mini-seg-btn' +
      (statsMode === 'month' ? ' is-active' : '') +
      '" data-action="set-stats-mode" data-mode="month" aria-selected="' +
      (statsMode === 'month' ? 'true' : 'false') +
      '">按月</button>' +
      '<button type="button" class="mini-seg-btn' +
      (statsMode === 'year' ? ' is-active' : '') +
      '" data-action="set-stats-mode" data-mode="year" aria-selected="' +
      (statsMode === 'year' ? 'true' : 'false') +
      '">按年</button>' +
      '</div>' +
      '</div>' +
      statsPickerHtml +
      '<div class="stat-rows">' +
      statsRowsHtml +
      '</div>' +
      '<div class="stat-summary-foot"><span>有日程天数：' +
      Object.keys(activeAllDays).length +
      ' 天</span><span>全部日程：' +
      allEvents.length +
      ' 条</span></div>' +
      '</section>';
  }

  function renderAll(state, store) {
    renderHeader(state);
    renderLegend(store);
    renderFilterAndSearch(store);

    var stage = document.getElementById('calendar-stage');
    if (stage) {
      if (state.view === 'week') {
        stage.innerHTML = renderWeekView(state, store);
      } else if (state.view === 'year') {
        stage.innerHTML = renderYearView(state, store);
      } else {
        stage.innerHTML = renderMonthView(state, store);
      }
    }

    renderSidebar(state, store);
  }

  WorkCal.Views = {
    escapeHtml: escapeHtml,
    renderAll: renderAll
  };
})(typeof window !== 'undefined' ? window : globalThis);
