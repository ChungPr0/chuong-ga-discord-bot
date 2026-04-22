package com.chung.bot.features;

import com.chung.bot.music.GuildMusicManager;
import com.chung.bot.music.PlayerManager;
import com.chung.bot.music.TrackScheduler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.awt.Color;
import java.util.List;
import java.util.Objects;

public class MusicControlHandler extends ListenerAdapter {

    // ==========================================
    // PHẦN 1: TẠO VÀ CẬP NHẬT CONTROL PANEL CHÍNH
    // ==========================================

    public static void sendNewControlPanel(TrackScheduler scheduler, MessageChannel channel, AudioTrack track) {
        long lastMessageId = scheduler.getLastMessageId();
        if (lastMessageId != 0L) {
            scheduler.setLastMessageId(0L);
            channel.deleteMessageById(lastMessageId).queue(
                    success -> performSend(scheduler, channel, track),
                    error -> performSend(scheduler, channel, track)
            );
        } else {
            performSend(scheduler, channel, track);
        }
    }

    private static void performSend(TrackScheduler scheduler, MessageChannel channel, AudioTrack track) {
        boolean isPaused = scheduler.player.isPaused();
        boolean isLooping = scheduler.isRepeating;
        boolean noHistory = scheduler.currentIndex <= 0;
        boolean noNext = scheduler.currentIndex >= scheduler.playlist.size() - 1;
        boolean noShuffle = (scheduler.playlist.size() - scheduler.currentIndex) <= 2;

        Button backBtn = noHistory ? Button.secondary("music_back", "◁").asDisabled() : Button.secondary("music_back", "◁");
        Button pauseBtn = isPaused ? Button.danger("music_pause", "▶") : Button.secondary("music_pause", "❚❚");
        Button skipBtn = noNext ? Button.secondary("music_skip", "▷").asDisabled() : Button.secondary("music_skip", "▷");
        Button loopBtn = isLooping ? Button.primary("music_loop", "↻") : Button.secondary("music_loop", "↻");
        Button listBtn = Button.secondary("music_queue_open", "☰");
        Button shuffleBtn = noShuffle ? Button.secondary("music_shuffle", "⇄").asDisabled() : Button.secondary("music_shuffle", "⇄");
        Button leaveBtn = Button.danger("music_leave", "▢");

        long duration = track.getDuration() / 1000;
        String timeStr = String.format("%d:%02d", duration / 60, duration % 60);

        int currentIndex = scheduler.currentIndex + 1;
        int totalTracks = scheduler.playlist.size();
        String titleText = String.format(":musical_note: Đang phát bài hát | Bài số: %d/%d", currentIndex, totalTracks);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(titleText)
                .setDescription("**" + track.getInfo().title + "**\n`" + timeStr + "`")
                .setThumbnail("https://cdn.pixabay.com/photo/2017/04/19/10/24/vinyl-2241789_1280.png")
                .setColor(Color.decode("#2ecc71"));

        ActionRow row1 = ActionRow.of(backBtn, pauseBtn, skipBtn, loopBtn, listBtn);
        ActionRow row2 = ActionRow.of(shuffleBtn, leaveBtn);

        channel.sendMessageEmbeds(embed.build())
                .setComponents(row1, row2)
                .queue(message -> scheduler.setLastMessageId(message.getIdLong()));
    }

    private void updateButtonUI(ButtonInteractionEvent event, TrackScheduler scheduler) {
        boolean isPaused = scheduler.player.isPaused();
        boolean isLooping = scheduler.isRepeating;
        boolean noHistory = scheduler.currentIndex <= 0;
        boolean noNext = scheduler.currentIndex >= scheduler.playlist.size() - 1;
        boolean noShuffle = (scheduler.playlist.size() - scheduler.currentIndex) <= 2;

        Button backBtn = noHistory ? Button.secondary("music_back", "◁").asDisabled() : Button.secondary("music_back", "◁");
        Button pauseBtn = isPaused ? Button.danger("music_pause", "▶") : Button.secondary("music_pause", "❚❚");
        Button skipBtn = noNext ? Button.secondary("music_skip", "▷").asDisabled() : Button.secondary("music_skip", "▷");
        Button loopBtn = isLooping ? Button.primary("music_loop", "↻") : Button.secondary("music_loop", "↻");
        Button listBtn = Button.secondary("music_queue_open", "☰");
        Button shuffleBtn = noShuffle ? Button.secondary("music_shuffle", "⇄").asDisabled() : Button.secondary("music_shuffle", "⇄");
        Button leaveBtn = Button.danger("music_leave", "▢");

        event.editComponents(ActionRow.of(backBtn, pauseBtn, skipBtn, loopBtn, listBtn), ActionRow.of(shuffleBtn, leaveBtn)).queue();
    }


    // ==========================================
    // PHẦN 2: XỬ LÝ SỰ KIỆN TƯƠNG TÁC
    // ==========================================

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        if (!buttonId.startsWith("music_")) return;

        if (isVoiceInvalid(event)) return;

        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(Objects.requireNonNull(event.getGuild()));
        TrackScheduler scheduler = musicManager.scheduler;

        switch (buttonId) {
            case "music_pause":
                scheduler.player.setPaused(!scheduler.player.isPaused());
                updateButtonUI(event, scheduler);
                break;
            case "music_skip":
                event.deferEdit().queue();
                scheduler.nextTrack();
                break;
            case "music_back":
                event.deferEdit().queue();
                scheduler.previousTrack();
                break;
            case "music_loop":
                scheduler.isRepeating = !scheduler.isRepeating;
                updateButtonUI(event, scheduler);
                break;
            case "music_leave":
                event.deferEdit().queue();
                Objects.requireNonNull(event.getGuild()).getAudioManager().closeAudioConnection();
                scheduler.stopAndCleanup();
                break;
            case "music_shuffle":
                scheduler.shuffleQueue();
                event.reply("Đã xáo trộn danh sách phát!").setEphemeral(true).queue();
                break;
            case "music_queue_open":
                event.deferEdit().queue();

                int playingPage = (scheduler.currentIndex / 10) + 1;
                sendNewQueueWindow(scheduler, event.getChannel(), playingPage);
                break;

            case "music_queue_close":
                event.deferEdit().queue();
                scheduler.deleteQueueMessage();
                break;

            case "music_queue_prev":
                updateQueueWindow(event, scheduler, getPageFromFooter(event) - 1);
                break;

            case "music_queue_next":
                updateQueueWindow(event, scheduler, getPageFromFooter(event) + 1);
                break;
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().equals("music_queue_jump")) return;
        if (isVoiceInvalid(event)) return;

        int targetIndex = Integer.parseInt(event.getValues().get(0));
        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(Objects.requireNonNull(event.getGuild()));

        event.reply("Đang nhảy tới bài số " + (targetIndex + 1)).setEphemeral(true).queue();
        musicManager.scheduler.jumpTo(targetIndex);
    }


    // ==========================================
    // PHẦN 3: LOGIC GIAO DIỆN XEM PLAYLIST
    // ==========================================

    public static void sendNewQueueWindow(TrackScheduler scheduler, MessageChannel channel, int page) {
        long lastQueueMessageId = scheduler.getLastQueueMessageId();
        if (lastQueueMessageId != 0L) {
            scheduler.setLastQueueMessageId(0L);
            channel.deleteMessageById(lastQueueMessageId).queue(
                    success -> performSendQueue(scheduler, channel, page),
                    error -> performSendQueue(scheduler, channel, page)
            );
        } else {
            performSendQueue(scheduler, channel, page);
        }
    }

    private static void performSendQueue(TrackScheduler scheduler, MessageChannel channel, int page) {
        List<AudioTrack> fullList = scheduler.getFullList();

        if (fullList.isEmpty()) {
            channel.sendMessage("Danh sách phát hiện đang trống!").queue(msg -> scheduler.setLastQueueMessageId(msg.getIdLong()));
            return;
        }

        int totalPages = (int) Math.ceil(fullList.size() / 10.0);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Danh sách phát (" + fullList.size() + " bài)")
                .setColor(Color.decode("#9b59b6"));

        StringSelectMenu.Builder menu = StringSelectMenu.create("music_queue_jump")
                .setPlaceholder("Chọn một bài để phát ngay...");

        int playingIndex = scheduler.currentIndex;
        AudioTrack currentTrack = scheduler.player.getPlayingTrack();

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

            String label = (i + 1) + ". " + truncate(track.getInfo().title);
            menu.addOption(label, String.valueOf(i));
        }

        embed.setFooter("Trang " + page + "/" + totalPages);

        Button prevBtn = page == 1
                ? Button.secondary("music_queue_prev", "◄◄").asDisabled()
                : Button.secondary("music_queue_prev", "◄◄");
        Button nextBtn = page == totalPages
                ? Button.secondary("music_queue_next", "►►").asDisabled()
                : Button.secondary("music_queue_next", "►►");
        Button closeBtn = Button.danger("music_queue_close", "✖");

        ActionRow buttons = ActionRow.of(prevBtn, nextBtn, closeBtn);
        ActionRow selectMenu = ActionRow.of(menu.build());

        channel.sendMessageEmbeds(embed.build())
                .setComponents(selectMenu, buttons)
                .queue(msg -> scheduler.setLastQueueMessageId(msg.getIdLong()));
    }

    private void updateQueueWindow(ButtonInteractionEvent event, TrackScheduler scheduler, int page) {
        List<AudioTrack> fullList = scheduler.getFullList();

        if (fullList.isEmpty()) {
            event.editMessage("Danh sách phát hiện đang trống!").setComponents().queue();
            return;
        }

        int totalPages = (int) Math.ceil(fullList.size() / 10.0);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Danh sách phát (" + fullList.size() + " bài)")
                .setColor(Color.decode("#9b59b6"));

        StringSelectMenu.Builder menu = StringSelectMenu.create("music_queue_jump")
                .setPlaceholder("Chọn một bài để phát ngay...");

        int playingIndex = scheduler.currentIndex;
        AudioTrack currentTrack = scheduler.player.getPlayingTrack();

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

            String label = (i + 1) + ". " + truncate(track.getInfo().title);
            menu.addOption(label, String.valueOf(i));
        }

        embed.setFooter("Trang " + page + "/" + totalPages);

        Button prevBtn = page == 1
                ? Button.secondary("music_queue_prev", "◄◄").asDisabled()
                : Button.secondary("music_queue_prev", "◄◄");
        Button nextBtn = page == totalPages
                ? Button.secondary("music_queue_next", "►►").asDisabled()
                : Button.secondary("music_queue_next", "►►");
        Button closeBtn = Button.danger("music_queue_close", "✖");

        ActionRow buttons = ActionRow.of(prevBtn, nextBtn, closeBtn);
        ActionRow selectMenu = ActionRow.of(menu.build());

        event.editMessageEmbeds(embed.build())
                .setComponents(selectMenu, buttons)
                .queue();
    }

    private int getPageFromFooter(ButtonInteractionEvent event) {
        try {
            String footerText = Objects.requireNonNull(event.getMessage().getEmbeds().get(0).getFooter()).getText();
            String pageString = Objects.requireNonNull(footerText).replace("Trang ", "").split("/")[0];
            return Integer.parseInt(pageString);
        } catch (Exception e) {
            return 1;
        }
    }

    // ==========================================
    // PHẦN 4: HÀM TIỆN ÍCH BỔ SUNG
    // ==========================================

    private boolean isVoiceInvalid(GenericComponentInteractionCreateEvent event) {
        net.dv8tion.jda.api.entities.GuildVoiceState memberVoiceState = Objects.requireNonNull(event.getMember()).getVoiceState();
        net.dv8tion.jda.api.entities.GuildVoiceState botVoiceState = Objects.requireNonNull(event.getGuild()).getSelfMember().getVoiceState();

        if (memberVoiceState == null || !memberVoiceState.inAudioChannel() || botVoiceState == null || !botVoiceState.inAudioChannel() || !Objects.equals(memberVoiceState.getChannel(), botVoiceState.getChannel())) {
            event.reply("Bạn phải ở chung phòng thoại với bot thì mới được giành mic nhé!").setEphemeral(true).queue();
            return true;
        }
        return false;
    }

    private static String truncate(String text) {
        int max = 80;
        return text.length() > max ? text.substring(0, max - 3) + "..." : text;
    }
}