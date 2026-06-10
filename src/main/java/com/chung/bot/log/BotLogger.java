package com.chung.bot.log;

import com.chung.bot.config.Config;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.OffsetDateTime;

public class BotLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(BotLogger.class);
    
    // Custom modern premium colors
    private static final Color COLOR_INFO = new Color(46, 204, 113);   // Soft Emerald Green
    private static final Color COLOR_WARN = new Color(241, 196, 15);   // Soft Sunflower Yellow
    private static final Color COLOR_ERROR = new Color(231, 76, 60);   // Soft Alizarin Red

    private static JDA jda;

    /**
     * Khởi tạo BotLogger với instance JDA
     * @param jdaInstance JDA instance sau khi đã awaitReady
     */
    public static void init(JDA jdaInstance) {
        jda = jdaInstance;
        LOGGER.info("BotLogger đã được khởi tạo thành công với JDA instance.");
    }

    /**
     * Log thông tin hoạt động hoặc lệnh của người dùng (Xanh lá cây)
     */
    public static void info(String title, String message) {
        sendEmbed(title, message, COLOR_INFO, null);
    }

    /**
     * Log cảnh báo hệ thống (Vàng)
     */
    public static void warn(String title, String message) {
        sendEmbed(title, message, COLOR_WARN, null);
    }

    /**
     * Log lỗi hệ thống kèm Stack Trace 5 dòng đầu (Đỏ rực)
     */
    public static void error(String title, String message, Throwable throwable) {
        sendEmbed(title, message, COLOR_ERROR, throwable);
    }

    private static void sendEmbed(String title, String message, Color color, Throwable throwable) {
        // Log ra console cục bộ trước để luôn lưu vết offline
        if (color == COLOR_ERROR) {
            LOGGER.error("LOG ERROR: {} - {}", title, message, throwable);
        } else if (color == COLOR_WARN) {
            LOGGER.warn("LOG WARN: {} - {}", title, message);
        } else {
            LOGGER.info("LOG INFO: {} - {}", title, message);
        }

        if (jda == null) {
            LOGGER.warn("BotLogger chưa được khởi tạo JDA! Bỏ qua gửi log qua Discord.");
            return;
        }

        String channelId = Config.get("BOT_LOG_CHANNEL_ID");
        if (channelId == null || channelId.isEmpty()) {
            LOGGER.error("Chưa cấu hình biến BOT_LOG_CHANNEL_ID trong file cấu hình/môi trường!");
            return;
        }

        try {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) {
                LOGGER.error("Không tìm thấy kênh Discord Log với ID: {}", channelId);
                return;
            }

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle(title);
            embed.setColor(color);
            embed.setTimestamp(OffsetDateTime.now());

            if (color == COLOR_ERROR) {
                StringBuilder sb = new StringBuilder();
                if (message != null && !message.isEmpty()) {
                    sb.append(message).append("\n\n");
                }
                if (throwable != null) {
                    sb.append("**Chi tiết ngoại lệ:** `").append(throwable.toString()).append("`\n");
                    sb.append("**Message:** ").append(throwable.getMessage()).append("\n\n");
                    sb.append("**Stack Trace (5 dòng đầu):**\n```java\n");
                    
                    StackTraceElement[] trace = throwable.getStackTrace();
                    int limit = Math.min(trace.length, 5);
                    for (int i = 0; i < limit; i++) {
                        sb.append("at ").append(trace[i].toString()).append("\n");
                    }
                    if (trace.length > 5) {
                        sb.append("... ").append(trace.length - 5).append(" dòng khác\n");
                    }
                    sb.append("```");
                } else {
                    sb.append("Không cung cấp thông tin ngoại lệ.");
                }
                embed.setDescription(sb.toString());
            } else {
                embed.setDescription(message);
            }

            channel.sendMessageEmbeds(embed.build()).queue(
                    success -> {},
                    error -> LOGGER.error("Lỗi khi queue tin nhắn log lên Discord: {}", error.getMessage())
            );
        } catch (Exception e) {
            LOGGER.error("Lỗi ngoại lệ trong quá trình build/gửi log Discord: ", e);
        }
    }
}
