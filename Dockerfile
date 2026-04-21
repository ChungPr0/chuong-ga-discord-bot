# Sử dụng JDK 17
FROM eclipse-temurin:17-jre-alpine

# Set múi giờ cho chuẩn log
ENV TZ=Asia/Ho_Chi_Minh

# Thư mục làm việc trong container
WORKDIR /app

# Copy file jar đã build vào container
COPY app.jar /app/app.jar

# Chạy bot
CMD ["java", "-jar", "app.jar"]