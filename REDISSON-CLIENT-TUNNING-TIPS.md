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