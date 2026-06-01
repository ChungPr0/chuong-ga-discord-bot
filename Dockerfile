FROM eclipse-temurin:17-jre

WORKDIR /app

COPY app.jar /app/app.jar

RUN mkdir -p /app/mytmp

ENTRYPOINT ["java", "-Xmx512M", "-Djava.io.tmpdir=/app/mytmp", "-Djava.net.preferIPv4Stack=true", "-jar", "app.jar"]