# llm-text2sql

Text-to-SQL REST service: Spring Boot 4 + Spring AI 2 + Oracle Database 26ai. LLM provider is switchable — local **Ollama** (default, no API key) or **Anthropic Claude**.

Natural-language question → grounded Oracle SQL (schema snapshot injected into the prompt) → validated (read-only guard) → executed with row cap and timeout → JSON result.

## Architecture

```
POST /api/v1/query
   └─ TextToSqlService
        ├─ SchemaService          in-memory schema snapshot from ALL_TABLES / ALL_TAB_COLUMNS / constraints
        ├─ ChatClient             Ollama (local) or Anthropic — structured output → {answerable, sql, explanation}
        ├─ SqlGuard               SELECT/WITH only, single statement, forbidden-keyword scan
        └─ QueryExecutionService  read-only pool, maxRows cap, query timeout
```

Database schema is versioned with **Flyway** (`src/main/resources/db/migration`) — runs on app startup over its own writable connection; the query pool stays read-only. `V1__demo_schema.sql` / `V2__demo_data.sql` ship a demo shop schema; replace with your own migrations.

## Configuration

| Env var | Default | Purpose |
|---|---|---|
| `LLM_PROVIDER` | `ollama` | `ollama` (local) or `anthropic` |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server |
| `OLLAMA_MODEL` | `qwen2.5-coder:7b` | Local model (good SQL model at 7B) |
| `ANTHROPIC_API_KEY` | — | Required only when `LLM_PROVIDER=anthropic` |
| `ORACLE_URL` | `jdbc:oracle:thin:@//localhost:1521/FREEPDB1` | JDBC URL |
| `ORACLE_USERNAME` | `app_user` | DB user |
| `ORACLE_PASSWORD` | `AppPassword1` | DB password — matches the `docker-compose.yaml` default; override both together |
| `TEXT2SQL_SCHEMA_OWNER` | `ORACLE_USERNAME` | Schema to introspect and query |

Tunables under `app.text2sql` in `application.yaml`: `default-max-rows` (100), `hard-max-rows` (1000), `query-timeout-seconds` (30).

## Run

Docker Compose starts only the infra (Oracle Free 26ai + Ollama, auto-pulling the model); run the app itself locally:

```sh
docker compose up          # starts oracle + ollama, waits on healthchecks
mvn spring-boot:run         # app connects to localhost:1521 / localhost:11434
```

- Oracle `localhost:1521/FREEPDB1`, Ollama `localhost:11434`. Both cached in volumes across restarts (`docker compose down -v` resets, including the database).
- Flyway creates and seeds the demo schema on first app start.
- First `docker compose up` is slow: Oracle initializes and Ollama downloads ~5 GB model.
- To use Claude instead of the local model: `LLM_PROVIDER=anthropic ANTHROPIC_API_KEY=sk-ant-... mvn spring-boot:run`
- NVIDIA GPU for Ollama: uncomment the `deploy` block on the `ollama` service in `docker-compose.yaml`.

## API

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/query` | Generate SQL, execute, return rows |
| POST | `/api/v1/sql/generate` | Generate SQL only (dry run) |
| GET | `/api/v1/schema` | Current schema snapshot |
| POST | `/api/v1/schema/refresh` | Rebuild snapshot after migrations |
| GET | `/actuator/health` | Health probe |

Example:

```sh
curl -s localhost:8080/api/v1/query \
  -H 'content-type: application/json' \
  -d '{"question": "Top 5 customers by total order amount", "maxRows": 50}'
```

Error mapping: `400` invalid request, `422` SQL rejected / question unanswerable / execution error, `502` model failure.

## Insomnia

Import `insomnia-collection.json` (Application menu → Import). Set `base_url` in the environment if not `http://localhost:8080`.

## Security notes

- DB connections used for generated SQL are read-only (Hikari `read-only: true`) **and** `SqlGuard` rejects anything but a single `SELECT`/`WITH` statement — defense in depth against prompt injection through question text. Flyway migrations use a separate writable connection at startup only.
- In production, grant the runtime DB user `SELECT` only and run Flyway with a separate privileged user (`spring.flyway.user`).
- Row cap (`hard-max-rows`) and query timeout bound resource usage.
