package game.server;

import java.util.*;

/**
 * The {@code GameManager} utility class controls the collections of information
 * about the game for the server to access and provide to clients that request it.
 * The class handles sending game-related details to other connected players.
 */
public class GameManager {

    // Constants.
    private static final int RANDOM_MAP = 3;

    // Game properties.
    private static List<ClientHandler> playersInGame = new ArrayList<>();
    private static Map<Integer, Integer> kartChoices = new HashMap<>();
    private static int map = 0;
    private static boolean isBadWeather = false;
    private static boolean gameActive = false;

    // Recent collision deduplication: key is "min(k1,k2)-max(k1,k2)", value is timestamp
    private static final Map<String, Long> recentCollisions = new HashMap<>();
    private static final long COLLISION_DEDUP_MS = 500; // ignore repeats within 500ms

    // Property access methods.
    public static boolean isGameActive() { return gameActive; }
    public static List<ClientHandler> getPlayersInGame() { return playersInGame; }

    // Prevent object creation from the implicit public constructor.
    private GameManager() {
        throw new IllegalStateException("Tried to instantiate the GameManager utility class");
    }

    public static void removePlayer(ClientHandler o) {
        playersInGame.remove(o);
    }

    public static synchronized void sendRaceWinnerToAllPlayers(ClientHandler winner) {
        int winnerNumber = winner.getPlayerNumber();
        // encode winner name (replace spaces with underscores) to send safely
        String winnerName = winner.getAuthenticatedUsername();
        String encodedName = (winnerName == null) ? "" : winnerName.replaceAll(" ", "_");
        for (ClientHandler handler : getPlayersInGame()) {
            if (winner.equals(handler)) continue; // Don't send to self.
            handler.raceLost(winnerNumber, encodedName);
        }
    }

    private static void sendGameMapToAllPlayers() {
        for (ClientHandler handler : getPlayersInGame()) {
            handler.updateChosenMap(map);
        }
    }

    private static void sendWeatherToAllPlayers() {
        for (ClientHandler handler : getPlayersInGame()) {
            handler.updateWeather(isBadWeather);
        }
    }

    public static synchronized void sendPlayerDisconnectedToAllPlayers(ClientHandler originator) {
        for (ClientHandler handler : getPlayersInGame()) {
            if (originator.equals(handler)) continue; // Don't send to self.
            int disconnectedPlayer = originator.getPlayerNumber();
            handler.removeDisconnectedPlayer(disconnectedPlayer);
        }
    }

    public static void initiateGame(List<ClientHandler> connectedPlayers, Map<Integer, Integer> playerKartChoices, int chosenMap) {
        // Collect and store game information.
        playersInGame = new ArrayList<>(connectedPlayers);
        kartChoices = playerKartChoices;
        gameActive = true;

        // Assign a random map if requested, and a 50% change of poor weather.
        map = (chosenMap == RANDOM_MAP) ? new Random().nextInt(3) : chosenMap;
        if (new Random().nextInt(2) == 0) isBadWeather = true;

        // Update connected clients with values post-randomisation in case they differ locally.
        sendGameMapToAllPlayers();
        sendWeatherToAllPlayers();
        ClientManager.startGameForAllPlayers();
    }

    public static void endGame() {
        if (gameActive) {
            playersInGame.clear();
            kartChoices.clear();
            map = 0;
            isBadWeather = false;
            gameActive = false;
        }
    }

    // Deduplication helper for collisions
    public static synchronized boolean shouldBroadcastCollision(int k1, int k2, long timestamp) {
        int a = Math.min(k1, k2);
        int b = Math.max(k1, k2);
        String key = a + "-" + b;
        Long last = recentCollisions.get(key);
        if (last != null && Math.abs(timestamp - last) < COLLISION_DEDUP_MS) {
            return false; // duplicate
        }
        recentCollisions.put(key, timestamp);
        return true;
    }
}