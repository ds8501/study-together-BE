# Study Together API

REST API for authentication, a two-person study workspace, workspace invite codes, and independently owned study plans/topics.

## Local setup

1. Copy `.env.example` to `.env` and replace `AUTH_SECRET` with a random string of at least 32 characters.
2. Start PostgreSQL with `docker compose up -d db`.
3. Install dependencies with `pnpm install`.
4. Run `pnpm db:generate` then `pnpm db:migrate`.
5. Start the API with `pnpm dev` (port 4000 by default).

The frontend uses `NEXT_PUBLIC_API_URL=http://localhost:4000`. Registration without an invite creates a workspace and personal plan; the account owner shares the workspace invite code with one other person, who enters it during registration. A workspace is capped at two members.

Sessions use a signed, HTTP-only, same-site cookie. Use HTTPS in production and set `NODE_ENV=production`. Passwords are hashed with bcrypt. Study plan and topic write operations are always scoped to the authenticated owner; workspace membership checks gate shared reads.

## Render deployment

Deploy this backend directory as the service root. Set the Render build command to `yarn install --non-interactive && yarn build` and the start command to `yarn start`. The build generates Prisma Client and compiles `src/server.ts` to `dist/server.js`, which is the production entry point. `render.yaml` contains the same commands for Blueprint-managed services.

Configure `DATABASE_URL`, `AUTH_SECRET`, `FRONTEND_ORIGIN`, and `NODE_ENV=production` in the Render service environment. Render supplies `PORT`; the API listens on `0.0.0.0` by default.

### Google sign-in

Create a Google OAuth client with application type **Web application**. Add the deployed frontend callback URL as an authorized redirect URI:

```text
https://study-together-five.vercel.app/api/auth/google/callback
```

Set these Render environment variables (keep the client secret private):

```text
GOOGLE_CLIENT_ID=<web client ID>
GOOGLE_CLIENT_SECRET=<web client secret>
GOOGLE_REDIRECT_URI=https://study-together-five.vercel.app/api/auth/google/callback
FRONTEND_URL=https://study-together-five.vercel.app
```

The Vercel `/api/*` rewrite forwards the callback to this backend, so the session cookie is set on the frontend domain. For local development, add `http://127.0.0.1:4000/auth/google/callback` as another authorized redirect URI and use these local backend values:

```text
GOOGLE_REDIRECT_URI=http://127.0.0.1:4000/auth/google/callback
FRONTEND_URL=http://127.0.0.1:3000
```

Google sign-in creates a personal workspace and roadmap for new accounts. Gmail and Google Workspace accounts are supported; a matching verified email signs into the existing account. Other Google accounts can use the existing email/password sign-in.
