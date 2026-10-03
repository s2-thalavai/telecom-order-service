# How to Run

## Prerequisites

JDK 21, Maven 3.9+ (or Docker only), Docker + Compose v2 for the stack and the MySQL IT, kubectl + minikube/kind for Kubernetes, and make/bash/curl/python3 for the scripts (on Windows, use WSL2 or Git Bash).

## 1. Local

```bash
make run          # H2; console at http://localhost:8080
```

Pick a scenario in the console and place an order. The timeline shows each step with elapsed time.

| Scenario (email contains) | Expect |
|---|---|
| normal | 201 PROVISIONING in ~0.7 s, then ACTIVE after ~3 s via the long-poll |
| `risky` | 201 REJECTED, no number |
| `provfail` | FAILED after ~3 s; the number is released |
| `manual` | Stays PROVISIONING (long-poll returns 202). Use the webhook buttons |
| `down` | 503 + `Retry-After` |
| `slow` | 504 after 5 s; no order saved, number released |

## 2. Tests

```bash
make test     # unit, MockMvc async, JPA
make it       # integration on real Tomcat; MySQL IT needs Docker
make verify   # all + coverage
```

## 3. Docker Compose

```bash
make up && make smoke
make logs / make down / make nuke
```

## 4. Kubernetes

```bash
minikube start && minikube addons enable ingress
make k8s-deploy && make k8s-port-forward
make k8s-delete
```

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `telecom.orders.workflow-deadline` | 5s | Order workflow deadline → 504; nothing is persisted after it |
| `telecom.orders.default-await` / `max-await` | 20s / 55s | Long-poll default and cap (keep below the ingress timeout) |
| `telecom.orders.minimum-credit-score` | 650 | Below → REJECTED |
| `telecom.orders.bulk-item-timeout` | 2s | Per-number timeout in bulk checks |
| `spring.mvc.async.request-timeout` | 30s | Default async timeout (→ 503) |
| `telecom.simulation.*` | see application.properties | Downstream latencies; `slow-delay` for the slow scenario |

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| 504 on normal orders | Downstream latencies add up beyond `workflow-deadline`, or the integration pool is saturated (`/actuator/metrics/executor.queued`) |
| Long-poll always 202 | No callback arrived (`manual` scenario, or the callback reached another pod). Poll again or send the webhook |
| 503 "Server Busy" | `integrationExecutor` queue is full. Raise the pool or shed load upstream |
| Webhook says `applied=false` | Duplicate or late callback; the order was already final (by design) |
