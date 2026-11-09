package com.mychess.view;

import com.mychess.model.GameModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public class GameInfoDialog extends JDialog {
    private final JTextField whiteField = new JTextField(15);
    private final JTextField blackField = new JTextField(15);
    private final JTextField eventField = new JTextField(15);
    private final JTextField siteField = new JTextField(15);
    private final JTextField dateField = new JTextField(15);
    private final JTextField roundField = new JTextField(5);
    private final JComboBox<String> resultCombo = new JComboBox<>(new String[]{"*", "1-0", "0-1", "1/2-1/2"});
    
    private final JTextArea customArea = new JTextArea(6, 20);
    
    private final GameModel model;
    private final Runnable onApplyCallback;

    public GameInfoDialog(Frame owner, GameModel model, Runnable onApplyCallback) {
        super(owner, "Game Information", true);
        this.model = model;
        this.onApplyCallback = onApplyCallback;
        
        setupUI();
        populateFields();
        setupKeyBindings();
        
        pack();
        setLocationRelativeTo(owner);
    }

    private void setupUI() {
        setLayout(new BorderLayout(10, 10));
        
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        int row = 0;
        addFormField(formPanel, gbc, "White:", whiteField, row++);
        addFormField(formPanel, gbc, "Black:", blackField, row++);
        addFormField(formPanel, gbc, "Event:", eventField, row++);
        addFormField(formPanel, gbc, "Site:", siteField, row++);
        addFormField(formPanel, gbc, "Date:", dateField, row++);
        addFormField(formPanel, gbc, "Round:", roundField, row++);
        
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(new JLabel("Result:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(resultCombo, gbc);
        
        add(formPanel, BorderLayout.NORTH);
        
        JPanel customPanel = new JPanel(new BorderLayout(5, 5));
        customPanel.setBorder(BorderFactory.createEmptyBorder(0, 15, 10, 15));
        customPanel.add(new JLabel("Custom Properties (Key: Value)"), BorderLayout.NORTH);
        
        customArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        customArea.setToolTipText("Examples:\nECO: B01\nBook: Pachman Chapter 2\nPuzzle: Mate in 3");
        customArea.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        customPanel.add(new JScrollPane(customArea), BorderLayout.CENTER);
        
        add(customPanel, BorderLayout.CENTER);
        
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("OK");
        JButton applyBtn = new JButton("Apply");
        JButton cancelBtn = new JButton("Cancel");
        
        okBtn.setToolTipText("Apply tags, save to database, and close");
        applyBtn.setToolTipText("Apply tags and save to database without closing");
        
        okBtn.addActionListener(e -> { applyChanges(); dispose(); });
        applyBtn.addActionListener(e -> applyChanges());
        cancelBtn.addActionListener(e -> dispose());
        
        btnPanel.add(okBtn);
        btnPanel.add(applyBtn);
        btnPanel.add(cancelBtn);
        add(btnPanel, BorderLayout.SOUTH);
        
        getRootPane().setDefaultButton(okBtn);
    }

    private void addFormField(JPanel panel, GridBagConstraints gbc, String label, Component comp, int row) {
        gbc.gridx = 0; gbc.gridy = row;
        gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label), gbc);
        
        gbc.gridx = 1; gbc.gridy = row;
        gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(comp, gbc);
        gbc.weightx = 0.0;
    }

    private void populateFields() {
        chesspresso.game.Game g = model.getGame();
        
        whiteField.setText(getSafeTag(g, "White"));
        blackField.setText(getSafeTag(g, "Black"));
        eventField.setText(getSafeTag(g, "Event"));
        siteField.setText(getSafeTag(g, "Site", "?"));
        dateField.setText(getSafeTag(g, "Date"));
        roundField.setText(getSafeTag(g, "Round", "-"));
        
        String res = getSafeTag(g, "Result");
        if (res != null && !res.isEmpty()) resultCombo.setSelectedItem(res);
        
        // Feed previously extracted custom properties straight into the text box
        customArea.setText(model.getCustomPropsText());
    }
    
    private String getSafeTag(chesspresso.game.Game g, String tag) {
        return getSafeTag(g, tag, "");
    }

    private String getSafeTag(chesspresso.game.Game g, String tag, String def) {
        String val = g.getTag(tag);
        return (val == null) ? def : val;
    }

    private void setupKeyBindings() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        getRootPane().getActionMap().put("cancel", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { dispose(); }
        });
    }

    private void applyChanges() {
        model.updateGameInfo(
            whiteField.getText(), blackField.getText(), eventField.getText(),
            siteField.getText(), dateField.getText(), roundField.getText(),
            (String) resultCombo.getSelectedItem(), customArea.getText()
        );
        
        // This triggers the save to SQLite and immediately refreshes the Table UI
        if (onApplyCallback != null) {
            onApplyCallback.run();
        }
    }
}