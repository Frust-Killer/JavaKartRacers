package game.client;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

/**
 * The {@code MenuDisplay} class is a concrete implementation
 * of {@code Display} for the menu.
 * From here, a user can:
 * <ul>
 * <li>Go to the game finder display.
 * <li>Exit the program.
 * <li>Mute/unmute sounds.
 * </ul>
 */
public class MenuDisplay implements Display {

    // Images.
    private ImageIcon menuBackground;
    private ImageIcon gameStart;
    private ImageIcon gameExit;
    private ImageIcon muteGame;
    private ImageIcon unmuteGame;

    // Buttons.
    private JButton startButton;
    private JButton exitButton;
    private JButton muteGameButton;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton registerButton;

    // Constructor.
    public MenuDisplay() {
        baseDisplay.clearComponents();
        loadImages();
        addDisplayComponents();
        AudioManager.playSound("MENU_THEME", true);
        
    }

    private void loadImages() {
        try {
            menuBackground = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/bg/gameMenuBackground.png")));
            gameStart = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/gameStart.png")));
            gameExit = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/gameExit.png")));
            muteGame = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/buttonMute.png")));
            unmuteGame = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/buttonUnmute.png")));
        }
        catch (NullPointerException e) {
            System.err.println("Failed to locate a necessary image file.");
        }
    }

    private void addDisplayComponents() {
    	int windowWidth = 896;
        int fieldWidth = 200;
        int fieldHeight = 30;
        int centerX = (windowWidth / 2) - (fieldWidth / 2); // Le vrai milieu
        muteGameButton = baseDisplay.addButton(muteGame, 10, 610);
        if (AudioManager.isMuted()) muteGameButton.setIcon(unmuteGame);
        
     // Boutons par défaut
        exitButton = baseDisplay.addButton(gameExit, 10, 10); // Placé dans un coin
        
        usernameField = new JTextField();
        usernameField.setBounds(centerX, 330, fieldWidth, fieldHeight);
        
        passwordField = new JPasswordField();
        passwordField.setBounds(centerX, 390, fieldWidth, fieldHeight);

        // Pour les boutons, utilise le même centerX
        startButton = baseDisplay.addButton(gameStart, 305, 540);
        
        registerButton = new JButton("REGISTER");
        registerButton.setBounds(400, 450,100, 40);
        registerButton.addActionListener(e -> buttonHandler(registerButton));
        // ... reste du style
        baseDisplay.add(usernameField);
        baseDisplay.add(passwordField);
        baseDisplay.add(registerButton);
        
       
    }

    @Override
    public void update(Graphics g) {
        menuBackground.paintIcon(baseDisplay, g, 0,0);

        // AJOUT : Texte indicatif au-dessus des champs
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.drawString("Username:", 260, 348);
        g.drawString("Password:", 260, 410);

        // Info version
        g.drawString(Main.VERSION, 757, 620);
        g.drawString(Main.STUDENT_ID, 255, 620);
        g.drawString(Main.TEACHER, 360, 300);
    }

    @Override
    public void buttonHandler(Object button) {
        
        String user = usernameField.getText();
        String pass = new String(passwordField.getPassword());

        if (button == startButton) {
            // Run network login off the Event Dispatch Thread to avoid freezing the UI
            startButton.setEnabled(false);
            new Thread(() -> {
                boolean success = Main.getGameClient().attemptLogin(user, pass);
                javax.swing.SwingUtilities.invokeLater(() -> {
                    startButton.setEnabled(true);
                    if (success) {
                        // Start the ServerHandler using the existing authenticated GameClient
                        ServerManager.connectToServer("localhost", Main.getGameClient());
                        baseDisplay.setCurrentDisplay(new GameJoinDisplay());
                    } else {
                        JOptionPane.showMessageDialog(null, "Login Failed!");
                    }
                });
            }).start();
        } 
        else if (button == registerButton) {
            // NOUVEAU : Logique d'enregistrement
            if (user.length() < 3 || pass.length() < 3) {
                JOptionPane.showMessageDialog(null, "Username/Pass trop courts !");
                return;
            }
            // Run registration off the EDT as it performs network I/O
            registerButton.setEnabled(false);
            new Thread(() -> {
                boolean created = Main.getGameClient().attemptRegister(user, pass);
                javax.swing.SwingUtilities.invokeLater(() -> {
                    registerButton.setEnabled(true);
                    if (created) {
                        JOptionPane.showMessageDialog(null, "Compte créé avec succès ! Connectez-vous.");
                    } else {
                        JOptionPane.showMessageDialog(null, "Erreur : Nom d'utilisateur déjà pris.");
                    }
                });
            }).start();
        }
        
       
        else if (button == exitButton) {
            System.exit(0);
        }
        else if (button == muteGameButton) {
            if (AudioManager.isMuted()) {
                muteGameButton.setIcon(muteGame);
                AudioManager.mute(false);
                AudioManager.playSound("MENU_THEME", true);
            }
            else {
                muteGameButton.setIcon(unmuteGame);
                AudioManager.mute(true);
                AudioManager.stopMusic();
            }
        }
    }

    @Override
    public void keyHandler(int keyCode, boolean keyActivated) {
        // No keys used on this display.
    }
}