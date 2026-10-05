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
| `messaging-ui` | Login and chat pages (Mustache + vanilla JS WebSocket) | — |

## Stack
Java 25 · Spring Boot 4.1 · Spring Security (OAuth2 resource server, Nimbus JOSE) · Spring Data JPA · Flyway · MapStruct · Lombok · ScyllaDB · PostgreSQL · gRPC · Testcontainers · Docker

## Run locally
```bash
cp .env.example .env          # adjust credentials
docker compose up -d          # PostgreSQL + ScyllaDB
./mvnw verify                 # build + all tests
```
Run the whole stack in Docker, then open **http://localhost:8080**:
```bash
docker compose --profile app up -d --build
```
Register two users in two browsers (or one normal and one private window) and chat.
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

**ScyllaDB data model (query-first):**
- `messages_by_conversation ((conversation_id, day_bucket), message_id DESC)`
  - Each read is a single-partition range scan.
  - The day bucket keeps partitions bounded, and paging walks back one bucket at a time.
- `direct_conversations ((user_low, user_high))`
  - Written with `INSERT … IF NOT EXISTS` (a lightweight transaction).
  - Two users starting a chat at the same moment get exactly one conversation.
- `conversations_by_user (user_id, conversation_id)`
  - Written `USING TIMESTAMP` = the message's Snowflake time, so a delayed older write can never overwrite a newer "last message".
- `cluster_members`: TTL heartbeats for the hash ring (milestone 4).

Message IDs are serialized as **strings**: they are 64-bit, and JavaScript numbers lose precision above 2^53.

## messaging-ui (BFF)
- **Login and registration happen on the server.** The **refresh token never reaches the browser.** It lives in the server-side HttpSession, behind an `HttpOnly` + `SameSite=Lax` cookie, and session ids never appear in URLs.
- **The page gets only the short-lived access token** (`POST /api/v1/session/token`, same origin). It uses it to talk to chat-service directly.
- **Token refresh is synchronized per session.** Several tabs share one session, so a parallel refresh would replay the same single-use refresh token, which auth-service treats as theft.
- **User search and lookup are proxied through the UI**, so auth-service is never exposed to browser traffic and needs no CORS.
- All user content is rendered with `textContent` and Mustache escaping, so it can't inject HTML or scripts (XSS).

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
  - `service.spec` (interfaces) and `service.impl` (implementations)
  - `repository`
  - `model.po` / `model.po.eo` / `model.dto` / `model.vo`
  - `mapper`, `exception`, `config`, `util`
- Each microservice has its own `Dockerfile`.

## Roadmap
1. ✅ Foundation: ID generators, compose, schema
2. ✅ auth-service
3. ✅ Single-node chat + UI
4. ⏳ Multi-node: hash ring + gRPC forwarding
5. Receipts, presence, offline sync, load test, CI
