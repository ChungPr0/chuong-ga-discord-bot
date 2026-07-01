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
import dev.lavalink.youtube.clients.Music;
import dev.lavalink.youtube.clients.Web;
import dev.lavalink.youtube.clients.Tv;
import dev.lavalink.youtube.clients.TvHtml5Simply;
import dev.lavalink.youtube.clients.AndroidVr;
import dev.lavalink.youtube.clients.skeleton.Client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PlayerManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerManager.class);
    private static PlayerManager INSTANCE;
    private final Map<Long, GuildMusicManager> musicManagers;
    private final AudioPlayerManager audioPlayerManager;
    private final YoutubeAudioSourceManager ytSourceManager;



    public PlayerManager() {
        this.musicManagers = new HashMap<>();
        this.audioPlayerManager = new DefaultAudioPlayerManager();

        // KHỞI TẠO YOUTUBE PLUGIN
        this.ytSourceManager = new YoutubeAudioSourceManager(true, new Client[] {
            new Music(),
            new Web(),
            new Tv(),
            new TvHtml5Simply(),
            new AndroidVr()
        });

        String refreshToken = com.chung.bot.config.Config.get("YOUTUBE_OAUTH_REFRESH_TOKEN");
        if (refreshToken != null && !refreshToken.trim().isEmpty()) {
            LOGGER.info("Đăng nhập YouTube bằng token cấu hình sẵn.");
            this.ytSourceManager.useOauth2(refreshToken, true);
        } else {
            LOGGER.warn("Chưa cấu hình token YouTube OAuth.");
            this.ytSourceManager.useOauth2(null, false);
        }

        this.audioPlayerManager.registerSourceManager(this.ytSourceManager);

        // Chặn YouTube cũ của Lavaplayer để không đụng độ
        AudioSourceManagers.registerRemoteSources(
                this.audioPlayerManager
        );

        // Đăng ký nguồn file local
        AudioSourceManagers.registerLocalSource(this.audioPlayerManager);
    }



    public static synchronized PlayerManager getInstance() {
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

    public synchronized List<String> getEntireQueueUrls() {
        List<String> urls = new java.util.ArrayList<>();
        for (GuildMusicManager manager : musicManagers.values()) {
            TrackScheduler scheduler = manager.scheduler;
            if (scheduler != null) {
                synchronized (scheduler) {
                    // 1. Lấy bài đang phát hiện tại
                    AudioTrack playing = scheduler.player.getPlayingTrack();
                    if (playing != null && playing.getInfo() != null && playing.getInfo().uri != null) {
                        urls.add(playing.getInfo().uri);
                    }
                    // 2. Lấy toàn bộ các bài đang xếp hàng phía sau
                    for (AudioTrack track : scheduler.getQueue()) {
                        if (track != null && track.getInfo() != null && track.getInfo().uri != null) {
                            urls.add(track.getInfo().uri);
                        }
                    }
                }
            }
        }
        return urls;
    }

    public void restoreQueue(Guild guild, List<String> urls) {
        if (urls == null || urls.isEmpty() || guild == null) return;
        GuildMusicManager musicManager = getMusicManager(guild);
        
        for (String url : urls) {
            this.audioPlayerManager.loadItemOrdered(musicManager, url, new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(AudioTrack track) {
                    musicManager.scheduler.queue(track);
                }

                @Override
                public void playlistLoaded(AudioPlaylist playlist) {
                    List<AudioTrack> tracks = playlist.getTracks();
                    if (!tracks.isEmpty()) {
                        if (playlist.isSearchResult()) {
                            musicManager.scheduler.queue(tracks.get(0));
                        } else {
                            musicManager.scheduler.queuePlaylist(tracks);
                        }
                    }
                }

                @Override
                public void noMatches() {
                    LOGGER.warn("Không tìm thấy bài hát khi khôi phục: {}", url);
                }

                @Override
                public void loadFailed(FriendlyException exception) {
                    com.chung.bot.log.BotLogger.error("Lỗi Khôi Phục Nhạc (Restore Music Failed)", 
                            "Không thể tải lại bài hát khi khôi phục từ URL: `" + url + "` ở Guild: " + (guild != null ? guild.getName() : "Không rõ"), 
                            exception);
                }
            });
        }
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
                List<AudioTrack> tracks = playlist.getTracks();
                if (tracks.isEmpty()) return;

                musicManager.scheduler.setChannel(channel);

                if (playlist.isSearchResult()) {
                    // Nếu là kết quả tìm kiếm (ví dụ "ytsearch: bài hát"), chỉ lấy bài đầu tiên
                    AudioTrack track = tracks.get(0);
                    musicManager.scheduler.queue(track);
                    channel.sendMessage("Đã tìm kiếm và thêm: **" + track.getInfo().title + "**").queue();
                } else {
                    musicManager.scheduler.queuePlaylist(tracks);
                    channel.sendMessage("Đã thêm **" + tracks.size() + "** bài hát từ playlist: **" + playlist.getName() + "** vào hàng đợi.").queue();
                }
            }

            @Override
            public void noMatches() {
                channel.sendMessage("Không tìm thấy bài hát nào trên YouTube với từ khóa này!").queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                com.chung.bot.log.BotLogger.error("Lỗi Tải Nhạc (Music Load Failed)", 
                        "Không thể tải nhạc từ đường dẫn hoặc từ khóa: `" + trackUrl + "` ở Guild: " + (guild != null ? guild.getName() : "Không rõ"), 
                        exception);
                checkAndTriggerWarpRescue(exception);
            }
        });
    }

    public void checkAndTriggerWarpRescue(FriendlyException exception) {
        if (exception == null) return;
        
        String msg = exception.getMessage();
        Throwable cause = exception.getCause();
        
        boolean isYoutubeBlock = false;
        
        // 1. Kiểm tra trực tiếp class name của exception/cause
        if (exception.getClass().getName().contains("AllClientsFailedException") ||
            (cause != null && cause.getClass().getName().contains("AllClientsFailedException"))) {
            isYoutubeBlock = true;
        }
        
        // 2. Kiểm tra qua tin nhắn exception
        if (msg != null) {
            String lowerMsg = msg.toLowerCase();
            if (lowerMsg.contains("this video requires login") || 
                lowerMsg.contains("all clients failed to load")) {
                isYoutubeBlock = true;
            }
        }
        
        // 3. Kiểm tra nguyên nhân gốc (cause) nếu có
        if (cause != null) {
            String causeMsg = cause.getMessage();
            if (causeMsg != null) {
                String lowerCause = causeMsg.toLowerCase();
                if (lowerCause.contains("this video requires login") || 
                    lowerCause.contains("all clients failed to load")) {
                    isYoutubeBlock = true;
                }
            }
        }
        
        // 4. Xử lý tạo file tín hiệu (Flag File) xoay IP WARP
        if (isYoutubeBlock) {
            try {
                Path flagPath = Paths.get("/opt/discord-bot/warp_rotate.flag");
                
                // Đảm bảo thư mục cha tồn tại
                if (flagPath.getParent() != null && !Files.exists(flagPath.getParent())) {
                    Files.createDirectories(flagPath.getParent());
                }
                
                if (!Files.exists(flagPath)) {
                    Files.createFile(flagPath);
                    LOGGER.warn("Đã tạo file cứu hộ xoay IP WARP tại: {}", flagPath);
                }
                
                com.chung.bot.log.BotLogger.warn("HỆ THỐNG MẠNG", 
                        "Phát hiện YouTube chặn IP (Yêu cầu login). Đang tạo file cứu hộ để VPS tự động xoay IP WARP...");
            } catch (Exception e) {
                LOGGER.error("Lỗi khi tạo file flag xoay IP WARP: ", e);
            }
        }
    }
}
