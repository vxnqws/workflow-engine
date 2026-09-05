# engram

**A durable workflow engine built on a pure decider core.**

engram runs long-lived business processes that survive crashes, restarts and deploys. Unlike replay-based engines, its core is a pure function — `decide(state, event) → (newState, commands)` — which makes workflow state ordinary serializable data rather than a suspended call stack.

![Java](https://img.shields.io/badge/Java-21-orange)
![License](https://img.shields.io/badge/License-Apache%202.0-blue)
![Status](https://img.shields.io/badge/status-early%20development-yellow)

---

## Status

**Early development. Not ready for production use.**

Stage 1 of 6 is complete: the pure core, the SPI layer, in-memory backends, and a working execution loop with crash recovery. Everything runs in a single process against in-memory storage.

| Capability | Status |
|---|---|
| Pure `decide` core, event log, replay | Done |
| SPI abstractions, in-memory backends | Done |
| Optimistic concurrency (log + state) | Done |
| Crash recovery via replay | Done |
| Annotation-based API | Planned — Stage 2 |
| Activities, retries, durable timers | Planned — Stage 3 |
| Kafka / PostgreSQL / RabbitMQ backends | Planned — Stage 4 |
| Snapshots, saga compensation, versioning | Planned — Stage 5 |
| Projections, HTTP API, web UI | Planned — Stage 6 |

The public API will change without notice until Stage 4.

---

## The problem

A business process that spans minutes, days or weeks cannot live in a single method call:

```java
var reservation = inventory.reserve(order);   // 200ms
var payment     = payments.charge(order);     // 2s, may fail
var approval    = waitForManagerApproval();   // up to 3 days
shipping.dispatch(order, reservation);        // 500ms
```

If the process dies after `charge` but before `dispatch`, the money is gone and nobody knows which step was reached. The execution position lived in the JVM call stack, and the stack died with the process.

Durable execution engines solve this by persisting the process position. The interesting question is *how*.

---

## Two ways to persist a process

**Replay-based engines** (Temporal, Cadence) keep workflow code imperative and re-execute it from the beginning on every step, substituting recorded results for already-completed calls. The position is implicit in the call stack.

This buys familiar sequential code, at a cost:

- workflow code must be strictly deterministic, enforced by convention and runtime checks
- the SDK needs its own deterministic cooperative thread scheduler
- snapshots are impossible — a call stack cannot be serialized
- history growth is handled manually via `continue-as-new`
- recovery cost grows linearly with history length

**engram** takes the other route. The core is a pure function:

```
decide(state, event) → (newState, commands)
```

State is a plain serializable object. The position is explicit in the data.

- snapshots are trivial: state is data, so it can be written to a database and loaded back
- determinism is guaranteed by the signature — `decide` has no access to time, randomness, or I/O
- no custom threading runtime is required
- testing is a plain function call: no mocks, no containers

The trade-off is real: branch-heavy processes read as a state machine rather than as sequential code, and state shape must be designed explicitly.

---

## Core model

```java
@FunctionalInterface
public interface Decider<S extends WorkflowState> {
    Decision<S> decide(S state, HistoryEvent event);
}
```

Three rules follow from this signature:

1. **The event log is the source of truth.** State is a derived cache and can always be recomputed.
2. **Commands are intentions, not effects.** `decide` decides; the runtime executes.
3. **Replay discards commands.** Their effects already happened and are recorded as later events.

### Example

```java
public final class OrderDecider implements Decider<State> {

    private final StateCodec codec;

    public OrderDecider(StateCodec codec) {
        this.codec = codec;
    }

    @Override
    public Decision<State> decide(State state, HistoryEvent event) {
        return switch (event.type()) {

            case EventTypes.ORDER_PLACED ->
                new Decision<>(state.withStatus(Status.RESERVING), List.of("reserve"));

            case EventTypes.RESERVE_COMPLETED -> {
                var payload = codec.decode(event.payload(), ReserveResult.class);
                yield new Decision<>(
                    state.withStatus(Status.CHARGING)
                         .withReservation(payload.reservationId()),
                    List.of("charge"));
            }

            case EventTypes.CHARGE_COMPLETED -> {
                var payload = codec.decode(event.payload(), ChargeResult.class);
                yield new Decision<>(
                    state.withStatus(Status.COMPLETED)
                         .withPayment(payload.paymentId()),
                    List.of());
            }

            default -> Decision.stateOnly(state);
        };
    }
}
```

> Stage 2 replaces the `switch` with annotated handlers that receive a decoded, typed payload. The engine will still see a plain `Decider` underneath.

### Running it

```java
StateCodec codec = new CborCodec();
EventLog log = new InMemoryEventLog();
StateStore store = new InMemoryStateStore(codec);

var coordinator = new Coordinator<>(
    log, store, new OrderDecider(codec), State.class, State.initial());

coordinator.handle("o-42", EventTypes.ORDER_PLACED,
    codec.encode(new OrderPlaced("order-1", "cust-7")));
coordinator.handle("o-42", EventTypes.RESERVE_COMPLETED,
    codec.encode(new ReserveResult("r-88")));
coordinator.handle("o-42", EventTypes.CHARGE_COMPLETED,
    codec.encode(new ChargeResult("p-7", 149_900L)));
```

### Surviving a crash

State can be thrown away entirely and rebuilt from the log:

```java
StateStore freshStore = new InMemoryStateStore(codec);   // state lost
var recovered = new Coordinator<>(
    log, freshStore, new OrderDecider(codec), State.class, State.initial());

// the next turn replays the log and catches up automatically
```

---

## Architecture

Dependencies point inward. The core knows nothing about storage, transport or frameworks.

```
  adapters   runtime · client · backend-* · server
                          |
   ports                 spi
                          |
   core                  core
```

| Module | Contents | Dependencies |
|---|---|---|
| `engram-core` | `HistoryEvent`, `WorkflowState`, `Decision`, `Decider`, `Replayer`, `StateCodec` | none (JDK only) |
| `engram-spi` | `EventLog`, `StateStore`, concurrency exceptions | `core` |
| `engram-codec-cbor` | Deterministic CBOR codec | `core`, Jackson |
| `engram-backend-memory` | In-memory `EventLog` and `StateStore` | `spi` |
| `engram-runtime` | `Coordinator` — one turn of the execution loop | `core`, `spi` |
| `examples` | Order fulfillment demo | runtime, backends |

Architectural rules are enforced by ArchUnit tests, not by review:

- `core` depends on nothing but the JDK
- `core` does not know about `spi`
- `spi` does not know about backends
- backends do not depend on each other
- `core` may not call `Instant.now()`, `System.currentTimeMillis()`, `Math.random()` or `UUID.randomUUID()`

The last rule is a mechanical check of the determinism invariant.

---

## Invariants

Five rules hold the system together. Violating any of them fails silently — the damage shows up weeks later as diverging state.

| | Invariant | Enforced by |
|---|---|---|
| **I1** | `decide` is deterministic | signature has no access to the outside world; ArchUnit rule; verify-replay in CI |
| **I2** | `sequence` is dense and monotonic per instance | `append` rejects gaps and stale expectations |
| **I3** | The codec produces identical bytes for identical data | sorted map keys, stable field order, fixed number encoding |
| **I4** | Replay never executes commands | `Replayer` reads only `newState()` |
| **I5** | Activities are idempotent | deterministic idempotency keys *(Stage 3)* |

---

## Design decisions

**`sequence` is not a transport offset.** It is a dense per-instance counter starting at 1. Offsets belong to the transport layer, change when a topic is recreated, and do not survive a backend migration. Snapshot pointers and idempotency keys are built on `sequence`.

**The log is written before the state.** A crash between the two leaves the state behind, which is recoverable — the next turn replays the tail. The reverse order would produce state claiming to be computed past events that do not exist.

**`StateStore` is a cache, not a source of truth.** It can be deleted entirely and rebuilt. If something is stored only there and cannot be derived from the log, that is a design error.

**Concurrency is optimistic, not locked.** Both the log and the state store take an expected version and reject mismatches. On conflict the coordinator retries the whole turn against fresh data, rather than retrying just the write — because the state has changed too, and the decision must be made again.

**Backends are separate modules.** A single `engram-backends` artifact would drag Kafka, JDBC, RabbitMQ and object-storage clients into every user's classpath. One module per backend keeps dependencies opt-in and lets third parties add their own on top of `engram-spi` alone.

---

## Building

Requires JDK 21.

```bash
git clone https://github.com/vxnqws/workflow-engine.git
cd engram
./gradlew build
```

Run the demo:

```bash
./gradlew :examples:run
```

---

## Roadmap

**Stage 2 — Declarative API.** Annotation module, registry with start-time validation, adapter mapping annotated handlers onto `Decider`, client with idempotent workflow start.

**Stage 3 — Activities and time.** Activity workers, retry policies with the outcome recorded but attempts kept out of history, durable timers as database rows rather than sleeping threads.

**Stage 4 — Real infrastructure.** PostgreSQL state store, Kafka event log partitioned by workflow id, RabbitMQ task transport, and an SPI conformance suite that every backend must pass.

**Stage 5 — Differentiators.** Transparent snapshots with history truncation, saga compensation as a first-class primitive, schema versioning through upcasters.

**Stage 6 — Observability.** Query API, ClickHouse projections for business-level SQL, claim-check blob storage, HTTP facade and web UI.

---

## Comparison with Temporal

Temporal is a mature, production-proven system, and engram borrows heavily from its concepts. The differences below are deliberate trade-offs, not claims of superiority.

| | Temporal | engram |
|---|---|---|
| Core model | imperative code + replay | pure `decide(state, event)` |
| State representation | JVM call stack | serializable object |
| History growth | manual `continue-as-new` | transparent snapshots *(planned)* |
| Task delivery | long-poll via Matching service | push via `TaskTransport` *(planned)* |
| Storage | fixed set | swappable through SPI |
| Analytics | visibility store | SQL projections *(planned)* |
| Saga | user-code pattern | platform primitive *(planned)* |
| Versioning | `getVersion()` branches in workflow code | schema upcasters *(planned)* |
| Entry point | Frontend service (gRPC) | Java library |

---

## Contributing

The project is in active early development and internal APIs change frequently. Issues and design discussions are welcome; please open an issue before starting substantial work so effort is not duplicated.

---

## License

Apache License 2.0. See [LICENSE](LICENSE).