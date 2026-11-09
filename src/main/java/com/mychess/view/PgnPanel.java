package com.mychess.view;

import chesspresso.game.Game;
import chesspresso.move.Move;
import com.mychess.model.GameModel;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.prefs.Preferences;

public class PgnPanel extends JPanel {
    private final GameModel model;
    private final JEditorPane editorPane;
    private final Preferences prefs = Preferences.userNodeForPackage(MainFrame.class);

    public PgnPanel(GameModel model) {
        this.model = model;
        this.model.addListener(this::refreshHtml);
        setLayout(new BorderLayout()); 

        editorPane = new JEditorPane();
        editorPane.setContentType("text/html");
        editorPane.setEditable(false);
        
        editorPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                try { 
                    model.getGame().gotoNode(Integer.parseInt(e.getDescription())); 
                    model.notifyListeners();
                } catch (Exception ignored) {}
            }
        });

        editorPane.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { showPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { showPopup(e); }

            private void showPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int pos = editorPane.viewToModel2D(e.getPoint());
                    if (pos >= 0) {
                        Document doc = editorPane.getDocument();
                        if (doc instanceof HTMLDocument) {
                            HTMLDocument hdoc = (HTMLDocument) doc;
                            Element elem = hdoc.getCharacterElement(pos);
                            AttributeSet attr = elem.getAttributes();
                            AttributeSet aAttr = (AttributeSet) attr.getAttribute(HTML.Tag.A);
                            if (aAttr != null) {
                                String href = (String) aAttr.getAttribute(HTML.Attribute.HREF);
                                if (href != null) {
                                    try {
                                        model.getGame().gotoNode(Integer.parseInt(href));
                                        model.notifyListeners();
                                        createContextMenu().show(e.getComponent(), e.getX(), e.getY());
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPopupMenu createContextMenu() {
        JPopupMenu popup = new JPopupMenu();

        JMenu annotateMenu = new JMenu("Annotate Move");
        annotateMenu.add(createNagItem("! (Good move)", (short) 1));
        annotateMenu.add(createNagItem("? (Bad move)", (short) 2));
        annotateMenu.add(createNagItem("!! (Brilliant move)", (short) 3));
        annotateMenu.add(createNagItem("?? (Blunder)", (short) 4));
        annotateMenu.add(createNagItem("!? (Interesting)", (short) 5));
        annotateMenu.add(createNagItem("?! (Dubious)", (short) 6));
        annotateMenu.addSeparator();
        annotateMenu.add(createNagItem("\u2A72 (White slight advantage)", (short) 14));
        annotateMenu.add(createNagItem("\u2A71 (Black slight advantage)", (short) 15));
        annotateMenu.add(createNagItem("\u00B1 (White advantage)", (short) 16));
        annotateMenu.add(createNagItem("\u2213 (Black advantage)", (short) 17));
        annotateMenu.add(createNagItem("+- (White winning)", (short) 18));
        annotateMenu.add(createNagItem("-+ (Black winning)", (short) 19));
        annotateMenu.addSeparator();
        annotateMenu.add(createNagItem("\u221E (Unclear)", (short) 13));
        annotateMenu.add(createNagItem("\u2313 (Better is)", (short) 142));
        annotateMenu.add(createNagItem("\u25A1 (Only move)", (short) 8));
        annotateMenu.add(createNagItem("\u0394 (With idea)", (short) 140));
        annotateMenu.add(createNagItem("\u2207 (Countering)", (short) 143));
        annotateMenu.add(createNagItem("N (Novelty)", (short) 146));
        annotateMenu.addSeparator();
        annotateMenu.add(createNagItem("\u2191 (Initiative)", (short) 36));
        annotateMenu.add(createNagItem("\u2192 (Attack)", (short) 40));
        annotateMenu.add(createNagItem("\u21C4 (Counterplay)", (short) 132));
        annotateMenu.add(createNagItem("\u21BB (Development)", (short) 32));
        annotateMenu.add(createNagItem("\u25CB (Space)", (short) 138));
        annotateMenu.add(createNagItem("\u2295 (Time trouble)", (short) 136));
        annotateMenu.add(createNagItem("\u2299 (Zugzwang)", (short) 22));

        JMenuItem clearNag = new JMenuItem("Clear Annotations");
        clearNag.addActionListener(e -> model.clearNAGs());
        annotateMenu.addSeparator();
        annotateMenu.add(clearNag);

        JMenuItem promoteVar = new JMenuItem("Promote Variation");
        promoteVar.addActionListener(e -> model.promoteVariation());
        
        JMenuItem promoteToMain = new JMenuItem("Promote to Main Line");
        promoteToMain.addActionListener(e -> model.promoteToMainLine());

        JMenuItem deleteRest = new JMenuItem("Delete Rest of Line");
        deleteRest.addActionListener(e -> model.deleteMovesFromHere());

        popup.add(annotateMenu);
        popup.addSeparator();
        popup.add(promoteVar);
        popup.add(promoteToMain);
        popup.addSeparator();
        popup.add(deleteRest);

        return popup;
    }

    private JMenuItem createNagItem(String label, short nag) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(e -> model.setNAG(nag));
        return item;
    }
    
    // --- STYLING HELPERS ---
    private String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private String getStyleString(Color color, boolean bold, boolean italic) {
        return "color:" + hex(color) + ";" + 
               (bold ? " font-weight:bold;" : " font-weight:normal;") + 
               (italic ? " font-style:italic;" : " font-style:normal;");
    }

    public void forceRefresh() { refreshHtml(); }

    private void refreshHtml() {
        String fontFam = prefs.get("pgn_font_family", "Monospaced");
        int fontSize = prefs.getInt("pgn_font_size", 14);
        
        StringBuilder sb = new StringBuilder("<html><body style=\"font-family:'").append(fontFam)
            .append("', monospace; font-size:").append(fontSize).append("px; padding:5px;\">");

        Game tempCursor = new Game(model.getGame().getModel());
        tempCursor.gotoStart();

        String startComment = tempCursor.getComment();
        if (startComment != null && !startComment.isEmpty()) {
            sb.append(formatComment(startComment)).append("<br/><br/>");
        }

        buildHtmlSequence(sb, tempCursor, 0, false);
        sb.append("</body></html>");
        
        DefaultCaret caret = (DefaultCaret) editorPane.getCaret();
        int updatePolicy = caret.getUpdatePolicy();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        editorPane.setText(sb.toString());
        caret.setUpdatePolicy(updatePolicy);
    }
    
    private String formatComment(String rawComment) {
        Color c = new Color(prefs.getInt("pgn_comment_color", new Color(0, 119, 0).getRGB()));
        boolean b = prefs.getBoolean("pgn_comment_bold", false);
        boolean i = prefs.getBoolean("pgn_comment_italic", true);
        
        String safeHtml = rawComment.replace("<", "&lt;")
                                    .replace(">", "&gt;")
                                    .replace("\n", "<br/>")
                                    .replace("[br]", "<br/>");
                                    
        return "<span style='" + getStyleString(c, b, i) + "'>" + safeHtml + "</span>";
    }

    private String formatNAGs(short[] nags) {
        if (nags == null || nags.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (short nag : nags) {
            String text = "";
            String color = "#780096"; // Purple default
            switch (nag) {
                case 1: text = "!"; color = "#0064C8"; break; 
                case 2: text = "?"; color = "#C80000"; break; 
                case 3: text = "!!"; color = "#0064C8"; break;
                case 4: text = "??"; color = "#C80000"; break;
                case 5: text = "!?"; color = "#C86400"; break; 
                case 6: text = "?!"; color = "#C86400"; break;
                case 8: text = "\u25A1"; break; 
                case 13: text = "\u221E"; break; 
                case 14: text = "\u2A72"; break; 
                case 15: text = "\u2A71"; break; 
                case 16: text = "\u00B1"; break; 
                case 17: text = "\u2213"; break; 
                case 18: text = "+-"; break;      
                case 19: text = "-+"; break;      
                case 22: text = "\u2299"; break; 
                case 32: text = "\u21BB"; break; 
                case 36: text = "\u2191"; break; 
                case 40: text = "\u2192"; break; 
                case 132: text = "\u21C4"; break; 
                case 136: text = "\u2295"; break; 
                case 138: text = "\u25CB"; break; 
                case 140: text = "\u0394"; break; 
                case 142: text = "\u2313"; break; 
                case 143: text = "\u2207"; break; 
                case 146: text = "N"; break; 
            }
            if (!text.isEmpty()) {
                sb.append("&nbsp;<span style='color:").append(color)
                  .append("; font-weight:bold; text-style:normal; text-decoration:none;'>")
                  .append(text).append("</span>");
            }
        }
        return sb.toString();
    }

    private void buildHtmlSequence(StringBuilder sb, Game cursor, int depth, boolean forceNum) {
        Move[] nextMoves = cursor.getNextMoves();
        if (nextMoves == null || nextMoves.length == 0) return;

        cursor.goForward(0);
        Move mainMove = cursor.getLastMove();
        boolean isWhite = chesspresso.Chess.isWhitePly(cursor.getCurrentPly() - 1); 
        
        Color mColor = new Color(prefs.getInt("pgn_main_color", Color.BLACK.getRGB()));
        boolean mBold = prefs.getBoolean("pgn_main_bold", false);
        boolean mItalic = prefs.getBoolean("pgn_main_italic", false);
        
        Color vColor = new Color(prefs.getInt("pgn_var_color", Color.DARK_GRAY.getRGB()));
        boolean vBold = prefs.getBoolean("pgn_var_bold", false);
        boolean vItalic = prefs.getBoolean("pgn_var_italic", true);

        // --- RENDER MOVE NUMBERS ---
        if (isWhite || forceNum) {
            sb.append("<b>").append((cursor.getCurrentPly() + 1) / 2).append(isWhite ? ". " : "... ").append("</b>");
        }

        // --- RENDER MAIN MOVE ---
        boolean isCurNode = (cursor.getCurNode() == model.getGame().getCurNode());
        String style = isCurNode 
                ? "background-color:#ADD8E6; text-decoration:none; " + getStyleString(mColor, mBold, mItalic)
                : "text-decoration:none; " + getStyleString(mColor, mBold, mItalic);
        
        sb.append("<a href='").append(cursor.getCurNode()).append("' style='").append(style).append("'>")
          .append(mainMove.getSAN()).append("</a>").append(formatNAGs(cursor.getNags())).append(" ");

        String comment = cursor.getComment();
        boolean nextForceNum = false;
        if (comment != null && !comment.isEmpty()) {
            sb.append(formatComment(comment)).append(" ");
            nextForceNum = true;
        }
        cursor.goBack();

        // --- RENDER VARIATIONS ---
        if (nextMoves.length > 1) {
            String vSpan = "<span style='" + getStyleString(vColor, false, false) + "'>";
            for (int i = 1; i < nextMoves.length; i++) {
                sb.append(depth == 0 ? "<br/>&nbsp;&nbsp;&nbsp;&nbsp;" : " ").append(vSpan).append("(").append("</span>");
                
                cursor.goForward(i);
                Move varMove = cursor.getLastMove();
                boolean vWhite = chesspresso.Chess.isWhitePly(cursor.getCurrentPly() - 1);
                
                sb.append("<b>").append((cursor.getCurrentPly() + 1) / 2).append(vWhite ? ". " : "... ").append("</b>");
                
                boolean isCurVNode = (cursor.getCurNode() == model.getGame().getCurNode());
                String vStyle = isCurVNode 
                        ? "background-color:#ADD8E6; text-decoration:none; " + getStyleString(vColor, vBold, vItalic)
                        : "text-decoration:none; " + getStyleString(vColor, vBold, vItalic);
                
                sb.append("<a href='").append(cursor.getCurNode()).append("' style='").append(vStyle).append("'>")
                  .append(varMove.getSAN()).append("</a>").append(formatNAGs(cursor.getNags())).append(" ");
                
                String vComment = cursor.getComment();
                boolean vForceNum = false;
                if (vComment != null && !vComment.isEmpty()) {
                    sb.append(formatComment(vComment)).append(" ");
                    vForceNum = true;
                }

                buildHtmlSequence(sb, cursor, depth + 1, vForceNum);
                cursor.goBack();

                sb.append(vSpan).append(")").append("</span> ");
                nextForceNum = true; 
            }
            if (depth == 0) sb.append("<br/>");
        }

        cursor.goForward(0);
        buildHtmlSequence(sb, cursor, depth, nextForceNum);
        cursor.goBack();
    }
}