# Sử dụng base image Java 17 (Bạn có thể giữ nguyên base image hiện tại của bạn)
FROM eclipse-temurin:17-jre

# Đặt thư mục làm việc bên trong container
WORKDIR /app

# Copy file app.jar (đã được đổi tên trong file .yml của bạn) vào container
COPY app.jar /app/app.jar

# Tạo sẵn thư mục temp nội bộ trong container cho chắc kèo để Lavaplayer xả lõi
RUN mkdir -p /app/mytmp

# Ép container tự động chạy Java kèm 2 cờ "mở khóa" âm thanh
ENTRYPOINT ["java", "-Djava.io.tmpdir=/app/mytmp", "-Djava.net.preferIPv4Stack=true", "-jar", "app.jar"]