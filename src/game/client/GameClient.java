package game.client;

import java.io.*;
import java.net.Socket;

public class GameClient {
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;

    private void ensureConnection() throws IOException {
        if (socket == null || socket.isClosed()) {
            socket = new Socket("localhost", 5000);
            // On utilise PrintWriter et BufferedReader comme dans le reste de l'appli
            writer = new PrintWriter(socket.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }
    }

    public boolean attemptLogin(String username, String password) {
        try {
            ensureConnection();

            // On envoie une ligne de texte : "LOGIN_REQUEST user pass"
            String request = "LOGIN_REQUEST " + username + " " + password;
            System.out.println("[Client] Sending: " + request);
            writer.println(request);

            // On lit la réponse du serveur
            String response = reader.readLine();
            System.out.println("[Client] Received: " + response);
            
            // Si le serveur a envoyé "LOGIN_SUCCESS", c'est bon !
            return "LOGIN_SUCCESS".equals(response);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean attemptRegister(String username, String password) {
        try {
            ensureConnection();

            String request = "REGISTER_REQUEST " + username + " " + password;
            System.out.println("[Client] Sending: " + request);
            writer.println(request);

            String response = reader.readLine();
            System.out.println("[Client] Received: " + response);
            // Le serveur renvoie "true" ou "REGISTER_SUCCESS" selon ton implémentation
            return "true".equals(response) || "REGISTER_SUCCESS".equals(response);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    
 // Dans GameClient.java, ajoute ces getters :
    public Socket getSocket() { return socket; }
    public PrintWriter getWriter() { return writer; }
    public BufferedReader getReader() { return reader; }
}