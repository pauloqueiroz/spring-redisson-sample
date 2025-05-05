# Base image with Java 8 and Tomcat 8
FROM tomcat:8-jdk8-temurin

# Create a non-root user and group
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Clean default webapps to harden the image
RUN rm -rf /usr/local/tomcat/webapps/*

# Set working directory
WORKDIR /usr/local/tomcat

# Copy your WAR file (rename to ROOT.war to run at root context)
COPY your-app.war ./webapps/ROOT.war

# Set environment variable for custom JVM options (e.g., -Xmx, -Xms)
ENV JAVA_OPTS=""

# Health check for ECS/containers
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:8080/ || exit 1

# Set permissions
RUN chown -R appuser:appuser /usr/local/tomcat

# Use non-root user
USER appuser

# Expose Tomcat port
EXPOSE 8080

# Start Tomcat using exec form for proper signal handling
CMD ["sh", "-c", "exec java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /usr/local/tomcat/bin/bootstrap.jar"]
