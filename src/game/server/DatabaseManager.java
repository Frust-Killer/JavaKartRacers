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