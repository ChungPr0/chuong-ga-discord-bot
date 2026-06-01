package com.chung.bot.features;

import com.chung.bot.music.GuildMusicManager;
import com.chung.bot.music.PlayerManager;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class VoiceStateListener extends ListenerAdapter {

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        // Kiểm tra xem bot có rời khỏi phòng voice hoàn toàn không
        if (event.getMember().getUser().equals(event.getJDA().getSelfUser())) {
            // Nếu bot bị rời khỏi kênh (after/joined là null)
            if (event.getChannelLeft() != null && event.getChannelJoined() == null) {
                GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
                musicManager.scheduler.stopAndCleanup();
                return;
            }
        }

        // Tự động rời phòng khi không còn người dùng thực trong kênh thoại của bot
        GuildVoiceState botVoiceState = event.getGuild().getSelfMember().getVoiceState();
        if (botVoiceState != null && botVoiceState.inAudioChannel()) {
            AudioChannel botChannel = botVoiceState.getChannel();
            if (botChannel != null) {
                // Đếm số lượng thành viên thực sự (không phải bot) trong phòng voice
                long humanCount = botChannel.getMembers().stream()
                        .filter(member -> !member.getUser().isBot())
                        .count();

                if (humanCount == 0) {
                    // Ngắt kết nối voice và dọn dẹp hàng chờ nhạc
                    event.getGuild().getAudioManager().closeAudioConnection();
                    GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
                    musicManager.scheduler.stopAndCleanup();
                }
            }
        }
    }
}