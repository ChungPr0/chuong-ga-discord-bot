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

    public PlayerManager() {
        this.musicManagers = new HashMap<>();
        this.audioPlayerManager = new DefaultAudioPlayerManager();

        // KHỞI TẠO YOUTUBE PLUGIN
        YoutubeAudioSourceManager ytSourceManager = new YoutubeAudioSourceManager(true, new Client[] {
            new Music(),
            new Web(),
            new Tv(),
            new TvHtml5Simply(),
            new AndroidVr()
        });

        String refreshToken = com.chung.bot.config.Config.get("YOUTUBE_OAUTH_REFRESH_TOKEN");
        if (refreshToken != null && !refreshToken.trim().isEmpty()) {
            com.chung.bot.log.BotLogger.info("HỆ THỐNG NHẠC", "Đăng nhập YouTube thành công!");
            ytSourceManager.useOauth2(refreshToken, true);
        } else {
            com.chung.bot.log.BotLogger.warn("HỆ THỐNG NHẠC", "Chưa đăng nhập YouTube!");
            ytSourceManager.useOauth2(null, false);
        }

        this.audioPlayerManager.registerSourceManager(ytSourceManager);

        // Chặn YouTube cũ của Lavaplayer để không đụng độ
        AudioSourceManagers.registerRemoteSources(
                this.audioPlayerManager
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
                // Kiểm tra mức độ nghiêm trọng của lỗi
                if (exception.severity == FriendlyException.Severity.COMMON) {
                    return;
                }
                com.chung.bot.log.BotLogger.error("Lỗi Tải Nhạc (Music Load Failed)", 
                        "Không thể tải nhạc từ đường dẫn hoặc từ khóa: `" + trackUrl + "` ở Guild: " + (guild != null ? guild.getName() : "Không rõ"), 
                        exception);
                checkAndTriggerWarpRescue(exception);
            }
        });
    }

    private void checkAndTriggerWarpRescue(FriendlyException exception) {
        if (exception == null) return;
        
        String msg = exception.getMessage();
        Throwable cause = exception.getCause();
        boolean isNetworkError = false;
        
        // 1. Kiểm tra qua tin nhắn exception
        if (msg != null) {
            String lowerMsg = msg.toLowerCase();
            if (lowerMsg.contains("read timed out") || 
                lowerMsg.contains("all clients failed to load") || 
                lowerMsg.contains("connectexception") || 
                lowerMsg.contains("timeoutexception")) {
                isNetworkError = true;
            }
        }
        
        // 2. Kiểm tra nguyên nhân gốc (cause) nếu có
        if (!isNetworkError && cause != null) {
            String causeMsg = cause.getMessage();
            if (causeMsg != null) {
                String lowerCause = causeMsg.toLowerCase();
                if (lowerCause.contains("read timed out") || 
                    lowerCause.contains("all clients failed to load") || 
                    lowerCause.contains("connectexception") || 
                    lowerCause.contains("timeoutexception")) {
                    isNetworkError = true;
                }
            }
            String causeClassName = cause.getClass().getSimpleName();
            if (causeClassName.contains("ConnectException") || causeClassName.contains("TimeoutException")) {
                isNetworkError = true;
            }
        }
        
        // 3. Nếu đúng lỗi mạng WARP, tạo file tín hiệu (Flag File) cứu hộ
        if (isNetworkError) {
            try {
                Path flagPath = Paths.get("/opt/discord-bot/warp_error.flag");
                
                // Đảm bảo thư mục cha tồn tại
                if (!Files.exists(flagPath.getParent())) {
                    Files.createDirectories(flagPath.getParent());
                }
                
                if (!Files.exists(flagPath)) {
                    Files.createFile(flagPath);
                    LOGGER.warn("Đã tạo file cứu hộ WARP thành công tại: {}", flagPath);
                }
                
                com.chung.bot.log.BotLogger.warn("HỆ THỐNG CỨU HỘ", 
                        "Phát hiện lỗi mạng WARP. Đang gửi tín hiệu yêu cầu VPS reset lại đường truyền...");
            } catch (Exception e) {
                LOGGER.error("Lỗi khi tạo file flag cứu hộ WARP: ", e);
            }
        }
    }
}
