package com.chung.bot.features;

import com.chung.bot.config.Config;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class BeszelClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(BeszelClient.class);
    private final HttpClient httpClient;
    private final String beszelUrl;

    public BeszelClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        
        String url = Config.get("BESZEL_URL");
        if (url == null || url.trim().isEmpty()) {
            url = "http://localhost:8090";
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        this.beszelUrl = url;
    }

    public static class BeszelMetrics {
        private final boolean available;
        private String systemName = "VPS Host";
        private double cpuPercent;
        private double ramPercent;
        private double ramUsedGb;
        private double ramTotalGb;
        private double diskPercent;
        private double diskUsedGb;
        private double diskTotalGb;
        private int activeContainers;

        public BeszelMetrics(boolean available) {
            this.available = available;
        }

        public boolean isAvailable() { return available; }
        public String getSystemName() { return systemName; }
        public double getCpuPercent() { return cpuPercent; }
        public double getRamPercent() { return ramPercent; }
        public double getRamUsedGb() { return ramUsedGb; }
        public double getRamTotalGb() { return ramTotalGb; }
        public double getDiskPercent() { return diskPercent; }
        public double getDiskUsedGb() { return diskUsedGb; }
        public double getDiskTotalGb() { return diskTotalGb; }
        public int getActiveContainers() { return activeContainers; }
    }

    public BeszelMetrics fetchMetrics() {
        String apiUrl = beszelUrl + "/api/collections/systems/records";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                if (json.has("items")) {
                    JSONArray items = json.getJSONArray("items");
                    if (items.length() > 0) {
                        JSONObject sys = items.getJSONObject(0);
                        BeszelMetrics metrics = new BeszelMetrics(true);
                        metrics.systemName = sys.optString("name", "VPS Host");

                        if (sys.has("info")) {
                            JSONObject info = sys.getJSONObject("info");
                            metrics.cpuPercent = info.optDouble("cpu", 0.0);
                            metrics.ramPercent = info.optDouble("mp", 0.0);
                            metrics.ramUsedGb = info.optDouble("mu", 0.0);
                            metrics.ramTotalGb = info.optDouble("m", 0.0);
                            metrics.diskPercent = info.optDouble("dp", 0.0);
                            metrics.diskUsedGb = info.optDouble("du", 0.0);
                            metrics.diskTotalGb = info.optDouble("d", 0.0);
                        }

                        if (sys.has("stats")) {
                            JSONObject stats = sys.getJSONObject("stats");
                            if (stats.has("cpu")) metrics.cpuPercent = stats.optDouble("cpu", metrics.cpuPercent);
                            if (stats.has("mp")) metrics.ramPercent = stats.optDouble("mp", metrics.ramPercent);
                            if (stats.has("dp")) metrics.diskPercent = stats.optDouble("dp", metrics.diskPercent);
                        }

                        if (sys.has("containers")) {
                            JSONArray containers = sys.getJSONArray("containers");
                            metrics.activeContainers = containers.length();
                        }

                        return metrics;
                    }
                }
            } else {
                LOGGER.warn("Beszel API trả về status code: {}", response.statusCode());
            }
        } catch (Exception e) {
            LOGGER.debug("Không thể lấy dữ liệu từ Beszel API tại {}: {}", apiUrl, e.getMessage());
        }

        return new BeszelMetrics(false);
    }
}
