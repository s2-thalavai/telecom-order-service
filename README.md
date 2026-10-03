# Telecom Order Service

A Spring Boot 3.5 / Java 21 new-connection order API that demonstrates Spring MVC's two main async types:
**`CompletableFuture`** (you start and compose the work) and **`DeferredResult`** (someone else delivers the result).

| Endpoint | Type | What it shows |
|---|---|---|
| `POST /api/v1/orders` | `CompletableFuture` | Credit check ∥ plan lookup (`thenCombine`) → reserve number (`thenCompose`) → deadline guard + persist (`thenApplyAsync`) → provisioning (`whenComplete`) → `orTimeout` (504) |
| `GET /api/v1/orders/{id}/await?timeoutSeconds=` | `DeferredResult` | Long-poll: 200 when final, completed AFTER_COMMIT by the provisioning callback; 202 + current state when the wait expires |
| `POST /api/v1/provisioning/callbacks` | sync | Webhook, idempotent and race-safe (`@Version` + state guard) |
| `POST /api/v1/portability/bulk-check` | `CompletableFuture` | Up to 50 independent checks in parallel (`allOf`), per-number `completeOnTimeout` + `exceptionally` |
| `GET /api/v1/plans/{code}` | `CompletableFuture` | `@Cacheable` with Caffeine async mode; failures not cached |
| `GET /api/v1/orders[/{id}]` | sync | Read model |

Simulated downstreams react to the customer's email: `slow` (504), `down` (503), `risky` (REJECTED),
`provfail` (FAILED, number released), `manual` (no automatic callback; use the webhook).

```bash
make run      # http://localhost:8080 → order console with a live timeline
make test     # unit, MockMvc async (asyncStarted/asyncDispatch), JPA
make verify   # + integration tests on real Tomcat + coverage
make up && make smoke
```

See [HOW_TO_RUN.md](HOW_TO_RUN.md), [docs/design.md](docs/design.md), [docs/testing.md](docs/testing.md) and
[CLAUDE.md](CLAUDE.md).
