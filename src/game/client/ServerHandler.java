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
        String[] messageData = messageFromServer.split(" ");
        String command = messageData[0];

        switch (command) {
            case "RESPOND_CONN_CHECK"       -> setConnectionActive();
            case "RESPOND_PLAYER_COUNT"     -> setServerFull(messageData);
            case "RESPOND_SERVER_STAGE"     -> setServerStage(messageData);
            case "RESPOND_PL_LOBBY_DATA"    -> updatePlayerLobbyData(messageData);
            case "REQUEST_START_GAME"       -> startGame(true);
            case "OP_ADD"                   -> addOpponent(messageData);
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
        sendCommand("SEND_KART_DATA " + kartNumber + " " + rotation + " " + speed + " " + positionX + " " + positionY);
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
            playerNumber = Integer.parseInt(data[1]);
            kartChoice = Integer.parseInt(data[2]);
            mapChoice = Integer.parseInt(data[3]);

            // New fields: username and wins may be present
            String username = data.length > 4 ? data[4] : "";
            int wins = 0;
            try { if (data.length > 5) wins = Integer.parseInt(data[5]); } catch (NumberFormatException ignored) {}

            chosenKarts.put(playerNumber, kartChoice);

            if (lobbyDisplay != null) {
                lobbyDisplay.setLocalPlayerInfo(username.replaceAll("_", " "), wins);
            }

            lobbyDisplay.prepareLobbyForPlayer();
            joinDisplay.sendPlayerToLobby();
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

    private void addOpponent(String[] data) {
        if (lobbyDisplay == null) return;
        try {
            int opponentNumber = Integer.parseInt(data[1]);
            opponents.add(opponentNumber);
            sendCommand("REQUEST_KART_CHOICE " + opponentNumber);
            lobbyDisplay.updateActiveOpponent(opponentNumber);
        }
        catch (NumberFormatException e) {
            System.err.println("Type conversion error when adding opponent: " + e.getMessage());
        }
    }

    private void removeOpponent(String[] data) {
        try {
            int opponentNumber = Integer.parseInt(data[1]);
            opponents.remove((Integer) opponentNumber);
            chosenKarts.remove(opponentNumber);

            if (isGameActive) activeGame.removeOpponent(opponentNumber);
            else lobbyDisplay.updateInactiveOpponent(opponentNumber);

            if (opponents.isEmpty() && isGameActive) endGameNoOpponents();
        }
        catch (NumberFormatException e) {
            System.err.println("Type conversion error when removing an opponent: " + e.getMessage());
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
}