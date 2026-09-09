const { Pool } = require('pg');
const fs = require('fs');
const path = require('path');

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  max: 10,
  idleTimeoutMillis: 30000,
});

async function init() {
  const sql = fs.readFileSync(path.join(__dirname, 'schema.sql'), 'utf8');
  await pool.query(sql);
  // First user ever created becomes admin automatically.
  await pool.query(
    `UPDATE users SET is_admin = TRUE WHERE id = (SELECT MIN(id) FROM users)
     AND NOT EXISTS (SELECT 1 FROM users WHERE is_admin = TRUE)`
  );
}

module.exports = { pool, init };
