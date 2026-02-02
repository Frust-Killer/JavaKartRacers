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
    // Increase prune timeout to 5 minutes to avoid false positives during tests
    private static final long CLIENT_TIMEOUT_MS = 300000; // 5 minutes timeout for heartbeats

    private static synchronized List<ClientHandler> getConnectedClients() {
        return connectedClients;
    }

    // Start a background thread to prune dead clients based on heartbeat
    private static void startPruneThread() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {}
                long now = System.currentTimeMillis();
                List<ClientHandler> snapshot;
                synchronized (connectedClients) {
                    snapshot = new ArrayList<>(connectedClients);
                }
                for (ClientHandler ch : snapshot) {
                    try {
                        long last = ch.getLastHeartbeat();
                        if (now - last > CLIENT_TIMEOUT_MS) {
                            String addr = "unknown";
                            try { addr = ch.getRemoteAddress(); } catch (Exception ignored) {}
                            String user = ch.getAuthenticatedUsername();
                            System.out.println("[Server] Pruning dead client: num=" + ch.getPlayerNumber()
                                    + " user=" + user + " addr=" + addr + " lastSeen=" + last);
                            ch.endServerConnection();
                        }
                    } catch (Exception e) {
                        System.err.println("[Server] Error while pruning clients: " + e.getMessage());
                    }
                }
            }
        });
        t.setDaemon(true);
        t.start();
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
        // Only broadcast new-player notifications if the originator has been assigned a valid player number
        if (originator == null || originator.getPlayerNumber() <= 0) return;
        System.out.println("[ClientManager] Broadcasting new player: " + originator.getPlayerNumber() + " to " + getConnectedClients().size() + " clients");
        for (ClientHandler handler : getConnectedClients()) {
            if (originator.equals(handler)) continue; // Don't send to self.
            try {
                handler.updateConnectedPlayers(originator);
            } catch (Exception e) {
                System.err.println("[Server] Failed to notify handler of new player: " + e.getMessage());
            }
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

        // Start pruning dead clients after server is up
        startPruneThread();

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