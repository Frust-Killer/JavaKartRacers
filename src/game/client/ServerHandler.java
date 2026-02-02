package game.client;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;

/**
 * The {@code ServerHandler} class sends requests/data to the server
 * and processes commands from the server.
 */
public class ServerHandler implements Runnable {

    // Constants.
    private static final int MAX_PLAYERS = 6;
    private static final int SERVER_PORT = 5000;

    // connection components
    private Socket clientSocket = null;
    private PrintWriter outputStreamToServer;
    private BufferedReader inputStreamFromServer = null;
    private String messageFromServer;
    private final String serverHostAddress;

    // Lobby information.
    private int playerNumber;
    private int kartChoice;
    private int mapChoice;
    private final Map<Integer, String> opponentNamesMap = new HashMap<>();
    private final Map<Integer, Integer> opponentWinsMap = new HashMap<>();

    // Game-related instances.
    private Game activeGame;
    private GameJoinDisplay joinDisplay;
    private GameLobbyDisplay lobbyDisplay;
    private GameDisplay gameDisplay;

    // Object properties.
    private final List<Integer> opponents = new ArrayList<>();
    public final Map<Integer, Integer> chosenKarts = new HashMap<>();
    private boolean connectionActive = false;
    private boolean isGameActive = false;
    private boolean isServerFull = false;
    private boolean loginResult = false;
    private boolean authResponseReceived = false;
    private GameClient authClient;

    // Property access methods.
    public int getPlayerNumber()                    { return playerNumber; }
    public int getKartChoice()                      { return kartChoice; }
    public int getMapChoice()                       { return mapChoice; }
    public List<Integer> getOpponents()             { return opponents; }
    public Map<Integer, Integer> getKartChoices()   { return chosenKarts; }

    // Add accessors for opponent metadata
    public String getOpponentName(int playerNumber) { return opponentNamesMap.getOrDefault(playerNumber, ""); }
    public int getOpponentWins(int playerNumber) { return opponentWinsMap.getOrDefault(playerNumber, 0); }

    public void setGame(Game activeGame) {
        this.activeGame = activeGame;
    }

    public void setLobbyDisplay(GameLobbyDisplay display) {
        lobbyDisplay = display;
    }

    public void setJoinDisplay(GameJoinDisplay display) {
        joinDisplay = display;
    }

    public void setGameDisplay(GameDisplay display) {
        joinDisplay = null;
        lobbyDisplay = null;
        gameDisplay = display;
        isGameActive = true;
    }

    public void startGame(boolean active) {
        isGameActive = active;
        lobbyDisplay.startGame();
    }

    private void setConnectionActive() {
        connectionActive = true;
    }

    private void setServerFull(String[] data) {
        int playerCount = Integer.parseInt(data[1]);
        isServerFull = playerCount == MAX_PLAYERS;
    }

    private void setServerStage(String[] data) {
        isGameActive = Boolean.parseBoolean(data[1]);
    }

    public void updateKartChoice(int chosenKart) {
        chosenKarts.put(playerNumber, chosenKart);
        sendCommand("UPDATE_OWN_KART_OPTION " + chosenKart);
    }

    public boolean isKartChoiceTaken(int chosenKart) {
        return chosenKarts.containsValue(chosenKart);
    }

    // Constructor
    public ServerHandler(String serverHostAddress, GameClient authClient) {
        this.serverHostAddress = serverHostAddress;
        this.authClient = authClient;
    }

    // Handler thread loops here.

    @Override
    public void run() {
        openConnection(); // Configure les flux et appelle requestLobbyData()

        if (isConnectionSetupValid()) {
            connectionActive = true;

            // Start a small heartbeat thread to prevent server pruning of active clients
            Thread hb = new Thread(() -> {
                while (connectionActive) {
                    try {
                        sendCommand("HEARTBEAT");
                        Thread.sleep(5000);
                    } catch (InterruptedException ignored) { break; }
                }
            });
            hb.setDaemon(true);
            hb.start();

            // On lance directement la boucle d'écoute. 
            // Les réponses à REQUEST_CONN_CHECK, PLAYER_COUNT, etc. 
            // seront traitées par le switch dans respondToServerCommands()
            try {
                while (connectionActive) {
                    handleServerCommand();
                }
            } catch (Exception e) {
                System.err.println("Error in handler loop: " + e.getMessage());
            } finally {
                closeConnection();
            }
        }
        ServerManager.disconnectFromServer();
    }
    
    private void openConnection() {
        // Au lieu de faire "new Socket()", on récupère celle du login
        this.clientSocket = authClient.getSocket();
        this.outputStreamToServer = authClient.getWriter();
        this.inputStreamFromServer = authClient.getReader();
        
        if (isConnectionSetupValid()) {
            connectionActive = true;
          
        }
    }
    private void handleServerCommand() {
        messageFromServer = listenForCommand();

        if (serverCommandReceived()) {
            try {
                respondToServerCommands();
            }
            catch (IllegalStateException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    private void closeConnection() {
        try {
            outputStreamToServer.close();
            inputStreamFromServer.close();
            clientSocket.close();
        }
        catch (IOException e) {
            System.err.println("Error closing connection: " + e);
        }
    }

    private void displayErrorMessage(String errorMessage) {
        System.err.println("Network Error: " + errorMessage); // Toujours logger en console
        if (joinDisplay != null) {
            if (serverHostAddress.equals("localhost")) joinDisplay.setErrorLocalLabel(errorMessage);
            else joinDisplay.setErrorOnlineLabel(errorMessage);
        }
    }

    private boolean isConnectionSetupValid() {
        return clientSocket != null
                && outputStreamToServer != null
                && inputStreamFromServer != null;
    }

    private boolean serverCommandReceived() {
        return messageFromServer != null;
    }

    private void respondToServerCommands() {
        String[] messageData;
        // Use a limited split for messages that may contain username fields so we don't over-split
        if (messageFromServer.startsWith("RESPOND_PL_LOBBY_DATA") || messageFromServer.startsWith("OP_ADD")) {
            messageData = messageFromServer.split(" ", 6);
        } else {
            messageData = messageFromServer.split(" ");
        }
        String command = messageData[0];

        switch (command) {
            case "RESPOND_CONN_CHECK"       -> setConnectionActive();
            case "RESPOND_PLAYER_COUNT"     -> setServerFull(messageData);
            case "RESPOND_SERVER_STAGE"     -> setServerStage(messageData);
            case "RESPOND_PL_LOBBY_DATA"    -> updatePlayerLobbyData(messageData);
            case "RESPOND_PL_LOBBY_DATA_FAILURE" -> displayErrorMessage("Server rejected lobby join: no available slots");
            case "REQUEST_START_GAME"       -> startGame(true);
            case "PLAYER_JOINED"            -> handlePlayerJoined(messageData);
            case "OP_REMOVE"                -> removeOpponent(messageData);
            case "END_CONNECTION"           -> disconnectPlayer();
            case "UPDATE_OP_KART_CHOICE"    -> updateOpponentKartChoice(messageData);
            case "UPDATE_OP_READY_STATE"    -> updateOpponentReadyState(messageData);
            case "UPDATE_MAP_CHOICE"        -> updateChosenMap(messageData);
            case "UPDATE_WEATHER"           -> updateWeather(messageData);
            case "SEND_OP_KART_DATA"        -> updateOpponentKartData(messageData);
            case "END_GAME"                 -> endGame();
            case "RACE_LOST"                -> activeGame.loseGame(messageData);
            default -> throw new IllegalStateException("Unrecognised server command: " + command);
        }
    }

    private void initiateCommunication() {
        sendCommand("REQUEST_CONN_CHECK");
        handleServerCommand();
    }

    private void getNumberOfPlayers() {
        sendCommand("REQUEST_PLAYER_COUNT");
        handleServerCommand();
    }

    private void getServerStage() {
        sendCommand("REQUEST_SERVER_STAGE");
        handleServerCommand();
    }

    public void terminateConnection() {
        if (connectionActive) sendCommand("END_CONNECTION");
    }

    private void terminateInvalidConnection() {
        if (connectionActive) sendCommand("END_CONN_INVALID");
    }

    public void sendKart(Kart kart) {
        int kartNumber = kart.getKartNumber();
        float rotation = kart.getRotation();
        float speed = kart.getSpeed();
        float positionX = kart.getPosition().x;
        float positionY = kart.getPosition().y;
        // Delta-send: only send when significant change from last sent
        if (shouldSendKartUpdate(kartNumber, rotation, speed, positionX, positionY)) {
            sendCommand("SEND_KART_DATA " + kartNumber + " " + rotation + " " + speed + " " + positionX + " " + positionY);
            updateLastSentKart(kartNumber, rotation, speed, positionX, positionY);
        }
    }

    // Simple per-kart last-sent state
    private final Map<Integer, Float> lastSentRotation = new HashMap<>();
    private final Map<Integer, Float> lastSentSpeed = new HashMap<>();
    private final Map<Integer, Float> lastSentPosX = new HashMap<>();
    private final Map<Integer, Float> lastSentPosY = new HashMap<>();

    private boolean shouldSendKartUpdate(int kartNumber, float rotation, float speed, float posX, float posY) {
        float eps = 0.01f; // threshold
        Float lr = lastSentRotation.get(kartNumber);
        Float ls = lastSentSpeed.get(kartNumber);
        Float lx = lastSentPosX.get(kartNumber);
        Float ly = lastSentPosY.get(kartNumber);
        if (lr == null || ls == null || lx == null || ly == null) return true;
        return Math.abs(lr - rotation) > eps || Math.abs(ls - speed) > eps || Math.abs(lx - posX) > eps || Math.abs(ly - posY) > eps;
    }

    private void updateLastSentKart(int kartNumber, float rotation, float speed, float posX, float posY) {
        lastSentRotation.put(kartNumber, rotation);
        lastSentSpeed.put(kartNumber, speed);
        lastSentPosX.put(kartNumber, posX);
        lastSentPosY.put(kartNumber, posY);
    }
    
    public void clearLocalLobby() {
        chosenKarts.clear();
    }

    private void updateOpponentKartData(String[] data) {
        try {
            int kartNumber = Integer.parseInt(data[1]);
            float rotation = Float.parseFloat(data[2]);
            float speed = Float.parseFloat(data[3]);
            float positionX = Float.parseFloat(data[4]);
            float positionY = Float.parseFloat(data[5]);
            if (gameDisplay != null) gameDisplay.updateOpponentKart(
                    kartNumber, rotation, speed, positionX, positionY);
        }
        catch (NumberFormatException e) {
            System.err.println("Type conversion error when updating an opponent's kart data: " + e.getMessage());
        }
    }

    private void updatePlayerLobbyData(String[] data) {
        try {
            // defend against short messages
            if (data.length < 4) {
                System.err.println("RESPOND_PL_LOBBY_DATA too short, ignoring: " + Arrays.toString(data));
                return;
            }
            playerNumber = Integer.parseInt(data[1]);
            kartChoice = Integer.parseInt(data[2]);
            mapChoice = Integer.parseInt(data[3]);

            // New fields: username and wins may be present
            String username = data.length > 4 ? data[4] : "";
            int wins = 0;
            try { if (data.length > 5) wins = Integer.parseInt(data[5]); } catch (NumberFormatException ignored) {}

            // Defensive checks: ensure playerNumber and choices are within expected ranges
            if (playerNumber <= 0 || playerNumber > 6) {
                System.err.println("Ignored RESPOND_PL_LOBBY_DATA with invalid playerNumber: " + playerNumber);
                return;
            }
            if (kartChoice < 0 || kartChoice >= 7) {
                System.err.println("Ignored RESPOND_PL_LOBBY_DATA with invalid kartChoice: " + kartChoice);
                return;
            }
            if (mapChoice < 0) {
                System.err.println("Ignored RESPOND_PL_LOBBY_DATA with invalid mapChoice: " + mapChoice);
                return;
            }

            chosenKarts.put(playerNumber, kartChoice);

            if (lobbyDisplay != null) {
                lobbyDisplay.setLocalPlayerInfo(username.replaceAll("_", " "), wins);
            }

            // safe call
            if (joinDisplay != null) {
                lobbyDisplay.prepareLobbyForPlayer();
                joinDisplay.sendPlayerToLobby();
            } else {
                System.err.println("updatePlayerLobbyData: joinDisplay is null, cannot proceed");
            }
        }
        catch (NumberFormatException e) {
            System.err.println("Type conversion error when updating the player's lobby data: " + e.getMessage());
        }
    }

    private void getPlayerProperties() {
        joinDisplay.createLocalLobby();
        sendCommand("REQUEST_PL_LOBBY_DATA");
    }

    public void disconnectPlayer() {
        connectionActive = false;
    }

    private void updateOpponentKartChoice(String[] data) {
        if (lobbyDisplay == null) return;
        try {
            int opponentNumber = Integer.parseInt(data[1]);
            int opponentKartChoice = Integer.parseInt(data[2]);
            chosenKarts.put(opponentNumber, opponentKartChoice);
            lobbyDisplay.updateOpponentKartChoice(opponentNumber, opponentKartChoice);
        }
        catch (NumberFormatException e) {
            System.err.println("Type conversion error when updating kart choice: " + e.getMessage());
        }
    }

    private void updateOpponentReadyState(String[] data) {
        if (lobbyDisplay == null) return;
        try {
            int opponentNumber = Integer.parseInt(data[1]);
            boolean opponentReadyState = Boolean.parseBoolean(data[2]);
            lobbyDisplay.updateOpponentReadyState(opponentNumber, opponentReadyState);
        }
        catch (NumberFormatException e) {
            System.err.println("Type conversion error when updating ready state: " + e.getMessage());
        }
    }

    private void updateChosenMap(String[] data) {
        if (lobbyDisplay == null) return;
        try {
            mapChoice = Integer.parseInt(data[1]);
            lobbyDisplay.updateSelectedMap(mapChoice);
        }
        catch (NumberFormatException e) {
            System.err.println("Type conversion error when updating map: " + e.getMessage());
        }
    }

    private void updateWeather(String[] data) {
        if (lobbyDisplay == null) return;
        try {
            boolean weather = Boolean.parseBoolean(data[1]);
            lobbyDisplay.updateWeather(weather);
        }
        catch (NumberFormatException e) {
            System.err.println("Type conversion error when updating weather: " + e.getMessage());
        }
    }

    private void handlePlayerJoined(String[] data) {
        // Expected: PLAYER_JOINED <playerNumber> <kartChoice> <ready> <username> <wins>
        if (lobbyDisplay == null) return;
        try {
            if (data.length < 6) {
                System.err.println("PLAYER_JOINED too short, ignoring: " + Arrays.toString(data));
                return;
            }
            int opponentNumber = Integer.parseInt(data[1]);
            if (opponentNumber <= 0 || opponentNumber > 6) {
                System.err.println("Ignored PLAYER_JOINED with invalid opponent number: " + opponentNumber);
                return;
            }
            int kartChoice = Integer.parseInt(data[2]);
            boolean ready = Boolean.parseBoolean(data[3]);
            String username = data[4].replaceAll("_", " ");
            int wins = 0;
            try { wins = Integer.parseInt(data[5]); } catch (NumberFormatException ignored) {}

            if (!opponents.contains(opponentNumber)) opponents.add(opponentNumber);
            chosenKarts.put(opponentNumber, kartChoice);
            lobbyDisplay.updateActiveOpponent(opponentNumber);
            lobbyDisplay.updateOpponentKartChoice(opponentNumber, kartChoice);
            lobbyDisplay.updateOpponentReadyState(opponentNumber, ready);
            lobbyDisplay.setOpponentInfo(opponentNumber, username, wins);
            opponentNamesMap.put(opponentNumber, username);
            opponentWinsMap.put(opponentNumber, wins);
        }
        catch (NumberFormatException e) {
            System.err.println("Type conversion error when handling PLAYER_JOINED: " + e.getMessage());
        }
    }

    private void endGameNoOpponents() {
        activeGame.endGame();
        endGame();
    }

    public void endGame() {
        isGameActive = false;
        gameDisplay = null;
        activeGame = null;
        if (opponents.isEmpty()) sendCommand("END_GAME");
        opponents.clear();
    }

    public void raceWon() {
        sendCommand("RACE_WON");
    }

    public void sendReadyState(boolean isReady) {
        if (isReady) sendCommand("PLAYER_READY");
        else sendCommand("PLAYER_UNREADY");
    }

    public void sendMapChoice(int chosenMap) {
        mapChoice = chosenMap;
        sendCommand("UPDATE_MAP_CHOICE " + mapChoice);
    }

    // Close the connection if an error occurred in communication.
    private void handleUnexpectedServerTermination() {
        connectionActive = false;
        isGameActive = false;
        
        // Ajout de vérifications de sécurité (Null Checks)
        if (gameDisplay != null) {
            gameDisplay.sendPlayerToMenu();
        } else if (lobbyDisplay != null) {
            lobbyDisplay.sendPlayerToMenu();
        } else if (joinDisplay != null) {
            // Si on est encore sur l'écran de connexion
            joinDisplay.setErrorLocalLabel("Connection lost to server.");
        }
    }

    private synchronized void sendCommand(String command) {
        if (outputStreamToServer != null) {
            outputStreamToServer.println(command); // .println ajoute le \n automatiquement
        }
    }

    private String listenForCommand() {
        try {
            return inputStreamFromServer.readLine();
        } catch (IOException e) {
            handleUnexpectedServerTermination();
            return null;
        }
    }
    

    // Modifie aussi requestLobbyData pour garantir l'ordre
    public void requestLobbyData() {
        // 1. Initialiser le lobby localement
        if (joinDisplay != null) {
            joinDisplay.createLocalLobby();
        }
        
        // 2. Envoyer les commandes d'initialisation dans l'ordre
        sendCommand("REQUEST_CONN_CHECK");
        sendCommand("REQUEST_PLAYER_COUNT");
        sendCommand("REQUEST_SERVER_STAGE");
        sendCommand("REQUEST_PL_LOBBY_DATA");
    }

    private void removeOpponent(String[] data) {
        try {
            int opponentNumber = Integer.parseInt(data[1]);
            opponents.remove((Integer) opponentNumber);
            chosenKarts.remove(opponentNumber);

            if (isGameActive && activeGame != null) activeGame.removeOpponent(opponentNumber);
            else if (lobbyDisplay != null) lobbyDisplay.updateInactiveOpponent(opponentNumber);
            if (lobbyDisplay != null) lobbyDisplay.setOpponentInfo(opponentNumber, "", 0);
        }
        catch (NumberFormatException e) {
            System.err.println("Type conversion error when removing an opponent: " + e.getMessage());
        }
    }
}
