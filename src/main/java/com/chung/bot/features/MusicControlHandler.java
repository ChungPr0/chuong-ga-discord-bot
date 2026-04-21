package com.chung.bot.features;

import com.chung.bot.music.GuildMusicManager;
import com.chung.bot.music.PlayerManager;
import com.chung.bot.music.TrackScheduler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.awt.Color;
import java.util.Objects;

public class MusicControlHandler extends ListenerAdapter {

    // Ép xóa xong mới gửi
    public static void sendNewControlPanel(TrackScheduler scheduler, MessageChannel channel, AudioTrack track) {
        if (scheduler.getLastMessageId() != 0L) {
            channel.deleteMessageById(scheduler.getLastMessageId()).queue(
                    success -> {
                        scheduler.setLastMessageId(0L);
                        performSend(scheduler, channel, track);
                    },
                    error -> {
                        scheduler.setLastMessageId(0L);
                        performSend(scheduler, channel, track);
                    }
            );
        } else {
            performSend(scheduler, channel, track);
        }
    }

    // HÀM PHỤ: Chỉ đảm nhiệm việc in giao diện
    private static void performSend(TrackScheduler scheduler, MessageChannel channel, AudioTrack track) {
        boolean isPaused = scheduler.player.isPaused();
        boolean isLooping = scheduler.isRepeating;
        boolean noHistory = scheduler.history.isEmpty();
        boolean noNext = scheduler.queue.isEmpty();

        Button pauseBtn = isPaused ? Button.danger("music_pause", "▶") : Button.secondary("music_pause", "❚❚");
        Button backBtn = noHistory
                ? Button.secondary("music_back", "◁").asDisabled()
                : Button.secondary("music_back", "◁");

        Button skipBtn = noNext
                ? Button.secondary("music_skip", "▷").asDisabled()
                : Button.secondary("music_skip", "▷");
        Button loopBtn = isLooping ? Button.primary("music_loop", "↻") : Button.secondary("music_loop", "↻");
        Button leaveBtn = Button.secondary("music_leave", "▢");

        long duration = track.getDuration() / 1000;
        String timeStr = String.format("%d:%02d", duration / 60, duration % 60);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(":musical_note: Đang phát bài hát")
                .setDescription("**" + track.getInfo().title + "**\n`" + timeStr + "`")
                .setThumbnail("https://cdn.pixabay.com/photo/2017/04/19/10/24/vinyl-2241789_1280.png")
                .setColor(Color.decode("#2ecc71"));

        channel.sendMessageEmbeds(embed.build())
                .setComponents(ActionRow.of(pauseBtn, backBtn, skipBtn, loopBtn, leaveBtn))
                .queue(message -> scheduler.setLastMessageId(message.getIdLong()));
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        if (!buttonId.startsWith("music_")) return;

        net.dv8tion.jda.api.entities.GuildVoiceState memberVoiceState = Objects.requireNonNull(event.getMember()).getVoiceState();
        net.dv8tion.jda.api.entities.GuildVoiceState botVoiceState = Objects.requireNonNull(event.getGuild()).getSelfMember().getVoiceState();

        // Kiểm tra: Người bấm có ở trong kênh thoại không? Có ở CÙNG kênh với bot không?
        assert memberVoiceState != null;
        if (!memberVoiceState.inAudioChannel() || !Objects.requireNonNull(botVoiceState).inAudioChannel() || !Objects.equals(memberVoiceState.getChannel(), botVoiceState.getChannel())) {
            event.reply("Bạn phải ở chung phòng thoại với bot thì mới được giành mic nhé!")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(Objects.requireNonNull(event.getGuild()));
        TrackScheduler scheduler = musicManager.scheduler;

        switch (buttonId) {
            case "music_pause":
                boolean isPaused = !scheduler.player.isPaused();
                scheduler.player.setPaused(isPaused);
                updateButtonUI(event, scheduler);
                break;

            case "music_loop":
                scheduler.isRepeating = !scheduler.isRepeating;
                updateButtonUI(event, scheduler);
                break;

            case "music_skip":
                scheduler.nextTrack();
                event.deferEdit().queue();
                break;

            case "music_back":
                scheduler.previousTrack();
                event.deferEdit().queue();
                break;

            case "music_leave":
                event.getGuild().getAudioManager().closeAudioConnection();
                scheduler.stopAndCleanup();
                event.getMessage().delete().queue();
                break;
        }
    }

    private void updateButtonUI(ButtonInteractionEvent event, TrackScheduler scheduler) {
        boolean isPaused = scheduler.player.isPaused();
        boolean isLooping = scheduler.isRepeating;
        boolean noHistory = scheduler.history.isEmpty();
        boolean noNext = scheduler.queue.isEmpty();

        Button pauseBtn = isPaused ? Button.danger("music_pause", "▶") : Button.secondary("music_pause", "❚❚");
        Button backBtn = noHistory ? Button.secondary("music_back", "◁").asDisabled() : Button.secondary("music_back", "◁");
        Button skipBtn = noNext ? Button.secondary("music_skip", "▷").asDisabled() : Button.secondary("music_skip", "▷");
        Button loopBtn = isLooping ? Button.primary("music_loop", "↻") : Button.secondary("music_loop", "↻");
        Button leaveBtn = Button.secondary("music_leave", "▢");

        event.editComponents(ActionRow.of(pauseBtn, backBtn, skipBtn, loopBtn, leaveBtn)).queue();
    }
}