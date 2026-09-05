# engram

**Durable workflows for Java. No server to run.**

engram executes long-running business processes that survive crashes, restarts and deploys. Workflows are plain annotated classes; state is a plain record. The engine is a library that runs inside your application and persists to storage you already operate.

![Java](https://img.shields.io/badge/Java-21-orange)
![License](https://img.shields.io/badge/License-Apache%202.0-blue)
![Status](https://img.shields.io/badge/status-alpha-yellow)

> **Alpha.** The API is unstable and storage backends are in-memory only. Not ready for production.

---

## Quick start

Define your state as a record:

```java
public record OrderState(
    Status status,
    String reservationId,
    String paymentId
) implements WorkflowState {

    public static OrderState initial() {
        return new OrderState(Status.NEW, null, null);
    }

    public OrderState withStatus(Status s)      { return new OrderState(s, reservationId, paymentId); }
    public OrderState withReservation(String id) { return new OrderState(status, id, paymentId); }
    public OrderState withPayment(String id)     { return new OrderState(status, reservationId, id); }
}
```

Describe the process as event handlers:

```java
@WorkflowInterface(type = "OrderWorkflow")
public class OrderWorkflow {

    @OnEvent("OrderPlaced")
    public Decision<OrderState> onPlaced(OrderState state, OrderPlaced payload) {
        return new Decision<>(state.withStatus(Status.RESERVING), List.of("reserve"));
    }

    @OnEvent("ReserveCompleted")
    public Decision<OrderState> onReserved(OrderState state, ReserveResult payload) {
        return new Decision<>(
            state.withStatus(Status.CHARGING).withReservation(payload.reservationId()),
            List.of("charge"));
    }

    @OnEvent("ChargeCompleted")
    public Decision<OrderState> onCharged(OrderState state, ChargeResult payload) {
        return new Decision<>(
            state.withStatus(Status.COMPLETED).withPayment(payload.paymentId()),
            List.of());
    }
}
```

Wire it up and run:

```java
StateCodec codec = new CborCodec();

EngramClient engram = EngramClient.builder()
    .codec(codec)
    .eventLog(new InMemoryEventLog())
    .stateStore(new InMemoryStateStore(codec))
    .register(OrderWorkflow.class, new OrderWorkflow(), OrderState.initial())
    .build();

engram.handle("OrderWorkflow", "order-42", "OrderPlaced",
    new OrderPlaced("order-42", "customer-7"));
```

That is the whole setup. No server process, no cluster, no sidecar.

---

## Why engram

**Your process survives anything.** Every state transition is appended to an event log before state is written. Lose the process, lose the database cache, lose the machine — state is rebuilt by replaying the log.

**Workflows are testable as plain functions.** A handler takes state and a payload and returns a decision. No mocks, no containers, no test harness:

```java
var decision = new OrderWorkflow()
    .onReserved(OrderState.initial(), new ReserveResult("r-88"));

assertEquals(Status.CHARGING, decision.newState().status());
```

**Mistakes fail at startup, not in production.** Registration rejects malformed declarations before your application accepts traffic: missing handlers, wrong signatures, duplicate event types, conflicting state types, and state that cannot be serialized.

**Bring your own storage.** The engine talks to five interfaces — event log, state store, task transport, blob store, projection store. Swap any of them without touching workflow code.

**Determinism you cannot accidentally break.** Handlers receive data and return data. There is no clock, no random source and no I/O in scope, so replay always produces the same state.

---

## Features

| | |
|---|---|
| **Declarative workflows** | `@WorkflowInterface` and `@OnEvent`; payloads decoded into typed parameters automatically |
| **Event-sourced execution** | Append-only log is the source of truth; state store is a rebuildable cache |
| **Crash recovery** | Automatic catch-up replay when state falls behind the log |
| **Startup validation** | Eight declaration checks, including a codec round-trip of your initial state |
| **Optimistic concurrency** | Version checks on both log and state; conflicting turns retry against fresh data |
| **Deterministic serialization** | CBOR codec with sorted keys and stable field order, so state hashes are reproducible |
| **Pluggable storage** | Five SPI interfaces; in-memory implementations ship for tests and local runs |
| **Flexible registration** | Explicit registration or classpath scanning |

### Not yet implemented

Activities, retries, durable timers, signals and queries arrive in the next release. Commands are currently plain strings and nothing executes them. Storage backends are in-memory only. Snapshots and saga compensation are further out — see the [roadmap](#roadmap).

---

## How it works

The engine core is one function:

```
decide(state, event) → (newState, commands)
```

An event arrives. The coordinator loads state, calls your handler, appends the event to the log, saves the new state, and returns the commands your handler asked for. Nothing else happens on the hot path.

Because state is data rather than a suspended call stack, it can be serialized, cached, snapshotted, queried and rebuilt at will. Most of what engram does well follows from that single property.

Replay-based engines take the opposite approach: workflow code stays imperative and is re-executed from the top on every step. That reads more naturally, but it makes state a call stack — which cannot be snapshotted, forces a custom deterministic thread scheduler into the SDK, and makes recovery cost grow with history length.

---

## Architecture

Dependencies point inward. The core knows nothing about storage, transport or frameworks.

| Module | Contents |
|---|---|
| `core` | Annotations, `Decision`, `WorkflowState`, `HistoryEvent`, `Decider`, `Replayer`, `StateCodec` |
| `spi` | `EventLog`, `StateStore`, concurrency exceptions |
| `codec-cbor` | Deterministic CBOR codec |
| `backend-memory` | In-memory event log and state store |
| `runtime` | Registry, annotation adapter, coordinator |
| `client` | `EngramClient` and its builder |

`core` has zero dependencies outside the JDK. Inside it, `core.api` holds what users touch and `core.internal` holds what the engine needs.

Architectural rules are enforced by ArchUnit rather than by review: the core stays dependency-free and clock-free, workflow classes cannot reach the runtime or any backend, the SPI does not know its implementations, and backends do not depend on each other.

---

## Guarantees

| | |
|---|---|
| **Deterministic handlers** | Nothing in scope can break replay; enforced structurally and by ArchUnit |
| **Dense event sequence** | Per-instance counter starting at 1; appends reject gaps and stale expectations |
| **Reproducible serialization** | Identical data always produces identical bytes |
| **Replay has no side effects** | Rebuilding state never re-runs commands |
| **Idempotent workflow start** | Starting the same workflow id twice returns the existing instance *(next release)* |

---

## Roadmap

**Next release — activities and time.** Typed commands, activity workers with retry policies, deduplication via deterministic idempotency keys, durable timers stored as rows rather than sleeping threads, signals and queries.

**Then — production storage.** PostgreSQL state store, Kafka event log partitioned by workflow id, RabbitMQ task transport, plus a conformance suite that any backend implementation must pass.

**After that — scale and safety.** Transparent snapshots with history truncation, saga compensation as a first-class primitive, schema versioning through upcasters.

**Later — observability.** SQL projections over business data, claim-check blob storage for large payloads, HTTP API and web UI.

---

## Compared to Temporal

Temporal is mature and production-proven; engram is alpha. These are design trade-offs, not claims of superiority.

| | Temporal | engram |
|---|---|---|
| Deployment | server cluster plus workers | library, uses your existing infrastructure |
| Workflow code | imperative body, re-executed on replay | event handlers returning decisions |
| State | JVM call stack | serializable record |
| History growth | manual `continue-as-new` | transparent snapshots *(planned)* |
| Storage | fixed set | swappable through SPI |
| Analytics | visibility store | SQL projections *(planned)* |
| Saga | pattern you implement | platform primitive *(planned)* |
| Versioning | version branches in workflow code | schema upcasters *(planned)* |

Choose Temporal if you need something battle-tested today, polyglot SDKs, or code that reads top to bottom. engram is worth a look if you want durable execution as a dependency rather than a cluster, storage you control, and workflows you can unit-test without a runtime.

---

## Building

Requires JDK 21.

```bash
git clone https://github.com/vxnqws/workflow-engine.git
cd engram
./gradlew build
./gradlew :examples:run
```

---

## Contributing

APIs change frequently at this stage. Issues and design discussions are welcome — please open an issue before starting substantial work.

## License

Apache License 2.0. See [LICENSE](LICENSE).