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
import java.util.ArrayList;
import java.util.List;

public class BeszelClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(BeszelClient.class);
    private final HttpClient httpClient;
    private final List<String> candidateUrls;
    private String authToken = null;

    public BeszelClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();
        
        this.candidateUrls = new ArrayList<>();
        String envUrl = Config.get("BESZEL_URL");
        if (envUrl != null && !envUrl.trim().isEmpty()) {
            candidateUrls.add(cleanUrl(envUrl));
        }
        candidateUrls.add("http://localhost:8090");
        candidateUrls.add("http://172.17.0.1:8090");
        candidateUrls.add("http://host.docker.internal:8090");
    }

    private String cleanUrl(String url) {
        url = url.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
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
        private String connectedUrl;

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
        public String getConnectedUrl() { return connectedUrl; }
    }

    private String authenticate(String baseUrl) {
        String user = Config.get("BESZEL_USER");
        String pass = Config.get("BESZEL_PASS");

        if (user == null || pass == null || user.trim().isEmpty() || pass.trim().isEmpty()) {
            LOGGER.debug("Không tìm thấy BESZEL_USER / BESZEL_PASS trong .env");
            return null;
        }

        String[] authEndpoints = {
            baseUrl + "/api/collections/users/auth-with-password",
            baseUrl + "/api/collections/_superusers/auth-with-password",
            baseUrl + "/api/admins/auth-with-password"
        };

        for (String endpoint : authEndpoints) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("identity", user.trim());
                payload.put("password", pass.trim());

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(4))
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JSONObject resJson = new JSONObject(response.body());
                    String token = resJson.optString("token", null);
                    if (token != null) {
                        LOGGER.info("Xác thực tài khoản Beszel thành công tại {}", endpoint);
                        return token;
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Đăng nhập Beszel thất bại tại {}: {}", endpoint, e.getMessage());
            }
        }

        LOGGER.warn("Đăng nhập Beszel thất bại với tài khoản: {}", user);
        return null;
    }

    public BeszelMetrics fetchMetrics() {
        for (String baseUrl : candidateUrls) {
            if (authToken == null) {
                authToken = authenticate(baseUrl);
            }

            BeszelMetrics metrics = queryBeszelRecords(baseUrl, authToken);
            if (metrics.isAvailable()) {
                return metrics;
            }

            authToken = authenticate(baseUrl);
            metrics = queryBeszelRecords(baseUrl, authToken);
            if (metrics.isAvailable()) {
                return metrics;
            }
        }

        LOGGER.error("Không thể lấy dữ liệu từ Beszel API.");
        return new BeszelMetrics(false);
    }

    private BeszelMetrics queryBeszelRecords(String baseUrl, String token) {
        String apiUrl = baseUrl + "/api/collections/systems/records";
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(3))
                    .GET();

            if (token != null && !token.trim().isEmpty()) {
                builder.header("Authorization", token);
            }

            HttpRequest request = builder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                if (json.has("items")) {
                    JSONArray items = json.getJSONArray("items");
                    if (items.length() > 0) {
                        JSONObject sys = items.getJSONObject(0);
                        BeszelMetrics metrics = new BeszelMetrics(true);
                        metrics.connectedUrl = baseUrl;
                        metrics.systemName = sys.optString("name", "VPS Host");

                        parseMetricsData(sys, metrics);

                        if (sys.has("containers")) {
                            JSONArray containers = sys.getJSONArray("containers");
                            metrics.activeContainers = containers.length();
                        }

                        LOGGER.info("Lấy dữ liệu Beszel thành công từ {}", baseUrl);
                        return metrics;
                    } else {
                        LOGGER.warn("Beszel API tại {} trả về danh sách rỗng (Cần đăng nhập BESZEL_USER và BESZEL_PASS)", apiUrl);
                    }
                }
            } else {
                LOGGER.warn("Beszel API tại {} trả về HTTP status: {}", apiUrl, response.statusCode());
            }
        } catch (Exception e) {
            LOGGER.warn("Thử kết nối Beszel thất bại tại {}: {}", apiUrl, e.getMessage());
        }

        return new BeszelMetrics(false);
    }

    private void parseMetricsData(JSONObject sys, BeszelMetrics metrics) {
        extractFromObject(sys, metrics);

        if (sys.has("info")) {
            Object infoObj = sys.get("info");
            if (infoObj instanceof JSONObject) {
                extractFromObject((JSONObject) infoObj, metrics);
            } else if (infoObj instanceof String && !((String) infoObj).isEmpty()) {
                try {
                    extractFromObject(new JSONObject((String) infoObj), metrics);
                } catch (Exception ignored) {}
            }
        }

        if (sys.has("stats")) {
            Object statsObj = sys.get("stats");
            if (statsObj instanceof JSONObject) {
                extractFromObject((JSONObject) statsObj, metrics);
            } else if (statsObj instanceof String && !((String) statsObj).isEmpty()) {
                try {
                    extractFromObject(new JSONObject((String) statsObj), metrics);
                } catch (Exception ignored) {}
            }
        }
    }

    private void extractFromObject(JSONObject obj, BeszelMetrics metrics) {
        if (obj.has("cpu")) metrics.cpuPercent = obj.optDouble("cpu", metrics.cpuPercent);
        if (obj.has("cp")) metrics.cpuPercent = obj.optDouble("cp", metrics.cpuPercent);

        if (obj.has("mp")) metrics.ramPercent = obj.optDouble("mp", metrics.ramPercent);
        if (obj.has("mu")) metrics.ramUsedGb = obj.optDouble("mu", metrics.ramUsedGb);
        if (obj.has("m")) metrics.ramTotalGb = obj.optDouble("m", metrics.ramTotalGb);

        if (obj.has("dp")) metrics.diskPercent = obj.optDouble("dp", metrics.diskPercent);
        if (obj.has("du")) metrics.diskUsedGb = obj.optDouble("du", metrics.diskUsedGb);
        if (obj.has("d")) metrics.diskTotalGb = obj.optDouble("d", metrics.diskTotalGb);
    }
}
