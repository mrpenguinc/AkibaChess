package com.mychess.model;

import chesspresso.game.Game;
import chesspresso.move.Move;
import chesspresso.pgn.PGNReader;
import chesspresso.pgn.PGNWriter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GameModel {
    private Game game;
    private String currentFilePath;
    private int currentDbId = -1;
    private boolean isDirty = false;
    private final List<Runnable> listeners = new ArrayList<>();
    
    // Tracks custom tags that chesspresso natively tries to drop
    private String customPropsText = "";

    public GameModel() { newGame(); }

    public void addListener(Runnable listener) { listeners.add(listener); }
    public void notifyListeners() { for (Runnable l : listeners) l.run(); }

    public Game getGame() { return game; }
    public String getCurrentFilePath() { return currentFilePath; }
    public void setCurrentFilePath(String path) { this.currentFilePath = path; }
    
    public int getCurrentDbId() { return currentDbId; }
    public void setCurrentDbId(int id) { this.currentDbId = id; }
    
    public boolean isDirty() { return isDirty; }
    public void setClean() { this.isDirty = false; }
    
    public String getCustomPropsText() { return customPropsText; }

    public void newGame() {
        game = new Game();
        game.setTag("Event", "?");
        game.setTag("Site", "?");
        game.setTag("Date", java.time.LocalDate.now().toString().replace("-", "."));
        game.setTag("Round", "?");
        game.setTag("White", "?");
        game.setTag("Black", "?");
        game.setTag("Result", "*");
        
        currentFilePath = null;
        currentDbId = -1;
        customPropsText = "";
        isDirty = false;
        notifyListeners();
    }
    
    public void setupFromFen(String fullFen) throws Exception {
        String pgnPayload = "[Event \"?\"]\n" +
                            "[Site \"?\"]\n" +
                            "[Date \"?\"]\n" +
                            "[Round \"?\"]\n" +
                            "[White \"?\"]\n" +
                            "[Black \"?\"]\n" +
                            "[Result \"*\"]\n" +
                            "[SetUp \"1\"]\n" +
                            "[FEN \"" + fullFen + "\"]\n\n*";
                            
        loadPgnString(pgnPayload, -1);
        this.isDirty = true;
        notifyListeners();
    }

    public void loadPgn(String filepath) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(filepath))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }
        String pgnText = sb.toString();
        
        try (FileInputStream fis = new FileInputStream(filepath)) {
            loadFromStream(fis);
            this.currentFilePath = filepath;
            this.currentDbId = -1;
            extractCustomProps(pgnText);
        }
    }

    public void loadPgnString(String pgn, int dbId) throws Exception {
        try (ByteArrayInputStream is = new ByteArrayInputStream(pgn.getBytes(StandardCharsets.UTF_8))) {
            loadFromStream(is);
            this.currentFilePath = null;
            this.currentDbId = dbId;
            extractCustomProps(pgn);
        }
    }
    
    private void extractCustomProps(String pgn) {
        StringBuilder custom = new StringBuilder();
        Matcher m = Pattern.compile("^\\[([A-Za-z0-9_]+)\\s+\"([^\"]+)\"\\]", Pattern.MULTILINE).matcher(pgn);
        while (m.find()) {
            String key = m.group(1);
            if (!isStandardTag(key)) {
                custom.append(key).append(": ").append(m.group(2)).append("\n");
            }
        }
        this.customPropsText = custom.toString().trim();
    }
    
    private boolean isStandardTag(String key) {
        String[] req = {"Event", "Site", "Date", "Round", "White", "Black", "Result", "SetUp", "FEN"};
        for (String r : req) if (r.equalsIgnoreCase(key)) return true;
        return false;
    }
    
    private void loadFromStream(InputStream is) throws Exception {
        PGNReader reader = new PGNReader(is, "stream");
        Game parsedGame = reader.parseGame();
        if (parsedGame != null) {
            this.game = parsedGame;
            this.game.gotoStart();
            this.isDirty = false;
            notifyListeners();
        } else throw new Exception("No game found.");
    }

    public void savePgn(String filepath) throws Exception {
        String pgnPayload = getGameAsPgnString();
        try (PrintWriter pw = new PrintWriter(new FileWriter(filepath))) {
            pw.write(pgnPayload);
            isDirty = false;
        }
    }
    
    public String getGameAsPgnString() throws Exception {
        sanitizeTags();
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            PGNWriter writer = new PGNWriter(pw);
            writer.write(game.getModel());
        }
        String pgn = sw.toString();
        
        // Forcibly inject custom tags by finding the Result tag and appending to the next line
        if (customPropsText != null && !customPropsText.trim().isEmpty()) {
            StringBuilder tagsToInject = new StringBuilder();
            for (String line : customPropsText.split("\n")) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    if (!isStandardTag(key)) {
                        tagsToInject.append("[").append(key).append(" \"").append(parts[1].trim()).append("\"]\n");
                    }
                }
            }
            if (tagsToInject.length() > 0) {
                int resultIdx = pgn.indexOf("[Result");
                if (resultIdx != -1) {
                    int endOfResultLine = pgn.indexOf('\n', resultIdx);
                    if (endOfResultLine != -1) {
                        // Bulletproof substring injection
                        pgn = pgn.substring(0, endOfResultLine + 1) + tagsToInject.toString() + pgn.substring(endOfResultLine + 1);
                    }
                } else {
                    pgn = tagsToInject.toString() + "\n" + pgn;
                }
            }
        }
        return pgn;
    }
    
    private void sanitizeTags() {
        String[] req = {"Event", "Site", "Date", "Round", "White", "Black", "Result"};
        for (String tag : req) {
            if (game.getTag(tag) == null) game.setTag(tag, "?");
        }
    }

    public void updateGameInfo(String white, String black, String event, String site, String date, String round, String result, String customProps) {
        if (white != null && !white.trim().isEmpty()) game.setTag("White", white.trim());
        if (black != null && !black.trim().isEmpty()) game.setTag("Black", black.trim());
        if (event != null && !event.trim().isEmpty()) game.setTag("Event", event.trim());
        if (site != null && !site.trim().isEmpty()) game.setTag("Site", site.trim());
        if (date != null && !date.trim().isEmpty()) game.setTag("Date", date.trim());
        if (round != null && !round.trim().isEmpty()) game.setTag("Round", round.trim());
        if (result != null && !result.trim().isEmpty()) game.setTag("Result", result.trim());

        this.customPropsText = customProps == null ? "" : customProps.trim();
        if (this.customPropsText.length() > 0) {
            for (String line : this.customPropsText.split("\n")) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    game.setTag(parts[0].trim(), parts[1].trim());
                }
            }
        }
        isDirty = true;
        notifyListeners(); 
    }

    // --- GAME TREE EDITING CAPABILITIES ---

    public void setNAG(short nag) {
        if (game.getCurNode() != 0) {
            for (short i = 1; i <= 255; i++) {
                if (game.hasNag(i)) game.removeNag(i);
            }
            game.addNag(nag);
            isDirty = true;
            notifyListeners();
        }
    }

    public void clearNAGs() {
        if (game.getCurNode() != 0) {
            for (short i = 1; i <= 255; i++) {
                if (game.hasNag(i)) game.removeNag(i);
            }
            isDirty = true;
            notifyListeners();
        }
    }

    public void deleteMovesFromHere() {
        if (game.getCurNode() != 0) {
            game.deleteCurrentLine();
            isDirty = true;
            notifyListeners();
        }
    }

    public void promoteVariation() {
        try {
            String currentPgn = getGameAsPgnString();
            loadPgnString(currentPgn, currentDbId);
            isDirty = true;
            notifyListeners();
        } catch (Exception ignored) {}
    }

    public void promoteToMainLine() {
        try {
            String currentPgn = getGameAsPgnString();
            loadPgnString(currentPgn, currentDbId);
            isDirty = true;
            notifyListeners();
        } catch (Exception ignored) {}
    }

    // ---------------------------------------

    public void forwardMove() { if (game.goForward()) notifyListeners(); }
    public void backwardMove() { if (game.goBack()) notifyListeners(); }

    public void nextVariation() {
        Move lastMove = game.getLastMove();
        if (lastMove == null) return;
        int originalNode = game.getCurNode();
        game.goBack();
        Move[] siblings = game.getNextMoves();
        for (int i = 0; i < siblings.length; i++) {
            if (siblings[i].getShortMoveDesc() == lastMove.getShortMoveDesc()) {
                if (i + 1 < siblings.length) { game.goForward(i + 1); notifyListeners(); return; }
                break;
            }
        }
        game.gotoNode(originalNode);
    }

    public void prevVariation() {
        Move lastMove = game.getLastMove();
        if (lastMove == null) return;
        int originalNode = game.getCurNode();
        game.goBack();
        Move[] siblings = game.getNextMoves();
        for (int i = 0; i < siblings.length; i++) {
            if (siblings[i].getShortMoveDesc() == lastMove.getShortMoveDesc()) {
                if (i > 0) { game.goForward(i - 1); notifyListeners(); return; }
                break;
            }
        }
        game.gotoNode(originalNode);
    }

    public void makeMove(short shortMove) {
        Move[] nextMoves = game.getNextMoves();
        for (int i = 0; i < nextMoves.length; i++) {
            if (nextMoves[i].getShortMoveDesc() == shortMove) {
                game.goForward(i);
                notifyListeners();
                return;
            }
        }
        try { game.getPosition().doMove(shortMove); isDirty = true; } catch (Exception ignored) {} 
        notifyListeners();
    }

    public String getComment() { return game.getComment() == null ? "" : game.getComment(); }

    public void setComment(String comment) {
        if (comment == null || comment.trim().isEmpty()) game.setComment(null); 
        else game.setComment(comment);
        isDirty = true;
        notifyListeners();
    }
}