package game.client;

/**
 * Entrance point for the client program.
 */
public class Main {

    // Program-related information.
    public static final String VERSION      = "1.0.0";
    public static final String STUDENT_ID   = "1602819";
    public static final String GAME_TITLE   = "Pixel Kart Racers";

    public static void main(String[] args) {
        AudioManager.loadAudioFiles();
        new Window();
    }
}
