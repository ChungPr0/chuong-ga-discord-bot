# Chuồng Gà Discord Bot

<p align="center">
  <a href="https://jdk.java.net/17/"><img src="https://img.shields.io/badge/Java-17-blue?style=for-the-badge&logo=openjdk" alt="Java 17"/></a>
  <a href="https://github.com/discord-jda/JDA"><img src="https://img.shields.io/badge/JDA-6.4.1-red?style=for-the-badge&logo=discord" alt="JDA"/></a>
  <a href="https://github.com/appleboy/ssh-action"><img src="https://img.shields.io/badge/CI%2FCD-GitHub_Actions-darkgreen?style=for-the-badge&logo=githubactions" alt="CI/CD"/></a>
</p>

---

## 🌟 Giới Thiệu
**Chuồng Gà Discord Bot** là một trợ lý đa năng chuyên nghiệp được thiết kế và tối ưu hóa riêng cho máy chủ Discord **Chuồng Gà**. Bot được xây dựng trên ngôn ngữ **Java 17** sử dụng thư viện **JDA (Java Discord API)** nhằm mang lại trải nghiệm quản lý máy chủ mượt mà và tự động hóa các tiện ích cho thành viên.

---

## 🛠️ Các Tính Năng Nổi Bật

### 🔊 1. Join-to-Create (Kênh Thoại Tạm Tự Động)
* **Tự tạo kênh thoại riêng**: Tự động khởi tạo kênh thoại cá nhân khi thành viên tham gia kênh kích hoạt.
* **Control Panel trực quan**: Cung cấp bảng điều khiển với nút bấm tương tác (khóa phòng, ẩn phòng, đổi tên phòng, giới hạn số người, kick thành viên, chuyển quyền chủ phòng).
* **Tự động dọn dẹp & Khôi phục trạng thái**: Tự động xóa kênh khi không còn thành viên và khôi phục kênh tạm khi bot khởi động lại.

### 👋 2. Quản Lý Thành Viên & Chào Mừng
* **Chào mừng tùy chỉnh (Custom Welcome Embed)**: Gửi tin nhắn Embed chào mừng trong kênh chung, đính kèm nút chuyển hướng nhanh đến kênh kiểm duyệt `#roles`.
* **Hướng dẫn qua tin nhắn riêng (Direct Message)**: Tự động nhắn tin riêng cho người dùng mới khi tham gia để hướng dẫn họ nhận role thành viên chính thức.
* **Tạm biệt thành viên (Farewell Embed)**: Cập nhật sĩ số server tự động khi có thành viên rời khỏi chuồng.

### 🎭 3. Reaction Role (Tự Động Cấp Rank)
* Cho phép phân quyền và cấp Rank tự động khi thành viên thả cảm xúc (emoji ✅) vào bài viết cấu hình trong kênh `#roles`. 
* Tự động tước Rank khi người dùng gỡ bỏ cảm xúc.

### 🚀 4. Vinh Danh Boost Nitro
* Tự động phát hiện và gửi Embed chúc mừng đầy màu sắc, tag tên đại gia khi máy chủ nhận được một cú Boost Nitro mới.

---

## ⚙️ Cấu Hình Môi Trường (`.env`)

Tạo một tệp tin `.env` ở thư mục gốc của dự án và điền đầy đủ các thông tin cấu hình bên dưới:

```env
# Mã Token bảo mật của Discord Bot
DISCORD_TOKEN=YOUR_DISCORD_TOKEN

# ID của Máy chủ Discord (Guild ID)
GUILD_ID=YOUR_GUILD_ID

# Các kênh thông báo chuyên dụng
WELCOME_CHANNEL_ID=YOUR_WELCOME_CHANNEL_ID
ROLES_CHANNEL_ID=YOUR_ROLES_CHANNEL_ID
CREATE_VOICE_CHANNEL_ID=YOUR_CREATE_VOICE_CHANNEL_ID

# ID tin nhắn để kiểm duyệt (Thả emoji ✅ để nhận role)
REACTION_MESSAGE_ID=YOUR_REACTION_MESSAGE_ID

# ID của vai trò (Role) sẽ cấp khi thả reaction (Ví dụ: Gà Con)
CHICKEN_ROLE_ID=YOUR_CHICKEN_ROLE_ID

# ID kênh hiển thị Bảng điều khiển hệ thống (Live System Monitor Panel)
STATUS_CHANNEL_ID=YOUR_STATUS_CHANNEL_ID

# Địa chỉ Beszel Hub API (Mặc định: http://localhost:8090)
BESZEL_URL=http://localhost:8090

# Đường dẫn lời mời tham gia máy chủ
INVITE_LINK=https://discord.gg/your_invite_link
```

---

## 🚀 Hướng Dẫn Cài Đặt & Chạy Bot

### 1. Yêu cầu hệ thống
* **Java Development Kit (JDK) 17** trở lên.
* **Apache Maven** dùng để quản lý thư viện và đóng gói dự án.

### 2. Chạy thủ công trên máy tính cá nhân
Chạy các dòng lệnh sau tại thư mục gốc của dự án:

```bash
# Tải các thư viện và biên dịch mã nguồn
mvn clean compile

# Đóng gói dự án thành tệp Jar (đã bao gồm các dependency)
mvn package -DskipTests

# Chạy bot bằng tệp JAR đã build (Lưu ý phải có file .env trong cùng thư mục chạy)
java -jar target/chuong-ga-discord-bot-1.0-SNAPSHOT-jar-with-dependencies.jar
```

---

## 🐳 Triển Khai Với Docker

### 1. Build Docker Image
```bash
# Chuẩn bị tệp jar đã build và di chuyển ra ngoài thư mục gốc
mv target/*-jar-with-dependencies.jar ./app.jar

# Build image từ Dockerfile
docker build -t chuong-ga-bot:latest .
```

### 2. Chạy Docker Container
```bash
docker run -d \
  --name chuong-ga-bot-container \
  --env-file .env \
  --restart unless-stopped \
  chuong-ga-bot:latest
```

---

## 🔄 Tự Động Hóa Triển Khai (CI/CD) qua GitHub Actions

Quy trình triển khai tự động lên VPS được định nghĩa tại tệp [.github/workflows/deploy-bot.yml](file:///.github/workflows/deploy-bot.yml). Mỗi khi bạn đẩy mã nguồn mới lên nhánh `main`, hệ thống sẽ tự động triển khai lên VPS.
