'use strict';

const AppConfig = require('../js/config');

function addDays(dateStr, delta) {
  const [y, m, d] = dateStr.split('-').map((n) => parseInt(n, 10));
  const dt = new Date(Date.UTC(y, m - 1, d + delta));
  const yy = dt.getUTCFullYear();
  const mm = String(dt.getUTCMonth() + 1).padStart(2, '0');
  const dd = String(dt.getUTCDate()).padStart(2, '0');
  return `${yy}-${mm}-${dd}`;
}

function escapeICSText(str) {
  return String(str || '')
    .replace(/\\/g, '\\\\')
    .replace(/\r?\n/g, '\\n')
    .replace(/;/g, '\\;')
    .replace(/,/g, '\\,');
}

function generateICS({ displayName, categories, events }) {
  const catMap = new Map();
  for (const c of categories || []) {
    catMap.set(c.id, c);
  }
  const fallback = AppConfig.fallbackCategory;
  const calName = displayName ? `${displayName}的工作日程` : '工作日程';
  const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d+Z$/, 'Z');

  const lines = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//WorkCalendar//Cloud Schedule//CN',
    'CALSCALE:GREGORIAN',
    'METHOD:PUBLISH',
    `X-WR-CALNAME:${escapeICSText(calName)}`,
    'X-WR-TIMEZONE:Asia/Shanghai',
    'REFRESH-INTERVAL;VALUE=DURATION:PT1H',
    'X-PUBLISHED-TTL:PT1H'
  ];

  for (const ev of events || []) {
    if (!ev.title || !ev.startDate) continue;
    const cat = catMap.get(ev.categoryId) || fallback;
    const endDate = ev.endDate && ev.endDate >= ev.startDate ? ev.endDate : ev.startDate;
    const summary = `[${cat.name}] ${ev.title}`;
    const descParts = [];
    if (ev.ledgerMeta && ev.ledgerMeta.stage) {
      descParts.push(`评估阶段：${ev.ledgerMeta.stage}`);
    }
    if (ev.ledgerMeta && ev.ledgerMeta.level) {
      descParts.push(`等级：${ev.ledgerMeta.level}`);
    }
    if (ev.ledgerMeta && ev.ledgerMeta.role) {
      descParts.push(`角色：${ev.ledgerMeta.role}`);
    }
    if (ev.notes) {
      descParts.push(ev.notes);
    }

    lines.push('BEGIN:VEVENT');
    lines.push(`UID:${escapeICSText(ev.id)}@work-calendar`);
    lines.push(`DTSTAMP:${stamp}`);

    if (ev.allDay !== false) {
      const exclusiveEnd = addDays(endDate, 1);
      lines.push(`DTSTART;VALUE=DATE:${ev.startDate.replace(/-/g, '')}`);
      lines.push(`DTEND;VALUE=DATE:${exclusiveEnd.replace(/-/g, '')}`);
    } else {
      const sTime = `${(ev.startTime || '09:00').replace(':', '')}00`;
      const eTime = `${(ev.endTime || '18:00').replace(':', '')}00`;
      lines.push(`DTSTART;TZID=Asia/Shanghai:${ev.startDate.replace(/-/g, '')}T${sTime}`);
      lines.push(`DTEND;TZID=Asia/Shanghai:${endDate.replace(/-/g, '')}T${eTime}`);
    }

    lines.push(`SUMMARY:${escapeICSText(summary)}`);
    lines.push(`CATEGORIES:${escapeICSText(cat.name)}`);
    if (descParts.length) {
      lines.push(`DESCRIPTION:${escapeICSText(descParts.join('\n'))}`);
    }
    lines.push('END:VEVENT');
  }

  lines.push('END:VCALENDAR');
  return lines.join('\r\n');
}

module.exports = {
  generateICS
};
