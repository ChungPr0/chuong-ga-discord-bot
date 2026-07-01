package com.chung.bot.features;

import com.chung.bot.config.Config;
import com.chung.bot.database.DatabaseManager;
import com.chung.bot.music.PlayerManager;
import com.chung.bot.music.GuildMusicManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class StateRecoveryListener extends ListenerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(StateRecoveryListener.class);
    private final JoinToCreateHandler joinToCreateHandler;

    public StateRecoveryListener(JoinToCreateHandler joinToCreateHandler) {
        this.joinToCreateHandler = joinToCreateHandler;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        LOGGER.info("[Recovery] Bot đã sẵn sàng. Bắt đầu quá trình khôi phục trạng thái (State Recovery)...");

        // Lấy Guild ID cấu hình của server private
        String guildId = Config.get("GUILD_ID");
        Guild guild = event.getJDA().getGuildById(guildId);
        if (guild == null) {
            LOGGER.error("[Recovery] Không tìm thấy Guild cấu hình với ID: {}. Hủy bỏ khôi phục.", guildId);
            return;
        }

        // 1. KHÔI PHỤC KÊNH TẠM (Temporary Channels)
        recoverTemporaryChannels(guild);

        // 2. KHÔI PHỤC HÀNG ĐỢI NHẠC (Music Queue)
        recoverMusicQueue(guild);
    }

    private void recoverTemporaryChannels(Guild guild) {
        LOGGER.info("[Recovery] Đang quét khôi phục các kênh thoại tạm...");
        DatabaseManager db = DatabaseManager.getInstance();
        List<Long> savedChannelIds = db.getAllTempChannels();

        for (long channelId : savedChannelIds) {
            VoiceChannel vc = guild.getVoiceChannelById(channelId);

            if (vc == null) {
                // Kênh không tồn tại (đã bị xóa tay lúc bot sập) -> Dọn dẹp trong DB và bộ nhớ RAM
                LOGGER.info("[Recovery] Kênh tạm {} không còn tồn tại trên Discord. Đang xóa bản ghi DB.", channelId);
                db.deleteTempChannel(channelId);
                joinToCreateHandler.removeChannelInMemory(String.valueOf(channelId));
            } else {
                // Kênh còn tồn tại, kiểm tra số lượng thành viên
                long memberCount = vc.getMembers().stream()
                        .filter(m -> !m.getUser().isBot())
                        .count();

                if (memberCount == 0) {
                    // Kênh còn tồn tại nhưng không có ai ở trong -> Xóa kênh trên Discord và DB/RAM
                    LOGGER.info("[Recovery] Kênh tạm '{}' ({}) trống thành viên. Tiến hành xóa kênh.", vc.getName(), channelId);
                    db.deleteTempChannel(channelId);
                    joinToCreateHandler.removeChannelInMemory(String.valueOf(channelId));
                    vc.delete().queue(
                            success -> LOGGER.info("[Recovery] Đã xóa kênh trống '{}' thành công.", vc.getName()),
                            error -> LOGGER.warn("[Recovery] Không thể xóa kênh trống '{}': {}", vc.getName(), error.getMessage())
                    );
                } else {
                    // Kênh đang hoạt động bình thường -> Giữ nguyên trạng thái để Listener theo dõi tiếp
                    LOGGER.info("[Recovery] Kênh tạm '{}' ({}) đang hoạt động với {} thành viên. Giữ nguyên.", 
                            vc.getName(), channelId, memberCount);
                }
            }
        }
    }

    private void recoverMusicQueue(Guild guild) {
        LOGGER.info("[Recovery] Đang quét khôi phục hàng đợi phát nhạc...");
        DatabaseManager db = DatabaseManager.getInstance();
        List<String> savedQueue = db.getSavedQueue();

        if (savedQueue == null || savedQueue.isEmpty()) {
            LOGGER.info("[Recovery] Không có bài hát nào được lưu trong hàng đợi cũ.");
            return;
        }

        // Tìm kênh thoại phù hợp để bot kết nối vào
        VoiceChannel targetVc = findBestVoiceChannel(guild);
        if (targetVc == null) {
            LOGGER.warn("[Recovery] Không tìm thấy kênh thoại nào phù hợp để hồi sinh hàng đợi nhạc.");
            return;
        }

        // Tìm kênh chữ dành cho nhạc để bot set cho scheduler gửi Control Panel
        String musicChannelId = Config.get("MUSIC_CHANNEL_ID");
        TextChannel textChannel = guild.getTextChannelById(musicChannelId);
        if (textChannel == null && !guild.getTextChannels().isEmpty()) {
            textChannel = guild.getTextChannels().get(0);
        }

        if (textChannel == null) {
            LOGGER.warn("[Recovery] Kênh text để phát nhạc không hợp lệ. Hủy bỏ khôi phục hàng đợi nhạc.");
            return;
        }

        // Lấy panel ID cũ từ DB và xóa trước khi nạp nhạc mới
        String lastPanelIdStr = db.getMetadata("last_panel_message_id");
        if (lastPanelIdStr != null && !lastPanelIdStr.isEmpty()) {
            try {
                long lastPanelId = Long.parseLong(lastPanelIdStr);
                LOGGER.info("[Recovery] Phát hiện panel cũ ID: {}. Đang xóa để tránh trùng lặp...", lastPanelId);
                
                final VoiceChannel finalTargetVc = targetVc;
                final TextChannel finalTxtChannel = textChannel;
                
                textChannel.deleteMessageById(lastPanelId).queue(
                        success -> {
                            LOGGER.info("[Recovery] Đã xóa panel cũ thành công.");
                            db.saveMetadata("last_panel_message_id", null);
                            proceedWithRestore(guild, finalTargetVc, finalTxtChannel, savedQueue, db);
                        },
                        error -> {
                            LOGGER.warn("[Recovery] Không tìm thấy panel cũ hoặc đã bị xóa trước đó.");
                            db.saveMetadata("last_panel_message_id", null);
                            proceedWithRestore(guild, finalTargetVc, finalTxtChannel, savedQueue, db);
                        }
                );
            } catch (Exception e) {
                LOGGER.error("[Recovery] Lỗi khi chuyển đổi ID panel hoặc gửi yêu cầu xóa: ", e);
                proceedWithRestore(guild, targetVc, textChannel, savedQueue, db);
            }
        } else {
            proceedWithRestore(guild, targetVc, textChannel, savedQueue, db);
        }
    }

    private void proceedWithRestore(Guild guild, VoiceChannel targetVc, TextChannel textChannel, List<String> savedQueue, DatabaseManager db) {
        LOGGER.info("[Recovery] Tiến hành kết nối bot vào kênh thoại '{}' và khôi phục hàng đợi nhạc...", targetVc.getName());
        
        // Mở kết nối âm thanh
        guild.getAudioManager().openAudioConnection(targetVc);

        // Khôi phục hàng đợi nhạc bằng PlayerManager
        PlayerManager pm = PlayerManager.getInstance();
        GuildMusicManager musicManager = pm.getMusicManager(guild);
        musicManager.scheduler.setChannel(textChannel);

        int targetIndex = 0;
        String indexStr = db.getMetadata("music_current_index");
        if (indexStr != null && !indexStr.isEmpty()) {
            try {
                targetIndex = Integer.parseInt(indexStr);
            } catch (NumberFormatException e) {
                LOGGER.error("[Recovery] Lỗi định dạng index khôi phục: ", e);
            }
        }

        pm.restoreQueue(guild, savedQueue, targetIndex, textChannel);

        // Sau khi khôi phục xong, xóa sạch hàng đợi cũ trong SQLite DB để tránh trùng lặp
        db.saveQueue(null);
        db.saveMetadata("music_current_index", null);
        LOGGER.info("[Recovery] Đã hồi sinh hàng đợi nhạc thành công và giải phóng DB.");
    }

    private VoiceChannel findBestVoiceChannel(Guild guild) {
        // 1. Tìm kênh thoại có người thật đang ở bên trong (ưu tiên kênh đông nhất)
        VoiceChannel bestVc = null;
        long maxMembers = 0;

        for (VoiceChannel vc : guild.getVoiceChannels()) {
            long nonBotCount = vc.getMembers().stream()
                    .filter(m -> !m.getUser().isBot())
                    .count();

            if (nonBotCount > maxMembers) {
                maxMembers = nonBotCount;
                bestVc = vc;
            }
        }

        if (bestVc != null) {
            return bestVc;
        }

        // 2. Nếu không có ai trong kênh thoại nào, tìm kênh thoại mà Guild Owner đang đứng
        Member owner = guild.getOwner();
        if (owner != null && owner.getVoiceState() != null && owner.getVoiceState().inAudioChannel()) {
            var channel = owner.getVoiceState().getChannel();
            if (channel instanceof VoiceChannel) {
                return (VoiceChannel) channel;
            }
        }

        // 3. Dự phòng: lấy kênh thoại đầu tiên trong server
        if (!guild.getVoiceChannels().isEmpty()) {
            return guild.getVoiceChannels().get(0);
        }

        return null;
    }
}
