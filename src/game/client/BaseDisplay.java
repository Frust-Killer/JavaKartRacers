package game.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * The {@code BaseDisplay} class delegates implementation to concrete displays
 * that draw their content back to this base instance.
 */
public class BaseDisplay extends JPanel implements ActionListener, KeyListener {

    // Constants.
    private static final int REFRESH_RATE   = 15;
    private static final int INITIAL_WIDTH  = 850;
    private static final int INITIAL_HEIGHT = 650;

    // Instance accessor.
    private static BaseDisplay instance;
    public static BaseDisplay getInstance() { return instance; }

    // Object properties.
    private Display currentDisplay;

    // Property access methods.
    public void setCurrentDisplay(Display newDisplay) {
        currentDisplay = newDisplay;
        requestFocus();
    }

    // Constructor.
    public BaseDisplay() {
        setPreferredSize(new Dimension(INITIAL_WIDTH, INITIAL_HEIGHT));
        setLayout(null);
        addKeyListener(this);

        instance = this;

        setCurrentDisplay(new MenuDisplay());

        new Timer(REFRESH_RATE, e -> repaint()).start();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        currentDisplay.update(g);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        AudioManager.playSound("BUTTON_CLICK", false);
        currentDisplay.buttonHandler(e.getSource());
        repaint();
    }

    @Override
    public void keyPressed(KeyEvent e) {
        currentDisplay.keyHandler(e.getKeyCode(), true);
    }

    public void keyReleased(KeyEvent e) {
        currentDisplay.keyHandler(e.getKeyCode(), false);
    }

    // Wipe components added from other displays to prevent them from building up.
    public void clearComponents() {
        for (Component component : getComponents()) remove(component);
    }

    public JButton addButton(ImageIcon image, int x, int y) {
        var button = new JButton(image);
        button.setSize(image.getIconWidth(), image.getIconHeight());
        button.setLocation(x, y);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        add(button);
        button.addActionListener(this);
        button.setVisible(true);
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }
        });
        return button;
    }

    public JLabel addLabel(String text, int width, int height, int x, int y, Color fontColour, int fontSize) {
        var label = new JLabel(text);
        label.setSize(width, height);
        label.setLocation(x, y);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setForeground(fontColour);
        label.setFont(new Font("Arial", Font.BOLD, fontSize));
        add(label);
        label.setVisible(true);
        return label;
    }

    public JTextField addUserInputBox(String templateText, int width, int height, int x, int y, Color fontColour, int fontSize) {
        var input = new JTextField(templateText);
        input.setSize(width, height);
        input.setLocation(x, y);
        input.setHorizontalAlignment(SwingConstants.CENTER);
        input.setForeground(fontColour);
        input.setBackground(new Color(18,18,18));
        input.setBorder(null);
        input.setFont(new Font("Arial", Font.BOLD, fontSize));
        add(input);
        input.setVisible(true);
        input.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                input.setText("");
            }
        });
        return input;
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // Not used.
    }
}
