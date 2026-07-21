## Current Community approach (at July 2026)

Flamingock Community stores the complete local change history in an append-oriented ledger, currently represented by `flamingockAuditLog`.

This local history serves two purposes:

1. it records the audit history of change executions;
2. Flamingock aggregates that history to reconstruct the current operational state and determine which changes have already been executed.

Community also exposes basic audit capabilities, including audit access through the CLI.

## Current intended Cloud approach

The current Cloud design, which has not yet reached production, moves the authoritative audit log from the customer’s local database to Flamingock Cloud.

Under that model:

* the complete audit history is stored in Cloud;
* the local database is no longer the authoritative audit store;
* during application startup, the Flamingock client contacts Cloud;
* Cloud uses the centralized audit history and the changes declared by the client to decide what must execute;
* the client depends on Cloud to reconstruct the execution state and receive the execution plan.

This makes Flamingock Cloud a synchronous and potentially blocking dependency in the application startup path.

The CLI can execute changes before application startup, and a self-hosted offering can move the server into the customer’s infrastructure, but neither option fully removes the architectural concern in the normal SaaS model.

# Motivation for changing the approach

There are two main motivations.

## 1. Protect the customer’s critical path

A customer should not lose the ability to determine what has already been executed merely because Flamingock Cloud is unavailable.

Making the Cloud service mandatory during application startup is likely to be viewed negatively, particularly by enterprise customers. A temporary outage of a third-party control plane should not prevent:

* an application restart;
* autoscaling;
* deployment recovery;
* disaster recovery;
* execution of changes that do not require an online governance decision.

Flamingock Cloud should normally participate as planner, orchestrator and governance authority, but the core operational state required for execution must remain local.

When Cloud is unavailable, Flamingock should degrade gracefully and preserve the critical path whenever policy permits.

Some Cloud-governed controls may still intentionally block particular changes. For example, if a production change requires approval and the approval cannot be verified, that change may need to fail closed unless an explicit emergency mechanism is used. That does not justify making Cloud necessary merely to discover the current local execution state.

## 2. Establish a clear Community/Enterprise boundary

Previously, the product boundary around audit was unclear:

* Community had the basic local audit ledger and CLI access;
* Cloud was expected to provide deeper audit, reporting, governance and compliance capabilities.

The revised approach should establish a clearer line:

* Community provides reliable local execution and operational state.
* Flamingock Cloud provides the audit product: centralized history, reporting, governance, organizational visibility and compliance capabilities.

Community still persists all generated events for durability and future portability, but it does not present those events as the supported audit feature.

Cloud receives those events asynchronously and transforms them into the centralized audit and governance model.

# Architectural decision

Flamingock is separating two responsibilities that were previously represented by the same local audit history:

1. the current operational state needed to execute safely;
2. the historical facts used to build centralized audit and governance capabilities.

These responsibilities now have different sources of truth, models and lifecycles.

# 1. Local operational source of truth

The Flamingock client must retain locally everything required to execute safely and independently of Flamingock Cloud.

The local operational state is authoritative for questions such as:

* Which changes have already been applied?
* Which changes remain pending?
* What is the current effective state of a change?
* Does an execution need recovery or retry?
* What should Flamingock execute during application startup?

This local state protects the customer’s critical path.

Flamingock Cloud must not be required merely to reconstruct the current operational state of the target system.

Cloud can still normally:

* inspect the client state;
* provide the execution plan;
* coordinate work between instances;
* evaluate policies;
* enforce approvals;
* provide other premium governance capabilities.

When Cloud is unavailable, the client must still know its operational state and be capable of graceful degradation according to the configured governance policy.

## Reuse of the existing audit storage

The existing local `audit_log` storage will continue to be used for compatibility and pragmatism, but its primary responsibility changes.

Its effective responsibility becomes:

> Store the current effective state required by Flamingock to determine what has been completed and what must be executed.

For newly produced records, the effective state will generally be updated using CRU semantics:

* create;
* read;
* update;
* no normal delete.

The table will no longer accumulate every transition purely to reconstruct the current state.

Legacy records may still contain multiple entries created by earlier versions. Existing state-reconstruction logic can continue supporting those records. There is no need for a disruptive migration solely to rewrite or remove the historical data.

The physical table name may remain `audit_log` even though its principal architectural responsibility becomes operational state.

Do not allow this historical table name to dictate the new domain model. In core code, reason in terms of operational state and current effective change state.

# 2. Durable local event buffer

The client will additionally persist every relevant Flamingock event in a durable, append-oriented local event buffer.

Examples include:

* execution created;
* execution started;
* execution completed;
* stage started;
* stage completed;
* stage failed;
* stage retried;
* change started;
* change applied;
* change failed;
* change rolled back;
* future governance or execution facts.

The event buffer and operational state store have separate responsibilities:

> Operational state answers: “Where are we now, and what should execute?”

> Events answer: “What happened over time?”

The event buffer is not used as the source of truth for deciding what must execute.

It is the durable record of facts waiting to be synchronized and later used by Cloud to construct audit and governance projections.

Whenever the underlying store permits it, the event and its corresponding operational-state transition should be persisted as part of the same logical atomic operation.

The client must never forget or clean an event before Flamingock Cloud has durably and idempotently acknowledged it.

# 3. Community and Enterprise behaviour

Both Community and Enterprise runtimes generate and persist the same local events.

## Community

In Community:

* the local operational state remains fully functional;
* events remain durably stored in the local event buffer;
* no Cloud consumer is active;
* the event buffer preserves future portability;
* Community does not expose the centralized audit product;
* the retained events can be synchronized if the customer later enables Flamingock Cloud.

Customers may eventually be allowed to purge retained events, but they must be clearly informed that purged events cannot subsequently be imported into Cloud as historical audit evidence.

The fact that the events physically exist in the customer’s database does not make the internal event buffer a supported Community audit API.

The table is internal Flamingock infrastructure. Its schema is managed by Flamingock and should not be treated as a stable public reporting contract.

## Enterprise and Cloud

In Enterprise/Cloud:

* the same events are generated locally;
* they are sent asynchronously to Flamingock Cloud in batches;
* application execution does not wait for every event to be sent;
* temporary Cloud unavailability does not lose events;
* the client retries delivery until the events are acknowledged;
* acknowledged events become eligible for later cleanup according to a configured retention policy.

Cleanup:

* is not part of the critical path;
* is not urgent;
* must be separate from acknowledgement;
* must occur only after durable Cloud acceptance.

Enterprise does not remove a functional Community capability. It adds:

* centralized ingestion;
* searchable audit history;
* reporting;
* governance;
* approvals;
* policy enforcement;
* organizational visibility;
* compliance capabilities;
* managed retention;
* support and enterprise operations.

The event buffer is the reliable transport and evidence source.

Flamingock Cloud is the audit product.

# 4. Source-of-truth boundaries

There is deliberately no single source of truth for every concern.

Use the following terminology:

> The client is the authoritative source of truth for current operational state and execution.

> Flamingock Cloud is the authoritative organizational record for centralized audit, governance, reporting and compliance.

Events originate in the client.

Before acknowledgement, they are durable local evidence waiting to be delivered.

Once Cloud has validated and durably accepted them, they become part of the authoritative centralized audit record.

Conceptually:

```text
Local operational state
    → authoritative for execution and recovery

Local durable events
    → original execution facts waiting for synchronization

Flamingock Cloud
    → authoritative organizational audit and governance record
```

# 5. Event production and persistence abstraction

Event generation and processing logic are centralized in the Flamingock core/client.

The supported audit-store implementations provide only the persistence abstraction needed to:

* save an event;
* retrieve a batch of pending events;
* mark events as acknowledged;
* eventually clean acknowledged events.

The initially supported stores are:

* SQL databases;
* MongoDB;
* DynamoDB;
* Couchbase.

The event domain model must remain persistence-agnostic.

Each adapter decides how to represent the event:

* SQL rows and serialized event data;
* MongoDB documents;
* DynamoDB items;
* Couchbase documents.

Do not introduce Jackson, BSON, DynamoDB, Couchbase or any other persistence technology into the event domain class.

The event class must remain a plain Java 8 domain POJO.

# 6. Event ingestion API

The client must not segregate event types into different server endpoints.

All asynchronous facts generated by the Flamingock runtime should be sent through one generic batched event-ingestion endpoint.

Conceptually:

```text
Client event buffer
    → generic batch ingestion endpoint
    → immutable raw-event persistence
    → asynchronous routing and projections
```

The client owns:

* generating events;
* persisting them durably;
* delivering them reliably;
* retrying until acknowledged;
* sending them in the best practical order.

The client must not know:

* which Cloud endpoint corresponds to each event subtype;
* which internal projection consumes the event;
* how Cloud stores audit versus execution information;
* how older event versions are transformed;
* how Cloud routes the event internally.

The server owns:

* authentication and authorization;
* envelope validation;
* idempotency;
* durable raw-event persistence;
* event-version handling;
* transformation;
* routing;
* projection generation.

The server acknowledgement means:

> The event has been accepted idempotently and persisted durably.

It must not mean that every projection has already completed.

Commands and queries requiring an immediate business response may continue to use specific typed endpoints.

The generic event-ingestion endpoint is specifically for asynchronous runtime facts.

# 7. Raw-event retention and projections in Cloud

Cloud should persist each immutable raw event as received before deriving projections.

The raw event is the basis for:

* replay;
* debugging;
* rebuilding projections;
* audit timelines;
* execution summaries;
* current-state views;
* reporting;
* compliance views;
* future event transformations.

Cloud projections are eventually consistent.

The ingestion endpoint should not synchronously perform every projection.

A suitable conceptual flow is:

```text
Ingestion endpoint
    → idempotent raw-event inbox/store
    → durable projection-work signal or server outbox
    → asynchronous projection workers
```

Cloud should be capable of receiving events out of transport order and later building the correct logical projections.

# 8. Ordering model

There is no global total order across all Flamingock events.

Stages may run in parallel and may eventually be assigned to different application instances. A global monotonically increasing sequence would introduce unnecessary coordination and conflict with that parallelism.

The client should send pending events in the best practical order, usually approximately oldest first.

This is a best-effort transport concern. Correctness must not depend on it.

Events may arrive at Cloud out of order.

Cloud must handle that.

## Stage-level sequencing

Strict sequencing is required within a stage stream.

The intended stream identity is currently the `stageId`, within the implicit local service and environment context.

Retries and attempts for the same stage remain in the same stream. Do not include an attempt identifier in the stream identity if doing so would split the required stage-level sequence.

For each stream:

* sequence numbers start from an agreed initial value;
* they increase monotonically;
* locally generated sequences must not contain gaps;
* two different events must never occupy the same `(streamId, streamSequence)`;
* retrying delivery of the same event must preserve its event ID and sequence.

Flamingock already holds at least a stage-level distributed lock.

Under that lock, the active writer can:

1. read the latest persisted sequence for the stage;
2. continue incrementing it in memory;
3. generate and persist events sequentially;
4. prevent concurrent writers from creating conflicting positions.

Cloud may temporarily receive:

```text
1, 3, 2
```

That is acceptable.

Cloud must distinguish between:

* a temporary transport gap;
* an older event arriving after a newer event;
* a permanently missing event;
* an integrity conflict.

Projection correctness must use the explicit stage-stream sequence rather than Cloud insertion order.

The event timestamp remains useful for:

* timelines;
* approximate event ordering;
* selecting best-effort delivery batches.

It is not the authoritative ordering mechanism within a stage stream.

# 9. Idempotency

Assume Cloud ingestion is idempotent.

Each event has a globally unique opaque event ID used as its idempotency key.

The event ID must not embed audit, execution, change or other business semantics.

Business identity belongs in the event data.

Cloud must accept repeated delivery of the same event without creating duplicate raw facts or projection effects.

The combination:

```text
(streamId, streamSequence)
```

must represent one logical stream position.

Receiving the same event again for that position is an idempotent retry.

Receiving a different event for an already occupied position is an integrity conflict and must not be accepted as another valid event.

# 10. Event model principles

The event domain model is generic and persistence-agnostic.

It conceptually contains:

* an opaque `eventId`;
* an event type;
* an event-schema version;
* a `streamId`;
* a sequence within that stream;
* the time at which the fact occurred;
* the actual domain data, such as an audit entry or execution object.

The outer object is the event.

The contained audit entry, execution or similar domain object is the event data.

Do not require a generic `executionId` in every event. Event types that require execution identity can carry it in their own domain data.

Use terminology equivalent to `occurredAt`, rather than `createdAt`, because this represents when the fact occurred.

Other timestamps have different meanings:

* locally persisted time;
* Cloud received time;
* Cloud acknowledged time;
* projection processing time.

The domain event must be a plain Java 8 POJO with no persistence or serialization annotations.

Each audit-store adapter owns the conversion to its persistence representation.

# 11. Cloud planning and graceful degradation

This architectural change does not remove Flamingock Cloud’s role as planner and orchestrator.

When Cloud is available, the intended normal path may still include:

* sending the declared changes and relevant local state to Cloud;
* receiving an execution plan;
* assigning stages to instances;
* coordinating parallel stage execution;
* evaluating governance policies;
* checking approvals;
* applying premium controls.

The important change is that Cloud is no longer required to reconstruct the local operational state.

If Cloud cannot be reached:

* the client still knows which changes have been executed;
* the client does not lose operational continuity;
* the execution path can degrade according to policy;
* locally generated events continue to be stored;
* those events are synchronized later.

Some governance capabilities may intentionally remain unavailable or fail closed while offline. Emergency and break-glass mechanisms will be designed separately.

# 12. Future cryptographic integrity

Cryptographic chaining is explicitly outside the MVP and must not be implemented as part of this task.

However, the initial architecture must preserve the foundations needed to add it later.

The future direction may include:

* a Cloud-issued execution anchor;
* an independent cryptographic chain per stage;
* previous-event hashes;
* event hashes;
* forward-secure authentication;
* batched verification by Cloud;
* signed Cloud receipts;
* aggregation of parallel stage heads into an execution-level commitment.

This future capability is intended to provide tamper-evident, cryptographically verifiable audit evidence for Flamingock-managed changes.

The MVP should preserve:

* immutable events;
* opaque and stable event IDs;
* explicit event versions;
* stage-level streams;
* monotonic, gapless stream sequences;
* durable local buffering;
* asynchronous batched delivery;
* idempotent Cloud ingestion;
* immutable raw-event persistence in Cloud.

Do not add speculative cryptographic fields or implementation complexity unless required by the current implementation. Preserve the architectural extension points and invariants instead.

# 13. Audit guarantees and scope

The centralized audit capability records and protects changes managed through Flamingock.

It must not claim to prove that nobody modified an external system outside Flamingock.

The honest scope is:

> Flamingock provides centralized and potentially cryptographically verifiable evidence for Flamingock-managed changes.

Future capabilities such as:

* drift detection;
* target-state verification;
* native external-system audit integration;
* out-of-band activity detection;

may provide additional evidence, but they are separate concerns.

# 14. Product boundary

The intended product boundary is:

```text
Flamingock Community
    → reliable local execution
    → current operational state
    → durable generation and retention of execution events
    → future portability to Cloud

Flamingock Cloud / Enterprise
    → centralized audit
    → reporting
    → governance
    → approvals
    → policies
    → organizational visibility
    → compliance capabilities
    → enterprise operations and support
```

Community storing internal events does not mean that Community provides the audit product.

Flamingock Cloud converts durable, isolated runtime facts into a centralized, searchable, governed and eventually cryptographically verifiable organizational audit trail.

# 15. Architectural summary

The core separation is:

```text
Local operational core
    → current state
    → execution
    → recovery
    → critical-path resilience

Asynchronous event channel
    → durable local facts
    → reliable batched synchronization

Centralized audit and governance layer
    → history
    → approvals
    → policies
    → reporting
    → compliance
    → organizational visibility
```

This architecture preserves Flamingock Cloud’s enterprise capabilities while removing the unnecessary requirement for Cloud to be available merely for the client to know its current execution state.