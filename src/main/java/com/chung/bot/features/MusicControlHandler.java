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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MusicControlHandler extends ListenerAdapter {

    // ==========================================
    // PHẦN 1: TẠO VÀ CẬP NHẬT CONTROL PANEL CHÍNH
    // ==========================================

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

    private static void performSend(TrackScheduler scheduler, MessageChannel channel, AudioTrack track) {
        boolean isPaused = scheduler.player.isPaused();
        boolean isLooping = scheduler.isRepeating;
        boolean noHistory = scheduler.history.isEmpty();
        boolean noNext = scheduler.queue.isEmpty();

        Button backBtn = noHistory ? Button.secondary("music_back", "◁").asDisabled() : Button.secondary("music_back", "◁");
        Button pauseBtn = isPaused ? Button.danger("music_pause", "▶") : Button.secondary("music_pause", "❚❚");
        Button skipBtn = noNext ? Button.secondary("music_skip", "▷").asDisabled() : Button.secondary("music_skip", "▷");
        Button loopBtn = isLooping ? Button.primary("music_loop", "↻") : Button.secondary("music_loop", "↻");
        Button listBtn = Button.secondary("music_queue_open", "☰");
        Button leaveBtn = Button.danger("music_leave", "▢");

        long duration = track.getDuration() / 1000;
        String timeStr = String.format("%d:%02d", duration / 60, duration % 60);

        int currentIndex = scheduler.history.size() + 1;
        int totalTracks = currentIndex + scheduler.queue.size();
        String titleText = String.format(":musical_note: Đang phát bài hát | Bài số: %d/%d", currentIndex, totalTracks);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(titleText)
                .setDescription("**" + track.getInfo().title + "**\n`" + timeStr + "`")
                .setThumbnail("https://cdn.pixabay.com/photo/2017/04/19/10/24/vinyl-2241789_1280.png")
                .setColor(Color.decode("#2ecc71"));

        ActionRow row1 = ActionRow.of(backBtn, pauseBtn, skipBtn, loopBtn, listBtn);
        ActionRow row2 = ActionRow.of(leaveBtn);

        channel.sendMessageEmbeds(embed.build())
                .setComponents(row1, row2)
                .queue(message -> scheduler.setLastMessageId(message.getIdLong()));
    }

    private void updateButtonUI(ButtonInteractionEvent event, TrackScheduler scheduler) {
        boolean isPaused = scheduler.player.isPaused();
        boolean isLooping = scheduler.isRepeating;
        boolean noHistory = scheduler.history.isEmpty();
        boolean noNext = scheduler.queue.isEmpty();

        Button backBtn = noHistory ? Button.secondary("music_back", "◁").asDisabled() : Button.secondary("music_back", "◁");
        Button pauseBtn = isPaused ? Button.danger("music_pause", "▶") : Button.secondary("music_pause", "❚❚");
        Button skipBtn = noNext ? Button.secondary("music_skip", "▷").asDisabled() : Button.secondary("music_skip", "▷");
        Button loopBtn = isLooping ? Button.primary("music_loop", "↻") : Button.secondary("music_loop", "↻");
        Button listBtn = Button.secondary("music_queue_open", "☰");
        Button leaveBtn = Button.danger("music_leave", "▢");

        event.editComponents(ActionRow.of(backBtn, pauseBtn, skipBtn, loopBtn, listBtn), ActionRow.of(leaveBtn)).queue();
    }


    // ==========================================
    // PHẦN 2: XỬ LÝ SỰ KIỆN NÚT BẤM
    // ==========================================

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        if (!buttonId.startsWith("music_")) return;

        net.dv8tion.jda.api.entities.GuildVoiceState memberVoiceState = Objects.requireNonNull(event.getMember()).getVoiceState();
        net.dv8tion.jda.api.entities.GuildVoiceState botVoiceState = Objects.requireNonNull(event.getGuild()).getSelfMember().getVoiceState();

        assert memberVoiceState != null;
        if (!memberVoiceState.inAudioChannel() || !Objects.requireNonNull(botVoiceState).inAudioChannel() || !Objects.equals(memberVoiceState.getChannel(), botVoiceState.getChannel())) {
            event.reply("Bạn phải ở chung phòng thoại với bot thì mới được giành mic nhé!").setEphemeral(true).queue();
            return;
        }

        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(Objects.requireNonNull(event.getGuild()));
        TrackScheduler scheduler = musicManager.scheduler;

        switch (buttonId) {
            case "music_pause":
                scheduler.player.setPaused(!scheduler.player.isPaused());
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

            case "music_queue_open":
                int playingPage = (scheduler.history.size() / 10) + 1;
                showQueueWindow(event, scheduler, playingPage, true);
                break;

            case "music_queue_prev":
                int prevPage = getPageFromFooter(event) - 1;
                showQueueWindow(event, scheduler, prevPage, false);
                break;

            case "music_queue_next":
                int nextPage = getPageFromFooter(event) + 1;
                showQueueWindow(event, scheduler, nextPage, false);
                break;
        }
    }


    // ==========================================
    // PHẦN 3: LOGIC GIAO DIỆN XEM PLAYLIST
    // ==========================================

    private void showQueueWindow(ButtonInteractionEvent event, TrackScheduler scheduler, int page, boolean isNewReply) {
        // TẠO DANH SÁCH TỔNG HỢP (Lịch sử + Đang phát + Hàng đợi)

        List<AudioTrack> historyList = new ArrayList<>(scheduler.history);
        List<AudioTrack> fullList = new ArrayList<>(historyList);

        //  Lấy bài đang phát (Lưu lại index để đánh dấu)
        int playingIndex = fullList.size();
        AudioTrack currentTrack = scheduler.player.getPlayingTrack();
        if (currentTrack != null) {
            fullList.add(currentTrack);
        }

        // Lấy hàng đợi
        fullList.addAll(scheduler.queue);

        if (fullList.isEmpty()) {
            if (isNewReply) {
                event.reply("Danh sách phát hiện đang trống!").setEphemeral(true).queue();
            } else {
                event.editMessage("Danh sách phát hiện đang trống!").setComponents().queue();
            }
            return;
        }

        // Tính toán phân trang
        int totalPages = (int) Math.ceil(fullList.size() / 10.0);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Danh sách phát (" + fullList.size() + " bài)")
                .setColor(Color.decode("#9b59b6"));

        int start = (page - 1) * 10;
        int end = Math.min(start + 10, fullList.size());

        for (int i = start; i < end; i++) {
            AudioTrack track = fullList.get(i);
            long duration = track.getDuration() / 1000;
            String timeStr = String.format("%d:%02d", duration / 60, duration % 60);

            String prefix;
            if (i < playingIndex) {
                prefix = "✅ ";
            } else if (i == playingIndex && currentTrack != null) {
                prefix = "▶️ ";
            } else {
                prefix = "⏳ ";
            }

            embed.addField(String.format("%sVị trí: `%d` | Thời lượng: `%s`", prefix, i + 1, timeStr), track.getInfo().title, false);
        }

        embed.setFooter("Trang " + page + "/" + totalPages);

        Button prevBtn = page == 1
                ? Button.secondary("music_queue_prev", "◄◄").asDisabled()
                : Button.secondary("music_queue_prev", "◄◄");
        Button nextBtn = page == totalPages
                ? Button.secondary("music_queue_next", "►►").asDisabled()
                : Button.secondary("music_queue_next", "►►");

        if (isNewReply) {
            event.replyEmbeds(embed.build())
                    .setComponents(ActionRow.of(prevBtn, nextBtn))
                    .setEphemeral(true)
                    .queue();
        } else {
            event.editMessageEmbeds(embed.build())
                    .setComponents(ActionRow.of(prevBtn, nextBtn))
                    .queue();
        }
    }

    private int getPageFromFooter(ButtonInteractionEvent event) {
        try {
            String footerText = Objects.requireNonNull(event.getMessage().getEmbeds().get(0).getFooter()).getText();
            assert footerText != null;
            String pageString = footerText.replace("Trang ", "").split("/")[0];
            return Integer.parseInt(pageString);
        } catch (Exception e) {
            return 1;
        }
    }
}