FROM eclipse-temurin:8-jdk-alpine

# Install Tomcat manually
ENV TOMCAT_VERSION=8.5.98
ENV CATALINA_HOME=/opt/tomcat
ENV PATH=$CATALINA_HOME/bin:$PATH

# Install tools
RUN apk update && apk add --no-cache curl openssl tar

# Install Tomcat
RUN mkdir -p $CATALINA_HOME && \
    curl -fSL https://downloads.apache.org/tomcat/tomcat-8/v$TOMCAT_VERSION/bin/apache-tomcat-$TOMCAT_VERSION.tar.gz \
    | tar -xz --strip-components=1 -C $CATALINA_HOME && \
    rm -rf $CATALINA_HOME/webapps/*

WORKDIR $CATALINA_HOME

# Copy WAR and configuration files
COPY your-app.war webapps/ROOT.war
COPY server.xml conf/server.xml
COPY keystore.p12 conf/keystore.p12

# Create non-root user
RUN addgroup -S appuser && adduser -S -G appuser appuser && \
    chown -R appuser:appuser $CATALINA_HOME

# Switch to non-root
USER appuser

# Custom JVM options
ENV JAVA_OPTS=""

# Expose HTTPS
EXPOSE 443

# Healthcheck using HTTPS
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -fk https://localhost:443/ || exit 1

CMD ["sh", "-c", "exec java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar $CATALINA_HOME/bin/bootstrap.jar"]
