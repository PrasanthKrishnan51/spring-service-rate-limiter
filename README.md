<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0"/>
<title>Spring Boot — Service-Level Token Bucket Rate Limiting</title>
<style>
  :root {
    --green:    #6DB33F;
    --green-dim: rgba(109,179,63,0.15);
    --green-border: rgba(109,179,63,0.35);
    --redis:    #DC382D;
    --mongo:    #47A248;
    --java:     #ED8B00;
    --blue:     #2496ED;
    --orange:   #ED8B00;
    --bg:       #0d1117;
    --surface:  #161b22;
    --surface2: #1c2128;
    --surface3: #21262d;
    --border:   #30363d;
    --text:     #e6edf3;
    --muted:    #8b949e;
    --code-bg:  #161b22;
  }
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif;
    background: var(--bg);
    color: var(--text);
    line-height: 1.75;
    font-size: 15px;
  }

/* ── Hero ─────────────────────────────── */
.hero {
background: linear-gradient(160deg, #0d1117 0%, #112211 55%, #0d1117 100%);
border-bottom: 1px solid var(--border);
padding: 64px 40px 52px;
text-align: center;
position: relative;
overflow: hidden;
}
.hero::before {
content: '';
position: absolute; inset: 0;
background: radial-gradient(ellipse 70% 55% at 50% -5%, rgba(109,179,63,0.18) 0%, transparent 65%);
pointer-events: none;
}
.hero-eyebrow {
display: inline-block;
background: var(--green-dim);
border: 1px solid var(--green-border);
color: #8ecf5a;
font-size: 12px;
font-weight: 700;
letter-spacing: 1.2px;
text-transform: uppercase;
padding: 4px 14px;
border-radius: 20px;
margin-bottom: 20px;
}
.hero h1 {
font-size: 2.6rem;
font-weight: 800;
letter-spacing: -1px;
margin-bottom: 16px;
background: linear-gradient(140deg, #ffffff 40%, #6DB33F 100%);
-webkit-background-clip: text;
-webkit-text-fill-color: transparent;
background-clip: text;
line-height: 1.2;
}
.hero p {
color: var(--muted);
font-size: 1.05rem;
max-width: 600px;
margin: 0 auto 28px;
}
.badges { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
.badge {
display: inline-flex; align-items: center; gap: 5px;
padding: 5px 13px; border-radius: 20px;
font-size: 12px; font-weight: 600; border: 1px solid;
letter-spacing: 0.2px;
}
.badge.green  { background: rgba(109,179,63,0.12); border-color: rgba(109,179,63,0.35); color: #8ecf5a; }
.badge.red    { background: rgba(220,56,45,0.12);  border-color: rgba(220,56,45,0.35);  color: #f07060; }
.badge.mongo  { background: rgba(71,162,72,0.12);  border-color: rgba(71,162,72,0.35);  color: #67c268; }
.badge.java   { background: rgba(237,139,0,0.12);  border-color: rgba(237,139,0,0.35);  color: #f0a840; }
.badge.blue   { background: rgba(36,150,237,0.12); border-color: rgba(36,150,237,0.35); color: #56aef0; }

/* ── Layout ───────────────────────────── */
.layout { display: flex; max-width: 1180px; margin: 0 auto; }
.sidebar {
width: 220px; min-width: 220px;
padding: 32px 16px 32px 20px;
position: sticky; top: 0;
height: 100vh; overflow-y: auto;
border-right: 1px solid var(--border);
}
.sidebar-title {
font-size: 10.5px; font-weight: 700;
text-transform: uppercase; letter-spacing: 1.2px;
color: var(--muted); padding: 0 8px 10px;
}
.sidebar a {
display: block; padding: 5px 8px;
border-radius: 6px; font-size: 13px;
color: var(--muted); text-decoration: none;
transition: all 0.15s; margin-bottom: 1px;
border-left: 2px solid transparent;
}
.sidebar a:hover { color: var(--text); background: var(--surface3); border-left-color: var(--green); }
.sidebar .sep {
font-size: 10px; font-weight: 700;
color: #3d7a1a; text-transform: uppercase;
letter-spacing: 0.8px; padding: 10px 8px 3px;
}
.content { flex: 1; padding: 44px 52px; max-width: 940px; }

/* ── Sections ─────────────────────────── */
section { margin-bottom: 60px; }
h2 {
font-size: 1.4rem; font-weight: 700; color: #fff;
margin-bottom: 20px; padding-bottom: 12px;
border-bottom: 1px solid var(--border);
display: flex; align-items: center; gap: 10px;
}
.h2-icon {
width: 30px; height: 30px;
background: var(--green-dim);
border: 1px solid var(--green-border);
border-radius: 8px;
display: flex; align-items: center; justify-content: center;
font-size: 15px; flex-shrink: 0;
}
h3 { font-size: 1rem; font-weight: 600; color: #cdd9e5; margin: 26px 0 10px; }
p  { color: #cdd9e5; margin-bottom: 14px; }

/* ── Architecture ─────────────────────── */
.arch {
background: var(--surface);
border: 1px solid var(--border);
border-radius: 14px;
padding: 28px 28px 20px;
margin: 20px 0;
}
.arch-flow {
display: flex; flex-direction: column; gap: 0;
}
.arch-node {
border: 1px solid; border-radius: 10px;
padding: 16px 20px; position: relative;
}
.arch-node.client  { border-color: #30363d; background: var(--surface2); }
.arch-node.service { border-color: rgba(109,179,63,0.45); background: rgba(109,179,63,0.05); }
.arch-node.redis   { border-color: rgba(220,56,45,0.35);  background: rgba(220,56,45,0.04); }
.arch-node.mongo   { border-color: rgba(71,162,72,0.35);  background: rgba(71,162,72,0.04); }
.arch-label {
font-size: 10px; font-weight: 700; text-transform: uppercase;
letter-spacing: 0.8px; border-radius: 10px;
padding: 2px 10px; display: inline-block; margin-bottom: 6px;
}
.arch-label.svc   { background: rgba(109,179,63,0.2); color: #8ecf5a; }
.arch-label.redis { background: rgba(220,56,45,0.2);  color: #f07060; }
.arch-label.mongo { background: rgba(71,162,72,0.2);  color: #67c268; }
.arch-label.cli   { background: rgba(130,130,130,0.2); color: #aaa; }
.arch-node h4 { font-size: 14px; font-weight: 600; color: #e6edf3; margin-bottom: 4px; }
.arch-node p  { font-size: 13px; color: var(--muted); margin: 0; }
.arch-arrow { text-align: center; padding: 6px 0; color: #444d56; font-size: 18px; }
.chips { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 10px; }
.chip {
font-size: 11.5px; font-weight: 600; padding: 3px 11px;
border-radius: 20px; border: 1px solid;
}
.chip.read   { background: rgba(109,179,63,0.1); border-color: rgba(109,179,63,0.3); color: #8ecf5a; }
.chip.write  { background: rgba(237,139,0,0.1);  border-color: rgba(237,139,0,0.3);  color: #f0a840; }
.chip.custom { background: rgba(220,56,45,0.1);  border-color: rgba(220,56,45,0.3);  color: #f07060; }
.chip.grey   { background: rgba(130,130,130,0.1);border-color: rgba(130,130,130,0.3);color: #aaa; }

/* ── How it works flow ────────────────── */
.flow {
display: flex; flex-direction: column; gap: 0;
margin: 20px 0;
}
.flow-step {
display: flex; gap: 14px; align-items: flex-start;
background: var(--surface);
border: 1px solid var(--border);
border-radius: 10px; padding: 16px 18px;
position: relative;
}
.flow-step + .flow-step { margin-top: -1px; border-radius: 0; }
.flow-step:first-child { border-radius: 10px 10px 0 0; }
.flow-step:last-child  { border-radius: 0 0 10px 10px; }
.flow-num {
width: 26px; height: 26px; flex-shrink: 0;
background: var(--green-dim); border: 1px solid var(--green-border);
border-radius: 50%; display: flex; align-items: center; justify-content: center;
font-size: 12px; font-weight: 700; color: #8ecf5a;
}
.flow-step h4 { font-size: 13.5px; font-weight: 600; color: #e6edf3; margin-bottom: 3px; }
.flow-step p  { font-size: 13px; color: var(--muted); margin: 0; }

/* ── Token bucket visualizer ──────────── */
.bucket-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 14px; margin: 20px 0; }
.bucket-card {
background: var(--surface);
border: 1px solid var(--border);
border-radius: 10px; padding: 18px;
}
.bucket-card h4 { font-size: 12.5px; font-weight: 600; margin-bottom: 12px; display: flex; align-items: center; gap: 6px; }
.bar-track { height: 10px; background: var(--surface3); border-radius: 6px; overflow: hidden; margin: 8px 0 6px; }
.bar-fill  { height: 100%; border-radius: 6px; }
.bar-full  { width: 100%; background: linear-gradient(90deg, #3d7a1a, #6DB33F); }
.bar-half  { width: 50%; background: linear-gradient(90deg, #9a5f00, #ED8B00); }
.bar-empty { width: 3%;  background: var(--redis); }
.bar-meta  { font-size: 11.5px; color: var(--muted); display: flex; justify-content: space-between; }

/* ── Code ─────────────────────────────── */
pre {
background: var(--code-bg);
border: 1px solid var(--border);
border-radius: 10px;
padding: 18px 20px;
overflow-x: auto;
font-size: 12.5px;
line-height: 1.65;
margin: 14px 0;
font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
}
code { font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace; font-size: 12.5px; }
:not(pre) > code {
background: var(--surface3); border: 1px solid var(--border);
border-radius: 4px; padding: 1px 6px; color: #e6edf3;
}
.pre-label {
font-size: 10px; font-weight: 700; text-transform: uppercase;
letter-spacing: 0.8px; color: var(--muted);
background: var(--surface3); border: 1px solid var(--border);
border-bottom: none; border-radius: 8px 8px 0 0;
padding: 6px 14px; display: inline-block; margin-bottom: -1px;
}
.pre-label + pre { border-radius: 0 10px 10px 10px; margin-top: 0; }

.kw   { color: #ff7b72; }
.fn   { color: #d2a8ff; }
.str  { color: #a5d6ff; }
.nb   { color: #ffa657; }
.cm   { color: #8b949e; }
.ann  { color: #79c0ff; }
.yk   { color: #79c0ff; }
.yv   { color: #a5d6ff; }
.yc   { color: #8b949e; }
.sk   { color: #8ecf5a; }
.sa   { color: #e6edf3; }
.sc   { color: #8b949e; }
.hm   { color: #79c0ff; font-weight:700; }
.h2xx { color: #8ecf5a; }
.h4xx { color: #f07060; }

/* ── Tables ───────────────────────────── */
.tbl-wrap { overflow-x: auto; margin: 14px 0; }
table { width: 100%; border-collapse: collapse; font-size: 13.5px; }
thead th {
background: var(--surface3); color: var(--muted);
font-size: 10.5px; font-weight: 700; text-transform: uppercase;
letter-spacing: 0.8px; padding: 10px 14px;
text-align: left; border-bottom: 1px solid var(--border); white-space: nowrap;
}
tbody td { padding: 9px 14px; border-bottom: 1px solid var(--border); color: #cdd9e5; vertical-align: middle; }
tbody tr:hover { background: var(--surface3); }
.mtag {
display: inline-block; padding: 2px 8px;
border-radius: 4px; font-size: 11px; font-weight: 700; font-family: monospace;
}
.m-get    { background: rgba(109,179,63,0.15); color: #8ecf5a; }
.m-post   { background: rgba(36,150,237,0.15); color: #56aef0; }
.m-put    { background: rgba(237,139,0,0.15);  color: #f0a840; }
.m-delete { background: rgba(220,56,45,0.15);  color: #f07060; }

/* ── Info boxes ───────────────────────── */
.box {
display: flex; gap: 12px;
border-radius: 10px; padding: 15px 18px;
margin: 16px 0; border: 1px solid;
}
.box.tip  { background: rgba(109,179,63,0.07); border-color: rgba(109,179,63,0.28); }
.box.warn { background: rgba(237,139,0,0.07);  border-color: rgba(237,139,0,0.28); }
.box.info { background: rgba(36,150,237,0.07); border-color: rgba(36,150,237,0.28); }
.box-icon { font-size: 17px; flex-shrink: 0; line-height: 1.5; }
.box p { margin: 0; font-size: 13.5px; }
.box.tip  p { color: #a8d48a; }
.box.warn p { color: #f0c060; }
.box.info p { color: #80c4f8; }

/* ── Redis key visual ─────────────────── */
.rkeys { display: flex; flex-direction: column; gap: 8px; margin: 14px 0; }
.rkey {
background: var(--surface2); border: 1px solid var(--border);
border-radius: 8px; padding: 10px 14px;
font-family: 'SF Mono', 'Fira Code', monospace; font-size: 12px;
display: flex; align-items: center; gap: 0; flex-wrap: wrap;
}
.rp { color: #f07060; }
.rc { color: #79c0ff; }
.rt { color: #ffa657; }
.rs { color: #a5d6ff; }
.rtag {
margin-left: auto; font-size: 10px; font-weight: 700;
text-transform: uppercase; padding: 2px 9px; border-radius: 10px;
background: rgba(109,179,63,0.18); color: #8ecf5a;
}

/* ── Cards grid ───────────────────────── */
.cards { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin: 18px 0; }
.card {
background: var(--surface); border: 1px solid var(--border);
border-radius: 10px; padding: 16px 18px; transition: border-color 0.18s;
}
.card:hover { border-color: var(--green-border); }
.card h4 { font-size: 13px; font-weight: 600; color: #e6edf3; margin-bottom: 5px; }
.card p  { font-size: 12.5px; color: var(--muted); margin: 0; line-height: 1.5; }

/* ── File tree ────────────────────────── */
.tree {
background: var(--code-bg); border: 1px solid var(--border);
border-radius: 10px; padding: 20px 24px;
font-family: 'SF Mono', 'Fira Code', monospace; font-size: 12.5px; line-height: 1.9;
overflow-x: auto;
}
.td { color: #79c0ff; }
.tf { color: #e6edf3; }
.tc { color: #8b949e; font-style: italic; }
.th { color: #8ecf5a; font-weight: 600; }

/* ── Footer ───────────────────────────── */
footer {
border-top: 1px solid var(--border);
padding: 28px; text-align: center;
color: var(--muted); font-size: 13px;
}
footer a { color: var(--green); text-decoration: none; }
footer a:hover { text-decoration: underline; }

@media (max-width: 860px) {
.sidebar { display: none; }
.content { padding: 24px 18px; }
.cards { grid-template-columns: 1fr; }
.bucket-grid { grid-template-columns: 1fr; }
}
</style>
</head>
<body>

<!-- Hero -->
<header class="hero">
  <div class="hero-eyebrow">Spring Boot 3.3 · Java 21 · Redis</div>
  <h1>Service-Level Token Bucket<br/>Rate Limiting</h1>
  <p>A clean, production-ready rate limiter built directly inside a Spring Boot microservice — no API gateway required. Uses Redis + atomic Lua scripts for distributed, per-client enforcement.</p>
  <div class="badges">
    <span class="badge green">⚡ Spring Boot 3.3</span>
    <span class="badge red">🪣 Redis Token Bucket</span>
    <span class="badge green">🔗 HandlerInterceptor</span>
    <span class="badge green">🏷 @RateLimit AOP</span>
    <span class="badge mongo">🍃 MongoDB</span>
    <span class="badge java">☕ Java 21</span>
    <span class="badge blue">🐳 Docker</span>
  </div>
</header>

<div class="layout">

<!-- Sidebar -->
<nav class="sidebar">
  <div class="sidebar-title">Contents</div>
  <a href="#arch">Architecture</a>
  <a href="#token-bucket">Token Bucket</a>
  <a href="#how-it-works">How It Works</a>
  <div class="sep">Components</div>
  <a href="#interceptor">HandlerInterceptor</a>
  <a href="#annotation">@RateLimit Annotation</a>
  <a href="#key-resolver">Key Resolver</a>
  <a href="#token-service">TokenBucketService</a>
  <a href="#redis-keys">Redis Keys</a>
  <div class="sep">Reference</div>
  <a href="#config">Configuration</a>
  <a href="#endpoints">API Endpoints</a>
  <a href="#headers">Response Headers</a>
  <a href="#structure">Project Structure</a>
  <a href="#quickstart">Quick Start</a>
  <a href="#testing">Testing</a>
  <a href="#admin">Admin Endpoints</a>
</nav>

<main class="content">

<!-- Architecture -->
<section id="arch">
  <h2><span class="h2-icon">🏛</span> Architecture</h2>
  <p>Rate limiting lives entirely inside the product service. Every HTTP request passes through the <strong>Token Bucket interceptor</strong> before reaching a controller. Annotated endpoints get a second, tighter bucket on top via AOP.</p>

  <div class="arch">
    <div class="arch-flow">
      <div class="arch-node client">
        <span class="arch-label cli">Client</span>
        <h4>HTTP Request</h4>
        <p>Any consumer — browser, mobile app, internal service, curl</p>
      </div>
      <div class="arch-arrow">↓</div>
      <div class="arch-node service">
        <span class="arch-label svc">Product Service :8081</span>
        <h4>RateLimitInterceptor &nbsp;·&nbsp; @RateLimit AOP Aspect</h4>
        <p>Checks Redis atomically before the request reaches any controller method</p>
        <div class="chips">
          <span class="chip read">GET → 10 req/s  burst 20</span>
          <span class="chip write">POST/PUT/DELETE → 5 req/s  burst 10</span>
          <span class="chip custom">/bulk → 1 req/s  burst 3</span>
          <span class="chip custom">/search → 5 req/s  burst 10</span>
        </div>
      </div>
      <div class="arch-arrow">↓ &nbsp;atomic Lua call</div>
      <div class="arch-node redis">
        <span class="arch-label redis">Redis :6379</span>
        <h4>Token Bucket State</h4>
        <p>Stores <code>tokens</code> + <code>timestamp</code> per client. Lua script guarantees no race conditions across multiple pods.</p>
      </div>
      <div class="arch-arrow">↓ &nbsp;on allowed requests</div>
      <div class="arch-node mongo">
        <span class="arch-label mongo">MongoDB :27017</span>
        <h4>Product Persistence + Redis Cache (10 min)</h4>
        <p>Allowed requests hit the controller → service → repository stack normally</p>
      </div>
    </div>
  </div>
</section>

<!-- Token Bucket -->
<section id="token-bucket">
  <h2><span class="h2-icon">🪣</span> Token Bucket Algorithm</h2>
  <p>The same atomic Lua script used by Spring Cloud Gateway's <code>RedisRateLimiter</code> — applied here directly at the service level.</p>

  <div class="bucket-grid">
    <div class="bucket-card">
      <h4>🟢 Full &mdash; burst allowed</h4>
      <div class="bar-track"><div class="bar-fill bar-full"></div></div>
      <div class="bar-meta"><span>20 / 20 tokens</span><span>→ 200 OK</span></div>
    </div>
    <div class="bucket-card">
      <h4>🟡 Draining &mdash; sustained load</h4>
      <div class="bar-track"><div class="bar-fill bar-half"></div></div>
      <div class="bar-meta"><span>10 / 20 tokens</span><span>+10/sec refill</span></div>
    </div>
    <div class="bucket-card">
      <h4>🔴 Exhausted &mdash; 429 fired</h4>
      <div class="bar-track"><div class="bar-fill bar-empty"></div></div>
      <div class="bar-meta"><span>0 / 20 tokens</span><span>→ 429</span></div>
    </div>
  </div>

<span class="pre-label">Lua — RedisConfig.java</span>
<pre><code><span class="cm">-- Runs atomically inside Redis, no Java locks needed</span>
<span class="kw">local</span> delta         = now - last_refreshed
<span class="kw">local</span> filled_tokens = min(capacity, last_tokens + delta * rate)

<span class="kw">if</span> filled_tokens >= requested <span class="kw">then</span>
    allowed   = <span class="nb">1</span>
    new_tokens = filled_tokens - requested
<span class="kw">else</span>
    allowed   = <span class="nb">0</span>   <span class="cm">-- → 429</span>
<span class="kw">end</span>

redis.call(<span class="str">'setex'</span>, tokens_key,    ttl, new_tokens)
redis.call(<span class="str">'setex'</span>, timestamp_key, ttl, now)
<span class="kw">return</span> { allowed, new_tokens }</code></pre>
</section>

<!-- How It Works -->
<section id="how-it-works">
  <h2><span class="h2-icon">⚙️</span> How It Works</h2>
  <p>Every request travels through up to two rate-limit checks before touching a controller method:</p>
  <div class="flow">
    <div class="flow-step">
      <div class="flow-num">1</div>
      <div>
        <h4>RateLimitInterceptor.preHandle()</h4>
        <p>Fires on every request to <code>/api/**</code>. Selects the <strong>read bucket</strong> (GET) or <strong>write bucket</strong> (POST/PUT/DELETE) based on HTTP method. If the bucket is exhausted → returns 429 immediately, request never reaches the controller.</p>
      </div>
    </div>
    <div class="flow-step">
      <div class="flow-num">2</div>
      <div>
        <h4>@RateLimit AOP Aspect (annotated methods only)</h4>
        <p>Fires only on controller methods annotated with <code>@RateLimit</code>. Applies a dedicated, tighter bucket for that specific endpoint or shared-key group. Runs <em>after</em> the interceptor — both checks must pass.</p>
      </div>
    </div>
    <div class="flow-step">
      <div class="flow-num">3</div>
      <div>
        <h4>Controller → Service → Repository</h4>
        <p>Only requests that cleared all rate-limit checks reach here. Service uses Redis <code>@Cacheable</code> (10 min TTL) before hitting MongoDB.</p>
      </div>
    </div>
  </div>
</section>

<!-- Interceptor -->
<section id="interceptor">
  <h2><span class="h2-icon">🛡</span> HandlerInterceptor — Global Buckets</h2>
  <p>Registered in <code>WebMvcConfig</code> to cover all <code>/api/**</code> paths. Actuator and Swagger are excluded.</p>

<span class="pre-label">RateLimitInterceptor.java</span>
<pre><code><span class="ann">@Component</span>
<span class="kw">public class</span> <span class="fn">RateLimitInterceptor</span> <span class="kw">implements</span> HandlerInterceptor {

  <span class="ann">@Override</span>
  <span class="kw">public boolean</span> <span class="fn">preHandle</span>(request, response, handler) <span class="kw">throws</span> IOException {

    <span class="kw">if</span> (!properties.<span class="fn">isEnabled</span>()) <span class="kw">return true</span>;  <span class="cm">// global kill-switch</span>

    String method    = request.<span class="fn">getMethod</span>().<span class="fn">toUpperCase</span>();
    String clientKey = clientKeyResolver.<span class="fn">resolve</span>(request);  <span class="cm">// IP / API-Key / User-ID</span>
    String bucketType = <span class="fn">isReadMethod</span>(method) ? <span class="str">"read"</span> : <span class="str">"write"</span>;
    String fullKey    = clientKey + <span class="str">":"</span> + bucketType;

    RateLimitResult result = tokenBucketService.<span class="fn">tryConsume</span>(
        fullKey,
        config.<span class="fn">getReplenishRate</span>(),   <span class="cm">// 10 (read) or 5 (write)</span>
        config.<span class="fn">getBurstCapacity</span>(),    <span class="cm">// 20 (read) or 10 (write)</span>
        <span class="nb">1</span>
    );

    response.<span class="fn">setHeader</span>(<span class="str">"X-RateLimit-Remaining"</span>, result.<span class="fn">remainingTokens</span>());
    response.<span class="fn">setHeader</span>(<span class="str">"X-RateLimit-Limit"</span>,     result.<span class="fn">burstCapacity</span>());

    <span class="kw">if</span> (result.<span class="fn">allowed</span>()) <span class="kw">return true</span>;

    response.<span class="fn">setStatus</span>(<span class="nb">429</span>);
    response.<span class="fn">setHeader</span>(<span class="str">"X-RateLimit-Retry-After"</span>, <span class="str">"1"</span>);
    <span class="cm">// writes JSON body...</span>
    <span class="kw">return false</span>;
  }
}</code></pre>

<span class="pre-label">WebMvcConfig.java</span>
<pre><code><span class="ann">@Override</span>
<span class="kw">public void</span> <span class="fn">addInterceptors</span>(InterceptorRegistry registry) {
    registry.<span class="fn">addInterceptor</span>(rateLimitInterceptor)
        .<span class="fn">addPathPatterns</span>(<span class="str">"/api/**"</span>)
        .<span class="fn">excludePathPatterns</span>(
            <span class="str">"/actuator/**"</span>,
            <span class="str">"/swagger-ui/**"</span>,
            <span class="str">"/v3/api-docs/**"</span>
        );
}</code></pre>
</section>

<!-- @RateLimit Annotation -->
<section id="annotation">
  <h2><span class="h2-icon">🏷</span> @RateLimit Annotation — Per-Endpoint Buckets</h2>
  <p>Place on any controller method to layer a tighter, dedicated bucket on top of the global interceptor. Both checks must pass.</p>

<span class="pre-label">RateLimit.java</span>
<pre><code><span class="ann">@Target</span>(ElementType.METHOD)
<span class="ann">@Retention</span>(RetentionPolicy.RUNTIME)
<span class="kw">public @interface</span> <span class="fn">RateLimit</span> {
    <span class="kw">int</span>    <span class="fn">replenishRate</span>()    <span class="kw">default</span> <span class="nb">10</span>;
    <span class="kw">int</span>    <span class="fn">burstCapacity</span>()    <span class="kw">default</span> <span class="nb">20</span>;
    <span class="kw">int</span>    <span class="fn">requestedTokens</span>() <span class="kw">default</span> <span class="nb">1</span>;
    String <span class="fn">key</span>()             <span class="kw">default</span> <span class="str">""</span>;  <span class="cm">// shared bucket key (optional)</span>
}</code></pre>

<span class="pre-label">ProductController.java — usage examples</span>
<pre><code><span class="cm">// 1. Tighter dedicated bucket for an expensive endpoint</span>
<span class="ann">@RateLimit</span>(replenishRate = <span class="nb">1</span>, burstCapacity = <span class="nb">3</span>, key = <span class="str">"products:bulk-create"</span>)
<span class="ann">@PostMapping</span>(<span class="str">"/bulk"</span>)
<span class="kw">public</span> ResponseEntity&lt;?&gt; <span class="fn">bulkCreate</span>(...) { ... }

<span class="cm">// 2. Named shared bucket — /search and other search endpoints share one quota</span>
<span class="ann">@RateLimit</span>(replenishRate = <span class="nb">5</span>, burstCapacity = <span class="nb">10</span>, key = <span class="str">"products:search"</span>)
<span class="ann">@GetMapping</span>(<span class="str">"/search"</span>)
<span class="kw">public</span> ResponseEntity&lt;?&gt; <span class="fn">search</span>(...) { ... }

<span class="cm">// 3. No annotation = only global interceptor applies</span>
<span class="ann">@GetMapping</span>
<span class="kw">public</span> ResponseEntity&lt;?&gt; <span class="fn">getAll</span>() { ... }</code></pre>

  <div class="box warn">
    <span class="box-icon">⚠️</span>
    <p>When <code>@RateLimit</code> is present, <strong>both</strong> the global interceptor and the annotation bucket must pass. The annotation is additive — not a replacement for the global limit.</p>
  </div>
</section>

<!-- Key Resolver -->
<section id="key-resolver">
  <h2><span class="h2-icon">🔑</span> ClientKeyResolver — Per-Client Isolation</h2>
  <p>Determines the Redis bucket key for each incoming request. Configured via <code>application.yml</code> — no code change needed to switch strategy.</p>

  <div class="tbl-wrap">
    <table>
      <thead><tr><th>Strategy</th><th>Key Source</th><th>Fallback</th><th>Example Key</th></tr></thead>
      <tbody>
        <tr><td><code>API_KEY</code></td><td><code>X-API-Key</code> header</td><td>Remote IP</td><td><code>api-key:my-client-123</code></td></tr>
        <tr><td><code>USER_ID</code></td><td><code>X-User-Id</code> header</td><td>Remote IP</td><td><code>user:user-42</code></td></tr>
        <tr><td><code>IP</code></td><td><code>X-Forwarded-For</code></td><td><code>remoteAddr</code></td><td><code>ip:203.0.113.42</code></td></tr>
      </tbody>
    </table>
  </div>

<span class="pre-label">ClientKeyResolver.java</span>
<pre><code><span class="kw">return switch</span> (strategy) {
    <span class="kw">case</span> API_KEY -> {
        String apiKey = request.<span class="fn">getHeader</span>(<span class="str">"X-API-Key"</span>);
        <span class="kw">yield</span> StringUtils.<span class="fn">hasText</span>(apiKey)
            ? <span class="str">"api-key:"</span> + apiKey
            : <span class="str">"ip:"</span> + <span class="fn">extractIp</span>(request);   <span class="cm">// graceful fallback</span>
    }
    <span class="kw">case</span> USER_ID -> { <span class="cm">/* X-User-Id header → fallback IP */</span> }
    <span class="kw">case</span> IP      -> <span class="str">"ip:"</span> + <span class="fn">extractIp</span>(request);
};</code></pre>
</section>

<!-- TokenBucketService -->
<section id="token-service">
  <h2><span class="h2-icon">⚡</span> TokenBucketService</h2>
  <p>The core Redis executor. Runs the Lua script atomically — no race conditions even when multiple pods call it simultaneously. Implements <strong>fail-open</strong> behaviour on Redis errors.</p>

  <div class="cards">
    <div class="card">
      <h4>tryConsume()</h4>
      <p>Consume tokens from a named bucket. Returns a <code>RateLimitResult</code> with <code>allowed</code> flag and <code>remainingTokens</code>.</p>
    </div>
    <div class="card">
      <h4>peek()</h4>
      <p>Read current token count without consuming — non-destructive. Used by the admin status endpoint.</p>
    </div>
    <div class="card">
      <h4>reset()</h4>
      <p>Delete both Redis keys for a client bucket. Used for testing and admin unblocking.</p>
    </div>
    <div class="card">
      <h4>Fail-open</h4>
      <p>If Redis throws, returns <code>allowed=true</code> so a Redis outage doesn't block all traffic. Swap to <code>false</code> for fail-closed.</p>
    </div>
  </div>

<span class="pre-label">TokenBucketService.java — tryConsume()</span>
<pre><code><span class="kw">public</span> RateLimitResult <span class="fn">tryConsume</span>(String clientKey, <span class="kw">int</span> rate, <span class="kw">int</span> capacity, <span class="kw">int</span> tokens) {
    String tokensKey = prefix + <span class="str">"."</span> + clientKey + <span class="str">".tokens"</span>;
    String tsKey     = prefix + <span class="str">"."</span> + clientKey + <span class="str">".timestamp"</span>;

    <span class="kw">try</span> {
        Long[] result = redisTemplate.<span class="fn">execute</span>(
            tokenBucketScript,
            List.of(tokensKey, tsKey),
            rate, capacity, Instant.now().<span class="fn">getEpochSecond</span>(), tokens
        );
        <span class="kw">return new</span> <span class="fn">RateLimitResult</span>(result[<span class="nb">0</span>] == <span class="nb">1L</span>, result[<span class="nb">1</span>], rate, capacity);

    } <span class="kw">catch</span> (Exception ex) {
        log.<span class="fn">error</span>(<span class="str">"Redis error — failing open"</span>);
        <span class="kw">return new</span> <span class="fn">RateLimitResult</span>(<span class="kw">true</span>, capacity, rate, capacity); <span class="cm">// fail-open</span>
    }
}</code></pre>
</section>

<!-- Redis Keys -->
<section id="redis-keys">
  <h2><span class="h2-icon">🗝</span> Redis Key Structure</h2>
  <p>Each client gets a pair of keys per bucket type. The prefix <code>svc_rate_limiter</code> is configurable — change it to avoid collisions if sharing Redis with other services.</p>

  <div class="rkeys">
    <div class="rkey"><span class="rp">svc_rate_limiter.</span><span class="rc">api-key:client-A</span><span class="rt">:read</span><span class="rs">.tokens</span><span class="rtag">read bucket</span></div>
    <div class="rkey"><span class="rp">svc_rate_limiter.</span><span class="rc">api-key:client-A</span><span class="rt">:read</span><span class="rs">.timestamp</span><span class="rtag">read bucket</span></div>
    <div class="rkey"><span class="rp">svc_rate_limiter.</span><span class="rc">api-key:client-A</span><span class="rt">:write</span><span class="rs">.tokens</span><span class="rtag">write bucket</span></div>
    <div class="rkey"><span class="rp">svc_rate_limiter.</span><span class="rc">api-key:client-A</span><span class="rt">:write</span><span class="rs">.timestamp</span><span class="rtag">write bucket</span></div>
    <div class="rkey"><span class="rp">svc_rate_limiter.</span><span class="rc">api-key:client-A</span><span class="rt">:method:products:bulk-create</span><span class="rs">.tokens</span><span class="rtag">@RateLimit</span></div>
  </div>

  <div class="box tip">
    <span class="box-icon">💡</span>
    <p>Read and write buckets are <strong>independent per client</strong> — exhausting the write bucket doesn't affect the client's read quota, and vice versa.</p>
  </div>
</section>

<!-- Configuration -->
<section id="config">
  <h2><span class="h2-icon">📐</span> Configuration Reference</h2>

<span class="pre-label">application.yml</span>
<pre><code><span class="yk">rate-limiter</span>:
  <span class="yk">enabled</span>: <span class="yv">true</span>             <span class="yc"># global kill-switch</span>
  <span class="yk">key-strategy</span>: <span class="yv">API_KEY</span>    <span class="yc"># IP | API_KEY | USER_ID</span>
  <span class="yk">redis-key-prefix</span>: <span class="yv">"svc_rate_limiter"</span>

  <span class="yk">routes</span>:
    <span class="yk">read</span>:
      <span class="yk">replenish-rate</span>: <span class="yv">10</span>    <span class="yc"># tokens added per second</span>
      <span class="yk">burst-capacity</span>: <span class="yv">20</span>    <span class="yc"># max bucket size (burst allowance)</span>
      <span class="yk">requested-tokens</span>: <span class="yv">1</span>  <span class="yc"># tokens consumed per request</span>
      <span class="yk">methods</span>: <span class="yv">GET</span>

    <span class="yk">write</span>:
      <span class="yk">replenish-rate</span>: <span class="yv">5</span>
      <span class="yk">burst-capacity</span>: <span class="yv">10</span>
      <span class="yk">requested-tokens</span>: <span class="yv">1</span>
      <span class="yk">methods</span>: <span class="yv">POST,PUT,DELETE,PATCH</span></code></pre>

  <div class="tbl-wrap">
    <table>
      <thead><tr><th>Property</th><th>Default</th><th>Description</th></tr></thead>
      <tbody>
        <tr><td><code>rate-limiter.enabled</code></td><td><code>true</code></td><td>Global on/off switch — no restart required if managed via config server</td></tr>
        <tr><td><code>rate-limiter.key-strategy</code></td><td><code>API_KEY</code></td><td>How client identity is resolved from the request</td></tr>
        <tr><td><code>rate-limiter.redis-key-prefix</code></td><td><code>svc_rate_limiter</code></td><td>Namespace prefix for all Redis keys</td></tr>
        <tr><td><code>routes.read.replenish-rate</code></td><td><code>10</code></td><td>Tokens added to read bucket per second</td></tr>
        <tr><td><code>routes.read.burst-capacity</code></td><td><code>20</code></td><td>Max size of read bucket</td></tr>
        <tr><td><code>routes.write.replenish-rate</code></td><td><code>5</code></td><td>Tokens added to write bucket per second</td></tr>
        <tr><td><code>routes.write.burst-capacity</code></td><td><code>10</code></td><td>Max size of write bucket</td></tr>
        <tr><td><code>REDIS_HOST</code></td><td><code>localhost</code></td><td>Redis hostname (env var)</td></tr>
        <tr><td><code>REDIS_PORT</code></td><td><code>6379</code></td><td>Redis port (env var)</td></tr>
      </tbody>
    </table>
  </div>
</section>

<!-- API Endpoints -->
<section id="endpoints">
  <h2><span class="h2-icon">🌐</span> API Endpoints</h2>
  <div class="tbl-wrap">
    <table>
      <thead><tr><th>Method</th><th>Path</th><th>Global Limit</th><th>Method Limit</th><th>Description</th></tr></thead>
      <tbody>
        <tr><td><span class="mtag m-get">GET</span></td><td><code>/api/v1/products</code></td><td>10/s burst 20</td><td>—</td><td>List all products</td></tr>
        <tr><td><span class="mtag m-get">GET</span></td><td><code>/api/v1/products/{id}</code></td><td>10/s burst 20</td><td>—</td><td>Get by ID</td></tr>
        <tr><td><span class="mtag m-get">GET</span></td><td><code>/api/v1/products/category/{cat}</code></td><td>10/s burst 20</td><td>—</td><td>Filter by category</td></tr>
        <tr><td><span class="mtag m-get">GET</span></td><td><code>/api/v1/products/in-stock</code></td><td>10/s burst 20</td><td>—</td><td>In-stock only</td></tr>
        <tr><td><span class="mtag m-get">GET</span></td><td><code>/api/v1/products/search?name=</code></td><td>10/s burst 20</td><td><strong>5/s burst 10</strong></td><td>Search by name</td></tr>
        <tr><td><span class="mtag m-post">POST</span></td><td><code>/api/v1/products</code></td><td>5/s burst 10</td><td>—</td><td>Create product</td></tr>
        <tr><td><span class="mtag m-put">PUT</span></td><td><code>/api/v1/products/{id}</code></td><td>5/s burst 10</td><td>—</td><td>Update product</td></tr>
        <tr><td><span class="mtag m-delete">DELETE</span></td><td><code>/api/v1/products/{id}</code></td><td>5/s burst 10</td><td>—</td><td>Delete product</td></tr>
        <tr><td><span class="mtag m-post">POST</span></td><td><code>/api/v1/products/bulk</code></td><td>5/s burst 10</td><td><strong>1/s burst 3</strong></td><td>Bulk create</td></tr>
        <tr><td><span class="mtag m-get">GET</span></td><td><code>/service/rate-limit/status</code></td><td colspan="2">No limit (not on /api/**)</td><td>Bucket status</td></tr>
        <tr><td><span class="mtag m-delete">DELETE</span></td><td><code>/service/rate-limit/reset</code></td><td colspan="2">No limit</td><td>Reset bucket</td></tr>
        <tr><td><span class="mtag m-get">GET</span></td><td><code>/service/rate-limit/config</code></td><td colspan="2">No limit</td><td>View config</td></tr>
      </tbody>
    </table>
  </div>
</section>

<!-- Response Headers -->
<section id="headers">
  <h2><span class="h2-icon">📋</span> Response Headers</h2>
  <p>Headers are set on <strong>every</strong> request so clients can track their quota proactively.</p>

<span class="pre-label">Allowed — 200 OK</span>
<pre><code><span class="hm">HTTP/1.1 200 OK</span>
X-RateLimit-Remaining: <span class="h2xx">18</span>
X-RateLimit-Limit:     <span class="h2xx">20</span></code></pre>

<span class="pre-label">Rate limited — 429</span>
<pre><code><span class="hm">HTTP/1.1 429 Too Many Requests</span>
X-RateLimit-Remaining:   <span class="h4xx">0</span>
X-RateLimit-Limit:       20
X-RateLimit-Retry-After: <span class="h4xx">1</span>
X-RateLimit-Message:     Too many requests. Token bucket exhausted. Retry after 1 second.
Content-Type:            application/json

{
  <span class="str">"status"</span>:          <span class="h4xx">429</span>,
  <span class="str">"error"</span>:           <span class="str">"Too Many Requests"</span>,
  <span class="str">"message"</span>:         <span class="str">"Service-level rate limit exceeded. Retry after 1 second(s)."</span>,
  <span class="str">"retryAfter"</span>:      <span class="nb">1</span>,
  <span class="str">"remainingTokens"</span>: <span class="nb">0</span>
}</code></pre>
</section>

<!-- Project Structure -->
<section id="structure">
  <h2><span class="h2-icon">📁</span> Project Structure</h2>
  <div class="tree">
<span class="td">product-service/</span>
├── <span class="td">src/main/java/com/example/app/</span>
│   ├── <span class="tf">Application.java</span>
│   │
│   ├── <span class="td">annotation/</span>
│   │   └── <span class="th">RateLimit.java</span>                 <span class="tc">← Custom method-level annotation</span>
│   │
│   ├── <span class="td">aspect/</span>
│   │   └── <span class="th">RateLimitAspect.java</span>           <span class="tc">← AOP: intercepts @RateLimit methods</span>
│   │
│   ├── <span class="td">config/</span>
│   │   ├── <span class="th">RateLimiterProperties.java</span>     <span class="tc">← Binds rate-limiter.* from yml</span>
│   │   ├── <span class="th">RedisConfig.java</span>               <span class="tc">← Lua script bean + Lettuce factory</span>
│   │   ├── <span class="th">WebMvcConfig.java</span>              <span class="tc">← Registers interceptor on /api/**</span>
│   │   ├── <span class="tf">CacheConfig.java</span>               <span class="tc">← Redis @Cacheable (10 min TTL)</span>
│   │   └── <span class="tf">OpenApiConfig.java</span>             <span class="tc">← Swagger UI</span>
│   │
│   ├── <span class="td">ratelimit/</span>                         <span class="tc">← Core rate-limiting package</span>
│   │   ├── <span class="th">RateLimitInterceptor.java</span>      <span class="tc">← HandlerInterceptor (global check)</span>
│   │   ├── <span class="th">TokenBucketService.java</span>        <span class="tc">← Redis Lua script executor</span>
│   │   ├── <span class="th">ClientKeyResolver.java</span>         <span class="tc">← IP / API-Key / User-ID strategy</span>
│   │   ├── <span class="th">RateLimitResult.java</span>           <span class="tc">← Immutable result record</span>
│   │   └── <span class="th">RateLimitStatusController.java</span> <span class="tc">← /service/rate-limit/* admin</span>
│   │
│   ├── <span class="td">controller/</span>
│   │   └── <span class="tf">ProductController.java</span>         <span class="tc">← CRUD + @RateLimit examples</span>
│   │
│   └── <span class="td">dto/</span>  <span class="td">model/</span>  <span class="td">service/</span>  <span class="td">repository/</span>  <span class="td">exception/</span>
│
├── <span class="td">src/test/java/com/example/app/</span>
│   ├── <span class="th">ratelimit/TokenBucketServiceTest.java</span>  <span class="tc">← Testcontainers Redis integration</span>
│   ├── <span class="tf">controller/ProductControllerTest.java</span>  <span class="tc">← MockMvc slice test</span>
│   └── <span class="tf">service/ProductServiceTest.java</span>        <span class="tc">← Mockito unit tests</span>
│
├── <span class="td">src/main/resources/</span>
│   └── <span class="th">application.yml</span>                        <span class="tc">← All rate-limit config here</span>
│
├── <span class="tf">pom.xml</span>
├── <span class="tf">Dockerfile</span>
└── <span class="tf">docker-compose.yml</span>                       <span class="tc">← MongoDB + Redis + Service</span>
  </div>
</section>

<!-- Quick Start -->
<section id="quickstart">
  <h2><span class="h2-icon">🚀</span> Quick Start</h2>

<h3>Docker (recommended)</h3>
<span class="pre-label">shell</span>
<pre><code><span class="sc"># Start MongoDB + Redis + product-service</span>
<span class="sk">docker-compose up -d</span>

<span class="sc"># Verify health</span>
<span class="sk">curl</span> <span class="sa">http://localhost:8081/actuator/health</span>

<span class="sc"># Swagger UI</span>
<span class="sk">open</span> <span class="sa">http://localhost:8081/swagger-ui.html</span></code></pre>

<h3>Local</h3>
<span class="pre-label">shell</span>
<pre><code><span class="sk">docker run</span> <span class="sa">-d -p 27017:27017 mongo:7.0</span>
<span class="sk">docker run</span> <span class="sa">-d -p 6379:6379  redis:7.2-alpine</span>

<span class="sk">mvn spring-boot:run</span>   <span class="sc"># starts on :8081</span></code></pre>

<h3>Build &amp; Test</h3>
<span class="pre-label">shell</span>
<pre><code><span class="sk">mvn clean package</span>          <span class="sc"># build fat-jar</span>
<span class="sk">mvn test</span>                   <span class="sc"># needs Docker (Testcontainers)</span></code></pre>
</section>

<!-- Testing -->
<section id="testing">
  <h2><span class="h2-icon">🔬</span> Testing Rate Limits</h2>

<h3>Exhaust the read bucket (burst = 20)</h3>
<span class="pre-label">shell</span>
<pre><code><span class="sk">for</span> i <span class="sa">in</span> {1..25}; <span class="sa">do</span>
  STATUS=$(curl -s -o /dev/null -w <span class="sa">"%{http_code}"</span> \
    -H <span class="sa">"X-API-Key: client-A"</span> \
    http://localhost:8081/api/v1/products)
  echo <span class="sa">"GET $i → $STATUS"</span>
<span class="sa">done</span>
<span class="sc"># 1–20 → 200,  21–25 → 429</span></code></pre>

<h3>Exhaust the write bucket (burst = 10)</h3>
<span class="pre-label">shell</span>
<pre><code><span class="sk">for</span> i <span class="sa">in</span> $(seq 1 12); <span class="sa">do</span>
  STATUS=$(curl -s -o /dev/null -w <span class="sa">"%{http_code}"</span> -X POST \
    http://localhost:8081/api/v1/products \
    -H <span class="sa">"Content-Type: application/json"</span> \
    -d <span class="sa">'{"name":"W","sku":"SKU-'$i'","price":9.99,"category":"tools","stock":1}'</span>)
  echo <span class="sa">"POST $i → $STATUS"</span>
<span class="sa">done</span>
<span class="sc"># 1–10 → 201,  11–12 → 429</span></code></pre>

<h3>Test @RateLimit bulk endpoint (burst = 3)</h3>
<span class="pre-label">shell</span>
<pre><code><span class="sk">for</span> i <span class="sa">in</span> $(seq 1 5); <span class="sa">do</span>
  STATUS=$(curl -s -o /dev/null -w <span class="sa">"%{http_code}"</span> -X POST \
    http://localhost:8081/api/v1/products/bulk \
    -H <span class="sa">"Content-Type: application/json"</span> \
    -d <span class="sa">'[{"name":"W","sku":"B-'$i'","price":1.99,"category":"tools","stock":1}]'</span>)
  echo <span class="sa">"BULK $i → $STATUS"</span>
<span class="sa">done</span>
<span class="sc"># 1–3 → 201,  4–5 → 429  (tighter @RateLimit fires first)</span></code></pre>

<h3>Verify per-client isolation</h3>
<span class="pre-label">shell</span>
<pre><code><span class="sc"># Exhaust client-A's read bucket</span>
<span class="sk">for</span> i <span class="sa">in</span> {1..21}; <span class="sa">do</span>
  curl -s -o /dev/null -H <span class="sa">"X-API-Key: client-A"</span> http://localhost:8081/api/v1/products
<span class="sa">done</span>

<span class="sc"># client-B should have a full bucket → 200</span>
<span class="sk">curl</span> -s -o /dev/null -w <span class="sa">"%{http_code}\n"</span> \
  -H <span class="sa">"X-API-Key: client-B"</span> http://localhost:8081/api/v1/products</code></pre>
</section>

<!-- Admin Endpoints -->
<section id="admin">
  <h2><span class="h2-icon">🔧</span> Admin Endpoints</h2>
  <span class="pre-label">shell</span>
<pre><code><span class="sc"># Inspect a client's bucket token counts</span>
<span class="sk">curl</span> <span class="sa">"http://localhost:8081/service/rate-limit/status?key=api-key:client-A"</span>

<span class="sc"># Reset both read and write buckets for a client</span>
<span class="sk">curl</span> -X DELETE <span class="sa">"http://localhost:8081/service/rate-limit/reset?key=api-key:client-A"</span>

<span class="sc"># View active rate-limiter configuration</span>
<span class="sk">curl</span> <span class="sa">"http://localhost:8081/service/rate-limit/config"</span>

<span class="sc"># Spring Boot actuator health</span>
<span class="sk">curl</span> <span class="sa">"http://localhost:8081/actuator/health"</span></code></pre>
</section>

</main>
</div>

<footer>
  Made with ☕ by <a href="https://github.com/PrasanthKrishnan51">PrasanthKrishnan51</a>
  &nbsp;·&nbsp;
  Spring Boot Service-Level Token Bucket Rate Limiting
</footer>

</body>
</html>