package com.mychess.view;

import chesspresso.Chess;
import chesspresso.move.Move;
import com.mychess.model.GameModel;
import com.mychess.util.ResourceLoader;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

public class BoardPanel extends JPanel {
    private final GameModel model;
    private boolean flipped = false;

    private int dragSqi = -1; 
    private int dragPiece = Chess.NO_STONE;
    private int dragX, dragY, dragOffsetX, dragOffsetY;

    public BoardPanel(GameModel model) {
        this.model = model;
        this.model.addListener(this::repaint);
        this.setMinimumSize(new Dimension(200, 200));
        this.setFocusable(true);
        this.setBackground(Color.WHITE);

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { requestFocusInWindow(); handleMousePress(e.getX(), e.getY()); }
            @Override public void mouseDragged(MouseEvent e) {
                if (dragPiece != Chess.NO_STONE) { dragX = e.getX() - dragOffsetX; dragY = e.getY() - dragOffsetY; repaint(); }
            }
            @Override public void mouseReleased(MouseEvent e) { handleMouseRelease(e.getX(), e.getY()); }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    public void flipBoard() { flipped = !flipped; repaint(); }

    private void handleMousePress(int x, int y) {
        int sqi = getSqiFromXY(x, y);
        if (sqi != -1) {
            int stone = model.getGame().getPosition().getStone(sqi);
            if (stone != Chess.NO_STONE && Chess.stoneToColor(stone) == model.getGame().getPosition().getToPlay()) {
                dragSqi = sqi;
                dragPiece = stone;
                Point pnt = getCoordinatesFromSqi(sqi);
                dragOffsetX = x - pnt.x; dragOffsetY = y - pnt.y;
                dragX = x - dragOffsetX; dragY = y - dragOffsetY;
            }
        }
    }

    private void handleMouseRelease(int x, int y) {
        if (dragPiece != Chess.NO_STONE) {
            int targetSqi = getSqiFromXY(x, y);
            if (targetSqi != -1 && targetSqi != dragSqi) {
                short[] legalMoves = model.getGame().getPosition().getAllMoves();
                for (short m : legalMoves) {
                    if (Move.getFromSqi(m) == dragSqi && Move.getToSqi(m) == targetSqi) {
                        model.makeMove(m);
                        break;
                    }
                }
            }
            dragPiece = Chess.NO_STONE; dragSqi = -1;
            repaint();
        }
    }

    private int getSquareSize() { return Math.min(getWidth() - 75, getHeight() - 25) / 8; }
    private int getBoardSize() { return getSquareSize() * 8; }
    private int getOffsetX() { return 25; }
    private int getOffsetY() { return 0; }

    private Point getCoordinatesFromSqi(int sqi) {
        int col = Chess.sqiToCol(sqi);
        int row = Chess.sqiToRow(sqi);
        if (flipped) col = 7 - col; else row = 7 - row;
        return new Point(getOffsetX() + (col * getSquareSize()), getOffsetY() + (row * getSquareSize()));
    }

    private int getSqiFromXY(int x, int y) {
        if (x < getOffsetX() || x >= getOffsetX() + getBoardSize() || y < getOffsetY() || y >= getOffsetY() + getBoardSize()) return -1;
        int col = (x - getOffsetX()) / getSquareSize();
        int row = (y - getOffsetY()) / getSquareSize();
        if (flipped) col = 7 - col; else row = 7 - row;
        return Chess.coorToSqi(col, row);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        try {
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

            Move lastMove = model.getGame().getLastMove();
            if (lastMove != null) {
                g2d.setColor(Color.RED);
                g2d.setStroke(new BasicStroke(3f));
                Point pFrom = getCoordinatesFromSqi(lastMove.getFromSqi());
                Point pTo = getCoordinatesFromSqi(lastMove.getToSqi());
                g2d.drawRect(pFrom.x + 1, pFrom.y + 1, sq - 3, sq - 3);
                g2d.drawRect(pTo.x + 1, pTo.y + 1, sq - 3, sq - 3);
            }

            g2d.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
            g2d.setColor(Color.DARK_GRAY); FontMetrics fm = g2d.getFontMetrics();
            for (int i = 0; i < 8; i++) {
                String rank = flipped ? String.valueOf(i + 1) : String.valueOf(8 - i);
                g2d.drawString(rank, ox - fm.stringWidth(rank) - 8, oy + (i * sq) + (sq / 2) + (fm.getAscent() / 2) - 2);
                String file = String.valueOf(flipped ? (char)('h' - i) : (char)('a' + i));
                g2d.drawString(file, ox + (i * sq) + (sq / 2) - (fm.stringWidth(file) / 2), oy + sz + fm.getAscent() + 4);
            }

            for (int i = 0; i < 64; i++) {
                int stone = model.getGame().getPosition().getStone(i);
                if (stone != Chess.NO_STONE && i != dragSqi) {
                    Point pnt = getCoordinatesFromSqi(i);
                    drawPiece(g2d, stone, pnt.x, pnt.y, sq);
                }
            }

            if (dragPiece != Chess.NO_STONE) drawPiece(g2d, dragPiece, dragX, dragY, sq);
            
            drawTurnIndicators(g2d);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void drawTurnIndicators(Graphics2D g2d) {
        int isz = 24; // Increased Turn Indicator size
        int xPos = getOffsetX() + getBoardSize() + 10;
        int centerY = getOffsetY() + (getBoardSize() / 2);
        
        int topY = centerY - isz - 2;
        int bottomY = centerY + 2;
        
        int topPlayerColor = flipped ? chesspresso.Chess.WHITE : chesspresso.Chess.BLACK;
        int bottomPlayerColor = flipped ? chesspresso.Chess.BLACK : chesspresso.Chess.WHITE;
        
        drawIndicatorRect(g2d, xPos, topY, isz, topPlayerColor == chesspresso.Chess.WHITE ? Color.WHITE : Color.BLACK, model.getGame().getPosition().getToPlay() == topPlayerColor);
        int topCapColor = (topPlayerColor == chesspresso.Chess.WHITE) ? chesspresso.Chess.BLACK : chesspresso.Chess.WHITE;
        drawCapturedPieces(g2d, xPos, topY - 4, topCapColor, true, isz);

        drawIndicatorRect(g2d, xPos, bottomY, isz, bottomPlayerColor == chesspresso.Chess.WHITE ? Color.WHITE : Color.BLACK, model.getGame().getPosition().getToPlay() == bottomPlayerColor);
        int bottomCapColor = (bottomPlayerColor == chesspresso.Chess.WHITE) ? chesspresso.Chess.BLACK : chesspresso.Chess.WHITE;
        drawCapturedPieces(g2d, xPos, bottomY + isz + 4, bottomCapColor, false, isz);
    }

    private void drawIndicatorRect(Graphics2D g2d, int x, int y, int size, Color fill, boolean isTurn) {
        if (!isTurn) return;
        g2d.setColor(fill); g2d.fillRect(x, y, size, size);
        g2d.setColor(Color.DARK_GRAY); g2d.setStroke(new BasicStroke(1f)); g2d.drawRect(x, y, size, size);
    }

    private void drawCapturedPieces(Graphics2D g2d, int indX, int startY, int capColor, boolean upwards, int indSize) {
        int[] cur = new int[6]; 
        for (int i = 0; i < 64; i++) {
            int stone = model.getGame().getPosition().getStone(i);
            if (stone != Chess.NO_STONE && chesspresso.Chess.stoneToColor(stone) == capColor) {
                int piece = chesspresso.Chess.stoneToPiece(stone);
                if (piece >= 0 && piece < cur.length) {
                    cur[piece]++;
                }
            }
        }
        
        int[] start = new int[6];
        start[Chess.PAWN] = 8;
        start[Chess.KNIGHT] = 2;
        start[Chess.BISHOP] = 2;
        start[Chess.ROOK] = 2;
        start[Chess.QUEEN] = 1;
        
        int drawSize = 32; // Increased to 32px
        int pieceX = indX - (drawSize - indSize) / 2; // Remains perfectly centered over the 24px indicator
        
        g2d.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        FontMetrics fm = g2d.getFontMetrics();
        int y = startY;
        
        int[] pieces = {Chess.PAWN, Chess.KNIGHT, Chess.BISHOP, Chess.ROOK, Chess.QUEEN};
        
        for (int p : pieces) {
            int count = Math.max(0, start[p] - cur[p]);
            if (count > 0) {
                if (upwards) y -= drawSize; 
                
                int stoneToDraw = chesspresso.Chess.pieceToStone(p, capColor);
                BufferedImage img = ResourceLoader.getPieceImage(stoneToDraw);
                if (img != null) g2d.drawImage(img, pieceX, y, drawSize, drawSize, null);
                
                if (count > 1) {
                    String text = String.valueOf(count);
                    g2d.setColor(Color.DARK_GRAY);
                    g2d.drawString(text, pieceX + drawSize + 2, y + (drawSize / 2) + (fm.getAscent() / 2) - 1);
                }
                
                if (upwards) y -= 2; 
                else y += (drawSize + 2); 
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