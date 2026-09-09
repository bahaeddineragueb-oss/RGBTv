const jwt = require('jsonwebtoken');

const SECRET = process.env.JWT_SECRET || 'change-me-in-production';
const EXPIRY = '60d';

function sign(user) {
  return jwt.sign(
    { id: user.id, email: user.email, admin: !!user.is_admin },
    SECRET,
    { expiresIn: EXPIRY }
  );
}

function required(req, res, next) {
  const h = req.headers.authorization || '';
  const tok = h.startsWith('Bearer ') ? h.slice(7) : null;
  if (!tok) return res.status(401).json({ error: 'missing token' });
  try {
    req.user = jwt.verify(tok, SECRET);
    next();
  } catch (e) {
    return res.status(401).json({ error: 'invalid token' });
  }
}

async function adminOnly(req, res, next) {
  required(req, res, () => {
    if (!req.user.admin) return res.status(403).json({ error: 'admin only' });
    next();
  });
}

module.exports = { sign, required, adminOnly };
