/**
 * 工作日程表全局配置
 */
(function (root) {
  'use strict';

  var WorkCal = (root.WorkCal = root.WorkCal || {});

  WorkCal.Config = {
    appName: '工作日程',
    legacyStorageKey: 'work_calendar_v1',

    /**
     * 默认四大类别（按同日取色优先级从高到低排列）
     * 列表顺序即优先级：索引 0 优先级最高
     */
    defaultCategories: [
      { id: 'cat-deadline', name: '提交截止', color: '#D93A3A' },
      { id: 'cat-assessment', name: '外出评估', color: '#D97A12' },
      { id: 'cat-consulting', name: '咨询出差', color: '#2B6CD4' },
      { id: 'cat-rest', name: '休息', color: '#2E9E5B' }
    ],

    /**
     * 当所属类别被删除时的兜底外观
     */
    fallbackCategory: {
      id: '__fallback__',
      name: '未归类（原类别已删除）',
      color: '#64748B',
      isFallback: true
    }
  };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = WorkCal.Config;
  }
})(typeof window !== 'undefined' ? window : globalThis);
