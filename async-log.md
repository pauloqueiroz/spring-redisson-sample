To improve your Spring 4.3.11.RELEASE + Java 8 WAR application's performance under high load when sending logs to AWS CloudWatch, you must decouple logging from request processing. Your current setup — using synchronous log4j 2.5 with logging to Tomcat’s catalina logs — blocks the request threads during I/O, especially under high load (e.g., 300 RPS). Here’s how to make logging asynchronous and export to CloudWatch with minimal performance impact.

✅ Strategy Overview
Use Log4j2 Async Appender (via LMAX Disruptor)

Log to a buffer (AsyncAppender), not catalina.out

Export logs to AWS CloudWatch via a background log shipper (e.g., CloudWatch Agent or Fluent Bit)

🔧 Step-by-Step Implementation
1. Upgrade Log4j to a More Recent Version (Optional but Recommended)
   You're using log4j-core 2.5, which is old and has known CVEs. Consider upgrading to 2.17.2+ for performance and security improvements (if possible in your WAR app and dependency tree).

If an upgrade isn't possible, you can still apply the same concepts below with 2.5.

2. Use AsyncAppender with an In-Memory Queue
   Edit your log4j2.xml or log4j2.properties configuration:

Example (log4j2.xml):

xml
Copiar
Editar
<Configuration status="WARN">
<Appenders>
<!-- File appender for CloudWatch agent to pick up -->
<File name="AsyncFile" fileName="/var/log/myapp/app.log">
<PatternLayout>
<Pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n</Pattern>
</PatternLayout>
</File>

    <!-- Async wrapper to avoid blocking the request thread -->
    <Async name="AsyncAppender">
      <AppenderRef ref="AsyncFile"/>
    </Async>
  </Appenders>

  <Loggers>
    <Root level="INFO">
      <AppenderRef ref="AsyncAppender"/>
    </Root>
  </Loggers>
</Configuration>
Key Points:

Logging is done to a local file asynchronously, using Log4j2's high-performance AsyncAppender.

This prevents log write latency from blocking the request thread.

3. Ship Logs to CloudWatch via a Daemon (CloudWatch Agent or Fluent Bit)
   This ensures your app only writes to local disk, and a separate service pushes logs to CloudWatch asynchronously.

Option A: Use CloudWatch Agent
Install AWS CloudWatch Agent in your ECS EC2 container and configure it to tail your app log:

json
Copiar
Editar
{
"logs": {
"logs_collected": {
"files": {
"collect_list": [
{
"file_path": "/var/log/myapp/app.log",
"log_group_name": "myapp-logs",
"log_stream_name": "{instance_id}"
}
]
}
}
}
}
CloudWatch agent runs independently of the application, consuming little to no CPU from your Java process.

Option B: Use Fluent Bit (lightweight alternative)
Install Fluent Bit in ECS to tail your logs and push to CloudWatch Logs using its CloudWatch output plugin.

4. Avoid Logging to catalina.out
   Make sure your Log4j2 configuration does NOT write logs to System.out, which ends up in catalina.out (synchronous, blocking I/O).

Avoid:

xml
Copiar
Editar
<Console name="Console" target="SYSTEM_OUT"> <!-- DON'T DO THIS -->
If Spring or libraries log via System.out, redirect them to your log4j2 async logger using a bridge:

```xml
<dependencies>
<dependency>
<groupId>org.apache.logging.log4j</groupId>
<artifactId>log4j-jul</artifactId>
<version>2.17.2</version>
</dependency>
</dependencies>
```
And register bridge handler early (e.g., in WebAppInitializer):

```java
java.util.logging.LogManager.getLogManager().reset();
org.apache.logging.log4j.jul.LogManager.getLogManager().getLogger("");
```
📈 Expected Benefits
Request thread is never blocked by log I/O

Log persistence is reliable (thanks to disk + shipper agent)

High throughput maintained under load

Low memory/CPU overhead from async queue

🚨 Extra Tips
Monitor disk space on ECS instance if logging to file.

Set proper file rotation in Log4j2 to avoid filling up disk.

If your ECS runs Fargate, consider direct CloudWatch appenders — though async buffering still applies.

📌 Summary
Feature	Old (Sync)	New (Async)
Log Destination	catalina.out	/var/log/myapp/app.log
I/O Mode	Blocking (sync)	Non-blocking (async queue)
CloudWatch Export	Direct or none	CloudWatch Agent/Fluent Bit
Thread Impact	High under load	Minimal
Log4j Appender	Console/File	AsyncAppender + File


```JAVA
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.*;
import org.apache.logging.log4j.core.config.*;
import org.apache.logging.log4j.core.layout.PatternLayout;

public class AsyncLoggingInitializer {

    public static void configureAsyncLogging() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();

        // Define Layout
        PatternLayout layout = PatternLayout.newBuilder()
                .withPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n")
                .build();

        // Define File Appender (writes to local file)
        String logFilePath = "/var/log/myapp/app.log";
        FileAppender fileAppender = FileAppender.newBuilder()
                .withFileName(logFilePath)
                .withName("FileAppender")
                .withLayout(layout)
                .setConfiguration(config)
                .build();
        fileAppender.start();

        // Define Async Appender
        AppenderRef ref = AppenderRef.createAppenderRef("FileAppender", null, null);
        AsyncAppender asyncAppender = AsyncAppender.newBuilder()
                .setName("AsyncAppender")
                .setConfiguration(config)
                .setAppenderRefs(new AppenderRef[]{ref})
                .setBlocking(false)
                .setBufferSize(8192)  // Tune buffer size for your load
                .build();
        asyncAppender.start();

        // Register appenders
        config.addAppender(fileAppender);
        config.addAppender(asyncAppender);

        // Link root logger to async appender
        LoggerConfig rootLogger = config.getRootLogger();
        rootLogger.addAppender(asyncAppender, null, null);

        // Apply config
        context.updateLoggers();
    }
}
```

logback.xml
```xml
<configuration>
  
  <!-- Async Appender config for high throughput -->
  <appender name="ASYNC_FILE" class="ch.qos.logback.classic.AsyncAppender">
    <queueSize>8192</queueSize>
    <discardingThreshold>0</discardingThreshold>
    <includeCallerData>false</includeCallerData>

    <appender-ref ref="FILE" />
  </appender>

  <!-- File Appender -->
  <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>/var/log/myapp/app.log</file>
    
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
      <!-- Daily rollover -->
      <fileNamePattern>/var/log/myapp/app-%d{yyyy-MM-dd}.log</fileNamePattern>
      <maxHistory>7</maxHistory> <!-- Keep last 7 days -->
    </rollingPolicy>

    <encoder>
      <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <!-- Root Logger -->
  <root level="INFO">
    <appender-ref ref="ASYNC_FILE" />
  </root>

</configuration>
```





