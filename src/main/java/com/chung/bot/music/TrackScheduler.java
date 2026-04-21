package com.chung.bot.music;

import com.chung.bot.features.MusicControlHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.util.LinkedList;
import java.util.Stack;

public class TrackScheduler extends AudioEventAdapter {
    public final AudioPlayer player;
    public final LinkedList<AudioTrack> queue;
    public final Stack<AudioTrack> history;
    public boolean isRepeating = false;

    private MessageChannel currentChannel;
    private long lastMessageId = 0L;

    public TrackScheduler(AudioPlayer player) {
        this.player = player;
        this.queue = new LinkedList<>();
        this.history = new Stack<>();
    }

    public void setChannel(MessageChannel channel) {
        this.currentChannel = channel;
    }

    public void setLastMessageId(long id) {
        this.lastMessageId = id;
    }

    public long getLastMessageId() {
        return this.lastMessageId;
    }

    public void queue(AudioTrack track) {
        // Thử phát ngay, nếu không phát được (đang bận) thì mới cho vào hàng đợi
        if (!player.startTrack(track, true)) {
            queue.offer(track);

            if (lastMessageId != 0L && currentChannel != null) {
                AudioTrack playingTrack = player.getPlayingTrack();
                if (playingTrack != null) {
                    // Gọi hàm gửi mới từ Handler
                    MusicControlHandler.sendNewControlPanel(this, currentChannel, playingTrack);
                }
            }
        }
    }

    public void nextTrack() {
        if (player.getPlayingTrack() != null) {
            history.push(player.getPlayingTrack().makeClone());
        }
        player.startTrack(queue.poll(), false);
    }

    public void previousTrack() {
        if (!history.isEmpty()) {
            if (player.getPlayingTrack() != null) {
                queue.addFirst(player.getPlayingTrack().makeClone());
            }
            player.startTrack(history.pop(), false);
        }
    }

    public void stopAndCleanup() {
        // Dừng nhạc và xóa hàng chờ
        player.stopTrack();
        queue.clear();
        history.clear();

        // Xóa tin nhắn bảng điều khiển cuối cùng
        if (currentChannel != null && lastMessageId != 0L) {
            currentChannel.deleteMessageById(lastMessageId).queue(
                    success -> { lastMessageId = 0L; },
                    error -> { lastMessageId = 0L; }
            );
        }
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        // Luôn in bảng điều khiển mới khi bài hát bắt đầu
        if (currentChannel != null) {
            MusicControlHandler.sendNewControlPanel(this, currentChannel, track);
        }
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        // LUÔN LUÔN xóa tin nhắn cũ khi bài hát kết thúc
        if (currentChannel != null && lastMessageId != 0L) {
            currentChannel.deleteMessageById(lastMessageId).queue(
                    success -> { lastMessageId = 0L; },
                    error -> { lastMessageId = 0L; }
            );
        }

        if (endReason.mayStartNext) {
            if (isRepeating) {
                player.startTrack(track.makeClone(), false);
            } else {
                nextTrack();
            }
        }
    }
}