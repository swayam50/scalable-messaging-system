# Load test results

**Setup:**
- **Hardware:** one 12-core laptop with 15 GB RAM. Everything runs on it at once: 3 chat-service nodes (1 GB heap each), ScyllaDB, PostgreSQL, SeaweedFS **and the load generator itself**. These numbers are a conservative floor, not a cluster benchmark.
- **Users:** 2,000 virtual users, so **2,000 concurrent WebSockets**, paired into 1,000 conversations. User ids are random ULIDs, so the consistent-hash ring spreads them over the 3 nodes. About **2/3 of conversations span two nodes**, so their messages cross nodes over gRPC.
- **Auth:** real RS256 JWTs (the nodes verify them with a configured public key), real `/api/v1/connect` lookups, and real WebSocket connections to each user's owner node.
- **Send pattern:** open-loop. Messages are sent on a fixed schedule whatever the server's speed, so overload shows up as latency, not as a quietly lower rate. Each stage has a 5 s warm-up and a 20 s measured window.
- **Cost per message:** 1 ScyllaDB write for the message + 2 inbox upserts, then delivery to the recipient's owner node.
- **ACK latency:** send → ACK (the message is durably stored).
- **Delivery latency:** send → MESSAGE frame on the recipient's socket. Sender and recipient share one JVM clock.

## ScyllaDB 1 shard / 1 GB (compose default)
| Target msg/s | Sent/s | ACKed/s | Delivered/s | ACK p50 / p95 / p99 (ms) | Same-node delivery p50 / p99 | Cross-node (gRPC) delivery p50 / p99 | Errors | Timeouts |
|---|---|---|---|---|---|---|---|---|
| 1000 | 1000 | 1000 | 1000 | 2.2 / 6.1 / 13.0 | 2.3 / 12.9 | 3.0 / 14.6 | 0 | 0 |
| 2000 | 2000 | 2000 | 2000 | 3.3 / 10.3 / 22.7 | 3.4 / 22.3 | 4.0 / 27.3 | 0 | 0 |
| 4000 | 4000 | 4000 | 4000 | 4.4 / 21.2 / 85.5 | 4.5 / 85.2 | 5.3 / 90.4 | 0 | 0 |
| 6000 | 6000 | 6000 | 6000 | 4.5 / 33.9 / 155.5 | 4.6 / 155.7 | 5.4 / 157.2 | 0 | 0 |
| 8000 | 8000 | 8000 | 8000 | 5.1 / 23.7 / 72.1 | 5.2 / 72.4 | 6.1 / 74.9 | 0 | 0 |
| 10000 | 10000 | 9789 | 9789 | 1238.8 / 5181.8 / 6694.4 | 1254.8 / 6743.4 | 1239.1 / 6667.8 | 0 | 4238 |
| 12000 | 12001 | 8364 | 8364 | 4697.6 / 13927.8 / 16956.7 | 4766.1 / 17016.2 | 4673.0 / 16922.1 | 0 | 72801 |

The 6k stage was repeated (p99 227 ms, then 155 ms). Single-machine runs show tail noise from GC and compaction.

## ScyllaDB 2 shards / 2 GB (`SCYLLA_SMP=2 SCYLLA_MEMORY=2G`)
| Target msg/s | Sent/s | ACKed/s | Delivered/s | ACK p50 / p95 / p99 (ms) | Same-node delivery p50 / p99 | Cross-node (gRPC) delivery p50 / p99 | Errors | Timeouts |
|---|---|---|---|---|---|---|---|---|
| 4000 | 4000 | 4000 | 4000 | 3.8 / 12.5 / 19.5 | 3.9 / 20.2 | 4.5 / 21.8 | 0 | 0 |
| 8000 | 8000 | 8000 | 8000 | 6.6 / 31.0 / 83.5 | 6.6 / 84.1 | 7.6 / 86.5 | 0 | 0 |
| 10000 | 9994 | 9994 | 9994 | 52.6 / 154.4 / 208.2 | 52.0 / 210.9 | 57.1 / 211.2 | 0 | 0 |
| 12000 | 11997 | 11997 | 11997 | 994.3 / 5930.6 / 8234.2 | 931.6 / 8406.0 | 1032.8 / 8155.6 | 0 | 0 |
| 14000 | 13994 | 12600 | 12599 | 1305.5 / 8516.8 / 11874.2 | 1264.8 / 11836.3 | 1340.9 / 11918.8 | 0 | 27960 |

## Findings
- **No errors at any load.** When overloaded, messages queue and get slower. Nothing was rejected or lost.
- **The ceiling is about 9.8k msg/s with ScyllaDB on 1 shard.** That's roughly 30k writes/s on a single core.
- **ScyllaDB was the bottleneck.** With 2 shards the ceiling rose to about **12k msg/s (+25%)**, p99 at 4k dropped from 85 ms to **19.5 ms**, and 10k msg/s was sustained with 0 timeouts. The next limit is CPU contention on the single machine.
- **Cross-node delivery over gRPC adds about 1 ms at p50** compared with same-node delivery.
- **Membership heals itself.** During the ScyllaDB restart, heartbeats failed, membership rows expired, the ring shrank to 2 nodes, and then it healed back to 3 with no restarts.

## Reproduce
```bash
./mvnw -q install -DskipTests
java -jar load-test/target/load-test-0.1.0-SNAPSHOT.jar keygen lt-keys
SCYLLA_SMP=2 SCYLLA_MEMORY=2G docker compose up -d
# start 3 chat nodes trusting the load-test key (ports 8082-8084, gRPC 9091-9093)
for n in 1 2 3; do
  AUTH_PUBLIC_KEY="$(cat lt-keys/public.pem)" AUTH_ISSUER=load-test \
  SERVER_PORT=808$((n+1)) CHAT_GRPC_PORT=909$n CHAT_NODE_ID=chat-$n CHAT_WORKER_ID=$n \
  CHAT_GRPC_ADDRESS=localhost:909$n CHAT_WS_URL=ws://localhost:808$((n+1))/ws/chat \
  java -Xmx1g -jar chat-service/target/chat-service-0.1.0-SNAPSHOT.jar &
done
java -Xmx2g -jar load-test/target/load-test-0.1.0-SNAPSHOT.jar run \
  --chat-urls=http://localhost:8082,http://localhost:8083,http://localhost:8084 \
  --users=2000 --rates=1000,2000,4000,8000,10000 --private-key=lt-keys/private.pem
```
