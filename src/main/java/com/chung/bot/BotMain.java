package com.chung.bot;

import com.chung.bot.commands.SlashCommandHandler;
import com.chung.bot.config.Config;
import com.chung.bot.features.JoinToCreateHandler;
import com.chung.bot.features.RoleReactionHandler;
import com.chung.bot.features.SystemMonitorHandler;
import com.chung.bot.features.WelcomeHandler;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BotMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(BotMain.class);
    public static boolean isShuttingDown = false;
    private static SystemMonitorHandler systemMonitorHandler;

    public static void main(String[] args) {
        String token = Config.get("DISCORD_TOKEN");

        if (token == null || token.isEmpty()) {
            LOGGER.error("Không tìm thấy DISCORD_TOKEN trong file .env!");
            return;
        }

        try {
            com.chung.bot.database.DatabaseManager.getInstance();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                com.chung.bot.BotMain.isShuttingDown = true;
                if (systemMonitorHandler != null) {
                    systemMonitorHandler.stop();
                }
                LOGGER.info("Đang đóng kết nối SQLite Database...");
                com.chung.bot.database.DatabaseManager.getInstance().close();
            }));

            JDABuilder builder = JDABuilder.createDefault(token);

            builder.enableIntents(
                    GatewayIntent.GUILD_MEMBERS,
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.MESSAGE_CONTENT,
                    GatewayIntent.GUILD_MESSAGE_REACTIONS,
                    GatewayIntent.GUILD_VOICE_STATES);

            builder.setStatus(OnlineStatus.ONLINE);
            builder.setActivity(Activity.playing("Bố Mày Đang Lùa Gà"));
            
            builder.addEventListeners(
                    new WelcomeHandler(),
                    new RoleReactionHandler(),
                    new SlashCommandHandler(),
                    new JoinToCreateHandler());

            net.dv8tion.jda.api.JDA jda = builder.build();
            jda.awaitReady();

            systemMonitorHandler = new SystemMonitorHandler(jda);
            systemMonitorHandler.start();

            jda.updateCommands().queue();

            String guildId = Config.get("GUILD_ID");
            net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(guildId);

            if (guild != null) {
                guild.updateCommands().queue();
                LOGGER.info("Đã dọn dẹp các lệnh Slash cho server {}", guild.getName());
            }

            LOGGER.info("Bot đã khởi động thành công!");

        } catch (Exception e) {
            LOGGER.error("Lỗi khi khởi động bot: ", e);
        }
    }
}
