# CLAUDE.md

Context for Claude Code (and other AI assistants) working in this repository.

## What this is

`telecom-order-service`: a Spring Boot 3.5 / Java 21 order API demonstrating `CompletableFuture` composition
(`OrderWorkflowService`) and `DeferredResult` long polling (`OrderAwaitService`) with transactions, events,
caching and concurrency control. Read `docs/design.md` first.

## Commands

| Task | Command |
|---|---|
| Run (H2, console at `/`) | `make run` |
| Unit + slice + JPA tests | `make test` |
| Integration tests | `make it` |
| Everything + coverage | `make verify` |
| Compose / smoke | `make up`, `make smoke` |

## Invariants (keep them true)

1. Every async stage runs on a **named bounded executor** (`integrationExecutor`). Never call `supplyAsync`/`thenApplyAsync` without one.
2. **Deadline guard before irreversible steps.** `orTimeout` doesn't stop earlier stages; `persist()` checks the deadline and releases reserved resources.
3. **Compensate** whatever was reserved when a later step fails (number release in `persist()`, `NumberReleaseListener` for FAILED).
4. **Side effects after commit.** Provisioning is submitted after `createOrder` commits; waiters and number release react in `@TransactionalEventListener(AFTER_COMMIT)`.
5. **Register, then re-check** in `OrderAwaitService`; always clean up in `onCompletion`.
6. Provisioning transitions only from PROVISIONING, protected by `@Version`; a lost race is `applied=false`, not an error.
7. Pass immutable records between stages and threads, never JPA entities.
8. `@Async`, `@Cacheable` and `@Transactional` are proxy-based: call them through other beans.

## Testing conventions

- Workflow: direct executor (`Runnable::run`) + mutable `Clock` in `OrderWorkflowServiceTest`.
- MockMvc: `asyncStarted()` then `asyncDispatch(result)`; slices need `@EnableConfigurationProperties` for property records.
- ITs: unique emails per test (`email(tag)`); assert invariants (no order saved, number released, exactly one applied).
