package com.chung.bot.music;

import com.chung.bot.features.MusicControlHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TrackScheduler extends AudioEventAdapter {
    public final AudioPlayer player;
    public final List<AudioTrack> playlist;
    public int currentIndex = 0;
    public boolean isRepeating = false;

    private MessageChannel currentChannel;
    private long lastMessageId = 0L;
    private long lastQueueMessageId = 0L;
    
    private String lastSentTrackIdentifier;

    public boolean isRestoring = false;

    public void setLastSentTrackIdentifier(String id) {
        this.lastSentTrackIdentifier = id;
    }

    private java.util.concurrent.CompletableFuture<Void> panelFuture = java.util.concurrent.CompletableFuture.completedFuture(null);

    public synchronized void queuePanelTask(java.util.function.BiConsumer<TrackScheduler, java.util.concurrent.CompletableFuture<Void>> task) {
        this.panelFuture = this.panelFuture.handle((v, ex) -> {
            java.util.concurrent.CompletableFuture<Void> nextFuture = new java.util.concurrent.CompletableFuture<>();
            try {
                task.accept(this, nextFuture);
            } catch (Exception e) {
                nextFuture.complete(null);
            }
            return nextFuture;
        }).thenCompose(f -> f);
    }

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

    public synchronized void deleteControlMessage() {
        if (currentChannel != null) {
            queuePanelTask((sched, future) -> {
                long idToDelete = sched.getLastMessageId();
                if (idToDelete != 0L) {
                    sched.setLastMessageId(0L);
                    com.chung.bot.database.DatabaseManager.getInstance().saveMetadata("last_panel_message_id", null);
                    currentChannel.deleteMessageById(idToDelete).queue(
                            success -> future.complete(null),
                            error -> future.complete(null)
                    );
                } else {
                    future.complete(null);
                }
            });
        }
    }

    public List<AudioTrack> getFullList() {
        return this.playlist;
    }

    public synchronized List<AudioTrack> getQueue() {
        List<AudioTrack> queue = new ArrayList<>();
        for (int i = currentIndex + 1; i < playlist.size(); i++) {
            queue.add(playlist.get(i));
        }
        return queue;
    }
    public void jumpTo(int targetIndex) {
        if (targetIndex < 0 || targetIndex >= playlist.size()) {
            return;
        }

        if (playlist.get(targetIndex) == null) {
            if (currentChannel != null) {
                currentChannel.sendMessage("Bài hát này chưa được tải xong, vui lòng thử lại sau.").queue();
            }
            return;
        }

        currentIndex = targetIndex;
        player.startTrack(playlist.get(currentIndex).makeClone(), false);

        deleteQueueMessage();
    }

    public void shuffleQueue() {
        if (playlist.size() > currentIndex + 1) {
            List<AudioTrack> subList = playlist.subList(currentIndex + 1, playlist.size());
            Collections.shuffle(subList);
            deleteQueueMessage();
        }
    }

    // ==========================================
    // CÁC HÀM XỬ LÝ NHẠC CƠ BẢN
    // ==========================================

    public void queue(AudioTrack track) {
        playlist.add(track);

        if (player.getPlayingTrack() == null) {
            player.startTrack(playlist.get(currentIndex).makeClone(), false);
        } else {
            if (lastMessageId != 0L && currentChannel != null) {
                AudioTrack playingTrack = player.getPlayingTrack();
                if (playingTrack != null) {
                    MusicControlHandler.sendNewControlPanel(this, currentChannel, playingTrack);
                }
            }
        }
        
        deleteQueueMessage();
    }

    public void queuePlaylist(List<AudioTrack> tracks) {
        playlist.addAll(tracks);

        if (player.getPlayingTrack() == null) {
            player.startTrack(playlist.get(currentIndex).makeClone(), false);
        } else {
            if (lastMessageId != 0L && currentChannel != null) {
                AudioTrack playingTrack = player.getPlayingTrack();
                if (playingTrack != null) {
                    MusicControlHandler.sendNewControlPanel(this, currentChannel, playingTrack);
                }
            }
        }

        deleteQueueMessage();
    }

    public void nextTrack() {
        currentIndex++;
        while (currentIndex < playlist.size() && playlist.get(currentIndex) == null) {
            currentIndex++;
        }
        if (currentIndex < playlist.size()) {
            player.startTrack(playlist.get(currentIndex).makeClone(), false);
        } else {
            player.stopTrack();
        }

        deleteQueueMessage();
    }

    public void previousTrack() {
        currentIndex--;
        while (currentIndex >= 0 && playlist.get(currentIndex) == null) {
            currentIndex--;
        }
        if (currentIndex >= 0 && currentIndex < playlist.size()) {
            player.startTrack(playlist.get(currentIndex).makeClone(), false);
        } else {
            currentIndex = 0;
            while (currentIndex < playlist.size() && playlist.get(currentIndex) == null) {
                currentIndex++;
            }
            if (currentIndex < playlist.size()) {
                player.startTrack(playlist.get(currentIndex).makeClone(), false);
            }
        }

        deleteQueueMessage();
    }
    public void stopAndCleanup() {
        player.stopTrack();
        if (!com.chung.bot.BotMain.isShuttingDown) {
            playlist.clear();
            currentIndex = 0;
            isRepeating = false;
            lastSentTrackIdentifier = null; 
        } 

        deleteControlMessage();
        deleteQueueMessage();
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        deleteQueueMessage();

        if (isRestoring) {
            return;
        }

        if (lastSentTrackIdentifier != null && lastSentTrackIdentifier.equals(track.getIdentifier())) {
            return;
        }

        if (currentChannel != null) {
            lastSentTrackIdentifier = track.getIdentifier();
            MusicControlHandler.sendNewControlPanel(this, currentChannel, track);
        }
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason != AudioTrackEndReason.REPLACED) {
             deleteControlMessage();
        }

        deleteQueueMessage();

        if (lastSentTrackIdentifier != null && lastSentTrackIdentifier.equals(track.getIdentifier())) {
            lastSentTrackIdentifier = null;
        }

        if (endReason.mayStartNext) {
            if (isRepeating) {
                player.startTrack(playlist.get(currentIndex).makeClone(), false);
            } else {
                nextTrack();
            }
        }
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        com.chung.bot.log.BotLogger.error("Lỗi Phát Nhạc (Track Exception)", 
                "Lỗi khi phát bài **" + track.getInfo().title + "** (URL: " + track.getInfo().uri + ")", 
                exception);
        PlayerManager.getInstance().checkAndTriggerWarpRescue(exception);
        nextTrack();
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        com.chung.bot.log.BotLogger.warn("Bài hát bị treo (Track Stuck)", 
                "Bài hát **" + track.getInfo().title + "** bị treo quá " + thresholdMs + "ms ở kênh thoại. Đang tự động chuyển bài tiếp theo...");
        nextTrack();
    }
}