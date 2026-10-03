# Design

## The order workflow (CompletableFuture)

```
request thread          integration-* threads                                      provisioning-* (later)
──────────────          ─────────────────────                                      ─────────────────────
placeOrder()
 ├─ creditCheck ───────▶ credit bureau ─┐
 ├─ plans.plan ────────▶ catalog/cache ─┴─ thenCombine → Eligibility
 │                                         thenCompose → reserveNumber (if approved)
 │                                         thenApplyAsync → deadline guard → createOrder (@Tx, event)
 │                                         whenComplete → provisioning.submit ─────▶ callback → completeProvisioning (@Tx)
 └─ orTimeout(5s) ─▶ Spring writes 201 / 503 / 422 / 504                                 AFTER_COMMIT → await waiters, number release
```

### Why the deadline guard exists

`orTimeout` completes only the **final** stage. The stages before it keep running. A slow credit check therefore still reserves a number and would persist an order that the client already received as 504. `persist()` checks the deadline before the first irreversible step. If it has passed, the number is released and nothing is saved (`WorkflowDeadlineException`). `OrderWorkflowIT.slowWorkflowIs504AndLeavesNothingBehind` proves it.

### Compensation

If persisting fails, the reserved number is released. If provisioning fails, `NumberReleaseListener` releases the number **after** the FAILED status commits.

## Long polling (DeferredResult)

```
await(id):  read → final? answer now
            else: register DeferredResult(timeout, () -> 202 + current state)
                  re-check → final? setResult (first wins)
callback:   completeProvisioning (@Tx) → publish event → commit → AFTER_COMMIT onStatusChanged → setResult for all waiters
```

- **Register, then re-check.** The event fires after commit, so a change between the first read and the registration is seen by the re-check. Otherwise the event completes the waiter. If both happen, `setResult` is first-wins.
- `onCompletion` removes the waiter however it ends (result, timeout, error).
- The timeout supplier runs lazily on the container thread and returns 202 with the current state.

## Concurrency control

| Risk | Control |
|---|---|
| Duplicate or concurrent provisioning callbacks | State guard (only PROVISIONING → final) + `@Version`; the loser gets `ConcurrencyFailureException` → `applied=false` |
| Two waiters for the same order | Concurrent set per order; all completed by the event |
| Number allocation under parallel orders | Atomic counter + concurrent set in the inventory |
| Shared intermediate state between stages | Immutable records (`Eligibility`, `OrderResponse`) |

## Deadline budget

```
downstream latency < workflow deadline (5s) < spring.mvc.async.request-timeout (30s)
long-poll max (55s) < ingress read timeout (75s)
```

## Known limitations

- **Waiters are per pod.** A callback completes waiters on the pod that receives it. A client waiting on another pod gets 202 when its wait expires, and its next poll returns the final state from the database. Use a broker (Redis pub/sub) to fan events out if instant cross-pod completion matters.
- **No idempotency key on `POST /orders`.** A client retry after a 504 creates a new order if the first one was saved (it wasn't, if the deadline guard tripped). Add an `Idempotency-Key` header for production.
- If the deadline expires *after* persisting but before the response is written, the order exists while the client saw 504. Clients should look it up (`GET /orders?status=…`) or use an idempotency key.
