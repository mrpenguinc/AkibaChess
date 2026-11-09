package com.mychess.db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class DatabaseService {
    private String dbPath;

    public void connect(String filepath) throws SQLException {
        this.dbPath = "jdbc:sqlite:" + filepath;
        try (Connection conn = DriverManager.getConnection(dbPath);
             Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS games (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "white TEXT," +
                    "black TEXT," +
                    "event TEXT," +
                    "date TEXT," +
                    "result TEXT," +
                    "pgn TEXT" +
                    ");";
            stmt.execute(sql);
        }
    }

    public int getGameCount(String targetDbPath) {
        String url = "jdbc:sqlite:" + targetDbPath;
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(id) FROM games")) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException ignored) {}
        return 0;
    }

    public void deleteGame(int id) throws SQLException {
        if (dbPath == null) throw new SQLException("No database connected.");
        String sql = "DELETE FROM games WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(dbPath); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    public int saveGame(int id, String white, String black, String event, String date, String result, String pgn) throws SQLException {
        if (dbPath == null) throw new SQLException("No database connected.");
        
        if (id == -1) {
            String sql = "INSERT INTO games(white, black, event, date, result, pgn) VALUES(?,?,?,?,?,?)";
            try (Connection conn = DriverManager.getConnection(dbPath); 
                 PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, white); pstmt.setString(2, black); pstmt.setString(3, event);
                pstmt.setString(4, date); pstmt.setString(5, result); pstmt.setString(6, pgn);
                pstmt.executeUpdate();
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } else {
            String sql = "UPDATE games SET white=?, black=?, event=?, date=?, result=?, pgn=? WHERE id=?";
            try (Connection conn = DriverManager.getConnection(dbPath); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, white); pstmt.setString(2, black); pstmt.setString(3, event);
                pstmt.setString(4, date); pstmt.setString(5, result); pstmt.setString(6, pgn);
                pstmt.setInt(7, id);
                pstmt.executeUpdate();
            }
            return id; 
        }
        return -1;
    }

    public String getGamePgn(int id) throws SQLException {
        String sql = "SELECT pgn FROM games WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(dbPath); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getString("pgn");
        }
        return null;
    }

    public List<Object[]> getAllGamesMetadata() throws SQLException {
        List<Object[]> games = new ArrayList<>();
        if (dbPath == null) return games;
        
        // Now fetching 'pgn' as well to dynamically extract tags
        String sql = "SELECT id, white, black, result, event, date, pgn FROM games ORDER BY id DESC";
        
        Pattern bookPattern = Pattern.compile("\\[Book\\s+\"([^\"]+)\"\\]");
        Pattern puzzlePattern = Pattern.compile("\\[Puzzle\\s+\"([^\"]+)\"\\]");

        try (Connection conn = DriverManager.getConnection(dbPath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String pgn = rs.getString("pgn");
                
                games.add(new Object[]{
                    rs.getInt("id"),
                    rs.getString("white"),
                    rs.getString("black"),
                    rs.getString("result"),
                    rs.getString("event"),
                    rs.getString("date"),
                    extractCustomTag(pgn, bookPattern),
                    extractCustomTag(pgn, puzzlePattern)
                });
            }
        }
        return games;
    }
    
    private String extractCustomTag(String pgn, Pattern pattern) {
        if (pgn == null) return "";
        java.util.regex.Matcher matcher = pattern.matcher(pgn);
        if (matcher.find()) return matcher.group(1);
        return "";
    }
}