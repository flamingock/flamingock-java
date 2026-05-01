# Lock REST API — Acquire, Extend, Release & Get

Reference for the Flamingock Runner Application's lock endpoints used by the
`flamingock-java` client to **acquire** a lock (typically when initialising
an execution plan), **extend** it as a heartbeat for long-running work,
**release** it when done, and **get** the current lock state without
mutating it.

---

## Overview

| Property        | Value                                   |
|-----------------|-----------------------------------------|
| Base URL        | `http://localhost:8080` (local default) |
| Content-Type    | `application/json`                      |
| Authentication  | `Authorization: Bearer <runner-jwt>`    |
| Runner identity | `flamingock-runner-id: <runner-id>`     |
| Lock service    | Mounted only on the runner application  |

All endpoints below are annotated `@SecuredRunnerOperation` server-side. That
means **two pieces of identity must be present on every request**:

1. A valid runner JWT carrying organisation / project / environment / service
   claims (obtained via the `exchange-token` auth flow — see
   [`runner-api-endpoints.md`](runner-api-endpoints.md)).
2. A `flamingock-runner-id` header carrying the caller's runner id. The runner
   id is also the **owner** of the lock — `acquire` *establishes* ownership;
   `extend` and `release` *enforce* it (the server compares the header against
   the stored `owner` column to authorise the operation).

If either is missing or invalid, the request is rejected before reaching
business logic (see [Authentication errors](#authentication-errors)).

---

## Lock semantics (just enough to use these endpoints correctly)

- A lock is identified by `(environment_id, key)`. `environment_id` is derived
  server-side from the JWT, so the client only sends `key` (in the URL path).
  `key` is typically the service id.
- A lock has an **owner** (the runner id) and an **`acquisitionId`** (a
  server-generated `{owner}-{uuid}` string returned at acquire time).
- **Acquire** has three modes, all returning `201 ACQUIRED` on success:
  - **New**: lock didn't exist; created with the caller as owner.
  - **Same-owner renewal**: lock existed and the caller was already the owner;
    a fresh `acquisitionId` is issued and `acquiredForMillis` is updated.
  - **Takeover-of-expired**: lock was held by *another* owner, but the
    previous lock had expired. Takeover succeeds only if the caller proves
    knowledge of the previous `acquisitionId` (`lastAcquisitionId`) **and**
    presents `elapsedMillis` strictly greater than the previous lock's
    `acquiredForMillis`. Otherwise: `409 R_LOCK_01`.
- **Extend** rotates the `acquisitionId` to a new value, keeps the owner,
  and does **not** change the duration. Use it as a heartbeat. Clients that
  pin to a specific `acquisitionId` for expiry checks should update their
  cached value from the response.
- **Release** deletes the lock row. Only the current owner can release.
- **Get** returns the current lock as a read-only resource. It does **not**
  require ownership — any authenticated runner in the environment can query.
  Returns `404` when no lock exists for the key, `200` otherwise.
- Extend and release require the caller to currently own the lock; otherwise
  the server returns `409`. Get does not.

---

## 1. Acquire a lock

### `POST /api/v1/{key}/lock/acquisition`

Acquire a new lock, renew an existing one held by the same owner, or take
over an expired lock from a previous owner.

**Path parameters**

| Param | Type   | Description                          |
|-------|--------|--------------------------------------|
| `key` | string | Lock key (typically the service id). |

**Headers**

| Header                  | Required | Description                                    |
|-------------------------|----------|------------------------------------------------|
| `Authorization`         | yes      | `Bearer <runner-jwt>`                          |
| `flamingock-runner-id`  | yes      | Runner id; becomes the lock owner on success   |
| `Content-Type`          | yes      | `application/json` (the body is required)      |

**Request body** (`LockAcquisitionRequest`)

| Field               | Type             | Required | Description                                                                                  |
|---------------------|------------------|----------|----------------------------------------------------------------------------------------------|
| `acquiredForMillis` | number (long)    | yes      | How long the lock should be held, in milliseconds. Stored on the lock for future expiry checks. |
| `lastAcquisitionId` | string \| null   | no       | The `acquisitionId` you previously observed on the lock (e.g. from a prior failed acquire). Required to take over an expired lock. |
| `elapsedMillis`     | number \| null   | no       | Time elapsed since `lastAcquisitionId` was observed, in milliseconds. The takeover succeeds only if `elapsedMillis > acquiredForMillis` of the existing lock (strict greater-than). |

For a first-time acquire (no existing lock) you can omit both
`lastAcquisitionId` and `elapsedMillis`.

**Successful response** — `201 Created` (`LockResponse`)

```json
{
  "status": "ACQUIRED",
  "lock": {
    "key": "service-1",
    "owner": "runner-A",
    "acquisitionId": "runner-A-9b8e2c4a-...",
    "acquiredForMillis": 30000
  }
}
```

| Field                    | Type   | Description                                                          |
|--------------------------|--------|----------------------------------------------------------------------|
| `status`                 | enum   | Always `"ACQUIRED"` on success (whether brand-new, renewal, or takeover). |
| `lock.key`               | string | Echo of the path variable.                                           |
| `lock.owner`             | string | The owner now holding the lock (== caller's runner id).              |
| `lock.acquisitionId`     | string | Server-generated. **Cache this** — it's needed to detect expiry on a subsequent failed acquire. |
| `lock.acquiredForMillis` | number | Echo of the requested duration.                                      |

**Error responses**

| HTTP | `code`      | When                                                                              |
|------|-------------|-----------------------------------------------------------------------------------|
| 400  | `R_GEN_01`  | `flamingock-runner-id` header missing.                                            |
| 401  | `R_AUTH_*`  | JWT missing / invalid / expired.                                                  |
| 409  | `R_LOCK_01` | Lock is held by a different, non-expired owner — or expired but takeover not proven (no/invalid `lastAcquisitionId`, or `elapsedMillis ≤ acquiredForMillis`). |

Error body shape (`ErrorResponse`):

```json
{
  "code": "R_LOCK_01",
  "message": "Lock is acquired by other process",
  "recoverable": true,
  "details": []
}
```

### Acquire — curl examples

**First-time acquire (no existing lock)**

```bash
curl -i -X POST \
  "http://localhost:8080/api/v1/service-1/lock/acquisition" \
  -H "Authorization: Bearer ${JWT}" \
  -H "flamingock-runner-id: runner-A" \
  -H "Content-Type: application/json" \
  -d '{"acquiredForMillis": 30000}'
```

```
HTTP/1.1 201
Content-Type: application/json

{"status":"ACQUIRED","lock":{"key":"service-1","owner":"runner-A","acquisitionId":"runner-A-...","acquiredForMillis":30000}}
```

**Take over an expired lock**

You learned the previous holder's `acquisitionId` from an earlier failed
acquire (the error body parameters echoed it back). The previous lock was
held for `5000` ms, you've observed it for `12000` ms, so it's safely
expired:

```bash
curl -i -X POST \
  "http://localhost:8080/api/v1/service-1/lock/acquisition" \
  -H "Authorization: Bearer ${JWT}" \
  -H "flamingock-runner-id: runner-B" \
  -H "Content-Type: application/json" \
  -d '{
        "acquiredForMillis": 30000,
        "lastAcquisitionId": "runner-A-9b8e2c4a-...",
        "elapsedMillis": 12000
      }'
```

```
HTTP/1.1 201
Content-Type: application/json

{"status":"ACQUIRED","lock":{"key":"service-1","owner":"runner-B",...}}
```

**Conflict (lock held by another, not expired)**

```bash
curl -i -X POST \
  "http://localhost:8080/api/v1/service-1/lock/acquisition" \
  -H "Authorization: Bearer ${JWT}" \
  -H "flamingock-runner-id: runner-B" \
  -H "Content-Type: application/json" \
  -d '{"acquiredForMillis": 30000}'
```

```
HTTP/1.1 409
Content-Type: application/json

{"code":"R_LOCK_01","message":"Lock is acquired by other process","recoverable":true,"details":[]}
```

---

## 2. Extend a lock

### `POST /api/v1/{key}/lock/extension`

Rotate the lock's `acquisitionId`, asserting that the caller is still the
owner. Use this as a heartbeat to keep a lock alive across long-running
work — call it before the previously-acquired duration elapses.

**Path parameters**

| Param | Type   | Description                          |
|-------|--------|--------------------------------------|
| `key` | string | Lock key (typically the service id). |

**Headers**

| Header                  | Required | Description                          |
|-------------------------|----------|--------------------------------------|
| `Authorization`         | yes      | `Bearer <runner-jwt>`                |
| `flamingock-runner-id`  | yes      | Runner id; must equal the lock owner |
| `Content-Type`          | optional | No body, header may be omitted       |

**Body**: empty (no JSON body, no query parameters).

**Successful response** — `200 OK` (`LockResponse`)

```json
{
  "status": "EXTENDED",
  "lock": {
    "key": "service-1",
    "owner": "runner-A",
    "acquisitionId": "runner-A-9b8e2c4a-...",
    "acquiredForMillis": 30000
  }
}
```

| Field                    | Type   | Description                                                        |
|--------------------------|--------|--------------------------------------------------------------------|
| `status`                 | enum   | Always `"EXTENDED"` on success.                                    |
| `lock.key`               | string | Echo of the path variable.                                         |
| `lock.owner`             | string | Confirmed lock owner (== caller's runner id).                      |
| `lock.acquisitionId`     | string | **New** acquisitionId — replaces the previous one client-side.     |
| `lock.acquiredForMillis` | number | Lock duration as set at acquire time. Extend does not change it.   |

**Error responses**

| HTTP | `code`      | When                                                    |
|------|-------------|---------------------------------------------------------|
| 400  | `R_GEN_01`  | `flamingock-runner-id` header missing.                  |
| 401  | `R_AUTH_*`  | JWT missing / invalid / expired.                        |
| 409  | `R_LOCK_01` | Lock does not exist, or it is held by a different owner.|

### Extend — curl examples

**Happy path**

```bash
curl -i -X POST \
  "http://localhost:8080/api/v1/service-1/lock/extension" \
  -H "Authorization: Bearer ${JWT}" \
  -H "flamingock-runner-id: runner-A"
```

```
HTTP/1.1 200
Content-Type: application/json

{"status":"EXTENDED","lock":{"key":"service-1","owner":"runner-A","acquisitionId":"runner-A-...","acquiredForMillis":30000}}
```

**Conflict (caller is not the owner)**

```bash
curl -i -X POST \
  "http://localhost:8080/api/v1/service-1/lock/extension" \
  -H "Authorization: Bearer ${JWT}" \
  -H "flamingock-runner-id: someone-else"
```

```
HTTP/1.1 409
Content-Type: application/json

{"code":"R_LOCK_01","message":"Lock is acquired by other process","recoverable":true,"details":[]}
```

**Missing runner-id header**

```bash
curl -i -X POST \
  "http://localhost:8080/api/v1/service-1/lock/extension" \
  -H "Authorization: Bearer ${JWT}"
```

```
HTTP/1.1 400
Content-Type: application/json

{"code":"R_GEN_01","message":"...","recoverable":true,"details":[]}
```

---

## 3. Release a lock

### `DELETE /api/v1/{key}/lock`

Release a lock that the caller currently owns. Idempotent in spirit (the row
is gone after the call) but **not** idempotent on the wire: a second call by
the same runner returns `409 R_LOCK_02` because the row no longer exists.

**Path parameters**

| Param | Type   | Description |
|-------|--------|-------------|
| `key` | string | Lock key.   |

**Headers**

| Header                  | Required | Description                          |
|-------------------------|----------|--------------------------------------|
| `Authorization`         | yes      | `Bearer <runner-jwt>`                |
| `flamingock-runner-id`  | yes      | Runner id; must equal the lock owner |

**Body**: empty.

**Successful response** — `200 OK` (`LockInfoResponse`)

The body is the lock as it stood **immediately before** deletion — convenient
for logging or audit but not strictly needed by clients.

```json
{
  "key": "service-1",
  "owner": "runner-A",
  "acquisitionId": "runner-A-9b8e2c4a-...",
  "acquiredForMillis": 30000
}
```

**Error responses**

| HTTP | `code`      | When                                                                |
|------|-------------|---------------------------------------------------------------------|
| 400  | `R_GEN_01`  | `flamingock-runner-id` header missing.                              |
| 401  | `R_AUTH_*`  | JWT missing / invalid / expired.                                    |
| 409  | `R_LOCK_02` | Lock does not exist, or it is held by a different owner.            |

### Release — curl examples

**Happy path**

```bash
curl -i -X DELETE \
  "http://localhost:8080/api/v1/service-1/lock" \
  -H "Authorization: Bearer ${JWT}" \
  -H "flamingock-runner-id: runner-A"
```

```
HTTP/1.1 200
Content-Type: application/json

{"key":"service-1","owner":"runner-A","acquisitionId":"runner-A-...","acquiredForMillis":30000}
```

**Conflict (caller is not the owner, or lock already released)**

```bash
curl -i -X DELETE \
  "http://localhost:8080/api/v1/service-1/lock" \
  -H "Authorization: Bearer ${JWT}" \
  -H "flamingock-runner-id: runner-A"
```

```
HTTP/1.1 409
Content-Type: application/json

{"code":"R_LOCK_02","message":"Lock cannot be deleted because it's acquired by other process","recoverable":true,"details":[]}
```

---

## 4. Get a lock

### `GET /api/v1/{key}/lock`

Read-only lookup of the current lock for `key`. Does **not** modify state and
does **not** require the caller to own the lock — any authenticated runner
in the environment can query. Useful before deciding to acquire or after
seeing a conflict to inspect who currently holds it.

**Path parameters**

| Param | Type   | Description |
|-------|--------|-------------|
| `key` | string | Lock key.   |

**Headers**

| Header                  | Required | Description                          |
|-------------------------|----------|--------------------------------------|
| `Authorization`         | yes      | `Bearer <runner-jwt>`                |
| `flamingock-runner-id`  | yes      | Runner id (auth contract; not checked against the lock owner) |

**Body**: empty.

**Successful response** — `200 OK` (`LockInfoResponse`)

```json
{
  "key": "service-1",
  "owner": "runner-A",
  "acquisitionId": "runner-A-9b8e2c4a-...",
  "acquiredForMillis": 30000
}
```

**Error responses**

| HTTP | `code`      | When                                                      |
|------|-------------|-----------------------------------------------------------|
| 400  | `R_GEN_01`  | `flamingock-runner-id` header missing.                    |
| 401  | `R_AUTH_*`  | JWT missing / invalid / expired.                          |
| 404  | `R_LOCK_03` | No lock exists for the given `key` in this environment.   |

The `404` body includes the queried key in `parameters` to make it easy to
correlate when logging:

```json
{
  "code": "R_LOCK_03",
  "message": "Lock not found",
  "recoverable": true,
  "details": []
}
```

### Get — curl examples

**Happy path**

```bash
curl -i -X GET \
  "http://localhost:8080/api/v1/service-1/lock" \
  -H "Authorization: Bearer ${JWT}" \
  -H "flamingock-runner-id: runner-A"
```

```
HTTP/1.1 200
Content-Type: application/json

{"key":"service-1","owner":"runner-A","acquisitionId":"runner-A-...","acquiredForMillis":30000}
```

**Reading a lock you don't own (still 200)**

```bash
curl -i -X GET \
  "http://localhost:8080/api/v1/service-1/lock" \
  -H "Authorization: Bearer ${JWT}" \
  -H "flamingock-runner-id: someone-else"
```

```
HTTP/1.1 200
Content-Type: application/json

{"key":"service-1","owner":"runner-A","acquisitionId":"runner-A-...","acquiredForMillis":30000}
```

**No lock for that key**

```bash
curl -i -X GET \
  "http://localhost:8080/api/v1/no-such-key/lock" \
  -H "Authorization: Bearer ${JWT}" \
  -H "flamingock-runner-id: runner-A"
```

```
HTTP/1.1 404
Content-Type: application/json

{"code":"R_LOCK_03","message":"Lock not found","recoverable":true,"details":[]}
```

---

## Authentication errors

These apply equally to all three endpoints and are produced by upstream
filters / the `@SecuredRunnerOperation` aspect, before the controller runs.

| HTTP | `code`     | Cause                                                                 |
|------|------------|-----------------------------------------------------------------------|
| 400  | `R_GEN_01` | `flamingock-runner-id` header missing.                                |
| 401  | (varies)   | JWT missing, malformed, expired, signature invalid, or wrong issuer.  |

If you hit any of these in tests, look at the request headers first — these
errors do not depend on lock state.

---

## End-to-end test recipe

Recommended sequence to verify the client implementation against a running
runner application:

1. **Get** a fresh key. Confirm `404 R_LOCK_03`.
2. **Acquire** the lock with `{"acquiredForMillis": 30000}`. Confirm `201`,
   `status == "ACQUIRED"`, and capture `lock.acquisitionId`.
3. **Get** the same key — possibly with a *different* `flamingock-runner-id`.
   Confirm `200` and that `owner` matches the runner from step 2 (read
   does not require ownership).
4. **Acquire** the same key from a different `flamingock-runner-id` without
   `lastAcquisitionId`. Confirm `409` and `code == "R_LOCK_01"`.
5. **Extend** as the original runner. Confirm `200`, `status == "EXTENDED"`,
   and that `lock.acquisitionId` in the response **differs** from the value
   captured at step 2.
6. **Extend** as a different `flamingock-runner-id`. Confirm `409` and
   `code == "R_LOCK_01"`.
7. **Release** as the original runner. Confirm `200`. A follow-up `DELETE`
   should return `409 R_LOCK_02`, and a follow-up `GET` should return
   `404 R_LOCK_03`.
8. **Negative**: omit the `flamingock-runner-id` header on any endpoint and
   confirm `400 R_GEN_01`.
9. **Takeover** (optional): acquire with a short `acquiredForMillis` (e.g.
   `1000`), wait > 1 s, then re-acquire from a different runner passing
   `lastAcquisitionId` (from step 9's first response) and `elapsedMillis`
   larger than the original duration. Confirm `201` and the new owner.

Pinning these assertions catches all the contract bugs the server-side
test suite covers.

---

## Reference (server-side, for navigation only)

- Controller: `lock/lock-service/src/main/kotlin/io/flamingock/server/lock/service/RestLockService.kt`
- API contract: `lock/lock-api/src/main/kotlin/io/flamingock/server/lock/controller/LockServiceApi.kt`
- Errors: `lock/lock-api/src/main/kotlin/io/flamingock/server/lock/controller/LockErrors.kt`
- Domain handler: `lock/lock-service/src/main/kotlin/io/flamingock/server/lock/logic/LockDomainHandler.kt`
- Server tests covering this contract:
  - `lock/lock-service/src/test/kotlin/.../logic/LockDomainHandlerTest.kt` (handler logic)
  - `lock/lock-service/src/test/kotlin/.../repository/jpa/SqlLockEntryRepositoryTest.kt` (JPQL + race-safety)
  - `lock/lock-service/src/test/kotlin/.../service/LockControllerWebTest.kt` (HTTP contract)
  - `runner-application/src/test/kotlin/.../lock/LockEndToEndIT.kt` (full stack)
