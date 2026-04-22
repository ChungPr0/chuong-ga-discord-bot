package com.chung.bot.commands;

import com.chung.bot.config.Config;
import com.chung.bot.music.GuildMusicManager;
import com.chung.bot.music.PlayerManager;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class SlashCommandHandler extends ListenerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SlashCommandHandler.class);

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String commandName = event.getName();

        // KIỂM TRA ĐIỀU KIỆN KÊNH CHAT (TEXT CHANNEL)
        String musicChannelId = Config.get("MUSIC_CHANNEL_ID");
        if (musicChannelId != null && !event.getChannel().getId().equals(musicChannelId)) {
            event.reply("Oops! Vui lòng vào kênh <#" + musicChannelId + "> để gọi lệnh nhạc nhé!")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        // Lấy trạng thái Voice của người dùng và của Bot
        Member member = event.getMember();
        GuildVoiceState memberVoiceState = Objects.requireNonNull(member).getVoiceState();
        GuildVoiceState botVoiceState = Objects.requireNonNull(event.getGuild()).getSelfMember().getVoiceState();

        // BẮT ĐẦU ĐỊNH TUYẾN LỆNH
        switch (commandName) {
            case "play":
                // Kiểm tra người dùng có ở trong Voice Channel không
                if (!Objects.requireNonNull(memberVoiceState).inAudioChannel()) {
                    event.reply("Bạn phải vào một kênh thoại trước mới gọi bot được chứ!").setEphemeral(true).queue();
                    return;
                }

                // KIỂM TRA BOT CÓ ĐANG PHÁT Ở KÊNH KHÁC KHÔNG
                if (Objects.requireNonNull(botVoiceState).inAudioChannel() && !Objects.equals(botVoiceState.getChannel(), memberVoiceState.getChannel())) {
                    event.reply("Bot đang bận " + Objects.requireNonNull(botVoiceState.getChannel()).getAsMention() + " rồi. Hãy vào chung kênh với bot để thêm nhạc!").setEphemeral(true).queue();
                    return;
                }

                // Cho Bot kết nối vào cùng kênh thoại với người dùng
                AudioManager audioManager = event.getGuild().getAudioManager();
                if (!audioManager.isConnected()) {
                    audioManager.openAudioConnection(Objects.requireNonNull(memberVoiceState.getChannel()));
                }

                // Xử lý URL hoặc tìm kiếm
                String query = Objects.requireNonNull(event.getOption("bai-hat")).getAsString();
                String trackUrl = query;
                if (!query.startsWith("http")) {
                    trackUrl = "ytsearch:" + query;
                }

                event.reply("Đang lên YouTube tìm: `" + query + "`...").queue();
                PlayerManager.getInstance().loadAndPlay(event.getMessageChannel(), event.getGuild(), trackUrl);
                LOGGER.info("User {} yêu cầu phát nhạc: {}", event.getUser().getName(), query);
                break;

            case "leave":
                // KIỂM TRA ĐIỀU KIỆN: User và Bot PHẢI ở chung kênh thoại
                assert memberVoiceState != null;
                if (!memberVoiceState.inAudioChannel() || !Objects.requireNonNull(botVoiceState).inAudioChannel() || !Objects.equals(memberVoiceState.getChannel(), botVoiceState.getChannel())) {
                    event.reply("Bạn phải ở chung kênh thoại với bot thì mới có thể đuổi nó đi được!").setEphemeral(true).queue();
                    return;
                }

                // Ngắt kết nối và gọi hàm dọn dẹp sạch sẽ (giống như khi bấm nút Leave)
                event.getGuild().getAudioManager().closeAudioConnection();
                GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
                musicManager.scheduler.stopAndCleanup();

                event.reply("Bot đã rời khỏi kênh thoại và dọn dẹp bảng điều khiển!").queue();
                LOGGER.info("User {} đã cho bot rời kênh.", event.getUser().getName());
                break;

            default:
                event.reply("Lệnh không được hỗ trợ!").setEphemeral(true).queue();
        }
    }
}