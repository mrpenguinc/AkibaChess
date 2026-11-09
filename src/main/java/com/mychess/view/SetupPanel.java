package com.mychess.view;

import chesspresso.Chess;
import com.mychess.util.ResourceLoader;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.function.Consumer;

public class SetupPanel extends JPanel {
    private final JTextField fenField;
    private final int[] board = new int[64];
    private final SetupBoardPreview previewPanel;

    private final JComboBox<String> colorCombo;
    private final JCheckBox wkCheck, wqCheck, bkCheck, bqCheck;
    private final JTextField epField;
    private final JSpinner halfSpinner, fullSpinner;
    
    private boolean isUpdating = false;

    public SetupPanel(Consumer<String> onConfirm, Runnable onCancel) {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        previewPanel = new SetupBoardPreview();
        add(previewPanel, BorderLayout.CENTER);

        // --- FEN State Controls ---
        colorCombo = new JComboBox<>(new String[]{"White to move", "Black to move"});
        wkCheck = new JCheckBox("K", true); wqCheck = new JCheckBox("Q", true);
        bkCheck = new JCheckBox("k", true); bqCheck = new JCheckBox("q", true);
        epField = new JTextField("-", 3);
        halfSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
        fullSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 9999, 1));

        JPanel statePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        statePanel.add(colorCombo);
        statePanel.add(new JLabel("Castle:"));
        statePanel.add(wkCheck); statePanel.add(wqCheck); statePanel.add(bkCheck); statePanel.add(bqCheck);
        statePanel.add(new JLabel("EP:")); statePanel.add(epField);
        statePanel.add(new JLabel("Half:")); statePanel.add(halfSpinner);
        statePanel.add(new JLabel("Full:")); statePanel.add(fullSpinner);

        fenField = new JTextField();
        fenField.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
        
        // Maps the Spacebar to output a '/' natively 
        fenField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (e.getKeyChar() == ' ') {
                    e.setKeyChar('/');
                }
            }
        });

        fenField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { handleInput(); }
            @Override public void removeUpdate(DocumentEvent e) { handleInput(); }
            @Override public void changedUpdate(DocumentEvent e) { handleInput(); }
        });

        fenField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "confirm");
        fenField.getActionMap().put("confirm", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { onConfirm.accept(generateFullFen()); }
        });

        fenField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        fenField.getActionMap().put("cancel", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { onCancel.run(); }
        });

        JPanel inputContainer = new JPanel(new BorderLayout(5, 5));
        inputContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JLabel lbl = new JLabel("Board:");
        lbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        
        JLabel helpLbl = new JLabel("Live setup: type pieces. Spacebar outputs '/'. Paste full FEN to auto-fill state. Enter to apply, Esc to cancel.");
        helpLbl.setForeground(Color.GRAY);
        helpLbl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));

        JPanel centerInput = new JPanel(new BorderLayout(0, 5));
        centerInput.add(fenField, BorderLayout.CENTER);
        centerInput.add(statePanel, BorderLayout.SOUTH);

        inputContainer.add(lbl, BorderLayout.WEST);
        inputContainer.add(centerInput, BorderLayout.CENTER);
        inputContainer.add(helpLbl, BorderLayout.SOUTH);
        
        add(inputContainer, BorderLayout.SOUTH);
    }

    private void handleInput() {
        if (isUpdating) return;
        String text = fenField.getText();
        String[] parts = text.split("\\s+");
        
        // If a full FEN was pasted, parse the state tokens and strip the text field down to just the board
        if (parts.length >= 4) {
            SwingUtilities.invokeLater(() -> {
                isUpdating = true;
                fenField.setText(parts[0]);
                
                if (parts.length > 1) colorCombo.setSelectedIndex(parts[1].equalsIgnoreCase("b") ? 1 : 0);
                if (parts.length > 2) {
                    String c = parts[2];
                    wkCheck.setSelected(c.contains("K")); wqCheck.setSelected(c.contains("Q"));
                    bkCheck.setSelected(c.contains("k")); bqCheck.setSelected(c.contains("q"));
                }
                if (parts.length > 3) epField.setText(parts[3]);
                if (parts.length > 4) {
                    try { halfSpinner.setValue(Integer.parseInt(parts[4])); } catch (Exception ignored) {}
                }
                if (parts.length > 5) {
                    try { fullSpinner.setValue(Integer.parseInt(parts[5])); } catch (Exception ignored) {}
                }
                isUpdating = false;
                updateBoard();
            });
        } else {
            updateBoard();
        }
    }

    public void startSetup() {
        isUpdating = true;
        fenField.setText("");
        colorCombo.setSelectedIndex(0);
        wkCheck.setSelected(true); wqCheck.setSelected(true);
        bkCheck.setSelected(true); bqCheck.setSelected(true);
        epField.setText("-");
        halfSpinner.setValue(0);
        fullSpinner.setValue(1);
        isUpdating = false;
        updateBoard();
        fenField.requestFocusInWindow();
    }

    private void updateBoard() {
        Arrays.fill(board, Chess.NO_STONE);
        String fenPart = fenField.getText();
        
        int row = 7, col = 0;
        for (char c : fenPart.toCharArray()) {
            if (row < 0) break;
            if (c == '/' || c == ' ') {
                row--; col = 0;
            } else if (Character.isDigit(c)) {
                col += (c - '0');
            } else {
                int stone = charToStone(c);
                if (stone != Chess.NO_STONE && col < 8) {
                    board[Chess.coorToSqi(col, row)] = stone;
                    col++;
                }
            }
        }
        previewPanel.repaint();
    }
    
    private int charToStone(char c) {
        int color = Character.isUpperCase(c) ? Chess.WHITE : Chess.BLACK;
        int piece = -1;
        switch(Character.toLowerCase(c)) {
            case 'p': piece = Chess.PAWN; break;
            case 'n': piece = Chess.KNIGHT; break;
            case 'b': piece = Chess.BISHOP; break;
            case 'r': piece = Chess.ROOK; break;
            case 'q': piece = Chess.QUEEN; break;
            case 'k': piece = Chess.KING; break;
        }
        return piece == -1 ? Chess.NO_STONE : Chess.pieceToStone(piece, color);
    }

    private String generateFullFen() {
        String partial = fenField.getText();
        String[] ranks = partial.split("[/ ]", -1);
        StringBuilder fullBoard = new StringBuilder();
        
        for (int i = 0; i < 8; i++) {
            if (i < ranks.length) {
                String r = ranks[i];
                int count = 0;
                for (char c : r.toCharArray()) {
                    if (Character.isDigit(c)) count += (c - '0');
                    else count++;
                }
                fullBoard.append(r);
                if (count < 8) fullBoard.append(8 - count);
            } else {
                fullBoard.append("8");
            }
            if (i < 7) fullBoard.append("/");
        }
        
        String color = colorCombo.getSelectedIndex() == 1 ? "b" : "w";
        
        StringBuilder castling = new StringBuilder();
        if (wkCheck.isSelected()) castling.append("K");
        if (wqCheck.isSelected()) castling.append("Q");
        if (bkCheck.isSelected()) castling.append("k");
        if (bqCheck.isSelected()) castling.append("q");
        if (castling.length() == 0) castling.append("-");
        
        String ep = epField.getText().trim();
        if (ep.isEmpty()) ep = "-";
        
        String half = halfSpinner.getValue().toString();
        String full = fullSpinner.getValue().toString();
        
        return fullBoard.toString() + " " + color + " " + castling.toString() + " " + ep + " " + half + " " + full;
    }

    private class SetupBoardPreview extends JPanel {
        public SetupBoardPreview() {
            setMinimumSize(new Dimension(200, 200));
            setBackground(Color.WHITE);
        }

        private int getSquareSize() { return Math.min(getWidth() - 75, getHeight() - 25) / 8; }
        private int getBoardSize() { return getSquareSize() * 8; }
        private int getOffsetX() { return 25; }
        private int getOffsetY() { return 0; }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            
            int sz = getBoardSize(), sq = getSquareSize(), ox = getOffsetX(), oy = getOffsetY();
            BufferedImage bg = ResourceLoader.getBoardImage();
            
            if (bg != null) {
                g2d.drawImage(bg, ox, oy, sz, sz, null);
            } else {
                for (int r = 0; r < 8; r++) {
                    for (int c = 0; c < 8; c++) {
                        g2d.setColor((r + c) % 2 == 0 ? Color.LIGHT_GRAY : Color.DARK_GRAY);
                        g2d.fillRect(ox + c * sq, oy + r * sq, sq, sq);
                    }
                }
            }

            for (int i = 0; i < 64; i++) {
                if (board[i] != Chess.NO_STONE) {
                    int col = Chess.sqiToCol(i);
                    int row = 7 - Chess.sqiToRow(i); 
                    drawPiece(g2d, board[i], ox + (col * sq), oy + (row * sq), sq);
                }
            }
        }

        private void drawPiece(Graphics2D g2d, int stone, int x, int y, int size) {
            BufferedImage img = ResourceLoader.getPieceImage(stone);
            if (img != null) { g2d.drawImage(img, x, y, size, size, null); }
            else {
                g2d.setColor(Chess.stoneToColor(stone) == Chess.WHITE ? Color.WHITE : Color.BLACK);
                g2d.fillOval(x + 5, y + 5, size - 10, size - 10);
                g2d.setColor(Chess.stoneToColor(stone) == Chess.WHITE ? Color.BLACK : Color.WHITE);
                g2d.drawString(String.valueOf(Chess.stoneToChar(stone)), x + size / 2 - 5, y + size / 2 + 5);
            }
        }
    }
}