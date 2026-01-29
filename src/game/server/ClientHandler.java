package game.server;

import java.io.*;
import java.net.Socket;

/**
 * The {@code ClientHandler} class sends requests/data to the client
 *  * and processes commands from the client.
 */
public class ClientHandler implements Runnable {

    // Object properties.
    private final Socket server;
    private BufferedReader inputStreamFromClient;
    private DataOutputStream outputStreamToClient;
    private String messageFromClient;
    private int playerNumber;
    private boolean connectionActive = false;

    // Property access methods.
    public int getPlayerNumber() { return playerNumber; }

    // Constructor.
    public ClientHandler(Socket server) { this.server = server; }

    public void updateOpponentKartChoice(int opponentNumber, int kartChoice) {
        sendCommand("UPDATE_OP_KART_CHOICE " + opponentNumber + " " + kartChoice);
    }

    public void updateOpponentReadyState(int opponentNumber, boolean readyState) {
        sendCommand("UPDATE_OP_READY_STATE " + opponentNumber + " " + readyState);
    }

    public void updateConnectedPlayers(int opponentNumber) {
        sendCommand("OP_ADD " + opponentNumber);
    }

    public void removeDisconnectedPlayer(int opponentNumber) {
        sendCommand("OP_REMOVE " + opponentNumber);
    }

    public void updateChosenMap(int chosenMap) {
        sendCommand("UPDATE_MAP_CHOICE " + chosenMap);
    }

    public void updateWeather(boolean weather) {
        sendCommand("UPDATE_WEATHER " + weather);
    }

    public void updateOpponentKart(int kartNum, float rot, float speed, float posX, float posY) {
        sendCommand("SEND_OP_KART_DATA " + kartNum + " " + rot + " " + speed + " " + posX + " " + posY);
    }

    public void startGame() {
        sendCommand("REQUEST_START_GAME");
    }

    public void raceLost(int winnerNumber) {
        sendCommand("RACE_LOST " + winnerNumber);
    }

    public void retrieveAllConnectedPlayers() {
        for (ClientHandler opponent : LobbyManager.getPlayersInLobby()) {
            int opponentNumber = opponent.getPlayerNumber();
            if (playerNumber == opponentNumber) continue; // Don't get their own.
            updateConnectedPlayers(opponentNumber);
        }
    }

    public void retrieveAllKartChoices() {
        for (ClientHandler opponent : LobbyManager.getPlayersInLobby()) {
            int opponentNumber = opponent.getPlayerNumber();
            if (playerNumber == opponentNumber) continue; // Don't get their own.
            updateOpponentKartChoice(opponentNumber, LobbyManager.getKartChoice(opponentNumber));
        }
    }

    public void retrieveAllReadyStates() {
        for (ClientHandler opponent : LobbyManager.getPlayersInLobby()) {
            int opponentNumber = opponent.getPlayerNumber();
            if (playerNumber == opponentNumber) continue; // Don't get their own.
            updateOpponentReadyState(opponentNumber, LobbyManager.getReadyState(opponentNumber));
        }
    }

    // Handler thread loops here.
    public void run() {
        openConnection();

        do handleClientCommands();
        while (connectionActive);

        closeConnection();
    }

    private void openConnection() {
        try {
            inputStreamFromClient = new BufferedReader(new InputStreamReader(server.getInputStream()));
            outputStreamToClient = new DataOutputStream(server.getOutputStream());
        }
        catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    private void handleClientCommands() {
        messageFromClient = listenForCommand();

        if (clientCommandReceived()) {
            try {
                respondToClientCommands();
            }
            catch (IllegalStateException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    private void closeConnection() {
        try {
            outputStreamToClient.close();
            inputStreamFromClient.close();
            server.close();
        }
        catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    private boolean clientCommandReceived() {
        return messageFromClient != null;
    }

    private void setConnectionActive() {
        connectionActive = true;
        sendCommand("RESPOND_CONN_CHECK");
    }

    private void getPlayerSize() {
        int playersJoined = LobbyManager.getPlayersInLobby().size();
        sendCommand("RESPOND_PLAYER_COUNT " + playersJoined);
    }

    private void getServerStage() {
        boolean isGameActive = GameManager.isGameActive();
        sendCommand("RESPOND_SERVER_STAGE " + isGameActive);
    }

    private void createPlayerLobbyData() {
        // Collect player information to then send back to the player.
        playerNumber = LobbyManager.addPlayer(this);
        ClientManager.sendNewPlayerToPlayers(this);

        int kartChoice = LobbyManager.setKartChoice(playerNumber);
        ClientManager.sendKartChoiceToPlayers(this);

        LobbyManager.setReadyState(playerNumber, false);
        ClientManager.sendReadyStateToPlayers(this);

        int mapChoice = LobbyManager.getChosenMap();
        ClientManager.sendMapChoiceToPlayers(this);

        retrieveAllConnectedPlayers();
        retrieveAllKartChoices();
        retrieveAllReadyStates();

        sendCommand("RESPOND_PL_LOBBY_DATA " + playerNumber + " " + kartChoice + " " + mapChoice);
    }

    private void setPlayerReady(boolean state) {
        LobbyManager.setReadyState(playerNumber, state);
        if (!GameManager.isGameActive()) ClientManager.sendReadyStateToPlayers(this);
    }

    private void endClientConnectionInvalid() {
        connectionActive = false;
        ClientManager.closeConnection(this);
    }

    private void endClientConnection() {
        sendCommand("END_CONNECTION");
        endServerConnection();
    }

    private void endServerConnection() {
        connectionActive = false;

        // Remove the player depending on the stage of the game they're in.
        if (GameManager.isGameActive()) {
            GameManager.removePlayer(this);
            GameManager.sendPlayerDisconnectedToAllPlayers(this);
        }
        else {
            LobbyManager.removePlayer(this);
            LobbyManager.sendPlayerDisconnectedToAllPlayers(this);
        }

        ClientManager.closeConnection(this);
    }

    private void updateOwnKartChoice(String[] data) {
        try {
            int chosenKart = Integer.parseInt(data[1]);
            LobbyManager.updateKartChoice(playerNumber, chosenKart);
            ClientManager.sendKartChoiceToPlayers(this);
        }
        catch (NumberFormatException e) {
            System.err.println("Type conversion error when updating own kart choice: " + e.getMessage());
        }
    }

    private void sendKartChoice(String[] data) {
        try {
            int opponentNumber = Integer.parseInt(data[1]);
            int kartChoiceRequest = LobbyManager.getKartChoice(opponentNumber);
            updateOpponentKartChoice(opponentNumber, kartChoiceRequest);
        }
        catch (NumberFormatException e) {
            System.err.println("Type conversion error when sending kart choice: " + e.getMessage());
        }
    }

    private void updateChosenMap(String[] data) {
        LobbyManager.updateMapChoice(Integer.parseInt(data[1]));
        ClientManager.sendMapChoiceToPlayers(this);
    }

    private void processKartData(String[] data) {
        try {
            int kartNumber = Integer.parseInt(data[1]);
            float rotation = Float.parseFloat(data[2]);
            float speed = Float.parseFloat(data[3]);
            float positionX = Float.parseFloat(data[4]);
            float positionY = Float.parseFloat(data[5]);

            ClientManager.sendKartToAllPlayers(this, kartNumber, rotation, speed, positionX, positionY);
        }
        catch (NumberFormatException e) {
            System.err.println("Type conversion error when processing kart data: " + e.getMessage());
        }
    }

    private void respondToClientCommands() {
        String[] messageData = messageFromClient.split(" ");
        String command = messageData[0];

        switch (command) {
            case "REQUEST_CONN_CHECK"           -> setConnectionActive();
            case "REQUEST_PLAYER_COUNT"         -> getPlayerSize();
            case "REQUEST_SERVER_STAGE"         -> getServerStage();
            case "REQUEST_PL_LOBBY_DATA"        -> createPlayerLobbyData();
            case "PLAYER_READY"                 -> setPlayerReady(true);
            case "PLAYER_UNREADY"               -> setPlayerReady(false);
            case "END_CONNECTION"               -> endClientConnection();
            case "END_CONN_INVALID"             -> endClientConnectionInvalid();
            case "UPDATE_OWN_KART_OPTION"       -> updateOwnKartChoice(messageData);
            case "REQUEST_KART_CHOICE"          -> sendKartChoice( messageData);
            case "UPDATE_MAP_CHOICE"            -> updateChosenMap(messageData);
            case "SEND_KART_DATA"               -> processKartData(messageData);
            case "END_GAME"                     -> GameManager.endGame();
            case "RACE_WON"                     -> GameManager.sendRaceWinnerToAllPlayers(this);
            default -> throw new IllegalStateException("Unrecognised client command: " + command);
        }
    }

    private synchronized void sendCommand(String command) {
        try {
            outputStreamToClient.writeBytes(command + "\n");
        }
        catch (IOException e) {
            endServerConnection();
        }
    }

    private String listenForCommand() {
        try {
            return inputStreamFromClient.readLine();
        }
        catch (IOException e) {
            endServerConnection();
            return null;
        }
    }
}
