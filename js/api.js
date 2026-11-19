/**
 * 后端 REST API 客户端封装
 * 统一携带同源 Cookie 与 CSRF 防护头，并处理 401 登录态失效回调
 */
(function (root) {
  'use strict';

  var WorkCal = (root.WorkCal = root.WorkCal || {});

  var unauthorizedHandler = null;

  function setUnauthorizedHandler(fn) {
    unauthorizedHandler = typeof fn === 'function' ? fn : null;
  }

  function request(method, url, body, options) {
    var opts = options || {};
    var headers = {
      'X-Requested-With': 'WorkCal',
      Accept: 'application/json'
    };
    var fetchOpts = {
      method: method,
      credentials: 'same-origin',
      headers: headers,
      cache: 'no-store'
    };
    if (body !== undefined && body !== null) {
      headers['Content-Type'] = 'application/json';
      fetchOpts.body = JSON.stringify(body);
    }

    return fetch(url, fetchOpts).then(function (resp) {
      return resp
        .text()
        .then(function (txt) {
          var data = null;
          if (txt) {
            try {
              data = JSON.parse(txt);
            } catch (e) {
              data = { error: txt };
            }
          }
          if (!resp.ok) {
            var err = new Error((data && data.error) || '请求失败（HTTP ' + resp.status + '）');
            err.status = resp.status;
            err.code = data && data.code;
            if (resp.status === 401 && !opts.ignoreUnauthorized && unauthorizedHandler) {
              unauthorizedHandler(err);
            }
            throw err;
          }
          return data;
        });
    });
  }

  WorkCal.Api = {
    setUnauthorizedHandler: setUnauthorizedHandler,

    // 认证与个人账号
    getMe: function () {
      return request('GET', '/api/auth/me', null, { ignoreUnauthorized: true });
    },
    login: function (username, password) {
      return request(
        'POST',
        '/api/auth/login',
        { username: username, password: password },
        { ignoreUnauthorized: true }
      );
    },
    logout: function () {
      return request('POST', '/api/auth/logout', {}, { ignoreUnauthorized: true });
    },
    updateProfile: function (displayName) {
      return request('PUT', '/api/auth/profile', { displayName: displayName });
    },
    changePassword: function (currentPassword, newPassword) {
      return request('PUT', '/api/auth/password', {
        currentPassword: currentPassword,
        newPassword: newPassword
      });
    },
    resetIcsToken: function () {
      return request('POST', '/api/auth/ics-token/reset', {});
    },

    // 日历状态与节假日
    getState: function () {
      return request('GET', '/api/state');
    },
    getHolidays: function () {
      return request('GET', '/api/holidays');
    },

    // 类别
    addCategory: function (name, color) {
      return request('POST', '/api/categories', { name: name, color: color });
    },
    updateCategory: function (id, patch) {
      return request('PATCH', '/api/categories/' + encodeURIComponent(id), patch);
    },
    deleteCategory: function (id) {
      return request('DELETE', '/api/categories/' + encodeURIComponent(id));
    },
    reorderCategories: function (orderedIds) {
      return request('PUT', '/api/categories/reorder', { orderedIds: orderedIds });
    },

    // 日程
    addEvent: function (data) {
      return request('POST', '/api/events', data);
    },
    updateEvent: function (id, patch) {
      return request('PATCH', '/api/events/' + encodeURIComponent(id), patch);
    },
    deleteEvent: function (id) {
      return request('DELETE', '/api/events/' + encodeURIComponent(id));
    },
    clearAllEvents: function () {
      return request('DELETE', '/api/events');
    },
    importLedger: function (entries) {
      return request('POST', '/api/events/import-ledger', { entries: entries });
    },
    loadSampleData: function (year, month) {
      return request('POST', '/api/events/sample', { year: year, month: month });
    },
    restoreBackup: function (data, mode) {
      return request('POST', '/api/backup/restore', {
        categories: data.categories,
        events: data.events,
        mode: mode || 'replace'
      });
    },

    // 管理员后台
    adminListUsers: function () {
      return request('GET', '/api/admin/users');
    },
    adminCreateUser: function (payload) {
      return request('POST', '/api/admin/users', payload);
    },
    adminUpdateUser: function (id, patch) {
      return request('PATCH', '/api/admin/users/' + encodeURIComponent(id), patch);
    },
    adminResetPassword: function (id, password) {
      return request('POST', '/api/admin/users/' + encodeURIComponent(id) + '/reset-password', {
        password: password || ''
      });
    },
    adminDeleteUser: function (id) {
      return request('DELETE', '/api/admin/users/' + encodeURIComponent(id));
    },
    adminGetHolidays: function () {
      return request('GET', '/api/admin/holidays');
    },
    adminSyncHolidays: function (year, overwriteManual) {
      return request('POST', '/api/admin/holidays/sync', {
        year: year,
        overwriteManual: Boolean(overwriteManual)
      });
    },
    adminUpsertHoliday: function (dateStr, type, name) {
      return request('PUT', '/api/admin/holidays/' + encodeURIComponent(dateStr), {
        type: type,
        name: name
      });
    },
    adminDeleteHoliday: function (dateStr) {
      return request('DELETE', '/api/admin/holidays/' + encodeURIComponent(dateStr));
    },
    adminListBackups: function () {
      return request('GET', '/api/admin/backups');
    },
    adminCreateBackup: function () {
      return request('POST', '/api/admin/backups', {});
    }
  };
})(typeof window !== 'undefined' ? window : globalThis);
