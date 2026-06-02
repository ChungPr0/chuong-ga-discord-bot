# Chuồng Gà Discord Bot

<p align="center">
  <a href="https://jdk.java.net/17/"><img src="https://img.shields.io/badge/Java-17-blue?style=for-the-badge&logo=openjdk" alt="Java 17"/></a>
  <a href="https://github.com/discord-jda/JDA"><img src="https://img.shields.io/badge/JDA-6.4.1-red?style=for-the-badge&logo=discord" alt="JDA"/></a>
  <a href="https://github.com/appleboy/ssh-action"><img src="https://img.shields.io/badge/CI%2FCD-GitHub_Actions-darkgreen?style=for-the-badge&logo=githubactions" alt="CI/CD"/></a>
</p>

---

## 🌟 Giới Thiệu
**Chuồng Gà Discord Bot** là một trợ lý đa năng chuyên nghiệp được thiết kế và tối ưu hóa riêng cho máy chủ Discord **Chuồng Gà**. Bot được xây dựng trên ngôn ngữ **Java 17** sử dụng thư viện **JDA (Java Discord API)** và **Lavaplayer** thế hệ mới nhằm mang lại trải nghiệm tương tác mượt mà và giải trí âm nhạc đỉnh cao cho các thành viên.

---

## 🛠️ Các Tính Năng Nổi Bật

### 🎵 1. Trình Phát Nhạc Hi-Fi Chuyên Nghiệp
* **Phát nhạc thông minh**: Hỗ trợ tìm kiếm bài hát theo từ khóa trên YouTube (`ytsearch:`) hoặc chạy trực tiếp từ liên kết (URL) của YouTube và các dịch vụ phát trực tuyến khác.
* **Bảng điều khiển tương tác (Interactive Control Panel)**: Điều khiển nhạc hoàn toàn bằng các nút bấm trực quan (Play/Pause, Skip, Previous, Loop, Shuffle, Leave, Mở hàng chờ).
* **Trình đơn hàng chờ nâng cao (Paginated Queue Selection)**: Hiển thị hàng chờ nhạc phân trang trực quan, cho phép người dùng chọn và nhảy trực tiếp đến bài hát bất kỳ thông qua menu thả xuống (Dropdown Select Menu).
* **Tối ưu hóa hình thu nhỏ (Dynamic YouTube Thumbnail)**: Tự động trích xuất và hiển thị Thumbnail thực tế của video YouTube đang phát làm ảnh nền cho bảng điều khiển, giúp giao diện phòng nhạc sinh động hơn.

### 👋 2. Quản Lý Thành Viên & Chào Mừng
* **Chào mừng tùy chỉnh (Custom Welcome Embed)**: Gửi tin nhắn Embed chào mừng cực đẹp trong kênh chung, đính kèm nút chuyển hướng nhanh đến kênh kiểm duyệt `#roles`.
* **Hướng dẫn qua tin nhắn riêng (Direct Message)**: Tự động nhắn tin riêng cho người dùng mới khi tham gia để hướng dẫn họ cách nhận role thành viên chính thức.
* **Tạm biệt thành viên (Farewell Embed)**: Cập nhật sĩ số server tự động khi có thành viên rời khỏi chuồng.

### 🎭 3. Reaction Role (Tự Động Cấp Rank)
* Cho phép phân quyền và cấp Rank tự động khi thành viên thả cảm xúc (emoji ✅) vào bài viết cấu hình trong kênh `#roles`. 
* Tự động tước Rank khi người dùng gỡ bỏ cảm xúc.

### 🚀 4. Vinh Danh Boost Nitro
* Tự động phát hiện và gửi Embed chúc mừng đầy màu sắc, tag tên đại gia khi máy chủ nhận được một cú Boost Nitro mới.

### ⚡ 5. Các Tối Ưu Hệ Thống Mới
* **Tự động rời phòng Voice (Auto-leave when empty)**: Bot sẽ tự động ngắt kết nối khỏi kênh thoại và làm sạch hàng chờ nhạc nếu tất cả người dùng thật (human) rời khỏi kênh thoại để bảo vệ tài nguyên mạng và CPU.
* **Chống nghẽn hàng đợi (Error & Stuck handling)**: Khi bài hát gặp lỗi tải (Exception) hoặc bị kẹt mạng (Stuck) quá lâu, bot sẽ tự động thông báo lỗi vào chat và bỏ qua (skip) bài đó để đảm bảo hàng đợi không bao giờ bị treo.

---

## 💻 Danh Sách Lệnh Slash (Slash Commands)

Để gọi bot, bạn hãy sử dụng các lệnh Slash được đăng ký chính thức với Discord:

| Lệnh | Mô tả | Cách sử dụng | Quyền hạn |
| :--- | :--- | :--- | :--- |
| `/play` | Tìm kiếm bài hát và thêm vào hàng đợi | `/play query: <Link YouTube hoặc tên bài hát>` | Thành viên chung phòng thoại với Bot |
| `/leave` | Đuổi Bot rời khỏi phòng thoại | `/leave` | Thành viên chung phòng thoại với Bot |

> [!IMPORTANT]
> Toàn bộ các lệnh gọi Bot nghe nhạc bắt buộc phải được thực hiện trong đúng kênh văn bản chuyên dụng được cấu hình ở tệp cấu hình `.env` (`MUSIC_CHANNEL_ID`). Nếu gõ ở kênh khác, Bot sẽ phản hồi ẩn nhắc nhở bạn quay lại đúng phòng.

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
MUSIC_CHANNEL_ID=YOUR_MUSIC_CHANNEL_ID
CREATE_VOICE_CHANNEL_ID=YOUR_CREATE_VOICE_CHANNEL_ID

# ID tin nhắn để kiểm duyệt (Thả emoji ✅ để nhận role)
REACTION_MESSAGE_ID=YOUR_REACTION_MESSAGE_ID

# ID của vai trò (Role) sẽ cấp khi thả reaction (Ví dụ: Gà Con)
CHICKEN_ROLE_ID=YOUR_CHICKEN_ROLE_ID

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

Dự án đã được tích hợp sẵn cấu hình Docker hóa giúp việc chạy trên máy chủ VPS trở nên vô cùng nhanh chóng.

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

Quy trình triển khai tự động lên VPS được định nghĩa tại tệp [.github/workflows/deploy-bot.yml](file:///.github/workflows/deploy-bot.yml). Mỗi khi bạn đẩy mã nguồn mới lên nhánh `main`, hệ thống sẽ tự động thực hiện:

1. Thiết lập môi trường Java 17 và biên dịch mã nguồn.
2. Đóng gói ứng dụng Java thành `app.jar`.
3. Kết nối an toàn đến mạng riêng ảo **Tailscale** của bạn.
4. Tải tệp `app.jar` và `Dockerfile` lên máy chủ VPS qua kết nối SCP an toàn.
5. Truy cập SSH vào VPS, tự động khởi tạo tệp cấu hình bảo mật `.env` từ kho lưu trữ **GitHub Secrets**.
6. Build lại Docker Image và chạy container trên VPS với cơ chế tự khởi động lại (`unless-stopped`).

### Các biến GitHub Secrets cần cấu hình trên GitHub:
Để CI/CD hoạt động, bạn cần cấu hình các biến sau trong phần **Settings > Secrets and variables > Actions** của Repository:
* `TAILSCALE_AUTHKEY`: Khóa xác thực mạng Tailscale.
* `VPS_HOST`, `VPS_USERNAME`, `VPS_PASSWORD`, `VPS_PORT`: Thông tin kết nối SSH tới máy chủ VPS.
* `DISCORD_TOKEN`, `GUILD_ID`, `WELCOME_CHANNEL_ID`, `ROLES_CHANNEL_ID`, `MUSIC_CHANNEL_ID`, `CREATE_VOICE_CHANNEL_ID`, `INVITE_LINK`, `REACTION_MESSAGE_ID`, `CHICKEN_ROLE_ID`: Các biến môi trường để sinh tệp `.env` trên VPS.
