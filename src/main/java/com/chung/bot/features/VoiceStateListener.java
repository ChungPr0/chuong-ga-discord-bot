package com.chung.bot.features;

import com.chung.bot.music.GuildMusicManager;
import com.chung.bot.music.PlayerManager;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class VoiceStateListener extends ListenerAdapter {

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        // Kiểm tra xem thực thể thay đổi có phải là chính Bot không
        if (event.getMember().getUser().equals(event.getJDA().getSelfUser())) {
            // Nếu bot bị rời khỏi kênh (after là null)
            if (event.getChannelLeft() != null && event.getChannelJoined() == null) {
                GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());

                musicManager.scheduler.stopAndCleanup();
            }
        }
    }
}