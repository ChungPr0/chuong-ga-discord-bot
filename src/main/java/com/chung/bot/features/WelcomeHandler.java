package com.chung.bot.features;

import com.chung.bot.config.Config;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateBoostTimeEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class WelcomeHandler extends ListenerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(WelcomeHandler.class);
    // Định dạng thời gian theo kiểu Việt Nam (dd/MM/yyyy HH:mm)
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Hàm tiện ích để lấy kênh thông báo
    private Optional<TextChannel> getWelcomeChannel(net.dv8tion.jda.api.entities.Guild guild) {
        String channelId = Config.get("WELCOME_CHANNEL_ID");
        if (channelId == null || channelId.isEmpty()) {
            LOGGER.error("Chưa cấu hình WELCOME_CHANNEL_ID trong file .env!");
            return Optional.empty();
        }
        return Optional.ofNullable(guild.getTextChannelById(channelId));
    }

    // Hàm tiện ích để lấy kênh #roles
    private Optional<TextChannel> getRolesChannel(net.dv8tion.jda.api.entities.Guild guild) {
        String channelId = Config.get("ROLES_CHANNEL_ID");
        if (channelId == null || channelId.isEmpty()) {
            LOGGER.error("Chưa cấu hình ROLES_CHANNEL_ID trong file .env!");
            return Optional.empty();
        }
        return Optional.ofNullable(guild.getTextChannelById(channelId));
    }

    // Thông báo CHÀO MỪNG
    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        getWelcomeChannel(event.getGuild()).ifPresent(channel -> {
            Member member = event.getMember();
            net.dv8tion.jda.api.entities.User user = member.getUser();
            String timestamp = TIME_FORMATTER.format(OffsetDateTime.now());

            // GỬI TIN NHẮN RIÊNG (DM)
            user.openPrivateChannel().flatMap(privateChannel -> {
                EmbedBuilder dmEmbed = new EmbedBuilder();
                dmEmbed.setTitle("Chào mừng bạn đến với **Chuồng Gà🐣**!");
                dmEmbed.setColor(Color.ORANGE);
                dmEmbed.setThumbnail(event.getGuild().getIconUrl());

                String rolesChannelMention = getRolesChannel(event.getGuild())
                        .map(net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel::getAsMention)
                        .orElse("# 👑 roles");

                dmEmbed.setDescription(String.format("""
                Chào mừng **%s**! Để có thể tham gia vào các hoạt động của server, bạn cần thực hiện bước cuối cùng:
                
                👉 Hãy truy cập vào kênh %s
                👉 Bấm vào emoji ✅ bên dưới tin nhắn hướng dẫn.
                
                Sau khi bấm, bạn sẽ trở thành một **Con Gà** chính hiệu và nhìn thấy toàn bộ các kênh chat khác!
                """, member.getEffectiveName(), rolesChannelMention));

                dmEmbed.setFooter("Chúc bạn có những giây phút vui vẻ tại Chuồng Gà!");

                return privateChannel.sendMessageEmbeds(dmEmbed.build());
            }).queue(
                    success -> LOGGER.info("Đã gửi hướng dẫn DM cho {}.", user.getName()),
                    error -> LOGGER.warn("Không thể gửi DM cho {} (có thể họ đã chặn DM).", user.getName())
            );

            // Gửi tin nhắn tag người dùng trước embed
            String mentionMessage = "Chào mừng, " + member.getAsMention();
            channel.sendMessage(mentionMessage).queue();

            // Tạo Embed chào mừng chi tiết
            EmbedBuilder embed = new EmbedBuilder();
            embed.setAuthor(member.getEffectiveName(), null, member.getUser().getEffectiveAvatarUrl());
            embed.setTitle("Chào mừng **" + member.getEffectiveName() + "** đã đến với **Chuồng Gà🐣**!");
            String rolesChannelMention = getRolesChannel(event.getGuild())
                    .map(GuildMessageChannel::getAsMention)
                    .orElse("# 👑 roles");
            String description = String.format("""
                    • **%s** sắp trở thành một phần của chúng ta!
                    • Bấm ✅ trong kênh %s để chấp nhận!
                    • Invite Link:
                    ```
                    %s
                    ```
                    """, member.getEffectiveName(), rolesChannelMention, Config.get("INVITE_LINK"));
            embed.setDescription(description);
            embed.setColor(Color.GREEN);
            embed.setThumbnail(member.getUser().getEffectiveAvatarUrl());
            embed.setFooter(timestamp);

            // Tạo nút bấm dẫn đến kênh roles
            Button rolesButton = getRolesChannel(event.getGuild())
                    .map(rolesChannel -> Button.link("https://discord.com/channels/" + event.getGuild().getId() + "/" + rolesChannel.getId(), "Đi đến 👑 roles"))
                    .orElse(Button.link("#", "Đi đến 👑 roles").asDisabled());

            // Gửi embed và nút
            channel.sendMessageEmbeds(embed.build())
                    .setComponents(ActionRow.of(rolesButton))
                    .queue();

            LOGGER.info("Gửi thông báo chào mừng cho {}.", member.getUser().getEffectiveName());
        });
    }

    // Thông báo TẠM BIỆT
    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        getWelcomeChannel(event.getGuild()).ifPresent(channel -> {
            net.dv8tion.jda.api.entities.User user = event.getUser();
            String timestamp = TIME_FORMATTER.format(OffsetDateTime.now());
            int memberCount = event.getGuild().getMemberCount();

            // Gửi tin nhắn tag người dùng trước embed
            String mentionMessage = "**" + user.getEffectiveName() + "** Đã cút khỏi **Chuồng Gà🐣**!";
            channel.sendMessage(mentionMessage).queue();

            // Tạo Embed tạm biệt
            EmbedBuilder embed = new EmbedBuilder();
            embed.setAuthor(user.getEffectiveName(), null, user.getEffectiveAvatarUrl());
            embed.setTitle("**" + user.getEffectiveName() + "** đã rời khỏi **Chuồng Gà🐣**!");
            String description = String.format("""
                    • **%s** Đã ra đi mãi mãi!
                    • Chuồng Gà🐣 vừa thiếu đi một người.
                    • Chúng ta còn lại **%d** thành viên.
                    """, user.getEffectiveName(), memberCount);
            embed.setDescription(description);
            embed.setColor(Color.RED);
            embed.setThumbnail(user.getEffectiveAvatarUrl());
            embed.setFooter(timestamp);
            // Gửi embed
            channel.sendMessageEmbeds(embed.build()).queue();

            LOGGER.info("Gửi thông báo tạm biệt cho {}.", user.getEffectiveName());
        });
    }

    // Thông báo BOOST NITRO
    @Override
    public void onGuildMemberUpdateBoostTime(@NotNull GuildMemberUpdateBoostTimeEvent event) {
        if (event.getNewTimeBoosted() != null) {
            getWelcomeChannel(event.getGuild()).ifPresent(channel -> {
                Member member = event.getMember();
                String timestamp = TIME_FORMATTER.format(OffsetDateTime.now());

                // Lấy thông tin Boost hiện tại của server
                int boostCount = event.getGuild().getBoostCount();
                int boostTier = event.getGuild().getBoostTier().getKey();

                // Gửi tin nhắn tag đại gia
                String mentionMessage = "Tuyệt vời! Cảm ơn đại gia " + member.getAsMention() + " đã tài trợ chương trình này!";
                channel.sendMessage(mentionMessage).queue();

                // Tạo Embed Boost
                EmbedBuilder embed = new EmbedBuilder();
                embed.setAuthor(member.getEffectiveName(), null, member.getUser().getEffectiveAvatarUrl());
                embed.setTitle("🚀 **" + member.getEffectiveName() + "** vừa Boost **Chuồng Gà🐣**!");
                String description = String.format("""
                        • Chuồng Gà🐣 vừa nhận được một cú Boost siêu to khổng lồ!
                        • Tổng số Boost hiện tại: **%d** (Cấp **%d**)
                        • Cảm ơn sự ủng hộ nhiệt tình của **%s**!
                        """, boostCount, boostTier, member.getEffectiveName());
                embed.setDescription(description);
                embed.setColor(new Color(244, 127, 255));
                embed.setThumbnail(member.getUser().getEffectiveAvatarUrl());
                embed.setFooter(timestamp);

                // Gửi embed
                channel.sendMessageEmbeds(embed.build()).queue();

                LOGGER.info("Gửi thông báo Boost thành công cho {}.", member.getUser().getEffectiveName());
            });
        }
    }
}