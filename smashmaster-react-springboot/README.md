# SmashMaster — React + Spring Boot + PostgreSQL

This is the React frontend and Spring Boot REST API edition of SmashMaster. Tournament rules, player validation, fixture generation, score validation, extra-match generation, leaderboard calculations, history archiving, and exports are handled by the Java backend. The frontend refreshes shared tournament state periodically so multiple open clients see updates from the same database. PostgreSQL persists the roster, matches, settings, and archived tournament summaries/statistics across restarts and redeploys.

## Backend features

- Case-insensitive duplicate-player prevention.
- Configurable tournament name/date, court names and court start/end times, and match duration.
- Server-side doubles fixture generation using the configured court windows and available player time; the scheduler avoids overlapping a player's matches.
- Server-side score validation: scores must be 0–11 and exactly one team must score 11.
- Extra-match generation constrained by eligible players and available court time.
- Leaderboard and current tournament statistics calculated on the server.
- CSV (Excel-compatible), JSON tournament, and WhatsApp text exports.
- Reset archives the current tournament roster, matches, and leaderboard statistics before clearing the active roster and matches.
- PostgreSQL persistence through Spring Data JPA. Tables are created/updated automatically on startup (`ddl-auto=update`).

## Requirements

- Java 17+
- Maven 3.8+
- Node.js 20+
- PostgreSQL 14+ (local development) or a managed PostgreSQL database such as Render PostgreSQL.

## Run locally

1. Create a PostgreSQL database and user, for example database `smashmaster`, user `smashmaster`.
2. In `backend/src/main/resources/application.properties`, the defaults point to `jdbc:postgresql://localhost:5432/smashmaster`, username `smashmaster`, and password `smashmaster`. Override these using environment variables for your own credentials:
   - `SPRING_DATASOURCE_URL`
   - `SPRING_DATASOURCE_USERNAME`
   - `SPRING_DATASOURCE_PASSWORD`
3. Start the backend:
   ```bash
   cd backend
   mvn spring-boot:run
   ```
4. In another terminal, start the frontend:
   ```bash
   cd frontend
   npm install
   npm run dev
   ```
5. Open the Vite URL printed in the terminal. By default the frontend calls `http://localhost:8080`. If the API is elsewhere, create `frontend/.env` from `.env.example` and set `VITE_API_URL`.
6. Check the API at `http://localhost:8080/api/health`.

## Deploy to Render.com

Use **one Render PostgreSQL database, one backend Web Service, and one frontend Static Site**. Keep the database and backend in the same Render region so the backend can use the database's internal connection address.

### 1. Create PostgreSQL

1. In Render, create **New + → PostgreSQL**.
2. Choose a name and region; create the database.
3. From the database's **Connections** panel, copy the **Internal Host**, database name, username, and password. Use the internal host for the backend service.

### 2. Deploy the Spring Boot backend

1. Push this project to a GitHub repository.
2. In Render, create **New + → Web Service** and connect the repository.
3. Set **Root Directory** to `backend`.
4. Build command: `mvn clean package -DskipTests`
5. Start command: `java -jar target/smashmaster-api-1.0.0.jar`
6. Set environment variables:
   - `SPRING_DATASOURCE_URL` = `jdbc:postgresql://INTERNAL_HOST:5432/DATABASE_NAME` (replace the host and database name with the values shown by Render; use the actual port if Render shows a different one).
   - `SPRING_DATASOURCE_USERNAME` = database username.
   - `SPRING_DATASOURCE_PASSWORD` = database password.
   - `FRONTEND_ORIGIN` = the frontend Static Site URL after it has been created, for example `https://your-smashmaster.onrender.com`.
7. Deploy. Check `https://YOUR-BACKEND.onrender.com/api/health`; a healthy response includes `"status":"ok"`.

### 3. Deploy the React frontend

1. In Render, create **New + → Static Site** using the same repository.
2. Set **Root Directory** to `frontend`.
3. Build command: `npm install && npm run build`.
4. Publish directory: `dist`.
5. Add build environment variable `VITE_API_URL=https://YOUR-BACKEND.onrender.com` (no trailing slash).
6. Deploy the frontend. Copy its final URL into the backend's `FRONTEND_ORIGIN` environment variable and redeploy the backend.

## API overview

- `GET /api/health`
- `GET /api/config`, `PUT /api/config`
- `GET /api/players`, `POST /api/players`
- `GET /api/matches`, `POST /api/matches/generate`, `POST /api/matches/extra`
- `POST /api/matches/{id}/score`
- `GET /api/leaderboard`, `GET /api/statistics`
- `GET /api/history`
- `GET /api/export/csv`, `GET /api/export/json`, `GET /api/export/whatsapp`
- `DELETE /api/tournament` — archives current roster, matches, and statistics, then clears the active tournament.

## Important deployment notes

- PostgreSQL is persistent; unlike the earlier in-memory starter, tournament records are not discarded when the backend restarts. Keep a managed database backup policy enabled.
- This edition currently models one active tournament shared by all connected clients. Reset archives it into tournament history.
- Add authentication/roles before exposing reset and scoring endpoints to an untrusted public audience. CORS is restricted using `FRONTEND_ORIGIN`; set it to the exact frontend origin.
- The scheduler respects court windows and player availability, but it is a practical first server-side migration, not a guarantee of a globally optimal schedule for every roster and court configuration. Review the generated fixtures before the tournament.

Author/contact: Bhushan T. — bhushan2005@gmail.com
