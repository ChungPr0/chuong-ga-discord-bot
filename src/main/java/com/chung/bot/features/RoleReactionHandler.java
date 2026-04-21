package com.chung.bot.features;

import com.chung.bot.config.Config;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoleReactionHandler extends ListenerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoleReactionHandler.class);
    private static final String TARGET_EMOJI = "✅";

    // Khi người dùng THÊM Reaction
    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        if (event.getUser() == null || event.getUser().isBot()) return;

        String targetMessageId = Config.get("REACTION_MESSAGE_ID");

        // Kiểm tra đúng tin nhắn và đúng Emoji
        if (targetMessageId != null && event.getMessageId().equals(targetMessageId)) {
            if (event.getEmoji().getName().equals(TARGET_EMOJI)) {

                String roleId = Config.get("CHICKEN_ROLE_ID");
                Role role = event.getGuild().getRoleById(roleId);

                if (role != null) {
                    event.getGuild().addRoleToMember(event.getUser(), role).queue();
                    LOGGER.info("Đã cấp rank Gà Con cho {}.", event.getUser().getEffectiveName());
                } else {
                    LOGGER.error("Không tìm thấy Role ID: {}", roleId);
                }
            }
        }
    }

    // Khi người dùng GỠ Reaction
    @Override
    public void onMessageReactionRemove(@NotNull MessageReactionRemoveEvent event) {
        String userId = event.getUserId();
        if (userId.equals(event.getJDA().getSelfUser().getId())) return;

        String targetMessageId = Config.get("REACTION_MESSAGE_ID");

        if (targetMessageId != null && event.getMessageId().equals(targetMessageId)) {
            if (event.getEmoji().getName().equals(TARGET_EMOJI)) {

                String roleId = Config.get("CHICKEN_ROLE_ID");
                Role role = event.getGuild().getRoleById(roleId);

                if (role != null) {
                    event.getGuild().removeRoleFromMember(net.dv8tion.jda.api.entities.UserSnowflake.fromId(userId), role).queue();

                    LOGGER.info("Đã tước rank Gà của User ID: {}.", userId);
                }
            }
        }
    }
}