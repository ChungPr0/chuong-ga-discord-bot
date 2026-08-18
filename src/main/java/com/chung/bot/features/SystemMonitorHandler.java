package com.chung.bot.features;

import com.chung.bot.config.Config;
import com.chung.bot.database.DatabaseManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import java.awt.Color;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SystemMonitorHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SystemMonitorHandler.class);
    private final JDA jda;
    private final BeszelClient beszelClient;
    private final ScheduledExecutorService scheduler;
    private final long startTime;

    public SystemMonitorHandler(JDA jda) {
        this.jda = jda;
        this.beszelClient = new BeszelClient();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.startTime = System.currentTimeMillis();
    }

    public void start() {
        String channelId = Config.get("STATUS_CHANNEL_ID");
        if (channelId == null || channelId.trim().isEmpty()) {
            LOGGER.info("Bỏ qua System Monitor Panel vì không tìm thấy STATUS_CHANNEL_ID trong .env");
            return;
        }

        LOGGER.info("Khởi chạy System Monitor Panel tự động cập nhật mỗi 60s cho kênh ID: {}", channelId);
        scheduler.scheduleAtFixedRate(this::updateStatus, 3, 60, TimeUnit.SECONDS);
    }

    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            LOGGER.info("Đã dừng scheduler của System Monitor Panel.");
        }
    }

    private void updateStatus() {
        try {
            String channelId = Config.get("STATUS_CHANNEL_ID");
            if (channelId == null || channelId.trim().isEmpty()) return;

            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) {
                LOGGER.warn("Không tìm thấy TextChannel với ID: {}", channelId);
                return;
            }

            BeszelClient.BeszelMetrics metrics = beszelClient.fetchMetrics();
            MessageEmbed embed = buildStatusEmbed(metrics);

            String savedMsgId = DatabaseManager.getInstance().getMetadata("system_status_message_id");

            if (savedMsgId != null && !savedMsgId.trim().isEmpty()) {
                channel.retrieveMessageById(savedMsgId).queue(
                        msg -> msg.editMessageEmbeds(embed).queue(
                                success -> LOGGER.debug("Đã cập nhật System Monitor Embed thành công."),
                                error -> sendNewMessage(channel, embed)
                        ),
                        error -> sendNewMessage(channel, embed)
                );
            } else {
                sendNewMessage(channel, embed);
            }
        } catch (Exception e) {
            LOGGER.error("Lỗi khi cập nhật System Monitor Panel: ", e);
        }
    }

    private void sendNewMessage(TextChannel channel, MessageEmbed embed) {
        channel.sendMessageEmbeds(embed).queue(msg -> {
            DatabaseManager.getInstance().saveMetadata("system_status_message_id", msg.getId());
            LOGGER.info("Đã gửi tin nhắn System Monitor Panel mới (ID: {})", msg.getId());
        }, error -> LOGGER.error("Không thể gửi tin nhắn System Monitor Panel: ", error));
    }

    private MessageEmbed buildStatusEmbed(BeszelClient.BeszelMetrics beszelMetrics) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("BẢNG ĐIỀU KHIỂN HỆ THỐNG");

        double cpuPercent;
        double ramPercent;
        String ramDetail;
        double diskPercent;
        String diskDetail;
        String dataSource;

        if (beszelMetrics.isAvailable()) {
            dataSource = "Beszel Monitoring Hub (" + beszelMetrics.getSystemName() + ")";
            cpuPercent = beszelMetrics.getCpuPercent();
            ramPercent = beszelMetrics.getRamPercent();
            ramDetail = String.format("%.2f GB / %.2f GB", beszelMetrics.getRamUsedGb(), beszelMetrics.getRamTotalGb());
            diskPercent = beszelMetrics.getDiskPercent();
            diskDetail = String.format("%.2f GB / %.2f GB", beszelMetrics.getDiskUsedGb(), beszelMetrics.getDiskTotalGb());
        } else {
            dataSource = "Java System Metrics (Fallback - Beszel Offline)";
            cpuPercent = getFallbackCpu();
            
            OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            long totalRam = osBean.getTotalMemorySize();
            long freeRam = osBean.getFreeMemorySize();
            long usedRam = totalRam - freeRam;
            ramPercent = (double) usedRam / totalRam * 100.0;
            ramDetail = String.format("%.2f GB / %.2f GB", usedRam / 1073741824.0, totalRam / 1073741824.0);

            File root = new File("/opt/discord-bot");
            if (!root.exists()) root = new File("/");
            long totalDisk = root.getTotalSpace();
            long freeDisk = root.getFreeSpace();
            long usedDisk = totalDisk - freeDisk;
            diskPercent = totalDisk > 0 ? (double) usedDisk / totalDisk * 100.0 : 0.0;
            diskDetail = String.format("%.2f GB / %.2f GB", usedDisk / 1073741824.0, totalDisk / 1073741824.0);
        }

        eb.setColor(new Color(52, 73, 94)); // Sleek dark slate theme

        long uptimeSeconds = (System.currentTimeMillis() - startTime) / 1000;
        String uptimeStr = formatUptime(uptimeSeconds);
        long gatewayPing = jda.getGatewayPing();

        eb.addField("CPU Usage", String.format("`%s` **%.1f%%**", getProgressBar(cpuPercent), cpuPercent), false);
        eb.addField("RAM Usage", String.format("`%s` **%.1f%%** (%s)", getProgressBar(ramPercent), ramPercent, ramDetail), false);
        eb.addField("Disk Storage", String.format("`%s` **%.1f%%** (%s)", getProgressBar(diskPercent), diskPercent, diskDetail), false);
        
        eb.addField("Trạng Thái Bot", String.format("Operational | Ping: `%d ms`", gatewayPing), true);
        eb.addField("Uptime", "`" + uptimeStr + "`", true);

        if (beszelMetrics.isAvailable() && beszelMetrics.getActiveContainers() > 0) {
            eb.addField("Containers", String.format("`%d active`", beszelMetrics.getActiveContainers()), true);
        }

        eb.addField("Nguồn Dữ Liệu", "`" + dataSource + "`", false);

        long nowEpoch = Instant.now().getEpochSecond();
        eb.setFooter("Tự động cập nhật mỗi 60s • Lần cuối cập nhật:");
        eb.setTimestamp(Instant.now());
        eb.setDescription("Cập nhật theo thời gian thực: <t:" + nowEpoch + ":R>");

        return eb.build();
    }

    private double getFallbackCpu() {
        try {
            OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            double load = osBean.getCpuLoad();
            return load >= 0 ? load * 100.0 : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String getProgressBar(double percent) {
        int totalBlocks = 16;
        int filled = (int) Math.round((percent / 100.0) * totalBlocks);
        filled = Math.max(0, Math.min(totalBlocks, filled));

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < totalBlocks; i++) {
            if (i < filled) {
                sb.append("█");
            } else {
                sb.append("-");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private String formatUptime(long totalSeconds) {
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (days > 0) {
            return String.format("%dn %dh %dm", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else {
            return String.format("%dm %ds", minutes, seconds);
        }
    }
}
