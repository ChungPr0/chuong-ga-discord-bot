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
                
                // ĐÃ BỎ: Không gửi cảnh báo tự động màu vàng lên Discord nữa để tránh spam log
            } 
            // 2. Kiểm tra nếu đăng nhập thành công và trả về Refresh Token mới
            else if (message.contains("Token retrieved successfully") || message.contains("refresh token")) {
                Matcher matcher = REFRESH_TOKEN_PATTERN.matcher(message);
                String extractedToken = null;
                if (matcher.find()) {
                    extractedToken = matcher.group();
                }

                if (extractedToken != null) {
                    // Tự động ghi vào file .env và nạp cấu hình mới vào RAM
                    PlayerManager.getInstance().updateYoutubeToken(extractedToken);
                    
                    // Chỉ gửi thông báo thành công lên Discord khi người dùng chủ động gọi lệnh /login
                    if (PlayerManager.isUserTriggeredLogin) {
                        BotLogger.sendDirectEmbed(
                            "ĐĂNG NHẬP YOUTUBE THÀNH CÔNG",
                            "Đã tự động bắt được Refresh Token mới và lưu vào file `.env` thành công!\n\n" +
                            "**Token mới:**\n`" + extractedToken + "`",
                            new Color(46, 204, 113) // Emerald Green
                        );
                        PlayerManager.isUserTriggeredLogin = false; // Reset flag
                    }
                }
            }
        }
    }
}
