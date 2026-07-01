package com.chung.bot.features;

import com.chung.bot.config.Config;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.modals.Modal;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.chung.bot.log.BotLogger;
import java.time.Instant;

import java.awt.Color;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class JoinToCreateHandler extends ListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JoinToCreateHandler.class);

    private final ConcurrentHashMap<String, String> channelOwners = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Long> panelMessages = new ConcurrentHashMap<>();

    private final Set<Long> processingUsers = ConcurrentHashMap.newKeySet();

    private final String triggerChannelId = Config.get("CREATE_VOICE_CHANNEL_ID");
    private final String chickenRoleId = Config.get("CHICKEN_ROLE_ID");

    public JoinToCreateHandler() {
        // Load existing temp channels from SQLite
        try {
            var db = com.chung.bot.database.DatabaseManager.getInstance();
            var saved = db.getAllTempChannelsWithOwner();
            for (var entry : saved.entrySet()) {
                channelOwners.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
            LOGGER.info("[JTC] Đã khôi phục {} kênh tạm từ database.", saved.size());
        } catch (Exception e) {
            LOGGER.error("[JTC] Lỗi khôi phục kênh tạm từ database: ", e);
        }
    }


    // =========================================================================
    // PHẦN 1: XỬ LÝ SỰ KIỆN VOICE (TẠO KÊNH, DỌN RÁC, AUTO-TRANSFER)
    // =========================================================================

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        AudioChannelUnion joinedChannel = event.getChannelJoined();
        AudioChannelUnion leftChannel = event.getChannelLeft();

        if (joinedChannel != null && joinedChannel.getId().equals(triggerChannelId)) {
            handleChannelCreation(event);
        }

        if (leftChannel != null && channelOwners.containsKey(leftChannel.getId())) {
            handleChannelLeave(event, leftChannel);
        }
    }

    private void handleChannelCreation(GuildVoiceUpdateEvent event) {
        Member member = event.getMember();
        Guild guild = event.getGuild();
        VoiceChannel triggerChannel = guild.getVoiceChannelById(triggerChannelId);
        if (triggerChannel == null) return;

        long userId = member.getIdLong();
        if (!processingUsers.add(userId)) {
            LOGGER.info("[JTC] Người dùng {} đang trong quá trình tạo kênh/di chuyển, bỏ qua event", member.getEffectiveName());
            return;
        }

        Category category = triggerChannel.getParentCategory();
        String channelName = "┗ " + member.getEffectiveName();

        var createAction = (category != null)
                ? category.createVoiceChannel(channelName)
                : guild.createVoiceChannel(channelName);

        createAction.queue(newChannel -> {
                    channelOwners.put(newChannel.getId(), member.getId());
                    com.chung.bot.database.DatabaseManager.getInstance().saveTempChannel(newChannel.getIdLong(), userId);
                    LOGGER.info("[JTC] Tạo kênh '{}' cho {}", channelName, member.getEffectiveName());

                    guild.moveVoiceMember(member, newChannel).queue(
                            success -> {
                                sendControlPanel(newChannel, member);
                                processingUsers.remove(userId);
                            },
                            error -> {
                                LOGGER.error("[JTC] Không thể di chuyển thành viên {}: {}", member.getEffectiveName(), error.getMessage());
                                BotLogger.error("Lỗi Di Chuyển Thành Viên", "Không thể di chuyển thành viên " + member.getEffectiveName() + " vào kênh thoại mới: " + newChannel.getName(), error);
                                processingUsers.remove(userId);
                            }
                    );
                },
                error -> {
                    LOGGER.error("[JTC] Không thể tạo kênh '{}' cho {}: {}", channelName, member.getEffectiveName(), error.getMessage());
                    BotLogger.error("Lỗi Tạo Kênh Thoại JTC", "Không thể tạo kênh '" + channelName + "' cho " + member.getEffectiveName(), error);
                    processingUsers.remove(userId);
                });
    }

    private void handleChannelLeave(GuildVoiceUpdateEvent event, AudioChannelUnion channel) {
        VoiceChannel vc = channel.asVoiceChannel();
        String channelId = vc.getId();

        if (vc.getMembers().isEmpty()) {
            channelOwners.remove(channelId);
            panelMessages.remove(channelId);
            com.chung.bot.database.DatabaseManager.getInstance().deleteTempChannel(vc.getIdLong());
            vc.delete().queue(
                    success -> LOGGER.info("[JTC] Đã xoá kênh trống: {}", vc.getName()),
                    error -> LOGGER.error("[JTC] Lỗi xoá kênh {}: {}", vc.getName(), error.getMessage())
            );
            return;
        }

        String leavingUserId = event.getMember().getId();
        String currentOwnerId = channelOwners.get(channelId);

        if (currentOwnerId != null && currentOwnerId.equals(leavingUserId)) {
            List<Member> remaining = vc.getMembers().stream()
                    .filter(m -> !m.getUser().isBot())
                    .collect(Collectors.toList());

            if (remaining.isEmpty()) return;

            Member newOwner = remaining.get(0);

            channelOwners.put(channelId, newOwner.getId());
            com.chung.bot.database.DatabaseManager.getInstance().saveTempChannel(vc.getIdLong(), newOwner.getIdLong());
            LOGGER.info("[JTC] Auto-transfer kênh {} từ {} sang {}",
                    vc.getName(), event.getMember().getEffectiveName(), newOwner.getEffectiveName());

            Long panelMsgId = panelMessages.get(channelId);
            if (panelMsgId != null) {
                vc.retrieveMessageById(panelMsgId).queue(msg -> {
                    msg.editMessageEmbeds(buildPanelEmbed(newOwner).build()).queue();
                }, error -> LOGGER.warn("[JTC] Không tìm thấy panel message để edit"));
            }

            vc.sendMessage("Chủ phòng đã rời đi! Quyền quản lý được tự động chuyển cho "
                    + newOwner.getAsMention() + ".").queue();
        }
    }

    // =========================================================================
    // PHẦN 2: GỬI CONTROL PANEL
    // =========================================================================

    private EmbedBuilder buildPanelEmbed(Member owner) {
        return new EmbedBuilder()
                .setAuthor("Bảng điều khiển của " + owner.getEffectiveName(),
                        null, owner.getEffectiveAvatarUrl())
                .setDescription(
                        "Sử dụng các nút bên dưới để quản lý kênh.\n" +
                        "Chỉ **chủ phòng** mới có quyền thao tác."
                )
                .setColor(Color.decode("#3498db"))
                .setThumbnail(owner.getEffectiveAvatarUrl());
    }

    private void sendControlPanel(VoiceChannel voiceChannel, Member owner) {
        ActionRow row1 = ActionRow.of(
                Button.danger("jtc:lock", "🔒 Khóa kênh"),
                Button.secondary("jtc:hide", "👁️ Ẩn kênh"),
                Button.primary("jtc:rename", "✏️ Đổi tên"),
                Button.primary("jtc:set_limit", "👥 Giới hạn")
        );

        ActionRow row2 = ActionRow.of(
                Button.danger("jtc:kick", "👢 Kick"),
                Button.secondary("jtc:transfer", "👑 Chuyển quyền")
        );

        voiceChannel.sendMessageEmbeds(buildPanelEmbed(owner).build())
                .setComponents(row1, row2)
                .queue(message -> {
                    // Lưu Message ID để edit embed khi đổi chủ phòng
                    panelMessages.put(voiceChannel.getId(), message.getIdLong());
                });
    }

    // =========================================================================
    // PHẦN 3: XỬ LÝ SỰ KIỆN NÚT BẤM (BUTTON INTERACTION)
    // =========================================================================

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        if (!buttonId.startsWith("jtc:")) return;

        Member member = event.getMember();
        if (member == null) return;

        String channelId = event.getChannel().getId();

        if (!isOwner(channelId, member.getId())) {
            event.reply("Bạn không phải chủ phòng! Chỉ chủ phòng mới được thao tác.")
                    .setEphemeral(true).queue();
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) return;

        VoiceChannel voiceChannel = guild.getVoiceChannelById(channelId);
        if (voiceChannel == null) {
            event.reply("Không tìm thấy kênh voice!").setEphemeral(true).queue();
            return;
        }

        switch (buttonId) {
            case "jtc:lock":
                handleLock(event, voiceChannel, guild);
                break;
            case "jtc:unlock":
                handleUnlock(event, voiceChannel, guild);
                break;
            case "jtc:hide":
                handleHide(event, voiceChannel, guild);
                break;
            case "jtc:show":
                handleShow(event, voiceChannel, guild);
                break;
            case "jtc:rename":
                handleRenameModal(event);
                break;
            case "jtc:set_limit":
                handleSetLimitModal(event);
                break;
            case "jtc:kick":
                handleKickMenu(event, voiceChannel, member);
                break;
            case "jtc:transfer":
                handleTransferMenu(event, voiceChannel, member);
                break;
        }
    }

    private void handleLock(ButtonInteractionEvent event, VoiceChannel vc, Guild guild) {
        Role chickenRole = guild.getRoleById(chickenRoleId);
        vc.upsertPermissionOverride(guild.getPublicRole())
                .deny(Permission.VOICE_CONNECT).queue();
        if (chickenRole != null) {
            vc.upsertPermissionOverride(chickenRole)
                    .deny(Permission.VOICE_CONNECT)
                    .queue(success -> {
                        updatePanelButtons(event, vc, guild);
                        String logMsg = String.format("• **Người thực hiện:** %s\n• **Lệnh thoại/Lệnh nút:** 🔒 Khóa kênh\n• **Phòng thoại:** `%s`\n• **Thời gian:** <t:%d:F>",
                                event.getMember().getAsMention(), vc.getName(), Instant.now().getEpochSecond());
                        BotLogger.info("Control Panel - Khóa phòng", logMsg);
                    });
        } else {
            updatePanelButtons(event, vc, guild);
            String logMsg = String.format("• **Người thực hiện:** %s\n• **Lệnh thoại/Lệnh nút:** 🔒 Khóa kênh\n• **Phòng thoại:** `%s`\n• **Thời gian:** <t:%d:F>",
                    event.getMember().getAsMention(), vc.getName(), Instant.now().getEpochSecond());
            BotLogger.info("Control Panel - Khóa phòng", logMsg);
        }
    }

    private void handleUnlock(ButtonInteractionEvent event, VoiceChannel vc, Guild guild) {
        Role chickenRole = guild.getRoleById(chickenRoleId);
        vc.upsertPermissionOverride(guild.getPublicRole())
                .clear(Permission.VOICE_CONNECT).queue();
        if (chickenRole != null) {
            vc.upsertPermissionOverride(chickenRole)
                    .clear(Permission.VOICE_CONNECT)
                    .queue(success -> {
                        updatePanelButtons(event, vc, guild);
                        String logMsg = String.format("• **Người thực hiện:** %s\n• **Lệnh thoại/Lệnh nút:** 🔓 Mở kênh\n• **Phòng thoại:** `%s`\n• **Thời gian:** <t:%d:F>",
                                event.getMember().getAsMention(), vc.getName(), Instant.now().getEpochSecond());
                        BotLogger.info("Control Panel - Mở phòng", logMsg);
                    });
        } else {
            updatePanelButtons(event, vc, guild);
            String logMsg = String.format("• **Người thực hiện:** %s\n• **Lệnh thoại/Lệnh nút:** 🔓 Mở kênh\n• **Phòng thoại:** `%s`\n• **Thời gian:** <t:%d:F>",
                    event.getMember().getAsMention(), vc.getName(), Instant.now().getEpochSecond());
            BotLogger.info("Control Panel - Mở phòng", logMsg);
        }
    }

    private void handleHide(ButtonInteractionEvent event, VoiceChannel vc, Guild guild) {
        Role chickenRole = guild.getRoleById(chickenRoleId);
        vc.upsertPermissionOverride(guild.getPublicRole())
                .deny(Permission.VIEW_CHANNEL).queue();
        if (chickenRole != null) {
            vc.upsertPermissionOverride(chickenRole)
                    .deny(Permission.VIEW_CHANNEL)
                    .queue(success -> {
                        updatePanelButtons(event, vc, guild);
                        String logMsg = String.format("• **Người thực hiện:** %s\n• **Lệnh thoại/Lệnh nút:** 👁️ Ẩn kênh\n• **Phòng thoại:** `%s`\n• **Thời gian:** <t:%d:F>",
                                event.getMember().getAsMention(), vc.getName(), Instant.now().getEpochSecond());
                        BotLogger.info("Control Panel - Ẩn phòng", logMsg);
                    });
        } else {
            updatePanelButtons(event, vc, guild);
            String logMsg = String.format("• **Người thực hiện:** %s\n• **Lệnh thoại/Lệnh nút:** 👁️ Ẩn kênh\n• **Phòng thoại:** `%s`\n• **Thời gian:** <t:%d:F>",
                    event.getMember().getAsMention(), vc.getName(), Instant.now().getEpochSecond());
            BotLogger.info("Control Panel - Ẩn phòng", logMsg);
        }
    }

    private void handleShow(ButtonInteractionEvent event, VoiceChannel vc, Guild guild) {
        Role chickenRole = guild.getRoleById(chickenRoleId);
        vc.upsertPermissionOverride(guild.getPublicRole())
                .clear(Permission.VIEW_CHANNEL).queue();
        if (chickenRole != null) {
            vc.upsertPermissionOverride(chickenRole)
                    .clear(Permission.VIEW_CHANNEL)
                    .queue(success -> {
                        updatePanelButtons(event, vc, guild);
                        String logMsg = String.format("• **Người thực hiện:** %s\n• **Lệnh thoại/Lệnh nút:** 👁️ Hiện kênh\n• **Phòng thoại:** `%s`\n• **Thời gian:** <t:%d:F>",
                                event.getMember().getAsMention(), vc.getName(), Instant.now().getEpochSecond());
                        BotLogger.info("Control Panel - Hiện phòng", logMsg);
                    });
        } else {
            updatePanelButtons(event, vc, guild);
            String logMsg = String.format("• **Người thực hiện:** %s\n• **Lệnh thoại/Lệnh nút:** 👁️ Hiện kênh\n• **Phòng thoại:** `%s`\n• **Thời gian:** <t:%d:F>",
                    event.getMember().getAsMention(), vc.getName(), Instant.now().getEpochSecond());
            BotLogger.info("Control Panel - Hiện phòng", logMsg);
        }
    }

    private void handleRenameModal(ButtonInteractionEvent event) {
        TextInput nameInput = TextInput.create("jtc:rename_input", TextInputStyle.SHORT)
                .setPlaceholder("Nhập tên phòng mới...")
                .setMinLength(1)
                .setMaxLength(50)
                .setRequired(true)
                .build();

        Label nameLabel = Label.of("Tên phòng mới", nameInput);

        Modal modal = Modal.create("jtc:modal_rename", "✏️ Đổi Tên Phòng")
                .addComponents(nameLabel)
                .build();

        event.replyModal(modal).queue();
    }

    private void handleSetLimitModal(ButtonInteractionEvent event) {
        TextInput limitInput = TextInput.create("jtc:limit_input", TextInputStyle.SHORT)
                .setPlaceholder("Nhập số từ 0 đến 99 (0 = không giới hạn)")
                .setMinLength(1)
                .setMaxLength(2)
                .setRequired(true)
                .build();

        Label limitLabel = Label.of("Giới hạn số người", limitInput);

        Modal modal = Modal.create("jtc:modal_limit", "👥 Thiết Lập Giới Hạn")
                .addComponents(limitLabel)
                .build();

        event.replyModal(modal).queue();
    }

    private void handleKickMenu(ButtonInteractionEvent event, VoiceChannel vc, Member owner) {
        List<Member> members = vc.getMembers().stream()
                .filter(m -> !m.getId().equals(owner.getId()))
                .filter(m -> !m.getUser().isBot())
                .collect(Collectors.toList());

        if (members.isEmpty()) {
            event.reply("Không có ai khác trong kênh để kick!").setEphemeral(true).queue();
            return;
        }

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("jtc:kick_select")
                .setPlaceholder("Chọn người muốn kick khỏi phòng...");
        for (Member m : members) {
            menuBuilder.addOption(m.getEffectiveName(), m.getId());
        }

        event.reply("Chọn người bạn muốn kick:")
                .addComponents(ActionRow.of(menuBuilder.build()))
                .setEphemeral(true).queue();
    }

    private void handleTransferMenu(ButtonInteractionEvent event, VoiceChannel vc, Member owner) {
        List<Member> members = vc.getMembers().stream()
                .filter(m -> !m.getId().equals(owner.getId()))
                .filter(m -> !m.getUser().isBot())
                .collect(Collectors.toList());

        if (members.isEmpty()) {
            event.reply("Không có ai khác trong kênh để chuyển quyền!").setEphemeral(true).queue();
            return;
        }

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("jtc:transfer_select")
                .setPlaceholder("Chọn người muốn chuyển quyền chủ phòng...");
        for (Member m : members) {
            menuBuilder.addOption(m.getEffectiveName(), m.getId());
        }

        event.reply("Chọn người bạn muốn chuyển quyền:")
                .addComponents(ActionRow.of(menuBuilder.build()))
                .setEphemeral(true).queue();
    }

    // =========================================================================
    // PHẦN 4: XỬ LÝ SỰ KIỆN MODAL (GIỚI HẠN + ĐỔI TÊN)
    // =========================================================================

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        String modalId = event.getModalId();
        if (!modalId.startsWith("jtc:")) return;

        String channelId = event.getChannel().getId();
        Member member = event.getMember();
        if (member == null) return;

        if (!isOwner(channelId, member.getId())) {
            event.reply("Bạn không phải chủ phòng!").setEphemeral(true).queue();
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) return;

        VoiceChannel voiceChannel = guild.getVoiceChannelById(channelId);
        if (voiceChannel == null) {
            event.reply("Không tìm thấy kênh voice!").setEphemeral(true).queue();
            return;
        }

        switch (modalId) {
            case "jtc:modal_limit":
                handleLimitSubmit(event, voiceChannel);
                break;
            case "jtc:modal_rename":
                handleRenameSubmit(event, voiceChannel);
                break;
        }
    }

    private void handleLimitSubmit(ModalInteractionEvent event, VoiceChannel vc) {
        String inputValue = event.getValue("jtc:limit_input").getAsString().trim();
        try {
            int limit = Integer.parseInt(inputValue);
            if (limit < 0 || limit > 99) {
                event.reply("Vui lòng nhập số từ 0 đến 99!").setEphemeral(true).queue();
                return;
            }
            vc.getManager().setUserLimit(limit).queue(
                    success -> {
                        String msg = (limit == 0)
                                ? "Đã **bỏ giới hạn** số người trong kênh!"
                                : "Đã đặt giới hạn kênh thành **" + limit + " người**!";
                        event.reply(msg).setEphemeral(true).queue();
                        String logMsg = String.format("• **Người thực hiện:** %s\n• **Lệnh thoại/Lệnh nút:** 👥 Giới hạn số người -> `%s`\n• **Phòng thoại:** `%s`\n• **Thời gian:** <t:%d:F>",
                                event.getMember().getAsMention(), limit == 0 ? "Không giới hạn" : limit + " người", vc.getName(), Instant.now().getEpochSecond());
                        BotLogger.info("Control Panel - Giới hạn kênh", logMsg);
                    },
                    error -> {
                        event.reply("Lỗi khi đặt giới hạn!").setEphemeral(true).queue();
                        BotLogger.error("Lỗi Đặt Giới Hạn", "Không thể đặt giới hạn kênh thành " + limit + " cho phòng " + vc.getName(), error);
                    }
            );
        } catch (NumberFormatException e) {
            event.reply("Giá trị không hợp lệ! Vui lòng nhập một số nguyên.").setEphemeral(true).queue();
        }
    }

    private void handleRenameSubmit(ModalInteractionEvent event, VoiceChannel vc) {
        String newName = event.getValue("jtc:rename_input").getAsString().trim();
        if (newName.isEmpty()) {
            event.reply("Tên kênh không được để trống!").setEphemeral(true).queue();
            return;
        }
        String oldName = vc.getName();
        String formattedName = "┗ " + newName;
        vc.getManager().setName(formattedName).queue(
                success -> {
                    event.reply("Đã đổi tên phòng thành **" + formattedName + "**!")
                            .setEphemeral(true).queue();
                    String logMsg = String.format("• **Người thực hiện:** %s\n• **Lệnh thoại/Lệnh nút:** ✏️ Đổi tên\n• **Tên cũ:** `%s`\n• **Tên mới:** `%s`\n• **Thời gian:** <t:%d:F>",
                            event.getMember().getAsMention(), oldName, formattedName, Instant.now().getEpochSecond());
                    BotLogger.info("Control Panel - Đổi tên", logMsg);
                },
                error -> {
                    event.reply("Lỗi khi đổi tên phòng!").setEphemeral(true).queue();
                    BotLogger.error("Lỗi Đổi Tên Phòng", "Không thể đổi tên phòng thành " + formattedName + " cho phòng " + oldName, error);
                }
        );
    }

    // =========================================================================
    // PHẦN 5: XỬ LÝ SỰ KIỆN SELECT MENU (KICK & CHUYỂN QUYỀN)
    // =========================================================================

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        String menuId = event.getComponentId();
        if (!menuId.startsWith("jtc:")) return;

        Member member = event.getMember();
        if (member == null) return;

        Guild guild = event.getGuild();
        if (guild == null) return;

        String targetUserId = event.getValues().get(0);

        switch (menuId) {
            case "jtc:kick_select":
                handleKickAction(event, guild, member, targetUserId);
                break;
            case "jtc:transfer_select":
                handleTransferAction(event, guild, member, targetUserId);
                break;
        }
    }

    private void handleKickAction(StringSelectInteractionEvent event, Guild guild, Member owner, String targetUserId) {
        String channelId = findOwnerVoiceChannelId(owner);
        if (channelId == null || !isOwner(channelId, owner.getId())) {
            event.reply("Bạn không phải chủ phòng hoặc không trong kênh!").setEphemeral(true).queue();
            return;
        }
        Member target = guild.getMemberById(targetUserId);
        if (target == null) {
            event.reply("Không tìm thấy người dùng này!").setEphemeral(true).queue();
            return;
        }
        VoiceChannel vc = guild.getVoiceChannelById(channelId);
        String channelName = vc != null ? vc.getName() : channelId;
        guild.kickVoiceMember(target).queue(
                success -> {
                    event.reply("Đã kick **" + target.getEffectiveName() + "** khỏi phòng!")
                            .setEphemeral(true).queue();
                    String logMsg = String.format("• **Người thực hiện:** %s\n• **Lệnh thoại/Lệnh nút:** 👢 Kick\n• **Bị kick:** %s\n• **Phòng thoại:** `%s`\n• **Thời gian:** <t:%d:F>",
                            owner.getAsMention(), target.getAsMention(), channelName, Instant.now().getEpochSecond());
                    BotLogger.info("Control Panel - Kick thành viên", logMsg);
                },
                error -> {
                    event.reply("Không thể kick người này!").setEphemeral(true).queue();
                    BotLogger.error("Lỗi Kick Thành Viên", "Không thể kick " + target.getEffectiveName() + " khỏi phòng " + channelName, error);
                }
        );
    }

    private void handleTransferAction(StringSelectInteractionEvent event, Guild guild, Member owner, String targetUserId) {
        String channelId = findOwnerVoiceChannelId(owner);
        if (channelId == null || !isOwner(channelId, owner.getId())) {
            event.reply("Bạn không phải chủ phòng hoặc không trong kênh!").setEphemeral(true).queue();
            return;
        }
        Member target = guild.getMemberById(targetUserId);
        if (target == null) {
            event.reply("Không tìm thấy người dùng này!").setEphemeral(true).queue();
            return;
        }

        channelOwners.put(channelId, targetUserId);
        com.chung.bot.database.DatabaseManager.getInstance().saveTempChannel(Long.parseLong(channelId), Long.parseLong(targetUserId));
        LOGGER.info("[JTC] Chuyển quyền kênh {} từ {} sang {}",
                channelId, owner.getEffectiveName(), target.getEffectiveName());

        Long panelMsgId = panelMessages.get(channelId);
        VoiceChannel vc = guild.getVoiceChannelById(channelId);
        if (panelMsgId != null && vc != null) {
            vc.retrieveMessageById(panelMsgId).queue(
                    msg -> msg.editMessageEmbeds(buildPanelEmbed(target).build()).queue(),
                    error -> LOGGER.warn("[JTC] Không tìm thấy panel message để edit")
            );
        }

        event.reply("Đã chuyển quyền chủ phòng cho **" + target.getEffectiveName() + "**! 👑")
                .setEphemeral(true).queue();

        String channelName = vc != null ? vc.getName() : channelId;
        String logMsg = String.format("• **Người thực hiện:** %s\n• **Lệnh thoại/Lệnh nút:** 👑 Chuyển quyền\n• **Chuyển quyền cho:** %s\n• **Phòng thoại:** `%s`\n• **Thời gian:** <t:%d:F>",
                owner.getAsMention(), target.getAsMention(), channelName, Instant.now().getEpochSecond());
        BotLogger.info("Control Panel - Chuyển quyền", logMsg);
    }

    // =========================================================================
    // PHẦN 6: HÀM TIỆN ÍCH
    // =========================================================================

    private boolean isOwner(String channelId, String userId) {
        return userId.equals(channelOwners.get(channelId));
    }

    private String findOwnerVoiceChannelId(Member member) {
        if (member.getVoiceState() == null || !member.getVoiceState().inAudioChannel()) {
            return null;
        }
        String currentChannelId = member.getVoiceState().getChannel().getId();
        return channelOwners.containsKey(currentChannelId) ? currentChannelId : null;
    }

    private void updatePanelButtons(ButtonInteractionEvent event, VoiceChannel vc, Guild guild) {
        Role chickenRole = guild.getRoleById(chickenRoleId);
        var roleOverride = (chickenRole != null) ? vc.getPermissionOverride(chickenRole) : null;
        boolean isLocked = roleOverride != null && roleOverride.getDenied().contains(Permission.VOICE_CONNECT);
        boolean isHidden = roleOverride != null && roleOverride.getDenied().contains(Permission.VIEW_CHANNEL);

        Button lockBtn = isLocked
                ? Button.success("jtc:unlock", "🔓 Mở kênh")
                : Button.danger("jtc:lock", "🔒 Khóa kênh");
        Button hideBtn = isHidden
                ? Button.primary("jtc:show", "👁️ Hiện kênh")
                : Button.secondary("jtc:hide", "👁️ Ẩn kênh");
        Button renameBtn = Button.primary("jtc:rename", "✏️ Đổi tên");
        Button limitBtn = Button.primary("jtc:set_limit", "👥 Giới hạn");
        Button kickBtn = Button.danger("jtc:kick", "👢 Kick");
        Button transferBtn = Button.secondary("jtc:transfer", "👑 Chuyển quyền");

        event.editComponents(
                ActionRow.of(lockBtn, hideBtn, renameBtn, limitBtn),
                ActionRow.of(kickBtn, transferBtn)
        ).queue();
    }
}
