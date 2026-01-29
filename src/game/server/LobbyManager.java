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
        int playerNumber = Collections.min(validPlayerNumbers);
        validPlayerNumbers.remove((Integer) playerNumber);
        playersInLobby.add(player);
        return playerNumber;
    }

    public static synchronized void removePlayer(ClientHandler player) {
        int playerNumber = player.getPlayerNumber();
        playersInLobby.remove(player);
        playerKartChoices.remove(playerNumber);
        playerReadyStates.remove(playerNumber);
        // Return number back to the list of unallocated numbers.
        if (!validPlayerNumbers.contains(playerNumber)) validPlayerNumbers.add(playerNumber);
    }

    public static synchronized void setReadyState(int playerNumber, boolean state) {
        playerReadyStates.put(playerNumber, state);
        checkGameStart();
    }

    public static synchronized int setKartChoice(int playerNumber) {
        int kartChoice = playerNumber - 1;
        // Prevent a player from choosing a kart already chosen.
        if (playerKartChoices.containsValue(kartChoice)) {
            kartChoice = getNextValidKartOption(kartChoice);
        }
        playerKartChoices.put(playerNumber, kartChoice);
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
        try {
            return playerKartChoices.get(playerNumber);
        }
        catch (NullPointerException e) {
            return 0;
        }
    }

    public static synchronized boolean getReadyState(int playerNumber) {
        return playerReadyStates.get(playerNumber);
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
