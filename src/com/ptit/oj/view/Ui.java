package com.ptit.oj.view;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.List;

/** Cac ham tien ich dung chung cho cac lop giao dien: thong bao, tieu de, bang, nhan do kho. */
public class Ui {

    /** Phong chu deu, dung cho ma nguon va du lieu test. */
    public static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 13);
    /** Phong chu thuong cho doan van dai nhu noi dung de bai. */
    public static final Font TEXT = new Font(Font.SANS_SERIF, Font.PLAIN, 14);

    /** Hien hop thoai thong bao binh thuong. */
    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Thông báo", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Hien hop thoai bao loi. */
    public static void error(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }

    /** Hien thong bao cua mot loi nghiep vu (sai mat khau, trung ma bai, khong du quyen...). */
    public static void error(Component parent, Exception e) {
        String message = e.getMessage();
        if (message == null || message.isEmpty()) {
            message = "Có lỗi xảy ra: " + e;
        }
        error(parent, message);
    }

    /** Tao nhan tieu de chu dam. */
    public static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 16f));
        return label;
    }

    /** Tao bang chi doc voi cac cot cho truoc: khong sua duoc o nao, moi lan chon mot dong. */
    public static JTable table(String[] columns) {
        JTable table = new JTable(new DefaultTableModel(columns, 0));
        table.setDefaultEditor(Object.class, null);         // khong cho sua truc tiep tren bang
        table.setRowHeight(24);
        table.setGridColor(new Color(0xD9D9D9));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        return table;
    }

    /** Xoa het cac dong cu cua bang roi them cac dong moi. */
    public static void setRows(JTable table, List<Object[]> rows) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.setRowCount(0);
        for (Object[] row : rows) {
            model.addRow(row);
        }
    }

    /** Doi do kho tu so sang chu: 1 = De, 2 = Vua, 3 = Kho; gia tri khac la chua danh gia. */
    public static String difficulty(int difficulty) {
        if (difficulty == 1) return "Dễ";
        if (difficulty == 2) return "Vừa";
        if (difficulty == 3) return "Khó";
        return "—";
    }
}
