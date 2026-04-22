package com.chung.bot;

import com.chung.bot.commands.SlashCommandHandler;
import com.chung.bot.config.Config;
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

public class BotMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(BotMain.class);

    public static void main(String[] args) {
        String token = Config.get("DISCORD_TOKEN");

        if (token == null || token.isEmpty()) {
            LOGGER.error("Không tìm thấy DISCORD_TOKEN trong file .env!");
            return;
        }

        try {
            JDABuilder builder = JDABuilder.createDefault(token);

            // Cấu hình Intent cho chức năng
            builder.enableIntents(
                    GatewayIntent.GUILD_MEMBERS,
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.MESSAGE_CONTENT,
                    GatewayIntent.GUILD_MESSAGE_REACTIONS,
                    GatewayIntent.GUILD_VOICE_STATES
            );

            // Cài đặt trạng thái hiển thị của bot
            builder.setStatus(OnlineStatus.ONLINE);
            builder.setActivity(Activity.playing("Lùa Gà"));

            // TODO: Thêm EventListener tại đây
            builder.addEventListeners(
                    new WelcomeHandler(),
                    new RoleReactionHandler(),
                    new SlashCommandHandler(),
                    new MusicControlHandler(),
                    new VoiceStateListener()
            );

            DaveFactory daveFactory = new NativeDaveFactory();

            builder.setAudioModuleConfig(
                    new AudioModuleConfig()
                            .withDaveSessionFactory(new LDJDADaveSessionFactory(daveFactory))
            );

            net.dv8tion.jda.api.JDA jda = builder.build();
            // BẮT BUỘC CHỜ JDA KẾT NỐI XONG MỚI CẬP NHẬT LỆNH
            jda.awaitReady();

            // XÓA TOÀN BỘ GLOBAL COMMAND CŨ TRÊN TOÀN DISCORD
            jda.updateCommands().queue();

            String guildId = Config.get("GUILD_ID");
            net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(guildId);

            if (guild != null) {
                // XÓA VÀ CẬP NHẬT LẠI LỆNH CHO SERVER ĐỂ GHI ĐÈ LỆNH SERVER CŨ (Cập nhật ngay lập tức)
                guild.updateCommands().addCommands(
                        // Lệnh /play kèm gợi ý (Option) bắt buộc nhập
                        Commands.slash("play", "Yêu cầu bot phát một bài nhạc")
                                .addOption(OptionType.STRING, "query", "Nhập link YouTube hoặc tên bài hát", true),

                        // Lệnh /leave
                        Commands.slash("leave", "Yêu cầu bot rời khỏi kênh thoại và dọn dẹp")
                ).queue();
                LOGGER.info("Đã cập nhật bộ lệnh Slash cho server {}", guild.getName());
            }

            LOGGER.info("Bot đã khởi động thành công!");

        } catch (Exception e) {
            LOGGER.error("Lỗi khi khởi động bot: ", e);
        }
    }
}
