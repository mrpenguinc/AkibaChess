package com.mychess.util;

import chesspresso.Chess;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ResourceLoader {
    private static final Map<Integer, BufferedImage> pieceImages = new HashMap<>();
    private static BufferedImage boardImage;

    static {
        boardImage = loadImg("/board.png");
        
        pieceImages.put(Chess.pieceToStone(Chess.KING, Chess.WHITE), loadImg("/wk.png"));
        pieceImages.put(Chess.pieceToStone(Chess.QUEEN, Chess.WHITE), loadImg("/wq.png"));
        pieceImages.put(Chess.pieceToStone(Chess.ROOK, Chess.WHITE), loadImg("/wr.png"));
        pieceImages.put(Chess.pieceToStone(Chess.BISHOP, Chess.WHITE), loadImg("/wb.png"));
        pieceImages.put(Chess.pieceToStone(Chess.KNIGHT, Chess.WHITE), loadImg("/wn.png"));
        pieceImages.put(Chess.pieceToStone(Chess.PAWN, Chess.WHITE), loadImg("/wp.png"));
        
        pieceImages.put(Chess.pieceToStone(Chess.KING, Chess.BLACK), loadImg("/bk.png"));
        pieceImages.put(Chess.pieceToStone(Chess.QUEEN, Chess.BLACK), loadImg("/bq.png"));
        pieceImages.put(Chess.pieceToStone(Chess.ROOK, Chess.BLACK), loadImg("/br.png"));
        pieceImages.put(Chess.pieceToStone(Chess.BISHOP, Chess.BLACK), loadImg("/bb.png"));
        pieceImages.put(Chess.pieceToStone(Chess.KNIGHT, Chess.BLACK), loadImg("/bn.png"));
        pieceImages.put(Chess.pieceToStone(Chess.PAWN, Chess.BLACK), loadImg("/bp.png"));
    }

    private static BufferedImage loadImg(String path) {
        // Using getResourceAsStream is significantly more robust for Maven/JAR packaged resources
        try (InputStream is = ResourceLoader.class.getResourceAsStream(path)) {
            if (is != null) {
                return ImageIO.read(is);
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not load image " + path);
        }
        return null;
    }

    public static BufferedImage getPieceImage(int stone) { return pieceImages.get(stone); }
    public static BufferedImage getBoardImage() { return boardImage; }

    public interface IconDrawer { void draw(Graphics2D g2d, int w, int h); }

    public static class VectorIcon implements Icon {
        private final int size; private final IconDrawer drawer;
        public VectorIcon(int size, IconDrawer drawer) { this.size = size; this.drawer = drawer; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2d = (Graphics2D) g.create(); g2d.translate(x, y);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            drawer.draw(g2d, size, size); g2d.dispose();
        }
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
    }
}