package com.chung.bot.music;

import com.chung.bot.features.MusicControlHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.util.ArrayList;
import java.util.List;

public class TrackScheduler extends AudioEventAdapter {
    public final AudioPlayer player;
    public final List<AudioTrack> playlist;
    public int currentIndex = 0;
    public boolean isRepeating = false;

    private MessageChannel currentChannel;
    private long lastMessageId = 0L;
    private long lastQueueMessageId = 0L;

    public TrackScheduler(AudioPlayer player) {
        this.player = player;
        this.playlist = new ArrayList<>();
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

    public void setLastQueueMessageId(long id) {
        this.lastQueueMessageId = id;
    }

    public long getLastQueueMessageId() {
        return this.lastQueueMessageId;
    }

    // ==========================================
    // HÀM HỖ TRỢ DANH SÁCH PHÁT VÀ NHẢY BÀI
    // ==========================================

    public void deleteQueueMessage() {
        if (currentChannel != null && lastQueueMessageId != 0L) {
            long idToDelete = lastQueueMessageId;
            lastQueueMessageId = 0L;

            currentChannel.deleteMessageById(idToDelete).queue(
                    success -> {},
                    error -> {}
            );
        }
    }

    public void deleteControlMessage() {
        if (currentChannel != null && lastMessageId != 0L) {
            long idToDelete = lastMessageId;
            lastMessageId = 0L;

            currentChannel.deleteMessageById(idToDelete).queue(
                    success -> {},
                    error -> {}
            );
        }
    }

    public List<AudioTrack> getFullList() {
        return this.playlist;
    }

    public void jumpTo(int targetIndex) {
        if (targetIndex < 0 || targetIndex >= playlist.size()) {
            return;
        }

        currentIndex = targetIndex;
        player.startTrack(playlist.get(currentIndex).makeClone(), false);

        deleteQueueMessage();
    }

    // ==========================================
    // CÁC HÀM XỬ LÝ NHẠC CƠ BẢN
    // ==========================================

    public void queue(AudioTrack track) {
        playlist.add(track);

        if (player.getPlayingTrack() == null) {
            player.startTrack(playlist.get(currentIndex).makeClone(), false);
        }

        deleteQueueMessage();

        if (lastMessageId != 0L && currentChannel != null) {
            AudioTrack playingTrack = player.getPlayingTrack();
            if (playingTrack != null) {
                MusicControlHandler.sendNewControlPanel(this, currentChannel, playingTrack);
            }
        }
    }

    public void nextTrack() {
        currentIndex++;
        if (currentIndex < playlist.size()) {
            player.startTrack(playlist.get(currentIndex).makeClone(), false);
        } else {
            player.stopTrack();
        }

        deleteQueueMessage();
    }

    public void previousTrack() {
        currentIndex--;
        if (currentIndex >= 0 && currentIndex < playlist.size()) {
            player.startTrack(playlist.get(currentIndex).makeClone(), false);
        } else {
            currentIndex = 0;
            if (!playlist.isEmpty()) {
                player.startTrack(playlist.get(currentIndex).makeClone(), false);
            }
        }

        deleteQueueMessage();
    }

    public void stopAndCleanup() {
        player.stopTrack();
        playlist.clear();
        currentIndex = 0;
        isRepeating = false;

        deleteControlMessage();
        deleteQueueMessage();
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        deleteQueueMessage();

        if (currentChannel != null) {
            MusicControlHandler.sendNewControlPanel(this, currentChannel, track);
        }
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason != AudioTrackEndReason.REPLACED) {
             deleteControlMessage();
        }

        deleteQueueMessage();

        if (endReason.mayStartNext) {
            if (isRepeating) {
                player.startTrack(playlist.get(currentIndex).makeClone(), false);
            } else {
                nextTrack();
            }
        }
    }
}