# 🪣 Spring Boot — Service-Level Token Bucket Rate Limiting

![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.0-6DB33F?style=flat&logo=springboot&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7.2-DC382D?style=flat&logo=redis&logoColor=white)
![MongoDB](https://img.shields.io/badge/MongoDB-7.0-47A248?style=flat&logo=mongodb&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat&logo=docker&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green?style=flat)

> Token Bucket rate limiting built directly inside a Spring Boot microservice using Redis atomic Lua scripts — no API gateway required. Per-client, per-endpoint, distributed enforcement across multiple pods.

---

## 📋 Table of Contents

- [Overview](#-overview)
- [Architecture](#-architecture)
- [Token Bucket Algorithm](#-token-bucket-algorithm)
- [How It Works](#-how-it-works)
- [Project Structure](#-project-structure)
- [Components](#-components)
- [Configuration](#-configuration)
- [API Endpoints](#-api-endpoints)
- [Response Headers](#-response-headers)
- [Quick Start](#-quick-start)
- [Validating Rate Limits](#-validating-rate-limits)
- [Admin Endpoints](#-admin-endpoints)
- [Running Tests](#-running-tests)

---

## 🔍 Overview

This project implements **service-level rate limiting** using the Token Bucket algorithm directly inside a Spring Boot microservice. It enforces per-client quotas on every HTTP endpoint without depending on an API gateway.

**Key features:**

- ✅ Token Bucket algorithm via atomic Redis Lua script — no race conditions
- ✅ Global rate limiting via `HandlerInterceptor` — covers all `/api/**` routes
- ✅ Per-endpoint overrides via `@RateLimit` custom AOP annotation
- ✅ Separate read and write buckets per client
- ✅ Three client key strategies — IP, API Key, User ID
- ✅ Fail-open on Redis errors — Redis outage won't block all traffic
- ✅ Admin endpoints to inspect and reset buckets
- ✅ Testcontainers integration tests for Redis

---

## 🏛 Architecture

```
  Client Request
       │
       ▼
┌─────────────────────────────────────────────────┐
│            Product Service  :8081               │
│                                                 │
│  ┌──────────────────────────────────────────┐   │
│  │       RateLimitInterceptor               │   │
│  │       (fires on every /api/** request)   │   │
│  │                                          │   │
│  │  GET         → read  bucket              │   │
│  │              → 10 req/s  burst 20        │   │
│  │                                          │   │
│  │  POST/PUT/   → write bucket              │   │
│  │  DELETE      → 5 req/s   burst 10        │   │
│  └────────────────────┬─────────────────────┘   │
│                       │ (if allowed)             │
│  ┌────────────────────▼─────────────────────┐   │
│  │       @RateLimit AOP Aspect              │   │
│  │       (annotated endpoints only)         │   │
│  │                                          │   │
│  │  /bulk    → 1 req/s   burst 3  (custom)  │   │
│  │  /search  → 5 req/s   burst 10 (custom)  │   │
│  └────────────────────┬─────────────────────┘   │
│                       │ (if allowed)             │
│          Controller → Service → Repository       │
│              ↕ Redis Cache (10 min TTL)          │
│              ↕ MongoDB                           │
└───────────────────────┼─────────────────────────┘
                        │ atomic Lua call
                        ▼
              ┌─────────────────┐
              │   Redis :6379   │
              │  token bucket   │
              │  state per      │
              │  client         │
              └─────────────────┘
```

---

## 🪣 Token Bucket Algorithm

The same atomic Lua script used internally by Spring Cloud Gateway's `RedisRateLimiter` — applied here at the service level.

```
┌──────────────────────────────────────────────────────────┐
│                                                          │
│  replenishRate   →  tokens added to bucket per second   │
│  burstCapacity   →  max tokens the bucket can hold      │
│  requestedTokens →  tokens consumed per request (= 1)   │
│                                                          │
│  On each request:                                        │
│                                                          │
│  delta      = now - lastRefillTime                       │
│  newTokens  = min(capacity, lastTokens + delta × rate)   │
│                                                          │
│  if newTokens >= requested  →  200 OK  (consume tokens) │
│  else                       →  429 Too Many Requests     │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

The script runs **atomically inside Redis** — no race conditions across multiple pods or concurrent requests.

**Lua script (RedisConfig.java):**

```lua
local tokens_key     = KEYS[1]
local timestamp_key  = KEYS[2]
local rate           = tonumber(ARGV[1])
local capacity       = tonumber(ARGV[2])
local now            = tonumber(ARGV[3])
local requested      = tonumber(ARGV[4])
local ttl            = math.ceil(capacity / rate) * 2

local last_tokens    = tonumber(redis.call('get', tokens_key))    or capacity
local last_refreshed = tonumber(redis.call('get', timestamp_key)) or 0

local delta         = math.max(0, now - last_refreshed)
local filled_tokens = math.min(capacity, last_tokens + (delta * rate))
local allowed       = filled_tokens >= requested
local new_tokens    = filled_tokens
local allowed_num   = 0

if allowed then
    new_tokens  = filled_tokens - requested
    allowed_num = 1
end

redis.call('setex', tokens_key,    ttl, new_tokens)
redis.call('setex', timestamp_key, ttl, now)

return { allowed_num, new_tokens }
```

> **Important:** The Lua script must return `List<Long>` — not `Long[]`. Spring Data Redis deserializes script results as `List`, so using `Long[]` causes a null result and triggers fail-open on every request.

```java
// ❌ Wrong — always returns null, all requests pass
DefaultRedisScript<Long[]> script = new DefaultRedisScript<>();
script.setResultType((Class<Long[]>) (Class<?>) Long[].class);

// ✅ Correct
DefaultRedisScript<List<Long>> script = new DefaultRedisScript<>();
script.setResultType((Class<List<Long>>) (Class<?>) List.class);
```

---

## ⚙️ How It Works

Every request goes through up to two rate-limit checks before reaching a controller:

**Step 1 — RateLimitInterceptor (global)**

Fires on every request to `/api/**`. HTTP method determines which bucket is used:

| HTTP Method | Bucket | Rate | Burst |
|------------|--------|------|-------|
| GET, HEAD, OPTIONS | read | 10 req/s | 20 |
| POST, PUT, DELETE, PATCH | write | 5 req/s | 10 |

If the bucket is exhausted → returns `429` immediately. The request never reaches the controller.

**Step 2 — @RateLimit AOP Aspect (per endpoint)**

Fires only on controller methods annotated with `@RateLimit`. Applies a dedicated, tighter bucket. Runs after the interceptor — **both checks must pass**.

**Step 3 — Controller → Service → Repository**

Requests that cleared all rate-limit checks reach the full stack. Service layer uses Redis `@Cacheable` with 10 min TTL before hitting MongoDB.

---

## 📁 Project Structure

```
spring-service-rate-limiter/
│
├── src/main/java/com/example/app/
│   │
│   ├── Application.java
│   │
│   ├── annotation/
│   │   └── RateLimit.java                  ← Custom method-level annotation
│   │
│   ├── aspect/
│   │   └── RateLimitAspect.java            ← AOP: intercepts @RateLimit methods
│   │
│   ├── config/
│   │   ├── RateLimiterProperties.java      ← Binds rate-limiter.* from yml
│   │   ├── RedisConfig.java                ← Lua script bean (List<Long> result type)
│   │   ├── WebMvcConfig.java               ← Registers interceptor on /api/**
│   │   ├── CacheConfig.java                ← Redis @Cacheable 10 min TTL
│   │   └── OpenApiConfig.java              ← Swagger UI
│   │
│   ├── ratelimit/
│   │   ├── RateLimitInterceptor.java       ← Global HandlerInterceptor
│   │   ├── TokenBucketService.java         ← Executes Lua script on Redis
│   │   ├── ClientKeyResolver.java          ← IP / API-Key / User-ID strategy
│   │   ├── RateLimitResult.java            ← Immutable result record
│   │   └── RateLimitStatusController.java  ← Admin endpoints
│   │
│   ├── controller/
│   │   └── ProductController.java          ← CRUD + @RateLimit examples
│   │
│   ├── dto/
│   │   ├── ProductRequest.java
│   │   └── ProductResponse.java
│   │
│   ├── model/
│   │   └── Product.java                    ← MongoDB document
│   │
│   ├── service/
│   │   └── ProductService.java             ← @Cacheable business logic
│   │
│   ├── repository/
│   │   └── ProductRepository.java
│   │
│   └── exception/
│       ├── ProductException.java
│       └── GlobalExceptionHandler.java
│
├── src/test/java/com/example/app/
│   ├── ratelimit/
│   │   └── TokenBucketServiceTest.java     ← Testcontainers Redis integration
│   ├── controller/
│   │   └── ProductControllerTest.java      ← MockMvc slice tests
│   └── service/
│       └── ProductServiceTest.java         ← Mockito unit tests
│
├── src/main/resources/
│   └── application.yml
│
├── pom.xml
├── Dockerfile
├── docker-compose.yml
└── README.md
```

---

## 🧩 Components

### RateLimitInterceptor

Registered in `WebMvcConfig` to intercept all `/api/**` requests. Actuator and Swagger paths are excluded.

```java
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {

        if (!properties.isEnabled()) return true;   // global kill-switch

        String method     = request.getMethod().toUpperCase();
        String clientKey  = clientKeyResolver.resolve(request);
        String bucketType = isReadMethod(method) ? "read" : "write";
        String fullKey    = clientKey + ":" + bucketType;

        RateLimitResult result = tokenBucketService.tryConsume(
            fullKey,
            config.getReplenishRate(),
            config.getBurstCapacity(),
            1
        );

        response.setHeader("X-RateLimit-Remaining",
                           String.valueOf(result.remainingTokens()));
        response.setHeader("X-RateLimit-Limit",
                           String.valueOf(result.burstCapacity()));

        if (result.allowed()) return true;

        response.setStatus(429);
        response.setHeader("X-RateLimit-Retry-After", "1");
        // writes JSON error body
        return false;
    }
}
```

### @RateLimit Annotation

Place on any controller method to layer a tighter, dedicated bucket. Both the global interceptor and the annotation bucket must pass.

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    int    replenishRate()   default 10;
    int    burstCapacity()   default 20;
    int    requestedTokens() default 1;
    String key()             default "";  // shared bucket key across endpoints
}
```

**Usage examples:**

```java
// Tighter bucket for an expensive endpoint
// Global write bucket (5/s) AND this bucket (1/s) must both pass
@RateLimit(replenishRate = 1, burstCapacity = 3, key = "products:bulk-create")
@PostMapping("/bulk")
public ResponseEntity<?> bulkCreate(...) { ... }

// Named shared bucket — /search and /search/advanced share the same quota
@RateLimit(replenishRate = 5, burstCapacity = 10, key = "products:search")
@GetMapping("/search")
public ResponseEntity<?> search(...) { ... }

// No annotation — only the global interceptor applies
@GetMapping
public ResponseEntity<?> getAll() { ... }
```

### ClientKeyResolver

Resolves the per-client Redis key from the incoming request. Change strategy in `application.yml` — no code change required.

| Strategy | Header Used | Fallback | Redis Key Example |
|---------|------------|---------|-----------------|
| `API_KEY` | `X-API-Key` | Remote IP | `api-key:client-abc` |
| `USER_ID` | `X-User-Id` | Remote IP | `user:user-42` |
| `IP` | `X-Forwarded-For` | `remoteAddr` | `ip:203.0.113.42` |

### TokenBucketService

Executes the Lua script atomically on Redis. Returns a `RateLimitResult` record.

| Method | Description |
|--------|-------------|
| `tryConsume()` | Consume tokens — returns `allowed` flag and `remainingTokens` |
| `peek()` | Read token count without consuming — used by admin status endpoint |
| `reset()` | Delete both Redis keys for a client — used for testing and admin ops |

**Fail-open:** if Redis throws an exception, the method returns `allowed = true` so a Redis outage does not cascade into a full service outage. Change to `false` in the catch block for fail-closed behaviour.

### Redis Key Structure

```
svc_rate_limiter.api-key:client-A:read.tokens
svc_rate_limiter.api-key:client-A:read.timestamp
svc_rate_limiter.api-key:client-A:write.tokens
svc_rate_limiter.api-key:client-A:write.timestamp
svc_rate_limiter.api-key:client-A:method:products:bulk-create.tokens
svc_rate_limiter.api-key:client-A:method:products:bulk-create.timestamp
```

> Read and write buckets are independent per client. Exhausting the write bucket does not affect the read bucket and vice versa.

---

## 📐 Configuration

```yaml
# application.yml

server:
  port: 8081

spring:
  data:
    mongodb:
      uri: ${MONGO_URI:mongodb://localhost:27017/productstoredb}
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

rate-limiter:
  enabled: true                      # global kill-switch
  key-strategy: API_KEY              # IP | API_KEY | USER_ID
  redis-key-prefix: "svc_rate_limiter"

  routes:
    read:
      replenish-rate: 10             # tokens added per second
      burst-capacity: 20             # max bucket size
      requested-tokens: 1            # tokens consumed per request
      methods: GET

    write:
      replenish-rate: 5
      burst-capacity: 10
      requested-tokens: 1
      methods: POST,PUT,DELETE,PATCH
```

**All configuration properties:**

| Property | Default | Description |
|---------|---------|-------------|
| `rate-limiter.enabled` | `true` | Global on/off switch |
| `rate-limiter.key-strategy` | `API_KEY` | Client identity source |
| `rate-limiter.redis-key-prefix` | `svc_rate_limiter` | Redis namespace prefix |
| `rate-limiter.routes.read.replenish-rate` | `10` | Read tokens per second |
| `rate-limiter.routes.read.burst-capacity` | `20` | Read max burst |
| `rate-limiter.routes.write.replenish-rate` | `5` | Write tokens per second |
| `rate-limiter.routes.write.burst-capacity` | `10` | Write max burst |
| `REDIS_HOST` env | `localhost` | Redis hostname |
| `REDIS_PORT` env | `6379` | Redis port |
| `MONGO_URI` env | `mongodb://localhost:27017/...` | MongoDB URI |

---

## 🌐 API Endpoints

### Product Endpoints

| Method | Path | Global Limit | @RateLimit | Description |
|--------|------|-------------|-----------|-------------|
| `GET` | `/api/v1/products` | 10/s burst 20 | — | List all products |
| `GET` | `/api/v1/products/{id}` | 10/s burst 20 | — | Get product by ID |
| `GET` | `/api/v1/products/category/{cat}` | 10/s burst 20 | — | Filter by category |
| `GET` | `/api/v1/products/in-stock` | 10/s burst 20 | — | In-stock products |
| `GET` | `/api/v1/products/search?name=` | 10/s burst 20 | **5/s burst 10** | Search by name |
| `POST` | `/api/v1/products` | 5/s burst 10 | — | Create product |
| `PUT` | `/api/v1/products/{id}` | 5/s burst 10 | — | Update product |
| `DELETE` | `/api/v1/products/{id}` | 5/s burst 10 | — | Delete product |
| `POST` | `/api/v1/products/bulk` | 5/s burst 10 | **1/s burst 3** | Bulk create |

### Admin Endpoints (no rate limit)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/service/rate-limit/status?key=` | View token count for a client |
| `DELETE` | `/service/rate-limit/reset?key=` | Reset buckets for a client |
| `GET` | `/service/rate-limit/config` | View active configuration |
| `GET` | `/actuator/health` | Service health check |
| `GET` | `/swagger-ui.html` | Swagger UI |

---

## 📋 Response Headers

Rate limit headers are set on **every** response so clients can track their quota proactively.

**Successful request:**

```
HTTP/1.1 200 OK
X-RateLimit-Remaining: 19
X-RateLimit-Limit: 20
```

**Rate limited (429):**

```
HTTP/1.1 429 Too Many Requests
X-RateLimit-Remaining: 0
X-RateLimit-Limit: 20
X-RateLimit-Retry-After: 1
X-RateLimit-Message: Too many requests. Token bucket exhausted. Retry after 1 second.
```

```json
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Service-level rate limit exceeded. Retry after 1 second(s).",
  "retryAfter": 1,
  "remainingTokens": 0
}
```

---

## 🚀 Quick Start

### Docker (recommended)

```bash
# Clone the repo
git clone https://github.com/PrasanthKrishnan51/spring-service-rate-limiter.git
cd spring-service-rate-limiter

# Start MongoDB + Redis + product-service
docker-compose up -d

# Check health
curl http://localhost:8081/actuator/health

# Open Swagger UI
open http://localhost:8081/swagger-ui.html
```

### Local

```bash
# Start infrastructure
docker run -d -p 27017:27017 mongo:7.0
docker run -d -p 6379:6379 redis:7.2-alpine

# Run the service
mvn spring-boot:run
# Service starts on http://localhost:8081
```

### Build

```bash
mvn clean package -DskipTests   # build fat-jar
mvn test                        # run all tests (requires Docker for Testcontainers)
```

---

## ✅ Validating Rate Limits

### 1. Check interceptor is working

```bash
curl -v -H "X-API-Key: client-A" http://localhost:8081/api/v1/products 2>&1 | grep -i x-ratelimit
```

Expected output:

```
X-RateLimit-Remaining: 19
X-RateLimit-Limit: 20
```

> If `Remaining` shows `20` on every request — the Lua script result type is `Long[]` instead of `List<Long>`. See the fix in the [Token Bucket Algorithm](#-token-bucket-algorithm) section above.

### 2. Exhaust the read bucket (burst = 20)

```bash
for i in {1..25}; do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-API-Key: client-A" \
    http://localhost:8081/api/v1/products)
  echo "GET $i → $STATUS"
done
```

Expected:

```
GET 1  → 200
...
GET 20 → 200
GET 21 → 429
GET 22 → 429
GET 23 → 429
GET 24 → 429
GET 25 → 429
```

### 3. Exhaust the write bucket (burst = 10)

```bash
for i in $(seq 1 12); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    http://localhost:8081/api/v1/products \
    -H "Content-Type: application/json" \
    -d '{"name":"Test","sku":"SKU-'$i'","price":9.99,"category":"tools","stock":1}')
  echo "POST $i → $STATUS"
done
```

Expected:

```
POST 1  → 201
...
POST 10 → 201
POST 11 → 429
POST 12 → 429
```

### 4. Test @RateLimit on bulk endpoint (burst = 3)

```bash
for i in $(seq 1 5); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    http://localhost:8081/api/v1/products/bulk \
    -H "Content-Type: application/json" \
    -d '[{"name":"W","sku":"BLK-'$i'","price":1.99,"category":"tools","stock":1}]')
  echo "BULK $i → $STATUS"
done
```

Expected:

```
BULK 1 → 201
BULK 2 → 201
BULK 3 → 201
BULK 4 → 429
BULK 5 → 429
```

### 5. Verify per-client isolation

```bash
# Exhaust client-A
for i in {1..21}; do
  curl -s -o /dev/null -H "X-API-Key: client-A" http://localhost:8081/api/v1/products
done

# client-B should still have a full bucket
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "X-API-Key: client-B" http://localhost:8081/api/v1/products
# Expected: 200
```

### 6. Verify token refill

```bash
# Exhaust bucket
for i in {1..21}; do
  curl -s -o /dev/null -H "X-API-Key: client-A" http://localhost:8081/api/v1/products
done

# Confirm 429
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "X-API-Key: client-A" http://localhost:8081/api/v1/products
# → 429

# Wait 2 seconds — replenishRate=10 so +20 tokens refilled
sleep 2

# Should be 200 again
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "X-API-Key: client-A" http://localhost:8081/api/v1/products
# → 200
```

### 7. Check Redis keys directly

```bash
docker exec -it redis redis-cli KEYS "svc_rate_limiter.*"
# svc_rate_limiter.api-key:client-A:read.tokens
# svc_rate_limiter.api-key:client-A:read.timestamp

docker exec -it redis redis-cli GET "svc_rate_limiter.api-key:client-A:read.tokens"
# "14"
```

---

## 🔧 Admin Endpoints

```bash
# Inspect live token count for a client (non-destructive)
curl "http://localhost:8081/service/rate-limit/status?key=api-key:client-A"

# Reset read and write buckets for a client
curl -X DELETE "http://localhost:8081/service/rate-limit/reset?key=api-key:client-A"

# View active rate-limiter config
curl "http://localhost:8081/service/rate-limit/config"
```

---

## 🧪 Running Tests

```bash
# All tests (unit + integration — requires Docker for Testcontainers)
mvn test

# Only unit tests (no Docker needed)
mvn test -Dgroups="unit"

# Only integration tests
mvn test -Dgroups="integration"
```

**Test coverage:**

| Test Class | Type | What it covers |
|-----------|------|----------------|
| `TokenBucketServiceTest` | Integration (Testcontainers) | First request allowed, burst respected, client isolation, reset, peek |
| `ProductControllerTest` | MockMvc slice | All CRUD endpoints, validation errors, 404 handling |
| `ProductServiceTest` | Unit (Mockito) | findById, create, update, delete, duplicate SKU |

---

## 🛠 Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 3.3, Spring Web MVC |
| Rate Limiting | Token Bucket via Redis Lua script |
| Database | MongoDB 7.0 |
| Cache | Redis 7.2 — Spring `@Cacheable` (10 min TTL) |
| AOP | Spring AOP — `@RateLimit` annotation |
| Docs | SpringDoc OpenAPI 3 / Swagger UI |
| Tests | JUnit 5, Mockito, Testcontainers, MockMvc |
| Build | Maven, Java 21 |
| Runtime | Docker, Docker Compose |

---

<p align="center">Made with ☕ by <a href="https://github.com/PrasanthKrishnan51">PrasanthKrishnan51</a></p>
