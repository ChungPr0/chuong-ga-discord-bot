package com.chung.bot;

import com.chung.bot.config.Config;
import com.chung.bot.features.RoleReactionHandler;
import com.chung.bot.features.WelcomeHandler;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
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
                    GatewayIntent.GUILD_MESSAGE_REACTIONS
            );

            // Cài đặt trạng thái hiển thị của bot
            builder.setStatus(OnlineStatus.ONLINE);
            builder.setActivity(Activity.playing("Lùa Gà"));

            // TODO: Thêm EventListener tại đây
            builder.addEventListeners(
                    new WelcomeHandler(),
                    new RoleReactionHandler()
            );

            builder.build();
            LOGGER.info("Bot đã khởi động thành công!");

        } catch (Exception e) {
            LOGGER.error("Lỗi khi khởi động bot: ", e);
        }
    }
}