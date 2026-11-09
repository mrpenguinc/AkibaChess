package com.mychess;

import com.mychess.view.MainFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import java.awt.Color;
import java.util.prefs.Preferences;

public class ChessApp {
    public static void main(String[] args) {
        Preferences prefs = Preferences.userNodeForPackage(MainFrame.class);
        String theme = prefs.get("app_theme", "Nimbus");

        try {
            if ("OS Default".equals(theme)) {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } else if ("Metal".equals(theme)) {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } else if ("Motif".equals(theme)) {
                UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel");
            } else if ("FlatLaf Light".equals(theme)) {
                UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
            } else if ("FlatLaf Dark".equals(theme)) {
                UIManager.setLookAndFeel("com.formdev.flatlaf.FlatDarkLaf");
            } else {
                // Fallback / Default to our customized Nimbus
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                        
                        // Override Nimbus's default black/dark-gray active internal frame colors
                        // with a subtle, modern pale blue.
                        ColorUIResource paleBlue = new ColorUIResource(new Color(200, 225, 245));
                        ColorUIResource darkText = new ColorUIResource(Color.DARK_GRAY);
                        
                        UIManager.put("activeCaption", paleBlue);
                        UIManager.put("InternalFrame.activeTitleBackground", paleBlue);
                        UIManager.put("activeCaptionText", darkText);
                        UIManager.put("InternalFrame.activeTitleForeground", darkText);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Theme LAF not available, falling back to default.");
        }

        SwingUtilities.invokeLater(() -> {
            MainFrame app = new MainFrame();
            app.setVisible(true);
        });
    }
}