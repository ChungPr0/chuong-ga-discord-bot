package com.chung.bot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlashCommandHandler extends ListenerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SlashCommandHandler.class);

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        LOGGER.info("Nhận slash command: {} từ user: {}", event.getName(), event.getUser().getEffectiveName());
        event.reply("Lệnh không được hỗ trợ!").setEphemeral(true).queue();
    }
}
