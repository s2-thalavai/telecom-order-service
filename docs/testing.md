# Testing Strategy

| Layer | Naming | Command |
|---|---|---|
| Unit, MockMvc async slices, `@DataJpaTest` | `*Test` | `make test` |
| Integration on real Tomcat (+ MySQL with Docker) | `*IT` | `make it` |
| All + coverage | | `make verify` |

MockMvc async pattern: `perform(...)` → `request().asyncStarted()` → `perform(asyncDispatch(result))`. MockMvc never fires async timeouts, so timeout behaviour lives in the ITs.

Web slices don't pick up `@ConfigurationPropertiesScan` types. `OrderApiTest` adds `@EnableConfigurationProperties(OrderProperties.class)`.

## Unit and slice tests

| Class | Covers |
|---|---|
| `OrderWorkflowServiceTest` | Approved path (number on the eligibility, provisioning submitted); declined → no number/provisioning; credit failure skips dependent stages; persist failure releases the number; **late workflow abandoned before persisting** (controllable clock); hanging downstream → `TimeoutException` |
| `OrderAwaitServiceTest` | Final order answered immediately; **status change between read and registration not lost**; final event completes every waiter and cleans the registry; non-final events ignored; unknown order |
| `ProvisioningCallbackServiceTest` | Applied, duplicate, losing a concurrent race |
| `NumberReleaseListenerTest` | FAILED releases; ACTIVE/REJECTED don't |
| `BulkEligibilityServiceTest` | Per-number result, error and timeout fallbacks, order preserved |
| `OrderApiTest` | 201 + Location; failed futures → 503 (`Retry-After`) / 504 / 422; field errors; `DeferredResult` set by another thread → 200; 202; unknown → 404 without async; `timeoutSeconds` validation; webhook `applied`; bulk validation and result; plan code validation |
| `OrderTransactionsTest` | Approved/rejected persistence + events; provisioning completes only once; unknown order |

## Integration tests (test profile: 30–50 ms downstreams, provisioning 500 ms, slow 2.5 s, deadline 1 s)

| Class | Covers |
|---|---|
| `OrderWorkflowIT` | 201 → long-poll ACTIVE; REJECTED; 503; 422/400; **504 within the deadline, then no order saved and the number released** once the slow call finishes; 10 parallel orders get distinct numbers; listing |
| `LongPollIT` | Wait expires → 202 + registry cleaned; **webhook completes a waiting request after commit**; several waiters answered; provfail → FAILED and number released after commit; late callback ignored; 404 |
| `ConcurrencyIT` | **10 concurrent duplicate callbacks → exactly one applied, version 1**; success vs failure race → exactly one final state |
| `CacheAndBulkIT` | Plan cached by value (catalog calls counted); failures not cached; bulk check in parallel with per-number fallbacks, within the item timeout |
| `MySqlFlywayIT` | Prod profile on MySQL 8.4: Flyway, BINARY(16) UUIDs, `@Version`, full lifecycle. Skipped without Docker |
