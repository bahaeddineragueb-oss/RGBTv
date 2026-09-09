require('dotenv').config();
const express = require('express');
const bcrypt = require('bcryptjs');
const path = require('path');
const { pool, init } = require('./db');
const auth = require('./auth');

const app = express();
app.use(express.json({ limit: '2mb' }));
app.use(require('cors')());

const pub = (u) => ({
  id: u.id, email: u.email, name: u.name,
  admin: !!u.is_admin, disabled: !!u.is_disabled,
});

/* ---------- auth ---------- */
app.post('/api/auth/register', async (req, res) => {
  try {
    const { email, password, name } = req.body || {};
    if (!email || !password || String(password).length < 4) {
      return res.status(400).json({ error: 'email + password (4+) required' });
    }
    const hash = bcrypt.hashSync(String(password), 10);
    const r = await pool.query(
      `INSERT INTO users (email, pass_hash, name) VALUES ($1, $2, $3)
       ON CONFLICT (email) DO NOTHING RETURNING *`,
      [String(email).toLowerCase().trim(), hash, String(name || '').slice(0, 60)]
    );
    if (!r.rows.length) return res.status(409).json({ error: 'email taken' });
    const u = r.rows[0];
    await pool.query(
      `UPDATE users SET is_admin = TRUE WHERE id = $1
       AND NOT EXISTS (SELECT 1 FROM users WHERE is_admin = TRUE AND id <> $1)`,
      [u.id]
    );
    const me = (await pool.query('SELECT * FROM users WHERE id = $1', [u.id])).rows[0];
    res.json({ token: auth.sign(me), user: pub(me) });
  } catch (e) {
    res.status(500).json({ error: 'register failed' });
  }
});

app.post('/api/auth/login', async (req, res) => {
  try {
    const { email, password } = req.body || {};
    const r = await pool.query('SELECT * FROM users WHERE email = $1', [String(email || '').toLowerCase().trim()]);
    const u = r.rows[0];
    if (!u || !bcrypt.compareSync(String(password || ''), u.pass_hash)) {
      return res.status(401).json({ error: 'bad credentials' });
    }
    if (u.is_disabled) return res.status(403).json({ error: 'account disabled' });
    res.json({ token: auth.sign(u), user: pub(u) });
  } catch (e) {
    res.status(500).json({ error: 'login failed' });
  }
});

app.get('/api/me', auth.required, async (req, res) => {
  const r = await pool.query('SELECT * FROM users WHERE id = $1', [req.user.id]);
  if (!r.rows.length) return res.status(404).json({ error: 'gone' });
  res.json({ user: pub(r.rows[0]) });
});

/* ---------- IPTV profiles ---------- */
app.get('/api/profiles', auth.required, async (req, res) => {
  const r = await pool.query('SELECT * FROM profiles WHERE user_id = $1 ORDER BY id', [req.user.id]);
  res.json({ profiles: r.rows });
});

app.post('/api/profiles', auth.required, async (req, res) => {
  const p = req.body || {};
  const r = await pool.query(
    `INSERT INTO profiles (user_id, name, type, url, username, password, mac, epg_url)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8) RETURNING *`,
    [req.user.id, p.name || '', p.type || 'xtream', p.url || '', p.username || '',
     p.password || '', p.mac || '', p.epg_url || '']
  );
  res.json({ profile: r.rows[0] });
});

app.put('/api/profiles/:id', auth.required, async (req, res) => {
  const p = req.body || {};
  const r = await pool.query(
    `UPDATE profiles SET name=$2, type=$3, url=$4, username=$5, password=$6,
     mac=$7, epg_url=$8, updated_at=NOW() WHERE id=$1 AND user_id=$9 RETURNING *`,
    [req.params.id, p.name || '', p.type || 'xtream', p.url || '', p.username || '',
     p.password || '', p.mac || '', p.epg_url || '', req.user.id]
  );
  if (!r.rows.length) return res.status(404).json({ error: 'not found' });
  res.json({ profile: r.rows[0] });
});

app.delete('/api/profiles/:id', auth.required, async (req, res) => {
  await pool.query('DELETE FROM profiles WHERE id=$1 AND user_id=$2', [req.params.id, req.user.id]);
  res.json({ ok: true });
});

/* ---------- sync: favs / history / positions ---------- */
app.post('/api/sync/push', auth.required, async (req, res) => {
  try {
    const b = req.body || {};
    const uid = req.user.id;
    for (const f of b.favs || []) {
      await pool.query(
        `INSERT INTO favs (user_id, acc_key, type, item_id, name, img, data, updated_at)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8)
         ON CONFLICT (user_id, acc_key, type, item_id) DO UPDATE SET
         name=EXCLUDED.name, img=EXCLUDED.img, data=EXCLUDED.data, updated_at=EXCLUDED.updated_at
         WHERE EXCLUDED.updated_at >= favs.updated_at`,
        [uid, f.acc || '', f.type, f.id, f.name || '', f.img || '', f.data || '', f.at || 0]
      );
    }
    for (const h of b.history || []) {
      await pool.query(
        `INSERT INTO history (user_id, acc_key, type, item_id, name, img, data, updated_at)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8)
         ON CONFLICT (user_id, acc_key, type, item_id) DO UPDATE SET
         name=EXCLUDED.name, img=EXCLUDED.img, data=EXCLUDED.data, updated_at=EXCLUDED.updated_at
         WHERE EXCLUDED.updated_at >= history.updated_at`,
        [uid, h.acc || '', h.type, h.id, h.name || '', h.img || '', h.data || '', h.at || 0]
      );
    }
    for (const p of b.positions || []) {
      await pool.query(
        `INSERT INTO positions (user_id, acc_key, item_key, pos, dur, updated_at)
         VALUES ($1,$2,$3,$4,$5,$6)
         ON CONFLICT (user_id, acc_key, item_key) DO UPDATE SET
         pos=EXCLUDED.pos, dur=EXCLUDED.dur, updated_at=EXCLUDED.updated_at
         WHERE EXCLUDED.updated_at >= positions.updated_at`,
        [uid, p.acc || '', p.key, p.pos || 0, p.dur || 0, p.at || 0]
      );
    }
    res.json({ ok: true });
  } catch (e) {
    res.status(500).json({ error: 'push failed' });
  }
});

app.get('/api/sync/pull', auth.required, async (req, res) => {
  try {
    const uid = req.user.id;
    const since = Number(req.query.since || 0);
    const [f, h, p] = await Promise.all([
      pool.query('SELECT * FROM favs WHERE user_id=$1 AND updated_at>$2 ORDER BY updated_at DESC LIMIT 500', [uid, since]),
      pool.query('SELECT * FROM history WHERE user_id=$1 AND updated_at>$2 ORDER BY updated_at DESC LIMIT 200', [uid, since]),
      pool.query('SELECT * FROM positions WHERE user_id=$1 AND updated_at>$2 LIMIT 500', [uid, since]),
    ]);
    res.json({ favs: f.rows, history: h.rows, positions: p.rows, ts: Date.now() });
  } catch (e) {
    res.status(500).json({ error: 'pull failed' });
  }
});

/* ---------- admin ---------- */
app.get('/api/admin/stats', auth.adminOnly, async (req, res) => {
  const [u, p, f] = await Promise.all([
    pool.query('SELECT COUNT(*) c FROM users'),
    pool.query('SELECT COUNT(*) c FROM profiles'),
    pool.query('SELECT COUNT(*) c FROM favs'),
  ]);
  res.json({ users: +u.rows[0].c, profiles: +p.rows[0].c, favs: +f.rows[0].c });
});

app.get('/api/admin/users', auth.adminOnly, async (req, res) => {
  const q = `%${req.query.q || ''}%`;
  const r = await pool.query(
    `SELECT u.*, (SELECT COUNT(*) FROM profiles p WHERE p.user_id = u.id) AS profiles
     FROM users u WHERE email ILIKE $1 OR name ILIKE $1 ORDER BY id DESC LIMIT 200`, [q]
  );
  res.json({ users: r.rows.map((u) => ({ ...pub(u), profiles: +u.profiles, created: u.created_at })) });
});

app.post('/api/admin/users/:id/disable', auth.adminOnly, async (req, res) => {
  if (+req.params.id === req.user.id) return res.status(400).json({ error: 'not yourself' });
  await pool.query('UPDATE users SET is_disabled = $2 WHERE id = $1', [req.params.id, !!req.body.disabled]);
  res.json({ ok: true });
});

app.post('/api/admin/users/:id/admin', auth.adminOnly, async (req, res) => {
  if (+req.params.id === req.user.id) return res.status(400).json({ error: 'not yourself' });
  await pool.query('UPDATE users SET is_admin = $2 WHERE id = $1', [req.params.id, !!req.body.admin]);
  res.json({ ok: true });
});

/* ---------- admin web ---------- */
app.get('/admin', (req, res) => res.sendFile(path.join(__dirname, 'admin.html')));
app.get('/api/health', (req, res) => res.json({ ok: true, ts: Date.now() }));

const PORT = process.env.PORT || 8080;
init().then(() => {
  app.listen(PORT, () => console.log('RGBTv backend on :' + PORT));
}).catch((e) => {
  console.error('DB init failed:', e.message);
  process.exit(1);
});
