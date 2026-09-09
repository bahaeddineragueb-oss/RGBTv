# RGBTv Backend — accounts, profiles, sync, admin

Node.js + Express + PostgreSQL. The Android app works **offline/local-first**;
the backend is optional cloud: user accounts, server-side IPTV profiles,
favorites/history/resume sync across devices, and an admin panel.

## Run locally (Docker — easiest)

```bash
cd backend
cp .env.example .env   # then edit secrets
docker compose up --build -d
```

- API: `http://localhost:8080/api/health`
- Admin panel: `http://localhost:8080/admin`
- The **first registered user becomes admin** automatically.

## Run without Docker

Needs Node 20+ and PostgreSQL 14+:

```bash
createdb rgbtv
cd backend && cp .env.example .env  # edit DATABASE_URL
npm install && npm start
```

Schema is created automatically on boot (`schema.sql`).

## VPS deploy (sketch)

1. Point a domain to the VPS, install Docker.
2. `docker compose up --build -d` with strong `DB_PASSWORD` + `JWT_SECRET`.
3. Put Caddy/Nginx in front with HTTPS:
   `https://api.yourdomain.com → localhost:8080`.
4. In the Android app: Settings → Cloud → enter `https://api.yourdomain.com`.

## API summary

- `POST /api/auth/register {email,password,name}` → `{token,user}`
- `POST /api/auth/login {email,password}` → `{token,user}`
- `GET /api/me` (auth)
- `GET/POST /api/profiles`, `PUT/DELETE /api/profiles/:id` (auth)
- `POST /api/sync/push {favs,history,positions}` (auth)
- `GET /api/sync/pull?since=ts` (auth)
- Admin: `GET /api/admin/stats`, `GET /api/admin/users?q=`,
  `POST /api/admin/users/:id/disable`, `POST /api/admin/users/:id/admin`
