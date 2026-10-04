# Study Together API (Java)

Spring Boot REST API for account access, Google OAuth, two-person workspaces, personal roadmaps, study sessions, and streaks. It uses JDBC against the existing PostgreSQL schema so it can replace the former Node API without changing the frontend routes or existing account data.

## Backend package layout

- `controller/AuthController`: email and session auth endpoints.
- `controller/StudyController`: workspace, roadmap, topic, study session, and streak endpoints.
- `controller/ExternalController`: Google OAuth endpoints.
- `service/AuthService`, `service/StudyService`, and `service/ExternalAuthService`: corresponding API service entry points.
- `repository/StudyTogetherRepository`: PostgreSQL JDBC operations.
- `config/ApiConfiguration`: datasource, transaction, password, and CORS configuration.

## Run locally

Requirements: Docker, Java 21, and Maven (or use the Docker build).

1. Copy `.env.example` to `.env` and set `DATABASE_URL` and an `AUTH_SECRET` of at least 32 characters.
2. If PostgreSQL is not already available, run `docker compose up -d db`.
3. Build and run:

   ```sh
   docker build -t study-together-api .
   docker run --rm --env-file .env -p 4000:4000 study-together-api
   ```

The API health endpoint is `http://localhost:4000/health`. The frontend defaults to this local API URL. Flyway creates the compatible schema on an empty database; it baselines an existing Prisma schema and leaves its data in place.

## Deploy the backend on Render

Deploy this backend repository as a **Web Service** using its Dockerfile:

1. Push the Java backend project to GitHub.
2. In Render, choose **New → Web Service**, connect this backend repository, and choose **Docker** as the runtime. Keep the old Node service running until the new Java service passes the checks below.
3. Keep the root directory as `.`. Render builds the Java 21 Spring Boot service from `Dockerfile`.
4. Add these environment variables:
   - `DATABASE_URL`: the existing Render Postgres **Internal Database URL**. Keep using the database that contains your existing users and plans.
   - `AUTH_SECRET`: keep the same secret as the current backend (and ensure it is at least 32 characters) so existing browser sessions remain valid.
   - `FRONTEND_ORIGIN`: `https://study-together-five.vercel.app`
   - `FRONTEND_URL`: `https://study-together-five.vercel.app`
   - `NODE_ENV`: `production`
   - `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI`: your Google OAuth settings.
5. Set health check path to `/health`, create the service, and wait for the Docker build and deploy to finish.
6. Confirm `https://<your-render-service>.onrender.com/health` returns `{"status":"ok"}`.
7. In the frontend repository, update the `/api/*` rewrite destination in `next.config.ts` to `https://<your-render-service>.onrender.com/:path*`, then redeploy Vercel. The current rewrite points to the old Node service.
8. Set `GOOGLE_REDIRECT_URI` to `https://<your-render-service>.onrender.com/auth/google/callback`, then add that exact URL under **Authorized redirect URIs** in Google Cloud Console. The authorized JavaScript origin should be `https://study-together-five.vercel.app`.

`render.yaml` includes the Docker service and health check. See [Render Docker deploys](https://render.com/docs/docker), [Blueprint reference](https://render.com/docs/blueprint-spec), and [health checks](https://render.com/docs/health-checks).

## API routes

- `GET /health`
- `POST /auth/register`, `POST /auth/login`, `POST /auth/logout`, `GET /auth/me`
- `GET /auth/google`, `GET /auth/google/callback`
- `GET /workspace`, `POST /workspace/invite/rotate`
- `GET /study-plans`, `POST /study-plans/{planId}/topics`
- `PATCH /topics/{topicId}`, `DELETE /topics/{topicId}`
- `GET /study-stats`, `POST /study-sessions`
