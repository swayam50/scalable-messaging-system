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
Java 25 · Spring Boot 4.1 · Spring Security · Spring Data JPA · Flyway · MapStruct · Lombok · ScyllaDB · PostgreSQL · gRPC · Testcontainers · Docker

## Run locally
```bash
cp .env.example .env          # adjust credentials
docker compose up -d          # PostgreSQL + ScyllaDB
./mvnw verify                 # build + all tests
```
Apply the ScyllaDB schema (automated in a later milestone):
```bash
docker compose exec -T scylla cqlsh < chat-service/src/main/resources/cql/V1__schema.cql
```

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

## Project conventions
- Layered packaging under `io.wulfcodes.messaging.<module>`:
  - `controller` (MVC) and `controller.resource.v1` (versioned REST)
  - `service.spec` (interfaces) and `service.impl` (implementations)
  - `repository`
  - `model.po` / `model.po.eo` / `model.dto` / `model.vo`
  - `mapper`, `exception`, `config`, `util`
- Each microservice has its own `Dockerfile`.

## Roadmap
1. ✅ Foundation: ID generators, compose, schema
2. ⏳ auth-service
3. Single-node chat + UI
4. Multi-node: hash ring + gRPC forwarding
5. Receipts, presence, offline sync, load test, CI
