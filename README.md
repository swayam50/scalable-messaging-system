# High-Performance Messaging System

A WhatsApp-style chat backend built to scale horizontally:

- **WebSocket nodes** (`chat-service`) hold client connections.
- A **consistent-hash ring** assigns each user to one node.
- Messages for users on another node are forwarded **node to node over gRPC**.
- Messages are stored in **ScyllaDB**, keyed by **Snowflake** IDs, which are time-ordered.
- Public IDs (users, conversations) are **ULIDs**.
- **auth-service** handles accounts and issues JWTs, backed by **PostgreSQL**.
- **messaging-ui** is a server-rendered Spring Boot + Mustache client.

## Modules
| Module | Purpose | Store |
|---|---|---|
| `messaging-common` | Shared library: Snowflake/ULID generators, shared value objects and DTOs | — |
| `auth-service` | Register, login, JWT access + refresh tokens, users | PostgreSQL |
| `chat-service` | WebSockets, hash ring, gRPC forwarding, message storage | ScyllaDB |
| `media-service` | Attachments: presigned uploads/downloads, verification, signed descriptors | PostgreSQL + SeaweedFS (S3) |
| `messaging-ui` | Login and chat pages (Mustache + vanilla JS WebSocket) | — |
| `load-test` | WebSocket load generator: per-stage ACK and delivery latency, throughput | — |

## Stack
Java 25 · Spring Boot 4.1 · Spring gRPC · SeaweedFS (S3) · Spring Security (OAuth2 resource server, Nimbus JOSE) · Spring Data JPA · Flyway · MapStruct · Lombok · ScyllaDB · PostgreSQL · gRPC · Testcontainers · Docker

## Run locally
```bash
cp .env.example .env          # adjust credentials
docker compose up -d          # PostgreSQL + ScyllaDB + SeaweedFS
./mvnw verify                 # build + all tests
```
Run the whole stack in Docker, then open **http://localhost:8080**:
```bash
docker compose --profile app up -d --build
```
This starts PostgreSQL, ScyllaDB, SeaweedFS, auth-service, **three chat-service nodes** (8082–8084), media-service (8085) and the UI.
Register two users in two browsers (or one normal and one private window) and chat. The status line
shows which node each user is connected to. Stop one node (`docker compose stop chat-service-1`) and
watch its users reconnect to their new owner.
chat-service applies its ScyllaDB schema at startup; the script is idempotent.

## auth-service API (v1)
| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/v1/auth/register` | — | Create account → access + refresh token |
| POST | `/api/v1/auth/login` | — | Login with username or email |
| POST | `/api/v1/auth/refresh` | — | Rotate refresh token, new access token |
| POST | `/api/v1/auth/logout` | — | Revoke refresh token |
| GET | `/api/v1/users/me` | Bearer | Own profile |
| GET | `/api/v1/users/{id}` | Bearer | Public profile |
| GET | `/api/v1/users?query=` | Bearer | Search by username prefix |
| GET | `/.well-known/jwks.json` | — | Public signing key for other services |

**Security design:**
- **Access tokens:** RS256 JWTs that live 15 minutes. Other services verify them with the public JWKS, so no secret is shared between services.
- **Refresh tokens:** opaque, random 256-bit values that live 7 days. Only their SHA-256 hash is stored.
- **Rotation:** refresh tokens are rotated on every use. If an old token is reused, every session for that user is revoked.
- **Passwords:** hashed with BCrypt via a delegating encoder. Error messages are generic, and the timing is the same whether or not the user exists.
- **Errors:** returned as RFC 9457 `application/problem+json`. API versioning uses Spring Framework 7's built-in support.

## chat-service
**WebSocket** `ws://host:8082/ws/chat?access_token=<JWT>`. The handshake is refused with 401 unless the JWT verifies against auth-service's JWKS.

| Frame | Direction | Example |
|---|---|---|
| `SEND` | client → server | `{"type":"SEND","conversationId":"01J…","clientMessageId":"c-1","body":"hi"}` |
| `ACK` | server → sender | stored message + echoed `clientMessageId` |
| `MESSAGE` | server → participants | new message (recipient + sender's other tabs) |
| `ERROR` | server → client | validation / permission errors |

**REST v1 (Bearer):**
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/conversations` | Inbox, most recently active first |
| POST | `/api/v1/conversations` `{"peerId"}` | Get or create a 1:1 conversation (idempotent) |
| GET | `/api/v1/conversations/{id}/messages?before=&limit=` | History, newest first, cursor-paged |
| GET | `/api/v1/connect` | Which node owns the caller (`nodeId`, `wsUrl`) |

**ScyllaDB data model (query-first):**
- `messages_by_conversation ((conversation_id, day_bucket), message_id DESC)`
  - Each read is a single-partition range scan.
  - The day bucket keeps partitions bounded, and paging walks back one bucket at a time.
- `direct_conversations ((user_low, user_high))`
  - Written with `INSERT … IF NOT EXISTS` (a lightweight transaction).
  - Two users starting a chat at the same moment get exactly one conversation.
- `conversations_by_user (user_id, conversation_id)`
  - Written `USING TIMESTAMP` = the message's Snowflake time, so a delayed older write can never overwrite a newer "last message".
- `cluster_nodes (cluster, node_id)`: live nodes, written with TTL heartbeats for the hash ring.

Message IDs are serialized as **strings**: they are 64-bit, and JavaScript numbers lose precision above 2^53.

## Cluster: consistent hashing + gRPC
```
                 ┌──────────── consistent-hash ring (128 virtual nodes / node) ────────────┐
 alice ──WS──▶ chat-1 ──gRPC Deliver──▶ chat-2 ◀──WS── bob          chat-3
                 │  store message (ScyllaDB)        │ push to bob's sockets
                 └── membership: cluster_nodes rows written USING TTL (heartbeats) ──┘
```
- **Membership:** each node upserts its row in `cluster_nodes` every 5s **with a 15s TTL**.
  - A crashed node's row expires on its own. A clean shutdown deletes the row straight away.
  - Every node re-reads membership every 2s and rebuilds the same ring.
  - No ZooKeeper, etcd or Redis is needed.
- **Ownership:** a user belongs to the first virtual node clockwise from `MD5(userId)`.
  - Adding a node moves only about 1/N of the users. With `hash % N`, most users would move. Both properties are covered by tests.
- **Connecting:** the client calls `GET /api/v1/connect`, which any node can answer, and opens its WebSocket on the owner.
  - A handshake on the wrong node is refused.
  - When the ring changes, nodes close sockets they no longer own with code **4001**, and those clients reconnect to their new owner.
- **Delivery:** the node that receives a message stores it, then:
  - writes it locally if it owns the recipient, or
  - forwards it with a gRPC `NodeDelivery.Deliver` call (async stub, 2s deadline, one HTTP/2 channel per peer).
  - A failed forward never fails the send. The message is already stored, so the client gets it from history on reconnect.
- **Internal security:** the internal gRPC port requires a shared **cluster token**, checked by a server interceptor with a constant-time comparison. Mutual TLS would be the production upgrade.
- **Snowflake IDs:** each node has its own worker id, so message ids never collide across nodes.
- **REST failover:** every node serves the full REST API. The UI gets the list of nodes, and the client moves to the next one if a node is down.
  - A load balancer would usually do this in production.

## Media messages (image · video · audio · file)
```
browser ──1. POST /api/v1/media/uploads──▶ media-service   (type/size policy + membership check via chat-service,
        ◀── presigned POST policy ───────                   relaying the user's own token)
browser ──2. multipart POST (file bytes)─▶ SeaweedFS S3   (policy enforces exact key, Content-Type, max size)
browser ──3. POST /api/v1/media/{id}/complete ▶ media-service (stat object: real size/type) ──▶ HMAC-signed descriptor
browser ──4. SEND {contentType, attachment}─▶ chat-service  (verifies the HMAC locally: uploader = sender,
                                                             same conversation & type; no network call)
viewer  ──5. GET /api/v1/media/{id}/download ▶ media-service (participants only) ──▶ short-lived presigned GET
```
- **File bytes never pass through our services.** Browsers upload to and download from object storage directly.
- **Limits are enforced by the storage itself.** The signed POST policy fixes the key, the Content-Type and a size range, so a client can't upload anything other than what it was approved for.

  | Type | Max size | Allowed formats |
  |---|---|---|
  | Image | 10 MB | JPEG, PNG, GIF, WebP |
  | Video | 100 MB | MP4, WebM, QuickTime |
  | Audio | 20 MB | MP3, OGG, WAV, WebM, M4A |
  | File | 25 MB | anything else; executables and scripts are refused |
- **Attachments are verified, not trusted.** On complete, media-service checks the stored object's real size and type. It then **signs** a descriptor with HMAC-SHA256, using a secret shared with chat-service. chat-service checks that signature without calling media-service, so a client can't forge, move or relabel an attachment.
- **In ScyllaDB, an attachment is a user-defined type (UDT)** embedded in the message row (`model.po.eo.AttachmentUdt`).
- **Object storage is any S3-compatible server.**
  - **Locally:** **SeaweedFS** (Apache-2.0) with its S3 gateway, on port 8333.
  - **In production:** AWS S3 or Cloudflare R2, with only config changes.
  - **Client:** the code uses the MinIO Java SDK purely as an S3 client. The integration test runs the full flow against real SeaweedFS, including SeaweedFS rejecting an upload that breaks the signed policy.

## messaging-ui (BFF)
- **Login and registration happen on the server.** The **refresh token never reaches the browser.** It lives in the server-side HttpSession, behind an `HttpOnly` + `SameSite=Lax` cookie, and session ids never appear in URLs.
- **The page gets only the short-lived access token** (`POST /api/v1/session/token`, same origin). It uses it to talk to chat-service directly.
- **Token refresh is synchronized per session.** Several tabs share one session, so a parallel refresh would replay the same single-use refresh token, which auth-service treats as theft.
- **User search and lookup are proxied through the UI**, so auth-service is never exposed to browser traffic and needs no CORS.
- All user content is rendered with `textContent` and Mustache escaping, so it can't inject HTML or scripts (XSS).

## Real-time events
| Frame | Direction | Purpose |
|---|---|---|
| `SEND` | client → server | text or media message |
| `READ` | client → server | read up to a message (moves the read pointer) |
| `TYPING` | both | typing indicator (never stored) |
| `ACK` | server → sender | stored; always sent **before** fan-out |
| `MESSAGE` | server → participants | new message |
| `RECEIPT` | server → sender | `DELIVERED` (reached a live session, also across nodes) / `READ` |
| `PRESENCE` | server → contacts | online / offline + last seen |
| `ERROR` | server → client | validation / permission errors |

- **One handler per client frame type:** each type has its own `FrameHandler` class, and `FrameDispatcher` routes to them. Adding a frame type means adding one class.
- **Ticks:** ✓ means stored, ✓✓ delivered, highlighted ✓✓ read. Read pointers are written `USING TIMESTAMP` = message time, so they only ever move forward.
- **Presence:** each user's owner node is the source of truth. `/api/v1/presence` asks each owner node over gRPC. Moving between nodes does not show the user as offline.

## CI (GitHub Actions)
The workflow is `.github/workflows/ci.yml`. It is **manual only**: no push or pull-request triggers, so it never runs on a commit by itself.
- **Run it:** from the GitHub **Actions** tab → **CI** → **Run workflow**, or with `gh workflow run ci.yml -f tests=all -f build-images=false`.
- **Inputs:**
  - `tests`: `all` runs unit tests plus Testcontainers integration tests on the runner's Docker (ScyllaDB, PostgreSQL, SeaweedFS). `unit-only` skips `*IntegrationTest`: about 80 tests in under a minute, no Docker needed.
  - `build-images`: also builds every service's Docker image in a matrix, with layer caching and no push.
- **Output:** a per-module test summary on the run page, with the Surefire reports uploaded as an artifact. Runs on the same branch cancel each other, so only the latest one runs.

## Performance
Load-tested with the `load-test` module: real JWTs, real WebSockets, and an open-loop send rate. Full tables and method are in [load-test/RESULTS.md](load-test/RESULTS.md).
- **Setup:** all on one 12-core laptop (3 chat nodes + ScyllaDB + PostgreSQL + SeaweedFS + the load generator), with **2,000 concurrent WebSockets**. About 2/3 of conversations span two nodes, so their messages go over gRPC.
- **Up to 8,000 msg/s with 0 errors and 0 lost messages.** At 4,000 msg/s, ACK p99 is **19.5 ms** (ScyllaDB on 2 shards).
- **10,000 msg/s is sustained with 0 timeouts** (p99 ≈ 210 ms), and the ceiling is about 12k msg/s on 2 shards. **ScyllaDB was the bottleneck:** going from 1 shard to 2 raised the ceiling about 25%.
- **Cross-node delivery over gRPC adds about 1 ms at p50.**

## IDs
**Snowflake (64-bit, message IDs)**
```
0 | 41 bits ms since 2026-01-01 | 10 bits workerId | 12 bits sequence
```
- They sort by time and are unique with no coordination between nodes.
- Each node can generate 4,096 IDs per millisecond.
- If the clock moves backwards, generation is refused so no ID can repeat.

**ULID (128-bit, public IDs)**
- Made of a 48-bit timestamp and 80 random bits, written as 26 Crockford Base32 characters.
- The strings sort by time, and IDs can't be guessed.
- Generation is monotonic within the same millisecond.

> Maven resolves from Maven Central via the project-local `.mvn/settings.xml`, so builds don't depend on any machine-wide `~/.m2/settings.xml`.

## Project conventions
- Layered packaging under `io.wulfcodes.messaging.<module>`:
  - `controller` (MVC) and `controller.resource.v1` (versioned REST)
  - `websocket` (WebSocket handlers and handshake interceptors)
  - `grpc` (gRPC endpoints and interceptors; generated stubs in `grpc.proto`)
  - `service.spec` (interfaces) and `service.impl` (implementations)
  - `repository`
  - `model.po` / `model.po.eo` / `model.dto` / `model.vo`
  - `mapper`, `exception`, `config`, `util`
- Each microservice has its own `Dockerfile`.

## Roadmap
1. ✅ Foundation: ID generators, compose, schema
2. ✅ auth-service
3. ✅ Single-node chat + UI
4. ✅ Multi-node: hash ring, TTL membership, gRPC forwarding, rebalancing
5. ✅ Receipts, typing, presence, offline sync
6. ✅ Media messages (image, video, audio, file)
7. ✅ Load test (see Performance)
8. ✅ CI (GitHub Actions, manually triggered)
