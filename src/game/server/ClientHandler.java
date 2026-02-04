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
    public String getAuthenticatedUsername() { return authenticatedUsername; }
    private volatile long lastHeartbeat = System.currentTimeMillis();
    public long getLastHeartbeat() { return lastHeartbeat; }
    // Expose remote socket address for logging
    public String getRemoteAddress() { return (server == null) ? "unknown" : server.getRemoteSocketAddress().toString(); }
    
    // Property access methods.
    public int getPlayerNumber() { return playerNumber; }
    // New setter to allow LobbyManager to assign the number before adding to list
    public void setPlayerNumber(int num) { this.playerNumber = num; }

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

    public void updateConnectedPlayers(ClientHandler opponent) {
        // Send a single, atomic snapshot for the opponent to avoid race conditions.
        int opponentNumber = opponent.getPlayerNumber();
        String name = opponent.getAuthenticatedUsername();
        if (name == null) name = "";
        String encoded = name.replaceAll(" ", "_");
        int wins = DatabaseManager.getPlayerWins(name);
        int kartChoice = LobbyManager.getKartChoice(opponentNumber);
        boolean ready = LobbyManager.getReadyState(opponentNumber);
        // Send an atomic PLAYER_JOINED message with all relevant fields
        sendCommand("PLAYER_JOINED " + opponentNumber + " " + kartChoice + " " + ready + " " + encoded + " " + wins);
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

    public void broadcastCollision(int kart1, int kart2, long timestamp, float orig1, float orig2) {
        // Broadcast to all players in-game
        for (ClientHandler handler : GameManager.getPlayersInGame()) {
            if (handler.equals(this)) continue; // don't send it back to sender
            handler.sendCommand("BROADCAST_COLLISION " + kart1 + " " + kart2 + " " + timestamp + " " + orig1 + " " + orig2);
        }
    }

    public void startGame() {
        sendCommand("REQUEST_START_GAME");
    }

    public void raceLost(int winnerNumber, String winnerNameEncoded) {
        // send encoded name so client can decode and display the winner's username
        sendCommand("RACE_LOST " + winnerNumber + " " + winnerNameEncoded);
    }

    public void retrieveAllConnectedPlayers() {
        for (ClientHandler opponent : LobbyManager.getPlayersInLobby()) {
            if (opponent == null) continue;
            int oppNum = opponent.getPlayerNumber();
            if (oppNum <= 0) continue; // Skip invalid entries
            if (oppNum == this.playerNumber) continue; // Don't get their own.
             // Use new signature to include username and wins
             updateConnectedPlayers(opponent);
         }
     }

     public void retrieveAllKartChoices() {
        for (ClientHandler opponent : LobbyManager.getPlayersInLobby()) {
            int opponentNumber = opponent.getPlayerNumber();
            if (opponentNumber <= 0) continue; // Skip invalid
            if (playerNumber == opponentNumber) continue; // Don't get their own.
            updateOpponentKartChoice(opponentNumber, LobbyManager.getKartChoice(opponentNumber));
        }
     }

     public void retrieveAllReadyStates() {
        for (ClientHandler opponent : LobbyManager.getPlayersInLobby()) {
            int opponentNumber = opponent.getPlayerNumber();
            if (opponentNumber <= 0) continue; // Skip invalid
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
                // Refresh lastHeartbeat on any incoming message to indicate activity
                this.lastHeartbeat = System.currentTimeMillis();
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
                        // Refresh heartbeat on successful authentication to avoid premature pruning
                        this.lastHeartbeat = System.currentTimeMillis();
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
        } finally {
            // Ensure cleanup on loop exit
            endServerConnection();
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
            case "UPDATE_MAP_CHOICE"        -> updateChosenMap(messageData);
            case "SEND_KART_DATA"          -> processKartData(messageData);
            case "SEND_COLLISION"          -> processCollision(messageData);
            case "END_CONNECTION"           -> endClientConnection();
            case "END_GAME"                -> GameManager.endGame();
            case "RACE_WON"                -> handleRaceWon(); // Appel d'une méthode dédiée
            case "HEARTBEAT"               -> handleHeartbeat();
        }
    }

    private void processCollision(String[] data) {
        try {
            // Client sends: SEND_COLLISION <kart1> <kart2> <timestamp> <origSpeed1> <origSpeed2>
            int kart1 = Integer.parseInt(data[1]);
            int kart2 = Integer.parseInt(data[2]);
            long timestamp = Long.parseLong(data[3]);
            float orig1 = Float.parseFloat(data[4]);
            float orig2 = Float.parseFloat(data[5]);

            // Deduplicate similar collision reports arriving within a short window
            if (!GameManager.shouldBroadcastCollision(kart1, kart2, timestamp)) {
                System.out.println("[Server] Ignoring duplicate collision: " + kart1 + " vs " + kart2 + " at " + timestamp);
                return;
            }

            // Broadcast collision to other players in game
            broadcastCollision(kart1, kart2, timestamp, orig1, orig2);

            // Also notify server-side GameManager if needed (not implemented here)
            System.out.println("[Server] Collision received: " + kart1 + " vs " + kart2 + " at " + timestamp);

        } catch (Exception e) {
            System.err.println("Error parsing SEND_COLLISION: " + e.getMessage());
        }
    }

    // Update lastHeartbeat when a heartbeat arrives from the client
    private void handleHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
        // Optionally acknowledge the heartbeat so clients can log/confirm
        sendCommand("HEARTBEAT_ACK");
    }
    
    private void handleRaceWon() {
        // Notify other players about the winner
        GameManager.sendRaceWinnerToAllPlayers(this);
        // Persist the win in the database if user authenticated
        if (this.authenticatedUsername != null) {
            DatabaseManager.recordWin(this.authenticatedUsername);
            // Try to record by username first
            DatabaseManager.recordRace(this.authenticatedUsername);
            int count = DatabaseManager.countRacesForPlayer(this.authenticatedUsername);
            if (count > 0) {
                System.out.println("Victoire SQL : " + this.authenticatedUsername + " (races recorded=" + count + ")");
            } else {
                // Fallback: try recording by numeric playerNumber if username lookup failed
                System.err.println("[Server] recordRace by username returned 0 rows; falling back to playerNumber=" + this.playerNumber);
                DatabaseManager.recordRace(this.playerNumber);
                int count2 = DatabaseManager.countRacesForPlayer(this.authenticatedUsername);
                System.out.println("Victoire SQL fallback : " + this.authenticatedUsername + " (races recorded now=" + count2 + ")");
            }
        } else {
            // If no authenticated username, try to record by playerNumber (best-effort)
            System.err.println("[Server] No authenticated username for winner, attempting to record race by playerNumber=" + this.playerNumber);
            DatabaseManager.recordRace(this.playerNumber);
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
        // If no valid player number was available, terminate the connection gracefully.
        if (playerNumber <= 0) {
            System.err.println("[Server] Failed to assign player number, rejecting lobby request");
            sendCommand("RESPOND_PL_LOBBY_DATA_FAILURE");
            return;
        }
        // Refresh lastHeartbeat when player successfully joins lobby
        this.lastHeartbeat = System.currentTimeMillis();
        // Assign initial kart choice for this player and set ready state BEFORE
        // notifying other players. This avoids race conditions where other
        // clients receive an OP_ADD before the server has stored the new
        // player's kart choice and ready state.
        int kartChoice = LobbyManager.setKartChoice(playerNumber);
        LobbyManager.setReadyState(playerNumber, false);
        int mapChoice = LobbyManager.getChosenMap();

        // Now notify other connected clients about the new player.
        System.out.println("[Server] createPlayerLobbyData: player=" + playerNumber + " kartChoice=" + kartChoice + " mapChoice=" + mapChoice);
        ClientManager.sendNewPlayerToPlayers(this);
        ClientManager.sendKartChoiceToPlayers(this);
        ClientManager.sendReadyStateToPlayers(this);
        ClientManager.sendMapChoiceToPlayers(this);

        retrieveAllConnectedPlayers();
        retrieveAllKartChoices();
        retrieveAllReadyStates();
        // Include authenticated username and wins if available
        String username = (authenticatedUsername == null) ? "" : authenticatedUsername.replaceAll(" ", "_");
        int wins = (authenticatedUsername == null) ? 0 : DatabaseManager.getPlayerWins(authenticatedUsername);
        sendCommand("RESPOND_PL_LOBBY_DATA " + playerNumber + " " + kartChoice + " " + mapChoice + " " + username + " " + wins);
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

    public void endServerConnection() {
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
            case "RACE_WON"                -> handleRaceWon();
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