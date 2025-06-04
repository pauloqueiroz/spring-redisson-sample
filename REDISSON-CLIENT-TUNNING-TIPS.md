## Tuning Tips
Enable cluster mode in your ElastiCache setup if not already using it (for scaling and resilience).

Use readMode: SLAVE to balance load across read replicas.

Use connection timeouts that are reasonable for your service latency expectations.

Monitor Redisson metrics: connections used, pool saturation, timeouts, etc.

Consider enabling Redisson’s built-in monitoring for runtime diagnostics.

Would you like a version of this config for single-node ElastiCache (non-cluster) or sentinel mode instead? Or should I tailor the pool sizes based on your expected QPS / thread count?

Nice — planning for x concurrent threads (via JMeter) means you’ll need your Redisson connection pools sized appropriately to avoid connection contention, timeouts, or stalls.

Here’s how to tune your redisson.yaml for that workload 👇

##  🔢 Key Considerations for x Threads
Each thread may hold at least one connection simultaneously, depending on your app logic and Redis usage pattern.

Redis itself is single-threaded, but you’ll likely be hitting multiple nodes in an ElastiCache Cluster (especially with readMode: SLAVE).

Redisson pools per node, so total connections = poolSize × number of nodes.

✅ Updated redisson.yaml for 300 concurrent threads
yaml
Copiar
Editar
```yaml
clusterServersConfig:
  nodeAddresses:
    - "redis://your-cluster-node1:6379"
    - "redis://your-cluster-node2:6379"
    - "redis://your-cluster-node3:6379"
  scanInterval: 2000
  readMode: "SLAVE"
  subscriptionMode: "SLAVE"
  loadBalancer: !<org.redisson.connection.balancer.RoundRobinLoadBalancer> {}

  # Increased for high concurrency
  masterConnectionPoolSize: 128
  slaveConnectionPoolSize: 128
  idleConnectionTimeout: 10000
  connectTimeout: 10000
  timeout: 3000
  retryAttempts: 3
  retryInterval: 1500
  failedSlaveReconnectionInterval: 3000
  failedSlaveCheckInterval: 60000

threads: 64         # 2x CPU cores (tune per your server)
nettyThreads: 64

codec: !<org.redisson.codec.SnappyCodecV2> {}

transportMode: "NIO"
```

##  🧠 Breakdown of Key Settings
Setting	Recommended Value	Why?
masterConnectionPoolSize	128	Supports concurrent operations with headroom
slaveConnectionPoolSize	128	Distributes reads across replicas
threads / nettyThreads	# cores × 2	Handles Redis tasks and network efficiently
timeout	3000 ms	Fails fast under pressure
retryAttempts	3	Reasonable for test & prod
readMode	SLAVE	Offloads reads from master
📈 Tip: Scale Pool Based on Nodes
If your cluster has 3 masters + 3 replicas:

You’ll have 128 × 3 master connections + 128 × 3 slave = 768 total potential connections.

Redis EC2-based nodes can handle that, but monitor via CloudWatch and Redisson metrics.

## ✅ Final Thoughts
Run your JMeter test with connection metrics enabled (via JMX or logs).

Monitor for:

Pool exhaustion

Timeout exceptions

Redis latency spikes

Adjust pool sizes gradually if needed (e.g., try 192 or 256 if still saturated)

Want help writing a JMeter test plan that simulates this Redis usage (e.g., reads/writes to a map or bucket)?

## Running With docker
```bash
docker build -t tomcat-https .
docker run -e JAVA_OPTS="-Xmx1g -Xms512m -XX:+UseG1GC" -p 443:443 my-tomcat-https
```

Yes — the AWS Application Load Balancer (ALB) is very likely contributing to your org.apache.http.NoHttpResponseException under high load.

Here's a breakdown of why this happens, and how to mitigate it.

🔍 Understanding the Root Cause
🔥 Why you're not seeing the issue locally:
Your local mock server likely holds keep-alive connections open indefinitely.

It doesn’t drop idle connections abruptly.

It behaves more leniently than an ALB, especially with connection reuse.

💣 Why it happens with ALB (AWS):
An AWS ALB will silently close idle HTTP connections after 60 seconds by default — even if your client connection pool considers them alive.

So when Apache HttpClient reuses a pooled connection, the ALB may have already closed it, causing NoHttpResponseException.

This is exacerbated under high load because:

More connections are pooled and reused.

Some are idle long enough to be silently closed by ALB.

Your client doesn't know it’s dead — and gets no response (a half-closed TCP socket).

🧪 How to Confirm It's the ALB
Use tcpdump or Wireshark on your server to see RST packets from ALB.

Enable HttpClient wire logging:

properties
```bash
-Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.SimpleLog
-Dorg.apache.commons.logging.simplelog.log.org.apache.http=DEBUG
```
Look for:

pgsql
DEBUG DefaultClientConnection: Connection closed
✅ How to Fix It (Reliably)
✅ 1. Enable Stale Connection Check
This is disabled by default in Apache PoolingHttpClientConnectionManager.

```java
 connManager.setValidateAfterInactivity(5000); // 5 seconds
```
This tells HttpClient to check if the connection is still alive (e.g., by doing a non-blocking read) before reusing it.

🔁 This works in many cases, but not always reliable under very high concurrency.

✅ 2. Limit Keep-Alive to Below ALB Timeout
Set your Keep-Alive timeout < 60 seconds (ALB default):

```java
ConnectionKeepAliveStrategy keepAliveStrategy = (response, context) -> {
return 30_000; // 30 seconds
};
```
This prevents the client from reusing a connection that ALB may have already closed.

✅ 3. Consider switching to OkHttp (recommended)
OkHttp is much better at detecting stale connections. It periodically pings sockets before reuse, and deals gracefully with silent connection drops.

If you're under heavy sustained load (500+ RPS), switching to OkHttp will likely eliminate these errors without needing custom keep-alive or stale checking logic.

✅ 4. Use retries for transient NoHttpResponseException
If you stick with Apache client:

```java
HttpRequestRetryHandler retryHandler = new DefaultHttpRequestRetryHandler(3, true);
```
This allows retries on recoverable failures like dropped connections — but be careful with idempotency.

✅ 5. Tune ALB Idle Timeout (Optional)
If you own the ALB configuration, consider lowering its idle timeout to 30s to force more frequent connection refreshes (or increase it if you prefer longer-lived connections and validate them properly in client).

ALB setting:

EC2 → Load Balancers → [Your ALB] → Description → “Idle timeout”

🚀 Summary
Problem	Solution
ALB silently closes idle connections	✅ setValidateAfterInactivity(5000)
HttpClient reuses dead sockets	✅ Keep-Alive strategy < ALB timeout
Local mocks don’t simulate real-world behavior	✅ Test with actual ALB under load
Persistent error under high concurrency	✅ Switch to OkHttp (handles it better)
Need resilience	✅ Add retry handler for NoHttpResponseException.

If you disabled the connection pool and are still getting connection closed errors (e.g., NoHttpResponseException, Connection reset, or Connection closed), then the issue is almost certainly not the pool, but instead one or more of the following:

🔍 Likely Causes (Even Without Pooling)
1. Server (ALB or Target App) Closes Connections Early
   Even without a pool, each new request creates a fresh connection. However:

If your client uses HTTP/1.1 and the server sends Connection: close, the connection will be closed immediately after the response.

If the server has reached connection or thread limits, it may abruptly close new connections.

The AWS ALB might also:

Drop connections if too many are queued.

Respond slowly and timeout clients (causing SocketTimeoutException).

2. Client Misconfiguration (Timeouts Too Short or Too Long)
   Without pooling, new connections are expensive:

If you set very aggressive timeouts (e.g. 1s), you might get connection timeout or read timeout errors under load.

Conversely, long timeouts can cause socket hangups if the server or ALB closes the connection early.

🔧 Try tuning:

```java
RequestConfig requestConfig = RequestConfig.custom()
.setConnectTimeout(2000)
.setSocketTimeout(5000)
.setConnectionRequestTimeout(2000)
.build();
```

3. Concurrency Bottlenecks or Thread Starvation
   If you're doing 500 RPS and each call creates a new connection:

Your app must open 500 TLS handshakes per second — extremely expensive!

You might see connection close errors due to:

Thread pool saturation

GC pauses

OS file descriptor limits (e.g., Too many open files)

TCP connection backlog

🛠️ Check your server and container logs (dmesg, netstat, VisualVM, etc.).

4. TLS Handshake Failures or Misalignment
   If you're calling HTTPS endpoints (as with ALB):

Creating a new TLS connection for every request can lead to:

Slowness

Connection refusal or reset errors

Under load, the server might reject new TLS sessions

ALBs enforce TLS settings and limits — some ciphers or handshake retries can silently fail.

5. No Keep-Alive or Mismatched Protocol Behavior
   If your client isn’t reusing connections and:

Doesn’t send Connection: close

Or the server assumes keep-alive

Then either side may close the connection while the other tries to use it.

This leads to race conditions and Connection closed/reset exceptions.

✅ Best Practice: Use a Pooled Client with Reliable Reuse Logic
Even though you disabled pooling to test, the reality is: you need pooling, but configured correctly.

OkHttp or a properly tuned Apache client with:

setValidateAfterInactivity

sensible keep-alive (30s)

retries on NoHttpResponseException

…is far more robust under load than opening fresh sockets on every request.

✅ Recommendation
✅ Use OkHttp with connection pool (50, 5 minutes)

✅ Set retry and backoff logic

✅ Use connection and read timeout

✅ Avoid per-request client creation

✅ Ensure ALB idle timeout is understood and aligned (60s default)
