# Sử dụng base image Java 17
FROM eclipse-temurin:17-jre

# Đặt thư mục làm việc bên trong container
WORKDIR /app

# Copy file app.jar vào container
COPY app.jar /app/app.jar

# Tạo sẵn thư mục temp nội bộ trong container để Lavaplayer xả lõi
RUN mkdir -p /app/mytmp

# Ép container chạy Java với cờ -Xmx512M (Giới hạn tối đa 512MB RAM)
ENTRYPOINT ["java", "-Xmx512M", "-Djava.io.tmpdir=/app/mytmp", "-Djava.net.preferIPv4Stack=true", "-jar", "app.jar"]