package com.chung.bot.config;

import io.github.cdimascio.dotenv.Dotenv;

public class Config {
    private static Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    private static String overridenYoutubeToken = null;

    public static String get(String key) {
        if ("YOUTUBE_OAUTH_REFRESH_TOKEN".equals(key) && overridenYoutubeToken != null) {
            return overridenYoutubeToken;
        }
        String value = System.getenv(key);
        if (value == null || value.isEmpty()) {
            value = dotenv.get(key);
        }
        return value;
    }

    public static void setYoutubeToken(String token) {
        overridenYoutubeToken = token;
        // Reload lại dotenv để phản ánh các thay đổi trong file .env mới lưu
        dotenv = Dotenv.configure().ignoreIfMissing().load();
    }
}