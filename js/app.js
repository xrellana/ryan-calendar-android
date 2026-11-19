/**
 * 应用入口：兼容检查、云端账号鉴权、状态驱动、搜索筛选、多端自动同步与全局事件绑定
 */
(function (root) {
  'use strict';

  var WorkCal = (root.WorkCal = root.WorkCal || {});

  function checkBrowserCompatibility() {
    var missing = [];
    if (typeof Promise === 'undefined') missing.push('Promise');
    if (typeof fetch === 'undefined') missing.push('fetch');
    if (typeof URLSearchParams === 'undefined') missing.push('URLSearchParams');
    if (typeof TextDecoder === 'undefined') missing.push('TextDecoder');
    if (typeof Uint8Array === 'undefined') missing.push('Uint8Array');
    if (typeof JSON === 'undefined') missing.push('JSON');
    return missing;
  }

  function showToast(message, type) {
    var container = document.getElementById('toast-container');
    if (!container) return;
    var el = document.createElement('div');
    el.className = 'toast' + (type ? ' is-' + type : '');
    el.textContent = message;
    container.appendChild(el);
    setTimeout(function () {
      if (el.parentNode) el.parentNode.removeChild(el);
    }, 3400);
  }

  function setScreen(mode, loadingMessage) {
    var loadingEl = document.getElementById('loading-screen');
    var loginEl = document.getElementById('login-screen');
    var shellEl = document.getElementById('app-shell');
    var loadingText = document.getElementById('loading-text');

    if (loadingText && loadingMessage) loadingText.textContent = loadingMessage;
    if (loadingEl) loadingEl.classList.toggle('hidden', mode !== 'loading');
    if (loginEl) loginEl.classList.toggle('hidden', mode !== 'login');
    if (shellEl) shellEl.classList.toggle('hidden', mode !== 'app');
  }

  function boot() {
    var missing = checkBrowserCompatibility();
    if (missing.length) {
      setScreen('none');
      var warn = document.getElementById('compat-warning');
      var detail = document.getElementById('compat-warning-detail');
      if (detail) detail.textContent = '缺少关键能力：' + missing.join('、') + '。';
      if (warn) warn.classList.remove('hidden');
      return;
    }

    var DU = WorkCal.DateUtils;
    var api = WorkCal.Api;
    var store = WorkCal.Storage.createStore(api);

    var currentUser = null;
    var lastSyncMs = 0;
    var isSyncing = false;

    var now = new Date();
    var state = {
      view: 'month',
      anchorYear: now.getFullYear(),
      anchorMonth: now.getMonth() + 1,
      anchorDate: DU.fromLocalDate(now),
      reminderRange: '7d',
      statsMode: 'month',
      statsYear: now.getFullYear(),
      statsMonth: now.getMonth() + 1
    };

    var render = function () {
      if (!currentUser) return;
      WorkCal.Views.renderAll(state, store);
    };

    function updateUserChrome(user) {
      currentUser = user;
      var nameEl = document.getElementById('user-display-name');
      var adminBtn = document.getElementById('btn-admin');
      if (nameEl) {
        nameEl.textContent = user ? user.displayName || user.username : '账号';
      }
      if (adminBtn) {
        adminBtn.classList.toggle('hidden', !(user && user.role === 'admin'));
      }
    }

    function showLoginScreen(reasonToast) {
      currentUser = null;
      store.resetMemoryState();
      var searchInp = document.getElementById('search-input');
      if (searchInp) searchInp.value = '';
      updateUserChrome(null);
      setScreen('login');
      var pwdInp = document.getElementById('login-password');
      if (pwdInp) pwdInp.value = '';
      var errEl = document.getElementById('login-error');
      if (errEl) errEl.classList.add('hidden');
      if (reasonToast) showToast(reasonToast, 'error');
      setTimeout(function () {
        var uInp = document.getElementById('login-username');
        if (uInp) uInp.focus();
      }, 30);
    }

    var modals = WorkCal.Modals.createModalManager({
      store: store,
      api: api,
      getCurrentUser: function () {
        return currentUser;
      },
      onUserUpdated: function (nextUser) {
        updateUserChrome(nextUser);
      },
      onLogout: function () {
        showLoginScreen();
        showToast('已退出登录', 'success');
      },
      onDataChanged: render,
      onJumpToMonth: function (year, month) {
        state.view = 'month';
        state.anchorYear = year;
        state.anchorMonth = month;
        state.anchorDate = DU.formatDate(year, month, 1);
        render();
      },
      ensureMonthViewForPrint: function () {
        if (state.view !== 'month') {
          state.view = 'month';
          render();
        }
      },
      showToast: showToast
    });

    api.setUnauthorizedHandler(function () {
      if (modals.isOpen()) modals.close();
      if (currentUser) {
        showLoginScreen('登录态已失效，请重新登录');
      } else {
        setScreen('login');
      }
    });

    function enterAppWithUser(user) {
      updateUserChrome(user);
      setScreen('loading', '正在同步云端日程...');

      return Promise.all([
        store.loadFromServer(),
        api.getHolidays().catch(function () {
          return null;
        })
      ])
        .then(function (results) {
          var holidayPayload = results[1];
          if (holidayPayload && WorkCal.Holidays && WorkCal.Holidays.loadFromServer) {
            WorkCal.Holidays.loadFromServer(holidayPayload);
          }
          lastSyncMs = Date.now();
          setScreen('app');
          render();

          // 检测旧版浏览器本地 localStorage 历史数据，提示一键迁移到云端
          var legacy = store.detectLegacyLocalData();
          if (legacy && legacy.events && legacy.events.length > 0) {
            setTimeout(function () {
              if (currentUser && !modals.isOpen()) {
                modals.open('migrate-local', { legacy: legacy });
              }
            }, 250);
          }
        })
        .catch(function (err) {
          if (err && err.status === 401) {
            showLoginScreen('登录已过期，请重新登录');
          } else {
            showLoginScreen((err && err.message) || '加载云端数据失败，请重试');
          }
        });
    }

    function stepPeriod(dir) {
      if (state.view === 'year') {
        state.anchorYear += dir;
        state.anchorDate = DU.formatDate(state.anchorYear, state.anchorMonth, 1);
      } else if (state.view === 'month') {
        var nextMonth = state.anchorMonth + dir;
        var dt = new Date(state.anchorYear, nextMonth - 1, 1);
        state.anchorYear = dt.getFullYear();
        state.anchorMonth = dt.getMonth() + 1;
        state.anchorDate = DU.fromLocalDate(dt);
      } else {
        state.anchorDate = DU.addDays(state.anchorDate, dir * 7);
        var p = DU.parseYMD(state.anchorDate);
        state.anchorYear = p.year;
        state.anchorMonth = p.month;
      }
      render();
    }

    function jumpToday() {
      var today = new Date();
      state.anchorYear = today.getFullYear();
      state.anchorMonth = today.getMonth() + 1;
      state.anchorDate = DU.fromLocalDate(today);
      render();
    }

    // 登录表单提交
    var loginForm = document.getElementById('login-form');
    if (loginForm) {
      loginForm.addEventListener('submit', function (e) {
        e.preventDefault();
        var uEl = document.getElementById('login-username');
        var pEl = document.getElementById('login-password');
        var errEl = document.getElementById('login-error');
        var submitBtn = document.getElementById('login-submit');

        var username = uEl ? uEl.value.trim() : '';
        var password = pEl ? pEl.value : '';
        if (errEl) errEl.classList.add('hidden');

        if (!username || !password) {
          if (errEl) {
            errEl.textContent = '请输入账号 ID 和密码';
            errEl.classList.remove('hidden');
          }
          return;
        }

        submitBtn.disabled = true;
        submitBtn.textContent = '登录中...';

        api
          .login(username, password)
          .then(function (res) {
            if (pEl) pEl.value = '';
            return enterAppWithUser(res.user);
          })
          .catch(function (err) {
            if (errEl) {
              errEl.textContent = (err && err.message) || '登录失败';
              errEl.classList.remove('hidden');
            }
          })
          .finally(function () {
            submitBtn.disabled = false;
            submitBtn.textContent = '登录';
          });
      });
    }

    // 顶栏按钮绑定
    document.getElementById('btn-prev').addEventListener('click', function () {
      stepPeriod(-1);
    });
    document.getElementById('btn-next').addEventListener('click', function () {
      stepPeriod(1);
    });
    document.getElementById('btn-today').addEventListener('click', jumpToday);

    var viewTabs = document.querySelectorAll('.view-tab');
    for (var i = 0; i < viewTabs.length; i++) {
      (function (tab) {
        tab.addEventListener('click', function () {
          var nextView = tab.getAttribute('data-view');
          if (!nextView || nextView === state.view) return;
          state.view = nextView;
          render();
        });
      })(viewTabs[i]);
    }

    document.getElementById('btn-new-event').addEventListener('click', function () {
      modals.open('event-editor', { date: DU.todayStr() });
    });
    document.getElementById('btn-import').addEventListener('click', function () {
      modals.open('import', {}, true);
    });
    document.getElementById('btn-more').addEventListener('click', function () {
      modals.open('more', {});
    });
    document.getElementById('btn-account').addEventListener('click', function () {
      modals.open('account', {});
    });
    document.getElementById('btn-admin').addEventListener('click', function () {
      if (currentUser && currentUser.role === 'admin') {
        modals.openAdmin('users');
      }
    });

    // 搜索与筛选绑定
    var searchInput = document.getElementById('search-input');
    var searchTimer = null;
    if (searchInput) {
      searchInput.addEventListener('input', function () {
        if (searchTimer) clearTimeout(searchTimer);
        searchTimer = setTimeout(function () {
          store.setFilterQuery(searchInput.value);
          render();
        }, 120);
      });
      searchInput.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && searchInput.value) {
          searchInput.value = '';
          store.setFilterQuery('');
          render();
        }
      });
    }

    var clearSearchBtn = document.getElementById('btn-clear-search');
    if (clearSearchBtn) {
      clearSearchBtn.addEventListener('click', function () {
        if (searchInput) searchInput.value = '';
        store.setFilterQuery('');
        render();
      });
    }

    var clearFiltersBtn = document.getElementById('btn-clear-filters');
    if (clearFiltersBtn) {
      clearFiltersBtn.addEventListener('click', function () {
        if (searchInput) searchInput.value = '';
        store.clearFilters();
        render();
      });
    }

    // 日历区、图例筛选、搜索结果与侧边栏事件委托
    document.getElementById('app-shell').addEventListener('click', function (e) {
      var actionEl = e.target.closest('[data-action]');
      if (!actionEl) return;
      var action = actionEl.getAttribute('data-action');

      if (action === 'open-event') {
        e.stopPropagation();
        modals.open('event-editor', { eventId: actionEl.getAttribute('data-event-id') });
      } else if (action === 'jump-to-event') {
        e.stopPropagation();
        var targetDate = actionEl.getAttribute('data-date');
        var targetId = actionEl.getAttribute('data-event-id');
        if (targetDate) {
          var p = DU.parseYMD(targetDate);
          if (p) {
            state.view = 'month';
            state.anchorYear = p.year;
            state.anchorMonth = p.month;
            state.anchorDate = targetDate;
            render();
          }
        }
        if (targetId) {
          modals.open('event-editor', { eventId: targetId });
        }
      } else if (action === 'open-day') {
        modals.open('day-detail', { date: actionEl.getAttribute('data-date') });
      } else if (action === 'new-event-date') {
        e.stopPropagation();
        modals.open('event-editor', { date: actionEl.getAttribute('data-date') });
      } else if (action === 'jump-month') {
        var yr = parseInt(actionEl.getAttribute('data-year'), 10);
        var mo = parseInt(actionEl.getAttribute('data-month'), 10);
        state.view = 'month';
        state.anchorYear = yr;
        state.anchorMonth = mo;
        state.anchorDate = DU.formatDate(yr, mo, 1);
        render();
      } else if (action === 'open-categories') {
        modals.open('categories', {});
      } else if (action === 'toggle-filter-cat') {
        var catId = actionEl.getAttribute('data-cat-id');
        if (catId) {
          store.toggleFilterCategory(catId);
          render();
        }
      } else if (action === 'clear-search') {
        if (searchInput) searchInput.value = '';
        store.setFilterQuery('');
        render();
      } else if (action === 'set-reminder-range') {
        var nextRange = actionEl.getAttribute('data-range');
        if (nextRange && nextRange !== state.reminderRange) {
          state.reminderRange = nextRange;
          render();
        }
      } else if (action === 'set-stats-mode') {
        var nextMode = actionEl.getAttribute('data-mode');
        if (nextMode && nextMode !== state.statsMode) {
          state.statsMode = nextMode;
          render();
        }
      } else if (action === 'load-sample') {
        actionEl.disabled = true;
        store
          .loadSampleData(state.anchorYear, state.anchorMonth)
          .then(function (cnt) {
            render();
            showToast('已加载 ' + cnt + ' 条示例日程', 'success');
          })
          .catch(function (err) {
            showToast((err && err.message) || '加载示例日程失败', 'error');
          })
          .finally(function () {
            if (actionEl.isConnected) actionEl.disabled = false;
          });
      }
    });

    // 侧边栏统计年月选择器事件委托
    var sidebarEl = document.getElementById('sidebar');
    if (sidebarEl) {
      sidebarEl.addEventListener('change', function (e) {
        var sel = e.target && e.target.closest('[data-sidebar-select]');
        if (!sel) return;
        var kind = sel.getAttribute('data-sidebar-select');
        var val = parseInt(sel.value, 10);
        if (isNaN(val)) return;
        if (kind === 'stats-year') {
          state.statsYear = val;
          render();
        } else if (kind === 'stats-month' && val >= 1 && val <= 12) {
          state.statsMonth = val;
          render();
        }
      });
    }

    // 键盘快捷键（Esc 关闭浮层，左右方向键翻页）
    document.addEventListener('keydown', function (e) {
      if (!currentUser) return;
      if (e.key === 'Escape' && modals.isOpen()) {
        modals.close();
        return;
      }
      if (modals.isOpen()) return;
      var tag = e.target && e.target.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;
      if (e.key === 'ArrowLeft') stepPeriod(-1);
      else if (e.key === 'ArrowRight') stepPeriod(1);
    });

    // 视口跨断点时自适应重排
    var lastIsMobile = window.innerWidth <= 640;
    window.addEventListener('resize', function () {
      var nowIsMobile = window.innerWidth <= 640;
      if (nowIsMobile !== lastIsMobile) {
        lastIsMobile = nowIsMobile;
        render();
      }
    });

    // 切回标签页时自动静默拉取最新云端数据（多端同步）
    document.addEventListener('visibilitychange', function () {
      if (document.visibilityState !== 'visible') return;
      if (!currentUser || isSyncing || modals.isOpen()) return;
      if (Date.now() - lastSyncMs < 15000) return;
      isSyncing = true;
      store
        .refreshIfChanged()
        .then(function (changed) {
          lastSyncMs = Date.now();
          if (changed) {
            render();
            showToast('已自动同步最新日程', 'success');
          }
        })
        .catch(function () {
          // 静默忽略临时网络波动
        })
        .finally(function () {
          isSyncing = false;
        });
    });

    // 启动鉴权检查
    setScreen('loading', '正在验证登录状态...');
    api
      .getMe()
      .then(function (res) {
        if (res && res.user) {
          return enterAppWithUser(res.user);
        }
        showLoginScreen();
      })
      .catch(function () {
        showLoginScreen('无法连接服务器，请检查网络后重试');
      });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})(typeof window !== 'undefined' ? window : globalThis);
