FROM eclipse-temurin:8-jdk-alpine

# Install Tomcat manually for more control
ENV TOMCAT_VERSION=8.5.98
ENV CATALINA_HOME=/opt/tomcat
ENV PATH=$CATALINA_HOME/bin:$PATH

# Install curl and openssl
RUN apk update && apk add --no-cache curl openssl tar && \
    mkdir -p $CATALINA_HOME && \
    curl -fSL https://downloads.apache.org/tomcat/tomcat-8/v$TOMCAT_VERSION/bin/apache-tomcat-$TOMCAT_VERSION.tar.gz \
    | tar -xz --strip-components=1 -C $CATALINA_HOME && \
    rm -rf $CATALINA_HOME/webapps/*

# Copy WAR into ROOT context
COPY your-app.war $CATALINA_HOME/webapps/ROOT.war

# Create non-root user
RUN addgroup -S appuser && adduser -S -G appuser appuser && \
    chown -R appuser:appuser $CATALINA_HOME

# Use non-root user
USER appuser

# Expose port 443 for HTTPS (assumes reverse proxy or TLS setup inside container)
EXPOSE 443

# Customizable JVM options
ENV JAVA_OPTS=""

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -fk https://localhost:443/ || exit 1

CMD ["sh", "-c", "exec java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar $CATALINA_HOME/bin/bootstrap.jar"]