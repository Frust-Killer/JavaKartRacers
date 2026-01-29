package game.client;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

/**
 * The {@code AudioManager} utility class allows sounds to be played
 * and stopped from different areas of the game.
 */
public class AudioManager {

    // The currently active sounds.
    private static Clip activeSoundEffect;
    private static Clip activeMusic;

    // Audio files.
    private static File menuThemeAudioFile;
    private static File raceThemeAudioFile;
    private static File gameOverAudioFile;
    private static File gameWinAudioFile;
    private static File collisionAudioFile;
    private static File newLapAudioFile;
    private static File buttonAudioFile;
    private static File raceCountdown;

    // Properties.
    private static boolean isMuted = false;
    private static boolean isMusicPlaying = false;
    private static boolean isSoundEffectPlaying = false;

    // Property access methods.
    public static boolean isMuted() { return isMuted; }
    public static void mute(boolean muted) { isMuted = muted; }

    // Prevent object creation from the implicit public constructor.
    private AudioManager() {
        throw new IllegalStateException("Tried to instantiate the AudioManager utility class");
    }

    public static void loadAudioFiles() {
        menuThemeAudioFile = new File("./src/game/client/audio/menuTheme.wav");
        raceThemeAudioFile = new File("./src/game/client/audio/raceTheme.wav");
        gameOverAudioFile = new File("./src/game/client/audio/gameOver.wav");
        gameWinAudioFile = new File("./src/game/client/audio/gameWin.wav");
        collisionAudioFile = new File("./src/game/client/audio/collision.wav");
        newLapAudioFile = new File("./src/game/client/audio/newLap.wav");
        buttonAudioFile = new File("./src/game/client/audio/button.wav");
        raceCountdown = new File("./src/game/client/audio/raceCountdown.wav");
    }

    public static void playSound(String sound, boolean loop) {
        if (!isMuted()) {
            try {
                // Setup audio objects.
                AudioInputStream inputStream = AudioSystem.getAudioInputStream(getSoundFile(sound));
                AudioFormat format = inputStream.getFormat();
                DataLine.Info lineInformation = new DataLine.Info(Clip.class, format);

                // Only play new sounds if the previous sound has finished.
                // Music is defined here as sounds that have been looped
                // and can be stopped externally.
                // SoundEffect is defined here as any non-looping sound.
                if (loop && !isMusicPlaying) {
                    activeMusic = (Clip) AudioSystem.getLine(lineInformation);
                    activeMusic.open(inputStream);
                    activeMusic.loop(Clip.LOOP_CONTINUOUSLY);
                    activeMusic.start();
                    isMusicPlaying = true;
                }
                else if (!loop && !isSoundEffectPlaying) {
                    activeSoundEffect = (Clip) AudioSystem.getLine(lineInformation);
                    activeSoundEffect.open(inputStream);
                    activeSoundEffect.addLineListener(e -> {
                        if (e.getType().equals(LineEvent.Type.STOP)) isSoundEffectPlaying = false;
                    });
                    activeSoundEffect.start();
                    isSoundEffectPlaying = true;
                }

            } catch (LineUnavailableException | UnsupportedAudioFileException | IOException e) {
                System.err.println("Audio File Error.");
            }
        }
    }

    public static void stopMusic() {
        if (isMusicPlaying) {
            activeMusic.stop();
            isMusicPlaying = false;
        }
    }

    private static File getSoundFile(String type) {
        return switch (type) {
            case "KART_COLLISION"   -> collisionAudioFile;
            case "RACE_COUNTDOWN"   -> raceCountdown;
            case "BUTTON_CLICK"     -> buttonAudioFile;
            case "MENU_THEME"       -> menuThemeAudioFile;
            case "RACE_THEME"       -> raceThemeAudioFile;
            case "GAME_OVER"        -> gameOverAudioFile;
            case "GAME_WIN"         -> gameWinAudioFile;
            case "NEW_LAP"          -> newLapAudioFile;
            default -> throw new IllegalStateException("Could not find file for sound " + type);
        };
    }
}
