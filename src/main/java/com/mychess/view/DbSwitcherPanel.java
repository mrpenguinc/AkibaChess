package com.mychess.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

public class DbSwitcherPanel extends JPanel {
    
    public static class DbInfo {
        public String name;
        public String path;
        public int gameCount;

        public DbInfo(String name, String path, int gameCount) {
            this.name = name;
            this.path = path;
            this.gameCount = gameCount;
        }
    }

    private final DefaultListModel<DbInfo> listModel;
    private final JList<DbInfo> list;

    public DbSwitcherPanel(Consumer<DbInfo> onDbSelected, Consumer<DbInfo> onDbRemove) {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        listModel = new DefaultListModel<>();
        list = new JList<>(listModel);
        
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1); 
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(new Color(250, 250, 250));
        
        list.setCellRenderer(new TileRenderer());

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    DbInfo selected = list.getSelectedValue();
                    if (selected != null) onDbSelected.accept(selected);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) { handlePopup(e); }

            @Override
            public void mouseReleased(MouseEvent e) { handlePopup(e); }

            private void handlePopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int index = list.locationToIndex(e.getPoint());
                    if (index != -1) {
                        list.setSelectedIndex(index);
                        JPopupMenu popup = new JPopupMenu();
                        JMenuItem remove = new JMenuItem("Delete database (hide)");
                        remove.addActionListener(a -> onDbRemove.accept(list.getModel().getElementAt(index)));
                        popup.add(remove);
                        popup.show(list, e.getX(), e.getY());
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);
    }

    public void setDatabases(List<DbInfo> databases) {
        listModel.clear();
        for (DbInfo db : databases) listModel.addElement(db);
    }

    private static class TileRenderer extends JPanel implements ListCellRenderer<DbInfo> {
        private final JLabel nameLabel;
        private final JLabel countLabel;

        public TileRenderer() {
            setLayout(new BorderLayout(5, 0));
            setPreferredSize(new Dimension(160, 45));
            
            nameLabel = new JLabel();
            nameLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            nameLabel.setForeground(new Color(40, 40, 40));

            countLabel = new JLabel();
            countLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            countLabel.setForeground(Color.GRAY);

            JPanel textPanel = new JPanel(new GridLayout(2, 1));
            textPanel.setOpaque(false);
            textPanel.add(nameLabel);
            textPanel.add(countLabel);
            
            JLabel iconLabel = new JLabel(" \uD83D\uDCC1 "); 
            
            add(iconLabel, BorderLayout.WEST);
            add(textPanel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends DbInfo> list, DbInfo value, int index, boolean isSelected, boolean cellHasFocus) {
            nameLabel.setText(value.name);
            countLabel.setText(value.gameCount + " Games");

            if (isSelected) {
                setBackground(new Color(200, 225, 245));
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createEmptyBorder(2, 2, 2, 2),
                        BorderFactory.createLineBorder(new Color(150, 190, 220), 1, true)
                ));
            } else {
                setBackground(Color.WHITE);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createEmptyBorder(2, 2, 2, 2),
                        BorderFactory.createLineBorder(new Color(230, 230, 230), 1, true)
                ));
            }
            return this;
        }
    }
}