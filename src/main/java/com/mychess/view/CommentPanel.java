package com.mychess.view;

import com.mychess.model.GameModel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Dimension;
import java.awt.Font;

public class CommentPanel extends JPanel {
    private final GameModel model;
    private final JTextArea textArea;
    private boolean isUpdatingFromModel = false;

    public CommentPanel(GameModel model) {
        this.model = model;
        this.model.addListener(this::syncFromModel);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        textArea = new JTextArea();
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        textArea.setLineWrap(true); textArea.setWrapStyleWord(true);

        textArea.getDocument().addDocumentListener(new DocumentListener() {
            private void updateModelSilently() {
                if (!isUpdatingFromModel) model.setComment(textArea.getText());
            }
            @Override public void insertUpdate(DocumentEvent e) { updateModelSilently(); }
            @Override public void removeUpdate(DocumentEvent e) { updateModelSilently(); }
            @Override public void changedUpdate(DocumentEvent e) { updateModelSilently(); }
        });

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setMinimumSize(new Dimension(150, 100));
        add(scrollPane);
    }

    public boolean isTextAreaFocused() { return textArea.hasFocus(); }
    public void focusTextArea() { textArea.requestFocusInWindow(); }

    private void syncFromModel() {
        String expected = model.getComment();
        if (expected == null) expected = "";
        
        // Only trigger the View update if the text is definitively out-of-sync
        if (!textArea.getText().equals(expected)) {
            isUpdatingFromModel = true;
            textArea.setText(expected);
            isUpdatingFromModel = false;
        }
    }
}