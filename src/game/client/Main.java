package game.client;

/**
 * Entrance point for the client program.
 */
public class Main {

    public static final String VERSION      = "Version 2.0";
    public static final String STUDENT_ID   = "Developpers: FRU, FAVOUR, HAPPI, KAMPETE, KIMBI";
    public static final String TEACHER      = "Superviseur: Dr. NZEBOP";
    public static final String GAME_TITLE   = "Java Kart Racers";
    
    // AJOUT : On stocke l'instance du client réseau ici
    private static GameClient gameClient;

    public static void main(String[] args) {
        gameClient = new GameClient(); // Initialisation du réseau
        AudioManager.loadAudioFiles();
        new Window();
    }
    
    public static GameClient getGameClient() {
        return gameClient;
    }
}