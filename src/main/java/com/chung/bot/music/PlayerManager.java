package com.chung.bot.music;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import dev.lavalink.youtube.YoutubeAudioSourceManager;

import java.util.HashMap;
import java.util.Map;

public class PlayerManager {
    private static PlayerManager INSTANCE;
    private final Map<Long, GuildMusicManager> musicManagers;
    private final AudioPlayerManager audioPlayerManager;

    public PlayerManager() {
        this.musicManagers = new HashMap<>();
        this.audioPlayerManager = new DefaultAudioPlayerManager();

        // KHỞI TẠO YOUTUBE PLUGIN
        YoutubeAudioSourceManager ytSourceManager = new YoutubeAudioSourceManager();

        this.audioPlayerManager.registerSourceManager(ytSourceManager);

        // Chặn YouTube cũ của Lavaplayer để không đụng độ
        AudioSourceManagers.registerRemoteSources(
                this.audioPlayerManager,
                com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class
        );

        // Đăng ký nguồn file local
        AudioSourceManagers.registerLocalSource(this.audioPlayerManager);
    }

    public static PlayerManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new PlayerManager();
        }
        return INSTANCE;
    }

    public GuildMusicManager getMusicManager(Guild guild) {
        return this.musicManagers.computeIfAbsent(guild.getIdLong(), (guildId) -> {
            GuildMusicManager guildMusicManager = new GuildMusicManager(this.audioPlayerManager);
            guild.getAudioManager().setSendingHandler(guildMusicManager.getSendHandler());
            return guildMusicManager;
        });
    }

    public void loadAndPlay(MessageChannel channel, Guild guild, String trackUrl) {
        GuildMusicManager musicManager = getMusicManager(guild);

        this.audioPlayerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {

            @Override
            public void trackLoaded(AudioTrack track) {
                musicManager.scheduler.setChannel(channel);
                musicManager.scheduler.queue(track);
                channel.sendMessage("Đã thêm vào hàng đợi: **" + track.getInfo().title + "**").queue();
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack track = playlist.getTracks().get(0);
                musicManager.scheduler.setChannel(channel);
                musicManager.scheduler.queue(track);
                channel.sendMessage("Đã tìm thấy và thêm: **" + track.getInfo().title + "**").queue();
            }

            @Override
            public void noMatches() {
                channel.sendMessage("Không tìm thấy bài hát nào trên YouTube với từ khóa này!").queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                channel.sendMessage("Có lỗi xảy ra khi tải nhạc: " + exception.getMessage()).queue();
            }
        });
    }
}