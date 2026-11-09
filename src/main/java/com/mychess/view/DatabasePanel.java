package com.mychess.view;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class DatabasePanel extends JPanel {
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final TableRowSorter<DefaultTableModel> sorter;
    
    private final JPanel searchPanel;
    private final JTextField searchField;
    
    public interface GameEditListener {
        void onGameEdited(int dbId, String column, String newValue);
    }

    public DatabasePanel(Consumer<Integer> onGameSelected, GameEditListener onGameEdited) {
        setLayout(new BorderLayout());

        // Expanded headers to accommodate the custom tags
        String[] columns = {"ID", "White", "Black", "Result", "Event", "Date", "Book", "Puzzle"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return column > 0; }
        };

        tableModel.addTableModelListener(e -> {
            if (e.getType() == TableModelEvent.UPDATE) {
                int row = e.getFirstRow();
                int col = e.getColumn();
                if (row >= 0 && col > 0) {
                    int dbId = (int) tableModel.getValueAt(row, 0);
                    String colName = tableModel.getColumnName(col);
                    Object cellValue = tableModel.getValueAt(row, col);
                    String newValue = (cellValue == null) ? "" : cellValue.toString();
                    onGameEdited.onGameEdited(dbId, colName, newValue);
                }
            }
        });

        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        
        // Hide the ID column but keep it in the model for reference
        table.getColumnModel().getColumn(0).setMinWidth(0);
        table.getColumnModel().getColumn(0).setMaxWidth(0);
        table.getColumnModel().getColumn(0).setWidth(0);
        
        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
        
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() != -1) {
                int modelRow = table.convertRowIndexToModel(table.getSelectedRow());
                int dbId = (int) tableModel.getValueAt(modelRow, 0);
                onGameSelected.accept(dbId);
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);
        
        searchPanel = new JPanel(new BorderLayout(5, 5));
        searchPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        searchPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        
        searchField = new JTextField();
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { updateFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { updateFilter(); }
        });
        
        searchField.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeSearch");
        searchField.getActionMap().put("closeSearch", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { hideSearch(); }
        });
        
        searchPanel.add(searchField, BorderLayout.CENTER);
        
        JButton closeBtn = new JButton("X");
        closeBtn.setMargin(new Insets(2, 4, 2, 4));
        closeBtn.setFocusable(false);
        closeBtn.addActionListener(e -> hideSearch());
        searchPanel.add(closeBtn, BorderLayout.EAST);
        
        searchPanel.setVisible(false);
        add(searchPanel, BorderLayout.NORTH);
    }
    
    public JTable getTable() {
        return table;
    }

    private void updateFilter() {
        String text = searchField.getText();
        if (text.trim().isEmpty()) sorter.setRowFilter(null);
        else sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
    }

    public void toggleSearch() {
        if (searchPanel.isVisible()) hideSearch();
        else {
            searchPanel.setVisible(true);
            searchField.requestFocusInWindow();
            searchField.selectAll();
            revalidate();
            repaint();
        }
    }
    
    public void hideSearch() {
        searchPanel.setVisible(false);
        searchField.setText("");
        revalidate();
        repaint();
    }

    public void refreshTable(List<Object[]> games) {
        tableModel.setRowCount(0);
        for (Object[] gameData : games) tableModel.addRow(gameData);
    }
    
    public void clearSelection() { table.clearSelection(); }

    public void selectNextRow() {
        if (table.getRowCount() == 0) return;
        int row = table.getSelectedRow();
        if (row < table.getRowCount() - 1) {
            table.setRowSelectionInterval(row + 1, row + 1);
            table.scrollRectToVisible(table.getCellRect(row + 1, 0, true));
        }
    }

    public void selectPrevRow() {
        if (table.getRowCount() == 0) return;
        int row = table.getSelectedRow();
        if (row > 0) {
            table.setRowSelectionInterval(row - 1, row - 1);
            table.scrollRectToVisible(table.getCellRect(row - 1, 0, true));
        } else if (row == -1) {
            table.setRowSelectionInterval(0, 0);
            table.scrollRectToVisible(table.getCellRect(0, 0, true));
        }
    }
}