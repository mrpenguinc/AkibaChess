package com.mychess.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.prefs.Preferences;

public class PgnOptionsDialog extends JDialog {
    private final Preferences prefs = Preferences.userNodeForPackage(MainFrame.class);
    private final Runnable onApply;

    private JComboBox<String> fontCombo;
    private JSpinner sizeSpinner;
    
    private Color mainColor, varColor, commentColor;
    private JButton mainColorBtn, varColorBtn, commentColorBtn;
    
    private JCheckBox mainBold, mainItalic;
    private JCheckBox varBold, varItalic;
    private JCheckBox commentBold, commentItalic;

    public PgnOptionsDialog(Frame owner, Runnable onApply) {
        super(owner, "PGN Panel Options", true);
        this.onApply = onApply;
        
        setupUI();
        loadPreferences();
        setupKeyBindings();
        
        pack();
        setLocationRelativeTo(owner);
    }

    private void setupUI() {
        setLayout(new BorderLayout(10, 10));
        
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Global Font Settings
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 1;
        panel.add(new JLabel("Font Family:"), gbc);
        
        String[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        fontCombo = new JComboBox<>(fonts);
        gbc.gridx = 1; gbc.gridwidth = 2;
        panel.add(fontCombo, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        panel.add(new JLabel("Font Size:"), gbc);
        sizeSpinner = new JSpinner(new SpinnerNumberModel(14, 8, 48, 1));
        gbc.gridx = 1; gbc.gridwidth = 2;
        panel.add(sizeSpinner, gbc);

        // Separator
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 4;
        panel.add(new JSeparator(), gbc);

        // Header for styles
        gbc.gridy = 3; gbc.gridwidth = 1; panel.add(new JLabel("Element"), gbc);
        gbc.gridx = 1; panel.add(new JLabel("Color"), gbc);
        gbc.gridx = 2; panel.add(new JLabel("Bold"), gbc);
        gbc.gridx = 3; panel.add(new JLabel("Italic"), gbc);

        // Main Moves
        gbc.gridy = 4; gbc.gridx = 0;
        panel.add(new JLabel("Main Moves:"), gbc);
        mainColorBtn = new JButton("Select");
        mainColorBtn.addActionListener(e -> chooseColor("Main Moves", mainColorBtn, color -> mainColor = color));
        gbc.gridx = 1; panel.add(mainColorBtn, gbc);
        mainBold = new JCheckBox(); gbc.gridx = 2; panel.add(mainBold, gbc);
        mainItalic = new JCheckBox(); gbc.gridx = 3; panel.add(mainItalic, gbc);

        // Variations
        gbc.gridy = 5; gbc.gridx = 0;
        panel.add(new JLabel("Variations:"), gbc);
        varColorBtn = new JButton("Select");
        varColorBtn.addActionListener(e -> chooseColor("Variations", varColorBtn, color -> varColor = color));
        gbc.gridx = 1; panel.add(varColorBtn, gbc);
        varBold = new JCheckBox(); gbc.gridx = 2; panel.add(varBold, gbc);
        varItalic = new JCheckBox(); gbc.gridx = 3; panel.add(varItalic, gbc);

        // Comments
        gbc.gridy = 6; gbc.gridx = 0;
        panel.add(new JLabel("Comments:"), gbc);
        commentColorBtn = new JButton("Select");
        commentColorBtn.addActionListener(e -> chooseColor("Comments", commentColorBtn, color -> commentColor = color));
        gbc.gridx = 1; panel.add(commentColorBtn, gbc);
        commentBold = new JCheckBox(); gbc.gridx = 2; panel.add(commentBold, gbc);
        commentItalic = new JCheckBox(); gbc.gridx = 3; panel.add(commentItalic, gbc);

        add(panel, BorderLayout.CENTER);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton applyBtn = new JButton("Apply & Close");
        JButton cancelBtn = new JButton("Cancel");
        
        applyBtn.addActionListener(e -> { savePreferences(); dispose(); });
        cancelBtn.addActionListener(e -> dispose());
        
        btnPanel.add(applyBtn);
        btnPanel.add(cancelBtn);
        add(btnPanel, BorderLayout.SOUTH);
        
        getRootPane().setDefaultButton(applyBtn);
    }

    private void chooseColor(String title, JButton btn, java.util.function.Consumer<Color> onSelect) {
        Color initial = btn.getBackground();
        Color chosen = JColorChooser.showDialog(this, "Select Color for " + title, initial);
        if (chosen != null) {
            btn.setBackground(chosen);
            btn.setForeground(isDark(chosen) ? Color.WHITE : Color.BLACK);
            onSelect.accept(chosen);
        }
    }

    private boolean isDark(Color c) {
        return (c.getRed() * 0.299 + c.getGreen() * 0.587 + c.getBlue() * 0.114) < 140;
    }

    private void applyColorToBtn(JButton btn, Color c) {
        btn.setBackground(c);
        btn.setForeground(isDark(c) ? Color.WHITE : Color.BLACK);
    }

    private void loadPreferences() {
        fontCombo.setSelectedItem(prefs.get("pgn_font_family", "Monospaced"));
        sizeSpinner.setValue(prefs.getInt("pgn_font_size", 14));
        
        mainColor = new Color(prefs.getInt("pgn_main_color", Color.BLACK.getRGB()));
        applyColorToBtn(mainColorBtn, mainColor);
        mainBold.setSelected(prefs.getBoolean("pgn_main_bold", false));
        mainItalic.setSelected(prefs.getBoolean("pgn_main_italic", false));
        
        varColor = new Color(prefs.getInt("pgn_var_color", Color.DARK_GRAY.getRGB()));
        applyColorToBtn(varColorBtn, varColor);
        varBold.setSelected(prefs.getBoolean("pgn_var_bold", false));
        varItalic.setSelected(prefs.getBoolean("pgn_var_italic", true));
        
        commentColor = new Color(prefs.getInt("pgn_comment_color", new Color(0, 119, 0).getRGB()));
        applyColorToBtn(commentColorBtn, commentColor);
        commentBold.setSelected(prefs.getBoolean("pgn_comment_bold", false));
        commentItalic.setSelected(prefs.getBoolean("pgn_comment_italic", true));
    }

    private void savePreferences() {
        prefs.put("pgn_font_family", (String) fontCombo.getSelectedItem());
        prefs.putInt("pgn_font_size", (Integer) sizeSpinner.getValue());
        
        prefs.putInt("pgn_main_color", mainColor.getRGB());
        prefs.putBoolean("pgn_main_bold", mainBold.isSelected());
        prefs.putBoolean("pgn_main_italic", mainItalic.isSelected());
        
        prefs.putInt("pgn_var_color", varColor.getRGB());
        prefs.putBoolean("pgn_var_bold", varBold.isSelected());
        prefs.putBoolean("pgn_var_italic", varItalic.isSelected());
        
        prefs.putInt("pgn_comment_color", commentColor.getRGB());
        prefs.putBoolean("pgn_comment_bold", commentBold.isSelected());
        prefs.putBoolean("pgn_comment_italic", commentItalic.isSelected());
        
        if (onApply != null) onApply.run();
    }

    private void setupKeyBindings() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        getRootPane().getActionMap().put("cancel", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { dispose(); }
        });
    }
}