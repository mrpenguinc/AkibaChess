package com.mychess.view;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PdfPanel extends JPanel {
    private PDDocument document;
    private PDFRenderer pdfRenderer;
    private int currentPage = 0;
    private float scale = 1.0f;
    private String currentPdfPath = null;

    private final JLabel imageLabel;
    private final JTextField pageField;
    private final JLabel totalPagesLabel;
    private final JScrollPane scrollPane;

    // --- CACHING CAPABILITIES ---
    private static final int MAX_CACHE = 8;
    private final Map<String, BufferedImage> pageCache = new LinkedHashMap<String, BufferedImage>(MAX_CACHE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > MAX_CACHE;
        }
    };
    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor();

    public PdfPanel() {
        setLayout(new BorderLayout());

        imageLabel = new JLabel("", SwingConstants.CENTER);
        scrollPane = new JScrollPane(imageLabel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(0, 0));
        scrollPane.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 0));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        
        add(scrollPane, BorderLayout.CENTER);
        setupInteractiveControls();

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        JButton openBtn = new JButton("Open PDF");
        JButton prevBtn = new JButton("< Prev");
        JButton nextBtn = new JButton("Next >");
        JButton zoomOutBtn = new JButton("Zoom -");
        JButton zoomInBtn = new JButton("Zoom +");
        
        pageField = new JTextField(4);
        pageField.setHorizontalAlignment(JTextField.CENTER);
        pageField.setToolTipText("Type page number and press Enter");
        pageField.addActionListener(e -> jumpToPage());
        
        totalPagesLabel = new JLabel(" / 0 ");

        openBtn.addActionListener(e -> openPdfDialog());
        prevBtn.addActionListener(e -> { if (document != null && currentPage > 0) { currentPage--; renderPage(); }});
        nextBtn.addActionListener(e -> { if (document != null && currentPage < document.getNumberOfPages() - 1) { currentPage++; renderPage(); }});
        
        zoomOutBtn.addActionListener(e -> { 
            scale = Math.max(0.25f, scale - 0.25f); 
            pageCache.clear(); 
            renderPage(); 
        });
        zoomInBtn.addActionListener(e -> { 
            scale += 0.25f; 
            pageCache.clear(); 
            renderPage(); 
        });

        toolBar.add(openBtn); toolBar.addSeparator();
        toolBar.add(prevBtn); 
        toolBar.add(new JLabel(" Page: "));
        toolBar.add(pageField);
        toolBar.add(totalPagesLabel); 
        toolBar.add(nextBtn);
        toolBar.addSeparator();
        toolBar.add(zoomOutBtn); toolBar.add(zoomInBtn);

        add(toolBar, BorderLayout.NORTH);
    }

    public String getCurrentPdfPath() { return currentPdfPath; }
    public int getCurrentPage() { return currentPage; }
    public float getScale() { return scale; }

    public void loadPdf(String path, int page, float savedScale) {
        try {
            File file = new File(path);
            if (file.exists()) {
                if (document != null) document.close();
                pageCache.clear();
                document = PDDocument.load(file);
                pdfRenderer = new PDFRenderer(document);
                currentPdfPath = path;
                currentPage = Math.min(page, document.getNumberOfPages() - 1);
                scale = savedScale;
                renderPage();
            }
        } catch (Exception ex) {
            // Fails silently on startup if file was moved
        }
    }
    
    private void openPdfDialog() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PDF Documents", "pdf"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            loadPdf(chooser.getSelectedFile().getAbsolutePath(), 0, 1.0f);
        }
    }
    
    private void jumpToPage() {
        if (document == null) return;
        try {
            int targetPage = Integer.parseInt(pageField.getText().trim()) - 1;
            if (targetPage >= 0 && targetPage < document.getNumberOfPages()) {
                currentPage = targetPage;
                renderPage();
            } else {
                pageField.setText(String.valueOf(currentPage + 1));
            }
        } catch (NumberFormatException ex) {
            pageField.setText(String.valueOf(currentPage + 1));
        }
        this.requestFocusInWindow();
    }

    private void setupInteractiveControls() {
        MouseAdapter dragAdapter = new MouseAdapter() {
            private Point origin;
            @Override public void mousePressed(MouseEvent e) { origin = e.getPoint(); imageLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); }
            @Override public void mouseReleased(MouseEvent e) { imageLabel.setCursor(Cursor.getDefaultCursor()); }
            @Override public void mouseDragged(MouseEvent e) {
                if (origin != null) {
                    JViewport viewPort = scrollPane.getViewport();
                    if (viewPort != null) {
                        int deltaX = origin.x - e.getX(), deltaY = origin.y - e.getY();
                        Rectangle view = viewPort.getViewRect();
                        view.x += deltaX; view.y += deltaY;
                        imageLabel.scrollRectToVisible(view);
                    }
                }
            }
        };
        
        imageLabel.addMouseListener(dragAdapter);
        imageLabel.addMouseMotionListener(dragAdapter);

        scrollPane.addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                if (e.getWheelRotation() < 0) scale += 0.15f; 
                else scale = Math.max(0.25f, scale - 0.15f); 
                pageCache.clear();
                renderPage();
                e.consume(); 
            }
        });
    }

    private void renderPage() {
        if (document == null) return;
        
        String cacheKey = currentPage + "_" + scale;
        
        // Instant load if cached
        if (pageCache.containsKey(cacheKey)) {
            imageLabel.setIcon(new ImageIcon(pageCache.get(cacheKey)));
            updateUIState();
            preloadSurroundingPages();
        } else {
            // Render on background thread if missing
            SwingWorker<BufferedImage, Void> worker = new SwingWorker<BufferedImage, Void>() {
                @Override protected BufferedImage doInBackground() throws Exception { 
                    return pdfRenderer.renderImage(currentPage, scale); 
                }
                @Override protected void done() {
                    try {
                        BufferedImage img = get();
                        pageCache.put(cacheKey, img);
                        imageLabel.setIcon(new ImageIcon(img));
                        updateUIState();
                        preloadSurroundingPages();
                    } catch (Exception ex) { ex.printStackTrace(); }
                }
            };
            worker.execute();
        }
    }
    
    private void updateUIState() {
        pageField.setText(String.valueOf(currentPage + 1));
        totalPagesLabel.setText(" / " + document.getNumberOfPages() + " ");
        scrollPane.revalidate(); 
        scrollPane.repaint();
    }
    
    private void preloadSurroundingPages() {
        // Cache 3 pages ahead, and 1 page behind (prioritize forward momentum)
        int[] offsets = {1, 2, 3, -1}; 
        
        for (int offset : offsets) {
            final int p = currentPage + offset;
            if (p >= 0 && p < document.getNumberOfPages()) {
                final String key = p + "_" + scale;
                if (!pageCache.containsKey(key)) {
                    renderExecutor.submit(() -> {
                        try {
                            // Double-check to avoid race conditions filling the cache
                            if (!pageCache.containsKey(key)) {
                                BufferedImage img = pdfRenderer.renderImage(p, scale);
                                SwingUtilities.invokeLater(() -> pageCache.put(key, img));
                            }
                        } catch (Exception ignored) {}
                    });
                }
            }
        }
    }
}