'use strict';

const express = require('express');
const { listCategoriesForUser, listEventsForUser } = require('../db');
const { generateICS } = require('../ics');

const TOKEN_RE = /^[a-f0-9]{32,128}$/i;

function createIcsRouter(db) {
  const router = express.Router();

  function handleIcs(req, res) {
    const rawParam = String(req.params.token || '').replace(/\.ics$/i, '');
    if (!TOKEN_RE.test(rawParam)) {
      return res.status(404).type('text/plain').send('Not Found');
    }

    const user = db
      .prepare('SELECT id, display_name, is_active FROM users WHERE ics_token = ?')
      .get(rawParam);

    if (!user || !user.is_active) {
      return res.status(404).type('text/plain').send('Not Found');
    }

    const categories = listCategoriesForUser(db, user.id);
    const events = listEventsForUser(db, user.id);
    const content = generateICS({
      displayName: user.display_name,
      categories,
      events
    });

    res.setHeader('Content-Type', 'text/calendar; charset=utf-8');
    res.setHeader('Content-Disposition', 'inline; filename="work-calendar.ics"');
    res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, private');
    res.send(content);
  }

  router.get('/:token', handleIcs);
  return router;
}

module.exports = createIcsRouter;
