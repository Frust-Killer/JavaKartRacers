package game.server;

import java.io.*;
import java.net.Socket;
import java.io.PrintWriter;

/**
 * The {@code ClientHandler} class sends requests/data to the client
 *  * and processes commands from the client.
 */
public class ClientHandler implements Runnable {

    // Object properties.
    private final Socket server;;
    private String messageFromClient;
    private int playerNumber;
    private boolean connectionActive = false;
    private BufferedReader inputStream;
    private PrintWriter outputStream;
    private String authenticatedUsername; // Pour stocker le nom après le login
    // Property access methods.
    public int getPlayerNumber() { return playerNumber; }

    // Constructor.
    public ClientHandler(Socket server) { 
    	this.server = server; 
    }

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
 // Dans ClientHandler.java (Serveur)
    public void run() {
        try {
            inputStream = new BufferedReader(new InputStreamReader(server.getInputStream()));
            outputStream = new PrintWriter(server.getOutputStream(), true);

            System.out.println("[Server] New client connected from " + server.getRemoteSocketAddress());

            String line;
            while ((line = inputStream.readLine()) != null) {
                System.out.println("[Server] Received from client: " + line);

                // Robust parse: split into at most 3 parts to allow spaces in password
                String[] data = line.split(" ", 3);
                String command = data.length > 0 ? data[0].trim() : null;
                String user = data.length > 1 ? data[1].trim() : null;
                String pass = data.length > 2 ? data[2].trim() : null;

                System.out.println("[Server] parsed -> command='" + command + "' user='" + user + "' (len=" + (user==null?0:user.length()) + ") pass='" + pass + "' (len=" + (pass==null?0:pass.length()) + ")");

                if ("LOGIN_REQUEST".equals(command)) {
                    if (user == null || pass == null) {
                        sendCommand("LOGIN_FAILURE");
                        System.out.println("[Server] LOGIN_REQUEST missing user/pass");
                        continue;
                    }
                    boolean isValid = DatabaseManager.authenticate(user, pass);
                    if (isValid) {
                        this.authenticatedUsername = user;
                        this.connectionActive = true;
                        sendCommand("LOGIN_SUCCESS");
                        System.out.println("[Server] User authenticated: " + this.authenticatedUsername);
                        // On ne fait PLUS createPlayerLobbyData() ici !
                    } else {
                        sendCommand("LOGIN_FAILURE");
                        System.out.println("[Server] Authentication failed for user: " + user);
                    }
                } else if ("REGISTER_REQUEST".equals(command)) {
                    // Expect: REGISTER_REQUEST username password
                    if (user == null || pass == null) {
                        sendCommand("REGISTER_FAILURE");
                        System.out.println("[Server] REGISTER_REQUEST missing user/pass");
                        continue;
                    }
                    boolean created = DatabaseManager.registerPlayer(user, pass);
                    if (created) {
                        sendCommand("REGISTER_SUCCESS");
                        System.out.println("[Server] New user registered: " + user);
                    } else {
                        sendCommand("REGISTER_FAILURE");
                        System.out.println("[Server] Registration failed for user: " + user);
                    }
                } else {
                    processCommand(line);
                }
            }
        } catch (IOException e) {
            System.err.println("[Server] Connection error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    
    private void processCommand(String message) {
        String[] messageData = message.split(" ");
        String command = messageData[0];

        switch (command) {
            case "REQUEST_CONN_CHECK"     -> sendCommand("RESPOND_CONN_CHECK");
            case "REQUEST_PLAYER_COUNT"    -> getPlayerSize();
            case "REQUEST_SERVER_STAGE"    -> getServerStage();
            case "REQUEST_PL_LOBBY_DATA"   -> createPlayerLobbyData();
            case "PLAYER_READY"            -> setPlayerReady(true);
            case "PLAYER_UNREADY"          -> setPlayerReady(false);
            case "UPDATE_OWN_KART_OPTION"  -> updateOwnKartChoice(messageData);
            case "REQUEST_KART_CHOICE"     -> sendKartChoice(messageData); // AJOUTÉ
            case "UPDATE_MAP_CHOICE"       -> updateChosenMap(messageData);
            case "SEND_KART_DATA"          -> processKartData(messageData);
            case "END_CONNECTION"          -> endClientConnection();
            case "END_GAME"                -> GameManager.endGame();
            case "RACE_WON"                -> handleRaceWon(); // Appel d'une méthode dédiée
        }
    }
    
    
    private void handleRaceWon() {
        // Notify other players about the winner
        GameManager.sendRaceWinnerToAllPlayers(this);
        // Persist the win in the database if user authenticated
        if (this.authenticatedUsername != null) {
            DatabaseManager.recordWin(this.authenticatedUsername);
            System.out.println("Victoire SQL : " + this.authenticatedUsername);
        }
    }

    private void closeConnection() {
        try {
            connectionActive = false;
            if (server != null) server.close();
        } catch (IOException e) { e.printStackTrace(); }
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
        sendCommand("RESPOND_PLAYER_COUNT " + LobbyManager.getPlayersInLobby().size());
    }

    private void getServerStage() {
        boolean isGameActive = GameManager.isGameActive();
        sendCommand("RESPOND_SERVER_STAGE " + GameManager.isGameActive());
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
        ClientManager.sendReadyStateToPlayers(this);
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
            case "REQUEST_CONN_CHECK"   -> setConnectionActive();
            case "REQUEST_PLAYER_COUNT"  -> getPlayerSize();
            case "REQUEST_SERVER_STAGE"  -> getServerStage();
            case "REQUEST_PL_LOBBY_DATA" -> createPlayerLobbyData();
            case "PLAYER_READY"          -> setPlayerReady(true);
            case "PLAYER_UNREADY"        -> setPlayerReady(false);
            case "END_CONNECTION"        -> endClientConnection();
            case "UPDATE_OWN_KART_OPTION" -> updateOwnKartChoice(messageData);
            case "REQUEST_KART_CHOICE"    -> sendKartChoice(messageData);
            case "UPDATE_MAP_CHOICE"     -> updateChosenMap(messageData);
            case "SEND_KART_DATA"        -> processKartData(messageData);
            case "END_GAME"              -> GameManager.endGame();
            case "RACE_WON" -> {
                // Notifier les autres
                GameManager.sendRaceWinnerToAllPlayers(this);
                // PERSISTANCE : Enregistre la victoire dans MySQL
                if (this.authenticatedUsername != null) {
                    DatabaseManager.recordWin(this.authenticatedUsername);
                    System.out.println("Victoire SQL : " + this.authenticatedUsername);
                }
            }
            default -> System.err.println("Commande inconnue: " + command);
        }
    }

    private synchronized void sendCommand(String command) {
        if (outputStream != null) {
            outputStream.println(command);
        }
    }

    private String listenForCommand() {
        try {
            return inputStream.readLine(); // Très important : readLine() et non readUTF()
        } catch (IOException e) {
            endServerConnection();
            return null;
        }
    }
}