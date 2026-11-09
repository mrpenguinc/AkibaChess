package com.mychess.view;

import bibliothek.gui.dock.common.CControl;
import bibliothek.gui.dock.common.CGrid;
import bibliothek.gui.dock.common.DefaultSingleCDockable;
import bibliothek.gui.dock.common.theme.ThemeMap;
import bibliothek.gui.dock.util.Priority;
import bibliothek.gui.dock.util.color.ColorManager;
import chesspresso.game.Game;
import chesspresso.pgn.PGNReader;
import chesspresso.pgn.PGNWriter;
import com.mychess.db.DatabaseService;
import com.mychess.model.GameModel;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;

public class MainFrame extends JFrame {
    private final GameModel model;
    
    private final JPanel boardContainer;
    private final CardLayout boardCardLayout;
    private final BoardPanel boardPanel;
    private final SetupPanel setupPanel;
    
    private final PgnPanel pgnPanel;
    private final CommentPanel commentPanel;
    private final PdfPanel pdfPanel;
    private final DbSwitcherPanel dbSwitcherPanel;
    
    private final DefaultSingleCDockable boardDockable;
    private final DefaultSingleCDockable pgnDockable;
    private final DefaultSingleCDockable cmtDockable;
    private final DefaultSingleCDockable dbDockable;
    private final DefaultSingleCDockable pdfDockable;
    private final DefaultSingleCDockable switcherDockable;
    
    private final CControl control;
    private final DatabaseService dbService;
    private final DatabasePanel dbPanel;
    
    private String currentDbPath = null;
    private final Preferences prefs;
    private final File layoutFile;

    public MainFrame() {
        super("Pro Chess");
        
        this.model = new GameModel();
        this.model.addListener(this::updateTitle);
        
        this.prefs = Preferences.userNodeForPackage(MainFrame.class);
        this.layoutFile = new File(System.getProperty("user.home"), ".prochess_layout.xml");

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); 
        setSize(prefs.getInt("window_w", 1400), prefs.getInt("window_h", 800));
        int x = prefs.getInt("window_x", -1), y = prefs.getInt("window_y", -1);
        if (x != -1 && y != -1) setLocation(x, y);
        else setLocationRelativeTo(null);

        this.dbService = new DatabaseService(); 
        this.dbPanel = new DatabasePanel(this::loadGameFromDb, this::handleTableEdit);
        setupDatabaseContextMenu();

        this.boardPanel = new BoardPanel(model);
        this.boardPanel.setBackground(Color.WHITE);
        
        this.boardPanel.addMouseWheelListener(e -> {
            if (e.getWheelRotation() > 0) model.forwardMove();
            else model.backwardMove();
        });
        
        this.boardPanel.setComponentPopupMenu(createBoardAnnotationMenu());
        
        this.boardCardLayout = new CardLayout();
        this.boardContainer = new JPanel(boardCardLayout);
        this.boardContainer.add(this.boardPanel, "PLAY");
        
        this.setupPanel = new SetupPanel(fen -> {
            try {
                model.setupFromFen(fen);
                boardCardLayout.show(boardContainer, "PLAY");
                boardPanel.requestFocusInWindow();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid FEN");
            }
        }, () -> {
            boardCardLayout.show(boardContainer, "PLAY");
            boardPanel.requestFocusInWindow();
        });
        
        this.boardContainer.add(this.setupPanel, "SETUP");

        this.pgnPanel = new PgnPanel(model);
        this.commentPanel = new CommentPanel(model);
        this.pdfPanel = new PdfPanel();
        
        String lastPdfPath = prefs.get("last_pdf_path", null);
        if (lastPdfPath != null && new File(lastPdfPath).exists()) {
            pdfPanel.loadPdf(lastPdfPath, prefs.getInt("last_pdf_page", 0), prefs.getFloat("last_pdf_scale", 1.0f));
        }
        
        this.dbSwitcherPanel = new DbSwitcherPanel(
            info -> {
                try {
                    if (!promptUnsavedChanges()) return;
                    currentDbPath = info.path;
                    dbService.connect(currentDbPath);
                    dbPanel.refreshTable(dbService.getAllGamesMetadata());
                    model.newGame(); 
                    addKnownDatabase(currentDbPath);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Failed to load database: " + ex.getMessage());
                }
            },
            info -> {
                removeKnownDatabase(info.path);
                if (info.path.equals(currentDbPath)) {
                    closeCurrentDatabase();
                }
            }
        );

this.control = new CControl(this);
        
        
        add(control.getContentArea(), BorderLayout.CENTER);

        boardDockable = new DefaultSingleCDockable("board", "Chess Board", boardContainer);
        boardDockable.setCloseable(false);
        pgnDockable = new DefaultSingleCDockable("pgn", "Game Moves", pgnPanel);
        cmtDockable = new DefaultSingleCDockable("comments", "Comment (Ctrl+E)", commentPanel);
        dbDockable = new DefaultSingleCDockable("database", "Current Database", dbPanel);
        pdfDockable = new DefaultSingleCDockable("pdf", "Study Material", pdfPanel);
        switcherDockable = new DefaultSingleCDockable("dbs", "Databases", dbSwitcherPanel);
        
        setupMenus(); 
        
        CGrid grid = new CGrid(control);
        grid.add(0, 0, 15, 100, switcherDockable);
        grid.add(15, 0, 55, 100, boardDockable); 
        grid.add(70, 0, 30, 50, pgnDockable);   
        grid.add(70, 50, 30, 20, cmtDockable);  
        grid.add(70, 70, 30, 30, dbDockable);
        grid.add(100, 0, 40, 100, pdfDockable);
        control.getContentArea().deploy(grid);

        // --- INIT DEFAULT DB AND LAYOUT ---
        ensureDefaultDatabase();

        if (layoutFile.exists()) {
            try { control.readXML(layoutFile); } catch (Exception ignored) {}
        } else {
            // Default first-time startup layout
            pgnDockable.setVisible(false);
            cmtDockable.setVisible(false);
            dbDockable.setVisible(false);
            pdfDockable.setVisible(false);
            switcherDockable.setVisible(false);
            boardDockable.setVisible(true);
        }
        
        String dockTheme = prefs.get("dock_theme", "Flat");
        switch (dockTheme) {
            case "Eclipse":
                this.control.setTheme(ThemeMap.KEY_ECLIPSE_THEME);
                break;
            case "Smooth":
                this.control.setTheme(ThemeMap.KEY_SMOOTH_THEME);
                break;
            case "Basic":
                this.control.setTheme(ThemeMap.KEY_BASIC_THEME);
                break;
            case "Flat":
            default:
                this.control.setTheme(ThemeMap.KEY_FLAT_THEME);
                
                Color flatBg = new Color(230, 230, 230);
                Color flatFg = Color.DARK_GRAY;
                Color paleBlueActive = new Color(200, 225, 245);
                ColorManager cm = control.getController().getColors();
                for (String key : new String[]{"title.active.left", "title.active.right", "title.active.center", "title.focus.left", "title.focus.right", "title.focus.center"}) cm.put(Priority.CLIENT, key, paleBlueActive);
                for (String key : new String[]{"title.inactive.left", "title.inactive.right", "title.inactive.center"}) cm.put(Priority.CLIENT, key, flatBg);
                cm.put(Priority.CLIENT, "title.active.text", flatFg);
                cm.put(Priority.CLIENT, "title.inactive.text", flatFg);
                cm.put(Priority.CLIENT, "title.focus.text", flatFg);
                break;
        }

        setupGlobalShortcuts();
        setupShutdownHook();
        
        String lastDbPath = prefs.get("last_db_path", null);
        int lastDbId = prefs.getInt("last_db_id", -1);
        String lastPgn = prefs.get("last_pgn", null);
        int lastNode = prefs.getInt("last_node", 0);
        
        boolean loaded = false;
        
        if (lastDbPath != null && new File(lastDbPath).exists()) {
            try {
                currentDbPath = lastDbPath;
                dbService.connect(currentDbPath);
                dbPanel.refreshTable(dbService.getAllGamesMetadata());
                if (lastDbId != -1) {
                    String pgn = dbService.getGamePgn(lastDbId);
                    if (pgn != null) {
                        model.loadPgnString(pgn, lastDbId);
                        loaded = true;
                    }
                }
            } catch (Exception ignored) {}
        }
        
        if (!loaded && lastPgn != null && new File(lastPgn).exists()) {
            try { model.loadPgn(lastPgn); loaded = true; } catch (Exception ignored) {}
        }
        
        if (loaded) {
            try { model.getGame().gotoNode(lastNode); model.notifyListeners(); } catch (Exception ignored) {}
        } else {
            model.newGame(); 
        }
        
        if (currentDbPath != null) addKnownDatabase(currentDbPath);
        refreshDbSwitcher();
        
        SwingUtilities.invokeLater(boardPanel::requestFocusInWindow);
    }
    
    private void ensureDefaultDatabase() {
        String known = prefs.get("known_dbs", "");
        if (known.isEmpty()) {
            try {
                File appDir = new File(System.getProperty("user.home"), ".prochess");
                if (!appDir.exists()) appDir.mkdirs();
                
                File defaultDb = new File(appDir, "default.db");
                currentDbPath = defaultDb.getAbsolutePath();
                
                dbService.connect(currentDbPath);
                addKnownDatabase(currentDbPath);
                dbPanel.refreshTable(dbService.getAllGamesMetadata());
            } catch (Exception ex) {
                System.err.println("Could not create default database: " + ex.getMessage());
            }
        }
    }
    
    private void closeCurrentDatabase() {
        if (!promptUnsavedChanges()) return;
        currentDbPath = null;
        dbPanel.refreshTable(new ArrayList<>());
        model.newGame();
        updateTitle();
    }

    private void setupDatabaseContextMenu() {
        JTable table = dbPanel.getTable();
        table.addMouseListener(new MouseAdapter() {
            private void showPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int r = table.rowAtPoint(e.getPoint());
                    if (r >= 0 && r < table.getRowCount()) {
                        table.setRowSelectionInterval(r, r);
                        int dbId = (int) table.getModel().getValueAt(table.convertRowIndexToModel(r), 0);
                        createDbContextMenu(dbId).show(e.getComponent(), e.getX(), e.getY());
                    } else {
                        createDbContextMenu(-1).show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
            @Override public void mousePressed(MouseEvent e) { showPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { showPopup(e); }
        });
    }

    private JPopupMenu createDbContextMenu(int dbId) {
        JPopupMenu popup = new JPopupMenu();
        
        JMenuItem info = new JMenuItem("Set Game Information");
info.addActionListener(e -> { 
            // Only open the dialog if the game successfully loaded
            if (loadGameFromDb(dbId)) {
            } else { 
                new GameInfoDialog(this, model, this::saveGameToDb).setVisible(true);
            }
        });
        info.setEnabled(dbId != -1);
        
        JMenuItem export = new JMenuItem("Export to PGN...");
        export.addActionListener(e -> exportGame(dbId));
        export.setEnabled(dbId != -1);
        
        JMenuItem cut = new JMenuItem("Cut");
        cut.addActionListener(e -> { copyGame(dbId); deleteGame(dbId); });
        cut.setEnabled(dbId != -1);
        
        JMenuItem copy = new JMenuItem("Copy");
        copy.addActionListener(e -> copyGame(dbId));
        copy.setEnabled(dbId != -1);
        
        JMenuItem paste = new JMenuItem("Paste");
        paste.addActionListener(e -> pasteGame());
        
        JMenuItem delete = new JMenuItem("Delete");
        delete.addActionListener(e -> deleteGame(dbId));
        delete.setEnabled(dbId != -1);
        
        popup.add(info); popup.addSeparator();
        popup.add(export); popup.addSeparator();
        popup.add(cut); popup.add(copy); popup.add(paste); popup.addSeparator();
        popup.add(delete);
        
        return popup;
    }

    private void copyGame(int dbId) {
        try {
            String pgn = (dbId == -1) ? model.getGameAsPgnString() : dbService.getGamePgn(dbId);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(pgn), null);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to copy game.");
        }
    }

    private void pasteGame() {
        if (currentDbPath == null) {
            JOptionPane.showMessageDialog(this, "Connect to a database first to paste.");
            return;
        }
        try {
            String pgn = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (pgn != null && !pgn.trim().isEmpty()) {
                GameModel temp = new GameModel();
                temp.loadPgnString(pgn, -1);
                String w = temp.getGame().getTag("White"); w = w != null ? w : "?";
                String b = temp.getGame().getTag("Black"); b = b != null ? b : "?";
                String e = temp.getGame().getTag("Event"); e = e != null ? e : "?";
                String d = temp.getGame().getTag("Date"); d = d != null ? d : "?";
                String r = temp.getGame().getTag("Result"); r = r != null ? r : "*";
                
                int newId = dbService.saveGame(-1, w, b, e, d, r, temp.getGameAsPgnString());
                dbPanel.refreshTable(dbService.getAllGamesMetadata());
                refreshDbSwitcher();
                loadGameFromDb(newId);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Clipboard does not contain a valid PGN game.");
        }
    }

    private void deleteGame(int dbId) {
        if (dbId == -1) return;
        if (currentDbPath == null) return;
        
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete this game entirely?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                dbService.deleteGame(dbId);
                dbPanel.refreshTable(dbService.getAllGamesMetadata());
                refreshDbSwitcher();
                if (model.getCurrentDbId() == dbId) {
                    model.newGame(); 
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to delete game: " + ex.getMessage());
            }
        }
    }

    private void exportGame(int dbId) {
        try {
            String pgn = (dbId == -1) ? model.getGameAsPgnString() : dbService.getGamePgn(dbId);
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("exported_game.pgn"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                String path = chooser.getSelectedFile().getAbsolutePath();
                if (!path.toLowerCase().endsWith(".pgn")) path += ".pgn";
                try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
                    pw.write(pgn);
                }
                JOptionPane.showMessageDialog(this, "Exported successfully.");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to export.");
        }
    }

    private void importMultiPgn() {
        if (currentDbPath == null) {
            JOptionPane.showMessageDialog(this, "Connect to a database first to import multiple games.");
            return;
        }
        
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PGN File", "pgn"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File pgnFile = chooser.getSelectedFile();
            
            JDialog progressDialog = new JDialog(this, "Importing...", true);
            JProgressBar progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            progressDialog.add(BorderLayout.CENTER, progressBar);
            progressDialog.add(BorderLayout.NORTH, new JLabel(" Importing games to database..."));
            progressDialog.setSize(300, 75);
            progressDialog.setLocationRelativeTo(this);
            
            SwingWorker<Integer, Void> worker = new SwingWorker<Integer, Void>() {
                @Override protected Integer doInBackground() throws Exception {
                    int count = 0;
                    try (FileInputStream fis = new FileInputStream(pgnFile)) {
                        PGNReader reader = new PGNReader(fis, "import");
                        Game parsedGame;
                        while ((parsedGame = reader.parseGame()) != null) {
                            String w = parsedGame.getTag("White"); w = w != null ? w : "?";
                            String b = parsedGame.getTag("Black"); b = b != null ? b : "?";
                            String e = parsedGame.getTag("Event"); e = e != null ? e : "?";
                            String d = parsedGame.getTag("Date"); d = d != null ? d : "?";
                            String r = parsedGame.getTag("Result"); r = r != null ? r : "*";
                            
                            StringWriter sw = new StringWriter();
                            try (PrintWriter pw = new PrintWriter(sw)) {
                                new PGNWriter(pw).write(parsedGame.getModel());
                            }
                            dbService.saveGame(-1, w, b, e, d, r, sw.toString());
                            count++;
                        }
                    }
                    return count;
                }
                @Override protected void done() {
                    progressDialog.dispose();
                    try {
                        int count = get();
                        dbPanel.refreshTable(dbService.getAllGamesMetadata());
                        refreshDbSwitcher();
                        JOptionPane.showMessageDialog(MainFrame.this, "Successfully imported " + count + " games.");
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(MainFrame.this, "Failed to import games.");
                    }
                }
            };
            worker.execute();
            progressDialog.setVisible(true);
        }
    }
    
    private void exportMultiPgn() {
        if (currentDbPath == null) {
            JOptionPane.showMessageDialog(this, "Connect to a database first to export games.");
            return;
        }
        
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("database_export.pgn"));
        chooser.setFileFilter(new FileNameExtensionFilter("PGN File", "pgn"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            if (!path.toLowerCase().endsWith(".pgn")) path += ".pgn";
            
            JDialog progressDialog = new JDialog(this, "Exporting...", true);
            JProgressBar progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            progressDialog.add(BorderLayout.CENTER, progressBar);
            progressDialog.add(BorderLayout.NORTH, new JLabel(" Exporting database to PGN file..."));
            progressDialog.setSize(300, 75);
            progressDialog.setLocationRelativeTo(this);
            
            final String finalPath = path;
            SwingWorker<Integer, Void> worker = new SwingWorker<Integer, Void>() {
                @Override protected Integer doInBackground() throws Exception {
                    List<Object[]> metadata = dbService.getAllGamesMetadata();
                    try (PrintWriter pw = new PrintWriter(new FileWriter(finalPath))) {
                        for (Object[] row : metadata) {
                            int dbId = (int) row[0];
                            String pgn = dbService.getGamePgn(dbId);
                            if (pgn != null) {
                                pw.println(pgn);
                                pw.println();
                                pw.println();
                            }
                        }
                    }
                    return metadata.size();
                }
                @Override protected void done() {
                    progressDialog.dispose();
                    try {
                        int count = get();
                        JOptionPane.showMessageDialog(MainFrame.this, "Successfully exported " + count + " games.");
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(MainFrame.this, "Failed to export games.");
                    }
                }
            };
            worker.execute();
            progressDialog.setVisible(true);
        }
    }

    private void addKnownDatabase(String path) {
        String known = prefs.get("known_dbs", "");
        Set<String> dbs = new LinkedHashSet<>();
        if (!known.isEmpty()) for (String s : known.split(";")) dbs.add(s);
        dbs.add(path);
        prefs.put("known_dbs", String.join(";", dbs));
        refreshDbSwitcher();
    }
    
    private void removeKnownDatabase(String path) {
        String known = prefs.get("known_dbs", "");
        Set<String> dbs = new LinkedHashSet<>();
        if (!known.isEmpty()) {
            for (String s : known.split(";")) {
                if (!s.equals(path)) dbs.add(s);
            }
        }
        prefs.put("known_dbs", String.join(";", dbs));
        refreshDbSwitcher();
    }
    
    private void refreshDbSwitcher() {
        String known = prefs.get("known_dbs", "");
        java.util.List<DbSwitcherPanel.DbInfo> dbInfos = new ArrayList<>();
        for (String path : known.split(";")) {
            if (!path.trim().isEmpty() && new File(path).exists()) {
                File f = new File(path);
                dbInfos.add(new DbSwitcherPanel.DbInfo(f.getName().replace(".db", ""), path, dbService.getGameCount(path)));
            }
        }
        dbSwitcherPanel.setDatabases(dbInfos);
    }
    
    private void handleTableEdit(int dbId, String column, String newValue) {
        try {
            String pgn = dbService.getGamePgn(dbId);
            if (pgn != null) {
                GameModel temp = new GameModel();
                temp.loadPgnString(pgn, dbId);
                temp.getGame().setTag(column, newValue);
                
                String w = temp.getGame().getTag("White"); w = w != null ? w : "";
                String b = temp.getGame().getTag("Black"); b = b != null ? b : "";
                String e = temp.getGame().getTag("Event"); e = e != null ? e : "";
                String d = temp.getGame().getTag("Date");  d = d != null ? d : "";
                String r = temp.getGame().getTag("Result"); r = r != null ? r : "";
                
                dbService.saveGame(dbId, w, b, e, d, r, temp.getGameAsPgnString());
                
                if (model.getCurrentDbId() == dbId) {
                    model.getGame().setTag(column, newValue);
                    updateTitle();
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to update database record: " + ex.getMessage());
        }
    }

    private void setupShutdownHook() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                prefs.putInt("window_w", getWidth());
                prefs.putInt("window_h", getHeight());
                prefs.putInt("window_x", getX());
                prefs.putInt("window_y", getY());
                
                if (model.getCurrentFilePath() != null) prefs.put("last_pgn", model.getCurrentFilePath());
                else prefs.remove("last_pgn");
                
                if (currentDbPath != null) {
                    prefs.put("last_db_path", currentDbPath);
                    prefs.putInt("last_db_id", model.getCurrentDbId());
                } else {
                    prefs.remove("last_db_path");
                    prefs.remove("last_db_id");
                }
                
                if (pdfPanel.getCurrentPdfPath() != null) {
                    prefs.put("last_pdf_path", pdfPanel.getCurrentPdfPath());
                    prefs.putInt("last_pdf_page", pdfPanel.getCurrentPage());
                    prefs.putFloat("last_pdf_scale", pdfPanel.getScale());
                } else {
                    prefs.remove("last_pdf_path");
                }
                
                prefs.putInt("last_node", model.getGame().getCurNode());
                try { control.writeXML(layoutFile); } catch (Exception ignored) {}
                System.exit(0);
            }
        });
    }

    private void setupGlobalShortcuts() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                boolean isEditableText = (focusOwner instanceof JTextComponent) && ((JTextComponent) focusOwner).isEditable();

                if (e.getKeyCode() == KeyEvent.VK_E && e.isControlDown()) {
                    if (commentPanel.isTextAreaFocused()) boardPanel.requestFocusInWindow();
                    else commentPanel.focusTextArea();
                    return true; 
                }
                if (e.getKeyCode() == KeyEvent.VK_F && e.isControlDown()) {
                    dbPanel.toggleSearch();
                    return true;
                }
                
                boolean isTable = focusOwner instanceof JTable;
                if (!e.isControlDown() && !e.isAltDown() && !e.isMetaDown() && !isEditableText && !isTable) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_J: model.forwardMove(); return true;
                        case KeyEvent.VK_K: model.backwardMove(); return true;
                        case KeyEvent.VK_L: model.nextVariation(); return true;
                        case KeyEvent.VK_H: model.prevVariation(); return true;
                        
                        case KeyEvent.VK_DOWN: model.forwardMove(); return true;
                        case KeyEvent.VK_UP: model.backwardMove(); return true;
                        case KeyEvent.VK_RIGHT: model.nextVariation(); return true;
                        case KeyEvent.VK_LEFT: model.prevVariation(); return true;
                        
                        case KeyEvent.VK_N: dbPanel.selectNextRow(); return true;
                        case KeyEvent.VK_P: dbPanel.selectPrevRow(); return true;
                    }
                }
            }
            return false;
        });
    }
    
    private boolean promptUnsavedChanges() {
        if (!model.isDirty()) return true;
        int choice = JOptionPane.showConfirmDialog(this, "Save changes to the current game?", "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION);
        
        if (choice == JOptionPane.YES_OPTION) {
            if (model.getCurrentDbId() != -1 && currentDbPath != null) {
                saveGameToDb();
                return true;
            } else if (model.getCurrentFilePath() != null) {
                try { model.savePgn(model.getCurrentFilePath()); return true; } catch (Exception e) { return false; }
            } else {
                saveAs(); 
                return true;
            }
        }
        return choice == JOptionPane.NO_OPTION; 
    }

private boolean loadGameFromDb(int id) {
        if (model.getCurrentDbId() == id) return true; 
        if (!promptUnsavedChanges()) { dbPanel.clearSelection(); return false; }
        
        try {
            String pgn = dbService.getGamePgn(id);
            if (pgn != null) {
                model.loadPgnString(pgn, id);
                return true;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to load game from DB.");
        }
        return false;
    }

    private void saveGameToDb() {
        if (currentDbPath == null) return;
        try {
            String w = model.getGame().getTag("White"); w = w != null ? w : "";
            String b = model.getGame().getTag("Black"); b = b != null ? b : "";
            String e = model.getGame().getTag("Event"); e = e != null ? e : "";
            String d = model.getGame().getTag("Date");  d = d != null ? d : "";
            String r = model.getGame().getTag("Result"); r = r != null ? r : "";
            String pgn = model.getGameAsPgnString(); pgn = pgn != null ? pgn : "";
            
            int newId = dbService.saveGame(model.getCurrentDbId(), w, b, e, d, r, pgn);
            model.setCurrentDbId(newId);
            
            model.setClean();
            refreshDbSwitcher(); 
            dbPanel.refreshTable(dbService.getAllGamesMetadata()); 
            updateTitle();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save to DB: " + ex.getMessage());
        }
    }

    // --- MENU OVERHAUL ---

    private void setupMenus() {
        JMenuBar menuBar = new JMenuBar();
        
        // 1. FILE MENU
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        
        JMenuItem newGame = new JMenuItem("New Game");
        newGame.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK));
        newGame.addActionListener(e -> { if (promptUnsavedChanges()) model.newGame(); });
        
        JMenuItem openGame = new JMenuItem("Open Game...");
        openGame.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        openGame.addActionListener(e -> {
            if (!promptUnsavedChanges()) return;
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("PGN", "pgn"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try { model.loadPgn(chooser.getSelectedFile().getAbsolutePath()); }
                catch (Exception ex) { JOptionPane.showMessageDialog(this, "Load failed."); }
            }
        });
        
        JMenuItem saveDb = new JMenuItem("Save Game to Current Database");
        saveDb.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        saveDb.addActionListener(e -> {
            if (currentDbPath != null) saveGameToDb();
            else JOptionPane.showMessageDialog(this, "No database connected.");
        });

        JMenuItem newDb = new JMenuItem("New Database...");
        newDb.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK));
        newDb.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("SQLite Database", "db"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    String path = chooser.getSelectedFile().getAbsolutePath();
                    if (!path.endsWith(".db")) path += ".db";
                    dbService.connect(path);
                    currentDbPath = path;
                    addKnownDatabase(currentDbPath);
                    dbPanel.refreshTable(dbService.getAllGamesMetadata());
                } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Failed to create DB."); }
            }
        });

        JMenuItem openDb = new JMenuItem("Open Database...");
        openDb.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        openDb.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("SQLite Database", "db"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    currentDbPath = chooser.getSelectedFile().getAbsolutePath();
                    dbService.connect(currentDbPath);
                    addKnownDatabase(currentDbPath);
                    dbPanel.refreshTable(dbService.getAllGamesMetadata());
                } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Failed to open DB."); }
            }
        });
        
        JMenuItem closeDb = new JMenuItem("Close Database");
        closeDb.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));
        closeDb.addActionListener(e -> closeCurrentDatabase());
        
        JMenuItem importPgn = new JMenuItem("Import PGN File...");
        importPgn.addActionListener(e -> {
            if (currentDbPath == null) {
                JOptionPane.showMessageDialog(this, "Connect to a database first to import a game.");
                return;
            }
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("PGN", "pgn"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    GameModel temp = new GameModel();
                    temp.loadPgn(chooser.getSelectedFile().getAbsolutePath());
                    String w = temp.getGame().getTag("White"); w = w != null ? w : "?";
                    String b = temp.getGame().getTag("Black"); b = b != null ? b : "?";
                    String ev = temp.getGame().getTag("Event"); ev = ev != null ? ev : "?";
                    String d = temp.getGame().getTag("Date"); d = d != null ? d : "?";
                    String r = temp.getGame().getTag("Result"); r = r != null ? r : "*";
                    
                    dbService.saveGame(-1, w, b, ev, d, r, temp.getGameAsPgnString());
                    dbPanel.refreshTable(dbService.getAllGamesMetadata());
                    refreshDbSwitcher();
                    JOptionPane.showMessageDialog(this, "Game imported successfully.");
                } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Import failed."); }
            }
        });
        
        JMenuItem exportPgn = new JMenuItem("Export Game as PGN File...");
        exportPgn.addActionListener(e -> exportGame(model.getCurrentDbId()));

        JMenuItem importMulti = new JMenuItem("Import Multi-PGN File...");
        importMulti.addActionListener(e -> importMultiPgn());

        JMenuItem exportMulti = new JMenuItem("Export Database as PGN File...");
        exportMulti.addActionListener(e -> exportMultiPgn());
        
        JMenuItem openPdf = new JMenuItem("Open PDF File...");
        openPdf.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("PDF Documents", "pdf"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                pdfPanel.loadPdf(chooser.getSelectedFile().getAbsolutePath(), 0, 1.0f);
                pdfDockable.setVisible(true);
            }
        });
        
        JMenuItem settings = new JMenuItem("Settings...");
        settings.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK));
        settings.addActionListener(e -> new SettingsDialog(this, () -> pgnPanel.forceRefresh()).setVisible(true));
        
        JMenuItem exit = new JMenuItem("Exit");
        exit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        exit.addActionListener(e -> {
            if (promptUnsavedChanges()) dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
        });

        fileMenu.add(newGame); fileMenu.add(openGame); fileMenu.add(saveDb); fileMenu.addSeparator(); 
        fileMenu.add(newDb); fileMenu.add(openDb); fileMenu.add(closeDb); fileMenu.addSeparator();
        fileMenu.add(importPgn); fileMenu.add(exportPgn); fileMenu.addSeparator();
        fileMenu.add(importMulti); fileMenu.add(exportMulti); fileMenu.addSeparator();
        fileMenu.add(openPdf); fileMenu.addSeparator();
        fileMenu.add(settings); fileMenu.addSeparator();
        fileMenu.add(exit);
        
        // 2. EDIT MENU
        JMenu editMenu = new JMenu("Edit");
        editMenu.setMnemonic(KeyEvent.VK_E);
        
        JMenuItem setupPosition = new JMenuItem("Position Setup");
        setupPosition.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK));
        setupPosition.addActionListener(e -> {
            boardCardLayout.show(boardContainer, "SETUP");
            setupPanel.startSetup();
        });
        
        JMenuItem copyGameItem = new JMenuItem("Copy Game");
        copyGameItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        copyGameItem.addActionListener(e -> copyGame(model.getCurrentDbId()));

        JMenuItem cutGameItem = new JMenuItem("Cut Game");
        cutGameItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        cutGameItem.addActionListener(e -> { copyGame(model.getCurrentDbId()); deleteGame(model.getCurrentDbId()); });
        
        JMenuItem pasteGameItem = new JMenuItem("Paste Game");
        pasteGameItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        pasteGameItem.addActionListener(e -> pasteGame());
        
        JMenuItem undoItem = new JMenuItem("Undo");
        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        undoItem.addActionListener(e -> model.backwardMove()); // Stubbed to move navigation for now
        
        JMenuItem redoItem = new JMenuItem("Redo");
        redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK));
        redoItem.addActionListener(e -> model.forwardMove()); // Stubbed to move navigation for now
        
        JMenuItem flipBoard = new JMenuItem("Flip Board");
        flipBoard.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
        flipBoard.addActionListener(e -> boardPanel.flipBoard());
        
        editMenu.add(setupPosition); editMenu.addSeparator();
        editMenu.add(copyGameItem); editMenu.add(cutGameItem); editMenu.add(pasteGameItem); editMenu.addSeparator();
        editMenu.add(undoItem); editMenu.add(redoItem); editMenu.addSeparator();
        editMenu.add(flipBoard);
        
        // 3. VIEW MENU
        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic(KeyEvent.VK_V);
        
        viewMenu.add(createToggleItem("Show Board", boardDockable));
        viewMenu.add(createToggleItem("Show Databases Switcher", switcherDockable));
        viewMenu.add(createToggleItem("Show Current Database", dbDockable));
        viewMenu.add(createToggleItem("Show Game Moves", pgnDockable));
        viewMenu.add(createToggleItem("Show Comment Editor", cmtDockable));
        viewMenu.add(createToggleItem("Show PDF Viewer", pdfDockable));
        
        // 4. DATABASE MENU (Placeholder for next batch)
        JMenu databaseMenu = new JMenu("Database");
        databaseMenu.setMnemonic(KeyEvent.VK_D);
        
        // 5. GAME MENU (Placeholder for next batch)
        JMenu gameMenu = new JMenu("Game");
        gameMenu.setMnemonic(KeyEvent.VK_G);
        
        // 6. HELP MENU (Placeholder for next batch)
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);
        
        menuBar.add(fileMenu); 
        menuBar.add(editMenu);
        menuBar.add(viewMenu);
        menuBar.add(databaseMenu);
        menuBar.add(gameMenu);
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);
    }
    
private JCheckBoxMenuItem createToggleItem(String name, DefaultSingleCDockable dockable) {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(name, dockable.isVisible());
        item.addActionListener(e -> dockable.setVisible(item.isSelected()));
        
        // Correct DockingFrames listener
        dockable.addCDockableStateListener(new bibliothek.gui.dock.common.event.CDockableAdapter() {
            @Override
            public void visibilityChanged(bibliothek.gui.dock.common.intern.CDockable d) {
                item.setSelected(d.isVisible());
            }
        });
        return item;
    }

    private void saveAs() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(model.getCurrentFilePath() != null ? model.getCurrentFilePath() : "game.pgn"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            if (!path.toLowerCase().endsWith(".pgn")) path += ".pgn";
            try { model.setCurrentFilePath(path); model.savePgn(path); JOptionPane.showMessageDialog(this, "Saved!"); } 
            catch (Exception ex) { JOptionPane.showMessageDialog(this, "Save failed."); }
        }
    }

    private void updateTitle() {
        String title;
        if (model.getCurrentDbId() == -1 && model.getCurrentFilePath() == null) {
            title = "New Game (Unsaved)";
        } else {
            String w = model.getGame().getTag("White");
            String b = model.getGame().getTag("Black");
            w = (w == null || w.equals("?")) ? "Unknown" : w;
            b = (b == null || b.equals("?")) ? "Unknown" : b;
            title = w + " vs. " + b;
            String event = model.getGame().getTag("Event");
            if (event != null && !event.equals("?")) title += " - " + event;
        }
        if (model.isDirty()) title += " *";
        boardDockable.setTitleText(title);
        setTitle("Pro Chess - " + title);
    }
    
    private JPopupMenu createBoardAnnotationMenu() {
        JPopupMenu popup = new JPopupMenu();

        JMenuItem n1 = new JMenuItem("! (Good move)"); n1.addActionListener(e -> model.setNAG((short) 1));
        JMenuItem n2 = new JMenuItem("? (Bad move)"); n2.addActionListener(e -> model.setNAG((short) 2));
        JMenuItem n3 = new JMenuItem("!! (Brilliant move)"); n3.addActionListener(e -> model.setNAG((short) 3));
        JMenuItem n4 = new JMenuItem("?? (Blunder)"); n4.addActionListener(e -> model.setNAG((short) 4));
        JMenuItem n5 = new JMenuItem("!? (Interesting)"); n5.addActionListener(e -> model.setNAG((short) 5));
        JMenuItem n6 = new JMenuItem("?! (Dubious)"); n6.addActionListener(e -> model.setNAG((short) 6));
        
        JMenuItem n14 = new JMenuItem("\u2A72 (White slight advantage)"); n14.addActionListener(e -> model.setNAG((short) 14));
        JMenuItem n15 = new JMenuItem("\u2A71 (Black slight advantage)"); n15.addActionListener(e -> model.setNAG((short) 15));
        JMenuItem n16 = new JMenuItem("\u00B1 (White advantage)"); n16.addActionListener(e -> model.setNAG((short) 16));
        JMenuItem n17 = new JMenuItem("\u2213 (Black advantage)"); n17.addActionListener(e -> model.setNAG((short) 17));
        JMenuItem n18 = new JMenuItem("+- (White winning)"); n18.addActionListener(e -> model.setNAG((short) 18));
        JMenuItem n19 = new JMenuItem("-+ (Black winning)"); n19.addActionListener(e -> model.setNAG((short) 19));
        
        JMenuItem n13 = new JMenuItem("\u221E (Unclear)"); n13.addActionListener(e -> model.setNAG((short) 13));
        JMenuItem n142 = new JMenuItem("\u2313 (Better is)"); n142.addActionListener(e -> model.setNAG((short) 142));
        JMenuItem n8 = new JMenuItem("\u25A1 (Only move)"); n8.addActionListener(e -> model.setNAG((short) 8));
        JMenuItem n140 = new JMenuItem("\u0394 (With idea)"); n140.addActionListener(e -> model.setNAG((short) 140));
        JMenuItem n143 = new JMenuItem("\u2207 (Countering)"); n143.addActionListener(e -> model.setNAG((short) 143));
        JMenuItem n146 = new JMenuItem("N (Novelty)"); n146.addActionListener(e -> model.setNAG((short) 146));

        JMenuItem n36 = new JMenuItem("\u2191 (Initiative)"); n36.addActionListener(e -> model.setNAG((short) 36));
        JMenuItem n40 = new JMenuItem("\u2192 (Attack)"); n40.addActionListener(e -> model.setNAG((short) 40));
        JMenuItem n132 = new JMenuItem("\u21C4 (Counterplay)"); n132.addActionListener(e -> model.setNAG((short) 132));
        JMenuItem n32 = new JMenuItem("\u21BB (Development)"); n32.addActionListener(e -> model.setNAG((short) 32));
        JMenuItem n138 = new JMenuItem("\u25CB (Space)"); n138.addActionListener(e -> model.setNAG((short) 138));
        JMenuItem n136 = new JMenuItem("\u2295 (Time trouble)"); n136.addActionListener(e -> model.setNAG((short) 136));
        JMenuItem n22 = new JMenuItem("\u2299 (Zugzwang)"); n22.addActionListener(e -> model.setNAG((short) 22));

        popup.add(n1); popup.add(n2); popup.add(n3); popup.add(n4); popup.add(n5); popup.add(n6);
        popup.addSeparator();
        popup.add(n14); popup.add(n15); popup.add(n16); popup.add(n17); popup.add(n18); popup.add(n19);
        popup.addSeparator();
        popup.add(n13); popup.add(n142); popup.add(n8); popup.add(n140); popup.add(n143); popup.add(n146);
        popup.addSeparator();
        popup.add(n36); popup.add(n40); popup.add(n132); popup.add(n32); popup.add(n138); popup.add(n136); popup.add(n22);
        
        popup.addSeparator();
        JMenuItem clearNag = new JMenuItem("Clear Annotations");
        clearNag.addActionListener(e -> model.clearNAGs());
        popup.add(clearNag);

        return popup;
    }
}