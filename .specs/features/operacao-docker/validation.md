# Operação & Docker Validation

**Date**: 2026-09-13
**Spec**: `.specs/features/operacao-docker/spec.md`
**Diff range**: `01fb69d..60d4b74` (commit before first `operacao-docker` commit → last task commit, T1–T9). No `design.md` exists for this feature (documented, user decision).
**Verifier**: independent sub-agent (author ≠ verifier)

Working-tree note: `.specs/features/operacao-docker/spec.md` had one uncommitted change at verification time — the Requirement Traceability table updated from `Design/Pending` to `Execute/Implementing` (19/19 mapped). This is pre-existing author housekeeping, not part of the code diff, and was left untouched (Verifier is read-only over the real tree).

---

## Task Completion

| Task | Status  | Notes |
| ---- | ------- | ----- |
| T1   | ✅ Done | `spring.datasource.*`, `spring.config.import`, `.env.example` |
| T2   | ✅ Done | `RequiredEnvironmentValidator` fail-fast |
| T3   | ✅ Done | `@Lazy` WebDriver (3 points) |
| T4   | ✅ Done | `Dockerfile` + `.dockerignore` |
| T5   | ✅ Done | `docker-compose.yml` (dev) |
| T6   | ✅ Done | `docker-compose.prod.yml` |
| T7   | ✅ Done | Log prefixes in DETECCAO/ColetaScheduler/AmazonProductScraper |
| T8   | ✅ Done | Cycle-end success/failure counts |
| T9   | ✅ Done | Flyway history + `ddl-auto=validate` lock-in test |

---

## Spec-Anchored Acceptance Criteria

| Requirement | Spec-defined outcome | `file:line` + evidence | Result |
| --- | --- | --- | --- |
| OPS-01 | `docker-compose.yml` dev brings up only Postgres, env vars from `.env` | `docker-compose.yml:1-15` — single `postgres` service, `POSTGRES_USER: ${DB_USER}` / `POSTGRES_PASSWORD: ${DB_PASSWORD}` (from `.env`, no `env_file:` needed — Compose auto-loads `.env` in the project dir) | ✅ PASS |
| OPS-02 | Postgres service in dev compose exposes a healthcheck | `docker-compose.yml:10-14` — `test: ["CMD-SHELL", "pg_isready -U $$POSTGRES_USER -d $$POSTGRES_DB"]`, `interval: 5s`, `retries: 5` | ✅ PASS |
| OPS-03 | WHILE `selenium.remote.url` empty THEN local ChromeDriver | `SeleniumConfig.java:32-38` — `if (!remoteUrl.isBlank())` → `RemoteWebDriver`, else `WebDriverManager.chromedriver().setup(); new ChromeDriver(...)`. `docker-compose.yml` never sets `SELENIUM_REMOTE_URL`, so the property resolves empty by default (`@Value("${selenium.remote.url:}")`). No dedicated test exercises this exact branch with an empty value (see Known Limitations) | ⚠️ PASS (code-verified), not test-verified |
| OPS-04 | `docker-compose.prod.yml` brings up postgres + selenium + app | `docker-compose.prod.yml:1-49` — 3 services: `postgres` (image `postgres:16-alpine`), `selenium` (image `selenium/standalone-chrome:4.37.0`), `app` (`build: .`) | ✅ PASS |
| OPS-05 | `app` depends on `service_healthy` of postgres AND selenium | `docker-compose.prod.yml:41-45` — `depends_on: { postgres: {condition: service_healthy}, selenium: {condition: service_healthy} }` | ✅ PASS |
| OPS-06 | WHILE `selenium.remote.url` set to `selenium` service THEN `RemoteWebDriver` | `docker-compose.prod.yml:40` — `SELENIUM_REMOTE_URL: http://selenium:4444/wd/hub`; `SeleniumConfig.java:34-35` — non-blank branch returns `new RemoteWebDriver(...)`. No unit/integration test drives this branch with a non-blank URL — see Known Limitations | ⚠️ PASS (code-verified), not test-verified |
| OPS-07 | `restart: unless-stopped` on all 3 prod services | `docker-compose.prod.yml:15,25,46` — present on `postgres`, `selenium`, `app` | ✅ PASS |
| OPS-08 | Postgres data survives `docker compose down` without `-v` | `docker-compose.prod.yml:8-9,48-49` — named volume `postgres_data:/var/lib/postgresql/data`, declared under top-level `volumes:` | ✅ PASS |
| OPS-09 | All credentials/endpoints (Postgres, Telegram, Evolution, OpenRouter, affiliate tag) read from env vars | `application.properties:7-9` (`${DB_URL:}`/`${DB_USER:}`/`${DB_PASSWORD:}`, no default); `RequiredEnvironmentValidator.java:17-27` — full list of 10 required properties (`spring.datasource.url/username/password`, `telegram.bot.token`, `evolution.api.url/key/instance`, `openrouter.api-key/model`, `afiliado.tag`) | ✅ PASS |
| OPS-10 | Versioned `.env.example` with placeholders; real `.env` git-ignored | `.env.example:1-24` — all 11 keys with non-real placeholder values; `.gitignore:36-38` — `.env`, `.env.local`, `.env.*.local` ignored, `.env.example` not matched | ✅ PASS |
| OPS-11 | Missing required env var → fail-fast at boot naming the variable | `RequiredEnvironmentValidator.java:40-50` — collects all missing/blank required properties, throws single `IllegalStateException` listing all; `RequiredEnvironmentValidatorTest.java:28-36` (`hasMessageContaining("afiliado.tag")` for one missing var), `:38-50` (3 missing, message contains all 3 names), `:52-57` (all present → no exception) | ✅ PASS |
| OPS-12 | Every relevant log line prefixed by cycle stage (COLETA/DETECCAO/ENRIQUECIMENTO/DISPARO) | `PromotionDetectionService.java:36,46` (`DETECCAO:`); `ColetaScheduler.java:18,20` (`COLETA:`); `AmazonProductScraper.java:42,59,65,77` (`COLETA:`); assertions: `PromotionDetectionServiceTest.java:176,191,204-206` (`startsWith("DETECCAO:")`), `ColetaSchedulerTest.java:49-52` (`.equals("COLETA: ciclo agendado iniciado"/"...finalizado")`), `AmazonProductScraperTest.java:160-213` (4 tests asserting `startsWith("COLETA:")`) | ✅ PASS |
| OPS-13 | End-of-cycle summary logs counts of processed/success/failure | `ColetaService.java:60-61` — `COLETA: ciclo concluido - categoriasProcessadas={}, produtosExtraidos={}, falhasCategoria={}`; `DisparoService.java:94-95` — `DISPARO: ciclo concluido - copiasViaLlm={}, copiasViaTemplate={}, enviosSucesso={}, enviosFalha={}`; asserted by `ColetaServiceTest.java:237-241` and `DisparoServiceTest.java:433-435,455-458` | ✅ PASS |
| OPS-14 (edge case) | Postgres healthcheck not reached in time → `app` does not start | `docker-compose.prod.yml:41-43` — `depends_on: postgres: condition: service_healthy`. This is native Compose behavior, not project code/test — see Known Limitations | ⚠️ PASS (config-verified), never forced/observed to fail |
| OPS-15 (edge case) | Selenium healthcheck not reached in time → `app` does not start | `docker-compose.prod.yml:43-45` — `depends_on: selenium: condition: service_healthy`, healthcheck at `:20-24` (`check-grid.sh`). Same caveat as OPS-14 | ⚠️ PASS (config-verified), never forced/observed to fail |
| OPS-16 | Schema managed exclusively via Flyway migrations, never `ddl-auto=update/create` | Migrations present: `src/main/resources/db/migration/{V1__create_scraping_coleta_tables.sql, V2__seed_enriquecimento_config.sql, V3__create_canais_disparo_tables.sql}`; `application.properties:16` — `ddl-auto=validate` (not `update`/`create`); `BotAmazonSpringApplicationIT.java:57-63` — asserts `flyway_schema_history` has exactly versions `1,2,3` all `success=true` | ✅ PASS |
| OPS-17 | `ddl-auto=validate` configured | `application.properties:16`; `BotAmazonSpringApplicationIT.java:64` — `assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate")` | ✅ PASS |
| OPS-18 | Seed-inserting migrations versioned alongside the schema migration they assume | `V2__seed_enriquecimento_config.sql` versioned next to `V1`/`V3` in the same `db/migration` folder; `BotAmazonSpringApplicationIT.java:41-45` (existing `contextLoadsAndMigrationApplies` test) asserts seed row counts (`categoriasSeed=5`, `appConfigSeed=10`) | ✅ PASS |
| OPS-19 | Boot fails fast if a pending migration wasn't applied | `application.properties:10-15` — comment documenting the native Flyway + `ddl-auto=validate` fail-fast mechanism; indirectly covered — `BotAmazonSpringApplicationIT.java:57-63` — a pending/unapplied migration could never appear in `flyway_schema_history` as `success=true`, so the assertion would fail in that scenario. No test forces an actual pending-migration boot failure | ⚠️ PASS (documented + indirectly covered), not directly tested |

**Status**: 14/19 criteria fully PASS with direct evidence; 5 flagged ⚠️ where the underlying config/code is verified but the exact live behavior (RemoteWebDriver materializing, a forced healthcheck timeout, or a forced pending-migration boot failure) was never directly observed by a test or a forced failure scenario. None of the 5 are unevidenced — all have concrete `file:line` backing — but they rest on documented framework/Compose behavior rather than a project-authored test proving the failure path. See Known Limitations.

---

## Discrimination Sensor

Isolated scratch: `git worktree add ../bot-amazon-spring-verify-scratch HEAD` (never `git stash`). Baseline `git status --porcelain` on the real tree before sensor work: ` M .specs/features/operacao-docker/spec.md` (pre-existing, unrelated to sensor work).

| # | File:line | Mutation | Command | Result |
| - | --- | --- | --- | --- |
| 1 | `scraper/AmazonProductScraper.java:30` | Removed `@Lazy` from the `WebDriver` constructor parameter (3rd of the 3 claimed `@Lazy` points) | `mvn test -Dtest=SeleniumConfigTest` | ✅ Killed — `containsSingleton("webDriver")` flipped from `false` to `true`, assertion failed |
| 2 | `service/PromotionDetectionService.java:44` | `quedaRelevante && !jaSinalizadoNessePreco` → dropped the `!` (`quedaRelevante && jaSinalizadoNessePreco`) | `mvn test -Dtest=PromotionDetectionServiceTest` | ✅ Killed — 5/9 tests failed (eligible-candidate assertions returned empty lists) |
| 3 | `service/PromotionDetectionService.java:46` | Changed log prefix `"DETECCAO: produto avaliado..."` → `"DETECTION: produto avaliado..."` | `mvn test -Dtest=PromotionDetectionServiceTest` | ✅ Killed — 2/9 tests failed (`startsWith("DETECCAO:")` assertions), confirming the T7 tests assert the literal prefix, not just "a log happened" |
| 4 | `resources/application.properties:16` | `spring.jpa.hibernate.ddl-auto=validate` → `=update` | `mvn verify -Dtest=BotAmazonSpringApplicationIT` | ✅ Killed — `flywaySchemaHistoryTemAsTresMigrationsAplicadasComSucessoEDdlAutoEhValidate` failed: `expected: "validate" but was: "update"` |
| 5 | `service/DisparoService.java:149` | `enviarComRetry` catch-block `return false;` → `return true;` (always reports success) | `mvn test -Dtest=DisparoServiceTest` | ✅ Killed — `logaContagemDeEnviosSucessoEFalhaAoFinalDoCiclo` (T8) failed: expected `enviosFalha=1`, no matching log line found |

**Sensor depth**: lightweight, expanded to 5 mutations (default tier is 1-3; 5 were run to directly probe every candidate area the orchestrator flagged as risk: `@Lazy` wiring, detection boolean logic, log-prefix precision, Flyway/ddl-auto lock-in, and the T8 send-outcome counters).

**Result**: 5/5 killed — ✅ PASS

Cleanup: `git worktree remove --force ../bot-amazon-spring-verify-scratch` unregistered the worktree from git (`git worktree list` now shows only the main tree, `.git/worktrees/` is empty). The on-disk directory itself could not be deleted (`rm -rf`, PowerShell `Remove-Item`, `cmd rmdir` all failed with "used by another process" / "Device or resource busy" — no `java`/`mvn`/OneDrive/Search-Indexer process was found holding it; likely a transient Windows/WSL handle). This is a harmless, empty, git-unregistered leftover directory outside the actual repository — it does not affect the real tree. **Real tree `git status --porcelain` confirmed identical before and after all sensor work**: ` M .specs/features/operacao-docker/spec.md` (same pre-existing line, unchanged).

---

## Test Integrity Check (5 protected pre-existing log-asserting classes, T7 Done-when)

| Class | Diff across whole feature (`01fb69d..60d4b74`) | Verdict |
| --- | --- | --- |
| `ColetaServiceTest.java` | +30 lines, 0 deletions (1 new test method: `logaResumoDoCicloComCategoriasProcessadasProdutosExtraidosEFalhasCategoria`, T8) | ✅ Additive only |
| `DisparoServiceTest.java` | +23 lines, 0 deletions (1 new test method: `logaContagemDeEnviosSucessoEFalhaAoFinalDoCiclo`, T8) | ✅ Additive only |
| `DisparoSchedulerTest.java` | No diff (not listed in `git diff --stat`) | ✅ Untouched |
| `CopyGenerationServiceTest.java` | No diff | ✅ Untouched |
| `BannerImageServiceIT.java` | No diff | ✅ Untouched |

Specifically confirmed unmodified inside `DisparoServiceTest.java`: the exact-2-WARN-count assertion (`canalComCategoriasAceitasVaziasOuNulasEhIgnoradoComWarn`, line 356-368, `.count()).isEqualTo(2)`) and the `copiasViaLlm=1`/`copiasViaTemplate=1` assertion (`logaContagemAgregadaDeCopiasViaLlmECopiasViaTemplateAoFinalDoCiclo`, line 414-436) — both pre-existing, byte-for-byte unchanged, sitting above the two newly appended T8 test methods.

---

## Gate Check

- **Gate command**: `mvn clean verify` (wrapper at `C:\Users\jmartinsc\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd`)
- **Docker**: confirmed running (`docker info` exit 0) before the gate — Testcontainers-backed ITs ran for real, not silently skipped
- **Result**: 109 passed (80 unit + 29 integration), 0 failed, 0 skipped — **BUILD SUCCESS**
- **Independently re-derived**, not taken from `tasks.md`'s self-reported T9 count of "109 testes, 0 falhas: 80 unit + 29 integration" — the numbers match exactly

---

## Code Quality

| Principle | Status |
| --- | --- |
| Minimum code | ✅ |
| Surgical changes | ✅ |
| No scope creep | ✅ |
| Matches patterns (existing `ETAPA:` log convention extended, not reinvented) | ✅ |
| Spec-anchored outcome check (asserted values match spec) | ✅ — see per-AC table |
| Every test maps to a spec requirement or Done-when criterion | ✅ |
| Documented guidelines followed | none found (no `AGENTS.md`) — strong defaults applied, consistent with prior 3 features per `tasks.md` |

---

## Known Limitations (independently verified)

1. **OPS-06 — `RemoteWebDriver` branch never observed live, and never unit-tested.** `SeleniumConfig.java:32-38` branches on `remoteUrl.isBlank()`. `SeleniumConfigTest.java` contains exactly one test, and it only asserts that the `webDriver` bean is not a materialized singleton after context refresh — it never sets `selenium.remote.url` to a non-blank value and never asserts a `RemoteWebDriver` instance is produced. Because T3 applied `@Lazy` at three points (bean, `webDriverWait` parameter, `AmazonProductScraper` constructor parameter), the driver only materializes on first real Selenium call, and the T6 smoke test never triggered an actual `buscarPorKeyword` scrape against the containerized `selenium` service. So the evidence for OPS-06 is: (a) the branch code itself is unchanged and correct by inspection, and (b) `docker compose config` in T6's session confirmed `SELENIUM_REMOTE_URL: http://selenium:4444/wd/hub` reaches the `app` container. There is no test — unit or integration — anywhere in the diff that exercises the non-blank-URL branch and asserts a `RemoteWebDriver` comes out. This is a genuine coverage gap on a P1 acceptance criterion, not just a documentation caveat.

2. **OPS-14/OPS-15 — healthcheck-blocks-`app`-start never forced.** Both edge cases rest entirely on `depends_on: condition: service_healthy` in `docker-compose.prod.yml:41-45`, which is Docker Compose's own documented, non-project behavior. Nothing in this feature (no test, no script) intentionally breaks a healthcheck (e.g., wrong `pg_isready` args, wrong grid port) and confirms `app` actually fails to start or stays `Created`/`Waiting`. The T6 session only observed the *normal* healthy-path ordering (`postgres`/`selenium` → `Healthy` → `app` → `Starting`), which demonstrates the dependency wiring exists but not that the failure branch works. This is standard, well-documented Compose behavior, so the residual risk is low, but it is still an unverified claim in the strict sense — there is no project-authored proof, only inference from correct config plus vendor documentation.

3. **OPS-19 — pending-migration-fails-boot never forced with a real pending migration**, mirroring OPS-14/15's pattern: the discrimination sensor mutation #4 (`ddl-auto=update`) is a reasonable adjacent proxy that confirms the *test* can catch a `ddl-auto` regression, but no test in the suite actually introduces a genuinely pending/unapplied Flyway migration and confirms the boot throws. This is a lower-severity version of the same "documented framework behavior, not project-tested" pattern as items 1-2, and was not called out as an open caveat in `tasks.md`, so it is flagged here as an independent finding.

None of these three items are FAIL-worthy on their own — each has partial evidence (correct, unchanged code; correct config; a killed adjacent mutation) and each was already flagged by the author in `tasks.md` (except item 3, found independently here) as an honest, deliberate gap rather than an oversight. They are documented as residual risk, not defects.

---

## Requirement Traceability Update

| Requirement | Previous Status | New Status |
| --- | --- | --- |
| OPS-01..05, OPS-07..13, OPS-16..18 | Implementing | ✅ Verified |
| OPS-06, OPS-14, OPS-15, OPS-19 | Implementing | ✅ Verified (config/code-evidenced; live/failure-path behavior not directly tested — see Known Limitations) |

(Not written back to `spec.md` by the Verifier — read-only over the real tree per role definition; orchestrator may apply this update.)

---

## Summary

**Overall**: ✅ Ready (PASS)

**Spec-anchored check**: 19/19 requirements have concrete `file:line` evidence; 14/19 fully test-verified, 5/19 config/code-verified with a documented live-behavior gap (no spec-precision gaps — the spec's defined outcomes are all matched by the evidence found)
**Sensor**: 5/5 mutations killed
**Gate**: 109 passed, 0 failed, 0 skipped (Docker confirmed active, Testcontainers ITs ran for real)

**What works**: Dev/prod Docker Compose topology, fail-fast env validation, lazy WebDriver wiring (all 3 points individually load-bearing per sensor), per-stage log prefixes with literal-string test assertions, end-of-cycle success/failure counters additive to existing tests, Flyway migration history + `ddl-auto=validate` locked in by a dedicated test.

**Issues found**: None FAIL-worthy. Three residual, honestly-scoped coverage gaps (OPS-06 RemoteWebDriver branch, OPS-14/15 healthcheck-blocks-start, OPS-19 pending-migration-fails-boot) rest on unchanged/correct code plus documented Compose/Flyway framework behavior rather than a project-authored test proving the failure path.

**Next steps**: Optional hardening (not blocking this feature): add a `SeleniumConfigTest` case that sets `selenium.remote.url` to a non-blank value and asserts a `RemoteWebDriver` is produced; optionally add a Testcontainers-based test that intentionally breaks a healthcheck to prove `app` doesn't start (higher cost/value tradeoff, likely acceptable to defer given Compose's well-documented behavior).
