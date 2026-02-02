package game.server;

import java.util.*;

/**
 * The {@code LobbyManager} utility class controls the collections of information
 * about the lobby for the server to access and provide to clients that request it.
 * The class handles sending lobby-related details to other connected players.
 * The class also handles checking if a new game is eligible to be created.
 */
public class LobbyManager {

    // Constants.
    private static final int VALID_KART_CHOICES = 7;

    // Lobby properties.
    private static List<Integer> validPlayerNumbers = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6));
    private static final List<ClientHandler> playersInLobby = new ArrayList<>();
    private static final Map<Integer, Integer> playerKartChoices = new HashMap<>();
    private static final Map<Integer, Boolean> playerReadyStates = new HashMap<>();
    private static int chosenMap = 0;

    // Prevent object creation from the implicit public constructor.
    private LobbyManager() {
        throw new IllegalStateException("Tried to instantiate the LobbyManager utility class");
    }

    public static synchronized int addPlayer(ClientHandler player) {
        // Borrow a number from the list of unallocated numbers.
        if (validPlayerNumbers.isEmpty()) {
            System.err.println("[Lobby] addPlayer: no available player numbers");
            // Do not add the client to playersInLobby because they were not
            // assigned a valid player number. The caller should handle the
            // failure and not attempt to include this client in lobby flows.
            return -1; // Indicate failure to assign a number
        }

        int playerNumber = Collections.min(validPlayerNumbers);
        validPlayerNumbers.remove((Integer) playerNumber);
        playersInLobby.add(player);
        System.out.println("[Lobby] addPlayer: assigned number=" + playerNumber + " playersInLobbySize=" + playersInLobby.size());
        return playerNumber;
    }

    public static synchronized void removePlayer(ClientHandler player) {
        int playerNumber = player.getPlayerNumber();
        // If the player number is not valid (e.g., 0 because it was never assigned),
        // avoid returning it to the pool to prevent assigning 0 to future players.
        if (playerNumber <= 0) {
            // Still remove the client object from the lobby list if present, but
            // do not modify the pools/maps that rely on a valid player number.
            playersInLobby.remove(player);
            System.out.println("[Lobby] removePlayer: ignored invalid playerNumber=" + playerNumber + " playersInLobbySize=" + playersInLobby.size());
            return;
        }

        playersInLobby.remove(player);
        playerKartChoices.remove(playerNumber);
        playerReadyStates.remove(playerNumber);
        // Return number back to the list of unallocated numbers.
        if (!validPlayerNumbers.contains(playerNumber)) validPlayerNumbers.add(playerNumber);
        // Keep the available numbers ordered so Collections.min() remains predictable.
        Collections.sort(validPlayerNumbers);
        System.out.println("[Lobby] removePlayer: removed number=" + playerNumber + " playersInLobbySize=" + playersInLobby.size());
    }

    public static synchronized void setReadyState(int playerNumber, boolean state) {
        playerReadyStates.put(playerNumber, state);
        System.out.println("[Lobby] setReadyState: player=" + playerNumber + " state=" + state + " allReadyStates=" + playerReadyStates);
        checkGameStart();
    }

    public static synchronized int setKartChoice(int playerNumber) {
        // Defensive guard: ensure playerNumber is valid before using it.
        if (playerNumber <= 0) {
            System.err.println("[Lobby] setKartChoice: invalid playerNumber=" + playerNumber + ", defaulting to kart 0");
            playerKartChoices.put(playerNumber, 0);
            return 0;
        }

        int kartChoice = playerNumber - 1;
        // Prevent a player from choosing a kart already chosen.
        if (playerKartChoices.containsValue(kartChoice)) {
            kartChoice = getNextValidKartOption(kartChoice);
        }
        playerKartChoices.put(playerNumber, kartChoice);
        System.out.println("[Lobby] setKartChoice: player=" + playerNumber + " assignedKart=" + kartChoice + " allChoices=" + playerKartChoices);
        return kartChoice;
    }

    private static synchronized int getNextValidKartOption(int kartChoice) {
        // Modulus is used to ensure kart option loops back around to the start.
        int potentialKartChoice = (kartChoice + 1) % VALID_KART_CHOICES;
        if (playerKartChoices.containsValue(potentialKartChoice)) {
            return getNextValidKartOption(kartChoice + 1);
        }
        else return potentialKartChoice;
    }

    public static synchronized void updateKartChoice(int playerNumber, int kartChoice) {
        playerKartChoices.put(playerNumber, kartChoice);
    }

    public static synchronized void updateMapChoice(int mapChoice) {
        chosenMap = mapChoice;
    }

    public static synchronized int getKartChoice(int playerNumber) {
        // Avoid NullPointerException by returning a default kart index of 0
        return playerKartChoices.getOrDefault(playerNumber, 0);
    }

    public static synchronized boolean getReadyState(int playerNumber) {
        // Default to false if not present
        return playerReadyStates.getOrDefault(playerNumber, false);
    }

    public static synchronized int getChosenMap() {
        return chosenMap;
    }

    public static synchronized List<ClientHandler> getPlayersInLobby() {
        return playersInLobby;
    }

    public static synchronized void sendPlayerDisconnectedToAllPlayers(ClientHandler originator) {
        for (ClientHandler handler : getPlayersInLobby()) {
            if (originator.equals(handler)) continue; // Don't send to self.
            int disconnectedPlayer = originator.getPlayerNumber();
            handler.removeDisconnectedPlayer(disconnectedPlayer);
        }
    }

    private static void checkGameStart() {
        // A minimum of 2 players is required to start.
        // All players in the lobby must be ready to start.
        System.out.println("[Lobby] checkGameStart: readyStates=" + playerReadyStates + " size=" + playerReadyStates.size());
        if (!playerReadyStates.containsValue(false) && playerReadyStates.size() >= 2) {
            GameManager.initiateGame(playersInLobby, playerKartChoices, chosenMap);
            closeLobby();
        }
    }

    private static void closeLobby() {
        // Reset lobby properties.
        validPlayerNumbers = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6));
        playersInLobby.clear();
        playerKartChoices.clear();
        playerReadyStates.clear();
        chosenMap = 0;
    }
}