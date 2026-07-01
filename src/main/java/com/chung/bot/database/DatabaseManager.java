package com.chung.bot.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseManager.class);
    private static final String DB_URL = "jdbc:sqlite:/opt/discord-bot/bot_data.db";
    private static DatabaseManager instance;
    private Connection connection;

    private DatabaseManager() {
        init();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    private void init() {
        try {
            // Đảm bảo thư mục cha tồn tại
            File dbFile = new File("/opt/discord-bot/bot_data.db");
            File parentDir = dbFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (parentDir.mkdirs()) {
                    LOGGER.info("Đã tạo thư mục chứa database: {}", parentDir.getPath());
                }
            }

            // Nạp class Driver SQLite một cách tường minh
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(DB_URL);
            LOGGER.info("Kết nối thành công đến database SQLite tại: {}", DB_URL);

            createTables();
        } catch (Exception e) {
            LOGGER.error("Không thể khởi tạo kết nối database SQLite: ", e);
        }
    }

    private void createTables() {
        String sqlTempChannels = "CREATE TABLE IF NOT EXISTS temporary_channels (" +
                "channel_id INTEGER PRIMARY KEY, " +
                "owner_id INTEGER" +
                ");";

        String sqlMusicQueues = "CREATE TABLE IF NOT EXISTS music_queues (" +
                "queue_order INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "track_url TEXT" +
                ");";

        String sqlMetadata = "CREATE TABLE IF NOT EXISTS bot_metadata (" +
                "key TEXT PRIMARY KEY, " +
                "value TEXT" +
                ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sqlTempChannels);
            stmt.execute(sqlMusicQueues);
            stmt.execute(sqlMetadata);
            LOGGER.info("Khởi tạo cấu trúc các bảng SQLite thành công.");
        } catch (SQLException e) {
            LOGGER.error("Lỗi khi khởi tạo các bảng SQLite: ", e);
        }
    }

    private synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(DB_URL);
        }
        return connection;
    }

    public synchronized void saveTempChannel(long channelId, long ownerId) {
        String sql = "INSERT OR REPLACE INTO temporary_channels(channel_id, owner_id) VALUES(?, ?)";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setLong(1, channelId);
            pstmt.setLong(2, ownerId);
            pstmt.executeUpdate();
            LOGGER.debug("Đã lưu kênh tạm vào DB: {} - Chủ sở hữu: {}", channelId, ownerId);
        } catch (SQLException e) {
            LOGGER.error("Lỗi khi lưu kênh tạm vào DB: ", e);
        }
    }

    public synchronized void deleteTempChannel(long channelId) {
        String sql = "DELETE FROM temporary_channels WHERE channel_id = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setLong(1, channelId);
            pstmt.executeUpdate();
            LOGGER.debug("Đã xóa kênh tạm khỏi DB: {}", channelId);
        } catch (SQLException e) {
            LOGGER.error("Lỗi khi xóa kênh tạm khỏi DB: ", e);
        }
    }

    public synchronized List<Long> getAllTempChannels() {
        List<Long> list = new ArrayList<>();
        String sql = "SELECT channel_id FROM temporary_channels";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                list.add(rs.getLong("channel_id"));
            }
        } catch (SQLException e) {
            LOGGER.error("Lỗi khi lấy danh sách kênh tạm: ", e);
        }
        return list;
    }

    public synchronized Map<Long, Long> getAllTempChannelsWithOwner() {
        Map<Long, Long> map = new HashMap<>();
        String sql = "SELECT channel_id, owner_id FROM temporary_channels";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getLong("channel_id"), rs.getLong("owner_id"));
            }
        } catch (SQLException e) {
            LOGGER.error("Lỗi khi lấy bản đồ kênh tạm và chủ sở hữu: ", e);
        }
        return map;
    }

    public synchronized void saveQueue(List<String> urls) {
        String deleteSql = "DELETE FROM music_queues";
        String insertSql = "INSERT INTO music_queues(track_url) VALUES(?)";
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false); // Bắt đầu transaction

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(deleteSql);
            }

            if (urls != null && !urls.isEmpty()) {
                try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                    for (String url : urls) {
                        pstmt.setString(1, url);
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }
            }

            conn.commit();
            LOGGER.info("Đã lưu hàng đợi nhạc gồm {} bài hát vào DB.", urls == null ? 0 : urls.size());
        } catch (SQLException e) {
            LOGGER.error("Lỗi khi lưu hàng đợi nhạc vào DB: ", e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    LOGGER.error("Lỗi rollback transaction: ", ex);
                }
            }
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    LOGGER.error("Lỗi khôi phục autoCommit: ", e);
                }
            }
        }
    }

    public synchronized List<String> getSavedQueue() {
        List<String> list = new ArrayList<>();
        String sql = "SELECT track_url FROM music_queues ORDER BY queue_order ASC";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                list.add(rs.getString("track_url"));
            }
        } catch (SQLException e) {
            LOGGER.error("Lỗi khi lấy hàng đợi nhạc từ DB: ", e);
        }
        return list;
    }

    public synchronized void saveMetadata(String key, String value) {
        String sql = "INSERT OR REPLACE INTO bot_metadata(key, value) VALUES(?, ?)";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            if (value == null) {
                pstmt.setString(1, key);
                pstmt.setNull(2, Types.VARCHAR);
            } else {
                pstmt.setString(1, key);
                pstmt.setString(2, value);
            }
            pstmt.executeUpdate();
            LOGGER.debug("Đã lưu metadata vào DB: {} = {}", key, value);
        } catch (SQLException e) {
            LOGGER.error("Lỗi khi lưu metadata vào DB: ", e);
        }
    }

    public synchronized String getMetadata(String key) {
        String sql = "SELECT value FROM bot_metadata WHERE key = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, key);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("value");
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Lỗi khi lấy metadata từ DB: ", e);
        }
        return null;
    }


    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
                LOGGER.info("Đã đóng kết nối database SQLite.");
            } catch (SQLException e) {
                LOGGER.error("Lỗi khi đóng kết nối database SQLite: ", e);
            }
        }
    }
}
