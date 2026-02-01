package game.client;

/**
 * The {@code ServerManager} utility class provides management
 * of a connection to a server, which is delegated to a thread.
 */
public class ServerManager {

    private static ServerHandler handler;

    public static ServerHandler getHandler() {
        return handler;
    }

    // Prevent object creation from the implicit public constructor.
    private ServerManager() {
        throw new IllegalStateException("Tried to instantiate the ServerManager utility class");
    }

    // Delegate the connection to a thread to handle.
    public static boolean connectToServer(String host, GameClient existingClient) {
        if (handler == null) {
            // On passe l'objet GameClient au handler pour qu'il récupère la socket
            handler = new ServerHandler(host, existingClient);
            new Thread(handler).start();
            return true;
        }
        return true; 
    }

    public static void disconnectFromServer() {
        handler = null;
    }
 // Dans ServerManager.java
    public static boolean connectToServer(String host) {
        // Si vous n'avez pas d'existingClient ici, passez null ou créez-en un nouveau
        if (handler == null) {
            handler = new ServerHandler(host, new GameClient()); 
            new Thread(handler).start();
            return true;
        }
        return true; 
    }
}
