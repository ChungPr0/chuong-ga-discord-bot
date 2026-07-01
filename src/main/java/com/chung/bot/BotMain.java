package com.chung.bot;

import com.chung.bot.commands.SlashCommandHandler;
import com.chung.bot.config.Config;
import com.chung.bot.features.JoinToCreateHandler;
import com.chung.bot.features.MusicControlHandler;
import com.chung.bot.features.RoleReactionHandler;
import com.chung.bot.features.VoiceStateListener;
import com.chung.bot.features.WelcomeHandler;
import moe.kyokobot.libdave.DaveFactory;
import moe.kyokobot.libdave.NativeDaveFactory;
import moe.kyokobot.libdave.jda.LDJDADaveSessionFactory;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.chung.bot.log.BotLogger;

public class BotMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(BotMain.class);

    public static void main(String[] args) {
        String token = Config.get("DISCORD_TOKEN");

        if (token == null || token.isEmpty()) {
            LOGGER.error("Không tìm thấy DISCORD_TOKEN trong file .env!");
            return;
        }

        try {
            // Khởi tạo Database SQLite
            com.chung.bot.database.DatabaseManager.getInstance();

            // Shutdown hook để đóng database khi ứng dụng tắt
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    LOGGER.info("Đang lưu hàng đợi nhạc trước khi shutdown...");
                    java.util.List<String> urls = com.chung.bot.music.PlayerManager.getInstance().getEntireQueueUrls();
                    com.chung.bot.database.DatabaseManager.getInstance().saveQueue(urls);
                } catch (Exception e) {
                    LOGGER.error("Lỗi khi lưu hàng đợi nhạc trước khi shutdown: ", e);
                }
                LOGGER.info("Đang đóng kết nối SQLite Database...");
                com.chung.bot.database.DatabaseManager.getInstance().close();
            }));

            JDABuilder builder = JDABuilder.createDefault(token);

            // Cấu hình Intent cho chức năng
            builder.enableIntents(
                    GatewayIntent.GUILD_MEMBERS,
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.MESSAGE_CONTENT,
                    GatewayIntent.GUILD_MESSAGE_REACTIONS,
                    GatewayIntent.GUILD_VOICE_STATES);

            // Cài đặt trạng thái hiển thị của bot
            builder.setStatus(OnlineStatus.ONLINE);
            builder.setActivity(Activity.playing("Bố Mày Đang Lùa Gà"));
            
            builder.addEventListeners(
                    new WelcomeHandler(),
                    new RoleReactionHandler(),
                    new SlashCommandHandler(),
                    new MusicControlHandler(),
                    new VoiceStateListener(),
                    new JoinToCreateHandler());

            DaveFactory daveFactory = new NativeDaveFactory();

            builder.setAudioModuleConfig(
                    new AudioModuleConfig()
                            .withDaveSessionFactory(new LDJDADaveSessionFactory(daveFactory)));

            net.dv8tion.jda.api.JDA jda = builder.build();
            // BẮT BUỘC CHỜ JDA KẾT NỐI XONG MỚI CẬP NHẬT LỆNH
            jda.awaitReady();

            // Khởi tạo logger Discord
            BotLogger.init(jda);

            // Đăng ký DiscordAppender programmatically bằng Java để tránh bị ghi đè file logback.xml trong file jar
            try {
                org.slf4j.ILoggerFactory factory = org.slf4j.LoggerFactory.getILoggerFactory();
                if (factory instanceof ch.qos.logback.classic.LoggerContext) {
                    ch.qos.logback.classic.LoggerContext context = (ch.qos.logback.classic.LoggerContext) factory;
                    ch.qos.logback.classic.Logger rootLogger = context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);

                    com.chung.bot.log.DiscordAppender discordAppender = new com.chung.bot.log.DiscordAppender();
                    discordAppender.setContext(context);
                    discordAppender.setName("DISCORD_APPENDER_PROGRAMMATIC");
                    discordAppender.start();

                    rootLogger.addAppender(discordAppender);

                    // Đặt mức log DEBUG cho youtube-source để thu được log OAuth2
                    context.getLogger("dev.lavalink.youtube").setLevel(ch.qos.logback.classic.Level.DEBUG);
                    LOGGER.info("Đã đăng ký DiscordAppender thành công thông qua mã Java.");
                } else {
                    LOGGER.warn("LoggerFactory không phải là Logback LoggerContext. Bỏ qua đăng ký DiscordAppender programmatically.");
                }
            } catch (Exception e) {
                LOGGER.error("Lỗi khi đăng ký DiscordAppender programmatically: ", e);
            }

            // XÓA TOÀN BỘ GLOBAL COMMAND CŨ TRÊN TOÀN DISCORD
            jda.updateCommands().queue();

            String guildId = Config.get("GUILD_ID");
            net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(guildId);

            if (guild != null) {
                // Khôi phục hàng đợi nhạc từ SQLite
                try {
                    java.util.List<String> savedQueue = com.chung.bot.database.DatabaseManager.getInstance().getSavedQueue();
                    if (!savedQueue.isEmpty()) {
                        com.chung.bot.music.PlayerManager.getInstance().restoreQueue(guild, savedQueue);
                        LOGGER.info("Đã khôi phục {} bài hát vào hàng đợi cho Guild: {}", savedQueue.size(), guild.getName());
                    }
                } catch (Exception e) {
                    LOGGER.error("Lỗi khi khôi phục hàng đợi nhạc từ database: ", e);
                }
                // XÓA VÀ CẬP NHẬT LẠI LỆNH CHO SERVER ĐỂ GHI ĐÈ LỆNH SERVER CŨ (Cập nhật ngay
                // lập tức)
                guild.updateCommands().addCommands(
                        // Lệnh /play kèm gợi ý (Option) bắt buộc nhập
                        Commands.slash("play", "Yêu cầu bot phát một bài nhạc")
                                .addOption(OptionType.STRING, "query", "Nhập link YouTube hoặc tên bài hát", true),

                        // Lệnh /leave
                        Commands.slash("leave", "Yêu cầu bot rời khỏi kênh thoại và dọn dẹp"),

                        // Lệnh /login
                        Commands.slash("login", "Khởi tạo luồng đăng nhập YouTube OAuth2 (chỉ dùng trong kênh botlog)")).queue();
                LOGGER.info("Đã cập nhật bộ lệnh Slash cho server {}", guild.getName());
            }

            LOGGER.info("Bot đã khởi động thành công!");

        } catch (Exception e) {
            LOGGER.error("Lỗi khi khởi động bot: ", e);
        }
    }
}
