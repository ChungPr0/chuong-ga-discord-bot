package com.chung.bot.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.chung.bot.music.PlayerManager;
import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiscordAppender extends AppenderBase<ILoggingEvent> {
    
    private static final Pattern DEVICE_CODE_PATTERN = Pattern.compile("[A-Z0-9]{4}-[A-Z0-9]{4}");
    private static final Pattern REFRESH_TOKEN_PATTERN = Pattern.compile("1//[a-zA-Z0-9_\\-]+");

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (eventObject == null) return;

        String loggerName = eventObject.getLoggerName();
        String message = eventObject.getFormattedMessage();

        // Chỉ lắng nghe log của YoutubeOauth2Handler
        if (loggerName != null && loggerName.contains("YoutubeOauth2Handler")) {
            
            // 1. Kiểm tra nếu là log yêu cầu đăng nhập (Chứa Link và Device Code)
            if (message.contains("google.com/device") || message.contains("enter the code") || message.contains("Device Code")) {
                Matcher matcher = DEVICE_CODE_PATTERN.matcher(message);
                if (matcher.find()) {
                    String deviceCode = matcher.group();
                    // Kích hoạt CompletableFuture để trả code về lệnh /login
                    if (PlayerManager.deviceCodeFuture != null && !PlayerManager.deviceCodeFuture.isDone()) {
                        PlayerManager.deviceCodeFuture.complete(deviceCode);
                    }
                }
                
                // Gửi thông tin trực tiếp lên kênh Discord log
                BotLogger.sendDirectEmbed(
                    "🔑 YÊU CẦU ĐĂNG NHẬP YOUTUBE OAUTH",
                    "Vui lòng thực hiện đăng nhập để kích hoạt tính năng phát nhạc:\n\n" + message,
                    new Color(241, 196, 15) // Sunflower Yellow
                );
            } 
            // 2. Kiểm tra nếu đăng nhập thành công và trả về Refresh Token mới
            else if (message.contains("Token retrieved successfully") || message.contains("refresh token")) {
                Matcher matcher = REFRESH_TOKEN_PATTERN.matcher(message);
                String extractedToken = null;
                if (matcher.find()) {
                    extractedToken = matcher.group();
                }

                if (extractedToken != null) {
                    // Tự động ghi vào file .env và nạp cấu hình mới mà không cần reboot bot
                    PlayerManager.getInstance().updateYoutubeToken(extractedToken);
                    
                    // Gửi tin nhắn chứa token và xác nhận thành công lên Discord log
                    BotLogger.sendDirectEmbed(
                        "ĐĂNG NHẬP YOUTUBE THÀNH CÔNG",
                        "Đã tự động bắt được Refresh Token mới và lưu vào file `.env` thành công!\n\n" +
                        "**Token mới:**\n`" + extractedToken + "`",
                        new Color(46, 204, 113) // Emerald Green
                    );
                } else {
                    // Nếu không regex được cụ thể token, chỉ in log báo thành công
                    BotLogger.sendDirectEmbed(
                        "ĐĂNG NHẬP YOUTUBE THÀNH CÔNG",
                        message,
                        new Color(46, 204, 113)
                    );
                }
            }
        }
    }
}
