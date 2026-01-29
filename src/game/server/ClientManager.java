package game.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code ClientManager} utility class provides management
 * of connections to clients, which are delegated to respective threads.
 * This class also handles inter-client communication for sending data to players.
 */
public class ClientManager {

    // Constants.
    private static final int SERVER_PORT = 5000;

    // Server properties.
    private static ServerSocket serverSocket;
    private static final List<ClientHandler> connectedClients = new ArrayList<>();

    private static synchronized List<ClientHandler> getConnectedClients() {
        return connectedClients;
    }

    // Prevent object creation from the implicit public constructor.
    private ClientManager() {
        throw new IllegalStateException("Tried to instantiate the ClientManager utility class");
    }

    public static void startGameForAllPlayers() {
        for (ClientHandler handler : GameManager.getPlayersInGame()) {
            handler.startGame();
        }
    }

    public static synchronized void sendKartChoiceToPlayers(ClientHandler originator) {
        for (ClientHandler handler : getConnectedClients()) {
            if (originator.equals(handler)) continue; // Don't send to self.
            int playerNumber = originator.getPlayerNumber();
            int kartChoice = LobbyManager.getKartChoice(playerNumber);
            handler.updateOpponentKartChoice(playerNumber, kartChoice);
        }
    }

    public static synchronized void sendReadyStateToPlayers(ClientHandler originator) {
        for (ClientHandler handler : getConnectedClients()) {
            if (originator.equals(handler)) continue; // Don't send to self.
            int playerNumber = originator.getPlayerNumber();
            boolean readyState = LobbyManager.getReadyState(playerNumber);
            handler.updateOpponentReadyState(playerNumber, readyState);
        }
    }

    public static synchronized void sendNewPlayerToPlayers(ClientHandler originator) {
        for (ClientHandler handler : getConnectedClients()) {
            if (originator.equals(handler)) continue; // Don't send to self.
            int playerNumber = originator.getPlayerNumber();
            handler.updateConnectedPlayers(playerNumber);
        }
    }

    public static synchronized void sendMapChoiceToPlayers(ClientHandler originator) {
        for (ClientHandler handler : getConnectedClients()) {
            if (originator.equals(handler)) continue; // Don't send to self.
            int chosenMap = LobbyManager.getChosenMap();
            handler.updateChosenMap(chosenMap);
        }
    }

    public static void sendKartToAllPlayers(ClientHandler originator, int kartNum, float rot, float speed, float posX, float posY) {
        for (ClientHandler handler : GameManager.getPlayersInGame()) {
            if (originator.equals(handler)) continue; // Don't send to self.
            handler.updateOpponentKart(kartNum, rot, speed, posX, posY);
        }
    }

    public static synchronized void closeConnection(ClientHandler originator) {
        connectedClients.remove(originator);
    }

    public static void establishConnection() {

        boolean isServerAlive = setupServer();

        while (isServerAlive) {
            Socket clientSocket = waitForClientConnection();
            addNewClientHandler(clientSocket);
        }
    }

    private static Socket waitForClientConnection() {
        try {
            return serverSocket.accept();
        }
        catch (IOException e) {
            System.err.println("Socket failed to accept: " + e.getMessage());
            return null;
        }
    }

    private static boolean setupServer() {
        try {
            serverSocket = new ServerSocket(SERVER_PORT);
            return true;
        }
        catch (IOException e) {
            System.err.println("Server setup failed: " + e.getMessage());
            return false;
        }
    }

    // Delegate the connection to a thread to handle.
    private static void addNewClientHandler(Socket clientSocket) {
        if (clientSocket == null) return;

        ClientHandler client = new ClientHandler(clientSocket);

        new Thread(client).start();

        connectedClients.add(client);
    }
}
