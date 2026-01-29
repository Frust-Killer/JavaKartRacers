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
    public static boolean connectToServer(String serverHostAddress) {
        if (handler == null) {
            handler = new ServerHandler(serverHostAddress);
            new Thread(handler).start();
            return true;
        }
        // If there is a dormant thread and handler, close them.
        handler.disconnectPlayer();
        handler = null;
        return false;
    }

    public static void disconnectFromServer() {
        handler = null;
    }
}
