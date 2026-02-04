package game.server;

import java.sql.*;

public class DatabaseManager {
    // Configuration MySQL (à adapter selon tes réglages)
    private static final String URL = "jdbc:mysql://localhost:3306/javakart_db?useSSL=false&serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASS = "root"; // Ton mot de passe MySQL

    static {
        try {
            // Explicitly try to load the MySQL driver for clearer error messages at startup
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("[DB] MySQL JDBC driver loaded.");
        } catch (ClassNotFoundException e) {
            System.err.println("[DB] MySQL JDBC driver not found on classpath: " + e.getMessage());
        }
    }

    // Méthode pour vérifier le login
    public static boolean authenticate(String username, String password) {
        username = username == null ? null : username.trim();
        password = password == null ? null : password.trim();
        String sql = "SELECT * FROM players WHERE username = ? AND password = ?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();

            boolean found = rs.next();
            System.out.println("[DB] authenticate(" + username + ") => " + found);

            if (!found) {
                // Extra diagnostic: check if username exists at all and log stored password length
                try (PreparedStatement checkUser = conn.prepareStatement("SELECT password FROM players WHERE username = ?")) {
                    checkUser.setString(1, username);
                    ResultSet r2 = checkUser.executeQuery();
                    if (r2.next()) {
                        String stored = r2.getString(1);
                        System.out.println("[DB] authenticate: username exists but password mismatch. stored_password_len=" + (stored==null?0:stored.length()));
                    } else {
                        System.out.println("[DB] authenticate: username does not exist in players table.");
                    }
                } catch (SQLException e) {
                    System.err.println("[DB] authenticate additional check failed: " + e.getMessage());
                }
            }

            return found; // Retourne true si une ligne correspond
        } catch (SQLException e) {
            System.err.println("Erreur SQL Authentication: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
    
 // Dans DatabaseManager.java (Serveur)
    public static boolean registerPlayer(String username, String password) {
        username = username == null ? null : username.trim();
        password = password == null ? null : password.trim();

        // Check for simple validity
        if (username == null || username.length() < 3 || username.contains(" ")) {
            System.out.println("[DB] registerPlayer invalid username: '" + username + "'");
            return false;
        }

        String checkSql = "SELECT COUNT(*) FROM players WHERE username = ?";
        String insertSql = "INSERT INTO players (username, password, total_wins) VALUES (?, ?, 0)";

        try (Connection conn = getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {

            checkStmt.setString(1, username);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                System.out.println("[DB] registerPlayer(" + username + ") => already exists");
                return false; // user exists
            }

            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, username);
                insertStmt.setString(2, password);
                int rows = insertStmt.executeUpdate();
                System.out.println("[DB] registerPlayer(" + username + ") => rows=" + rows);
                return rows > 0;
            }

        } catch (SQLException e) {
            // More detailed logging
            System.err.println("Erreur SQL Register: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Méthode pour enregistrer une victoire
    public static void recordWin(String username) {
        String sql = "UPDATE players SET total_wins = total_wins + 1 WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            int updated = stmt.executeUpdate();
            System.out.println("[DB] recordWin(" + username + ") => updated=" + updated);
            System.out.println("Victoire enregistrée pour : " + username);
        } catch (SQLException e) {
            System.err.println("Erreur SQL Record Win: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Get the player's total wins
    public static int getPlayerWins(String username) {
        username = username == null ? null : username.trim();
        String sql = "SELECT total_wins FROM players WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int wins = rs.getInt(1);
                System.out.println("[DB] getPlayerWins(" + username + ") => " + wins);
                return wins;
            }
            System.out.println("[DB] getPlayerWins(" + username + ") => not found");
            return 0;
        } catch (SQLException e) {
            System.err.println("[DB] getPlayerWins failed: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    // New: record a completed race entry into the races table. winnerNumber is player's assigned numeric id.
    public static void recordRace(int winnerNumber) {
        String sql = "INSERT INTO races (winner_id) VALUES (?)"; // race_date defaults to CURRENT_TIMESTAMP
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, winnerNumber);
            int rows = stmt.executeUpdate();
            System.out.println("[DB] recordRace(winnerNumber=" + winnerNumber + ") => rows=" + rows);
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    System.out.println("[DB] recordRace inserted race_id=" + id + " for winner_id=" + winnerNumber);
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] recordRace failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // New overload: accept username, look up player id, and insert into races
    public static void recordRace(String username) {
        if (username == null || username.isEmpty()) {
            System.err.println("[DB] recordRace: username is null or empty, aborting");
            return;
        }
        String lookupSql = "SELECT id FROM players WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement lookupStmt = conn.prepareStatement(lookupSql)) {
            lookupStmt.setString(1, username);
            ResultSet rs = lookupStmt.executeQuery();
            if (rs.next()) {
                int id = rs.getInt(1);
                System.out.println("[DB] recordRace: found id=" + id + " for username='" + username + "'");
                recordRace(id);
            } else {
                System.err.println("[DB] recordRace: username not found: " + username);
            }
        } catch (SQLException e) {
            System.err.println("[DB] recordRace(lookup) failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Get player id by username, or -1 if found
    public static int getPlayerId(String username) {
        if (username == null || username.isEmpty()) return -1;
        String sql = "SELECT id FROM players WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
            return -1;
        } catch (SQLException e) {
            System.err.println("[DB] getPlayerId failed: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    // Count number of races recorded for a username (helper for diagnostics)
    public static int countRacesForPlayer(String username) {
        int id = getPlayerId(username);
        if (id <= 0) return 0;
        String sql = "SELECT COUNT(*) FROM races WHERE winner_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
            return 0;
        } catch (SQLException e) {
            System.err.println("[DB] countRacesForPlayer failed: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    // Count number of races recorded for a numeric player id (helper for diagnostics)
    public static int countRacesForPlayerId(int playerId) {
        if (playerId <= 0) return 0;
        String sql = "SELECT COUNT(*) FROM races WHERE winner_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
            return 0;
        } catch (SQLException e) {
            System.err.println("[DB] countRacesForPlayerId failed: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    // Debug helper: dump players table to console
    public static void debugDumpPlayers() {
        String sql = "SELECT id, username, password, total_wins FROM players";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            System.out.println("[DB] players table contents:");
            while (rs.next()) {
                int id = rs.getInt("id");
                String user = rs.getString("username");
                String pass = rs.getString("password");
                int wins = rs.getInt("total_wins");
                System.out.println("[DB] id=" + id + " user='" + user + "' pass_len=" + (pass==null?0:pass.length()) + " wins=" + wins);
            }
        } catch (SQLException e) {
            System.err.println("[DB] debugDumpPlayers failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Small CLI to quickly dump players table without starting server
    public static void main(String[] args) {
        System.out.println("[DB] Running debug dump...");
        debugDumpPlayers();
    }
}