package game.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * The {@code GameJoinDisplay} class is a concrete implementation
 * of {@code Display} for finding a game hosted on a server.
 * From here, a user can:
 * <ul>
 * <li>Join a local game lobby.
 * <li>Join a game lobby with a custom IP address.
 * <li>Return to the menu.
 * </ul>
 */
public class GameJoinDisplay implements Display {

    // Buttons.
    private JButton backButton;
    private JButton joinLocalButton;
    private JButton joinOnlineButton;

    // Labels.
    private JLabel errorLocalLabel;
    private JLabel errorOnlineLabel;

    // Text Fields.
    private JTextField joinOnlineServerInput;

    // Images.
    private ImageIcon gameJoinBackground;
    private ImageIcon back;
    private ImageIcon joinLocal;
    private ImageIcon joinOnline;
    private ImageIcon joinHover;
    private ImageIcon loading;

    // Object properties.
    private boolean joinLocalHoverActive = false;
    private boolean joinOnlineHoverActive = false;
    private boolean joinLocalDisabled = false;
    private boolean joinOnlineDisabled = false;

    private GameLobbyDisplay lobby;

    // Property access methods.
    public void setErrorLocalLabel(String errorMessage) {
        errorLocalLabel.setText(errorMessage);
        joinLocalDisabled = false;
    }

    public void setErrorOnlineLabel(String errorMessage) {
        errorOnlineLabel.setText(errorMessage);
        joinOnlineDisabled = false;
    }

    // Constructor.
    public GameJoinDisplay() {
        baseDisplay.clearComponents();
        loadImages();
        addDisplayComponents();
    }

    private void loadImages() {
        try {
            gameJoinBackground = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/bg/gameJoinBackground.png")));
            back = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/buttonBack.png")));
            joinLocal = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/buttonJoinLocal.png")));
            joinOnline = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/buttonJoinOnline.png")));
            joinHover = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/joinHover.png")));
            loading = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/loadingSymbol.gif")));
        }
        catch (NullPointerException e) {
            System.err.println("Failed to locate a necessary image file.");
        }
    }

    private void addDisplayComponents() {
        backButton = baseDisplay.addButton(back, 20, 37);

        joinLocalButton = baseDisplay.addButton(joinLocal, 190, 228);
        joinLocalButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) { joinLocalHoverActive = true; }
            @Override
            public void mouseExited(MouseEvent e) { joinLocalHoverActive = false; }
        });

        joinOnlineButton = baseDisplay.addButton(joinOnline, 181, 496);
        joinOnlineButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) { joinOnlineHoverActive = true; }
            @Override
            public void mouseExited(MouseEvent e) { joinOnlineHoverActive = false; }
        });

        errorLocalLabel = baseDisplay.addLabel("", 468, 40, 191, 168, new Color(223,53, 53), 20);
        errorOnlineLabel = baseDisplay.addLabel("", 488, 40, 181, 436, new Color(223,53, 53), 20);

        joinOnlineServerInput = baseDisplay.addUserInputBox("Enter IP Address", 256, 48, 297, 561, Color.white, 20);
    }

    @Override
    public void update(Graphics g) {
        gameJoinBackground.paintIcon(baseDisplay, g, 0,0);

        if (joinLocalHoverActive) joinHover.paintIcon(baseDisplay, g, 0, 114);
        if (joinOnlineHoverActive) joinHover.paintIcon(baseDisplay, g, 0, 392);

        if (joinLocalDisabled) loading.paintIcon(baseDisplay, g, 409, 172);
        if (joinOnlineDisabled) loading.paintIcon(baseDisplay, g, 409, 440);
    }

    public void createLocalLobby() {
        // IMPORTANT : On instancie l'objet AVANT de l'assigner au handler
        this.lobby = new GameLobbyDisplay();
        ServerManager.getHandler().setLobbyDisplay(this.lobby);
    }

    public void sendPlayerToLobby() {
        baseDisplay.setCurrentDisplay(lobby);
    }

    private void sendPlayerToMenu() {
        baseDisplay.setCurrentDisplay(new MenuDisplay());
    }

    private void joinServerLocal() {
        resetErrorMessage(errorLocalLabel);
        joinLocalDisabled = true;

        // IMPORTANT : On n'appelle plus ServerManager.connectToServer() 
        // car le GameClient est déjà connecté depuis le Login !
        
        ServerHandler handler = ServerManager.getHandler();
        if (handler != null) {
            handler.setJoinDisplay(this);
            handler.requestLobbyData(); // Appel de la nouvelle méthode
        } else {
            // Si jamais le handler est null, on tente de reconnecter (sécurité)
            ServerManager.connectToServer("localhost");
            ServerManager.getHandler().setJoinDisplay(this);
        }
    }
    
    private void joinServerOnline() {
        resetErrorMessage(errorOnlineLabel);
        joinOnlineDisabled = true;

        // Récupérer le handler actuel (celui du Login)
        ServerHandler handler = ServerManager.getHandler();

        if (handler != null) {
            // On est déjà connecté ! On définit juste l'affichage actuel et on demande le lobby
            handler.setJoinDisplay(this);
            handler.requestLobbyData();
        } else {
            // Pas de connexion préalable (le joueur n'est pas passé par le login)
            try {
                String userInput = joinOnlineServerInput.getText();
                String serverAddress = InetAddress.getByName(userInput).getHostAddress();

                if (ServerManager.connectToServer(serverAddress)) {
                    ServerManager.getHandler().setJoinDisplay(this);
                    // Le thread run() de ServerHandler s'occupera du reste
                } else {
                    joinOnlineDisabled = false;
                }
            } catch (UnknownHostException e) {
                errorOnlineLabel.setText("Could not resolve address");
                joinOnlineDisabled = false;
            }
        }
    }
    private void resetErrorMessage(JLabel errorLabel) {
        errorLabel.setText("");
    }

    @Override
    public void buttonHandler(Object button) {
        if (button == backButton) sendPlayerToMenu();
        else if (button == joinLocalButton && !joinLocalDisabled) joinServerLocal();
        else if (button == joinOnlineButton && !joinOnlineDisabled) joinServerOnline();
    }

    @Override
    public void keyHandler(int keyCode, boolean keyActivated) {
        // No keys used on this display.
    }
}
