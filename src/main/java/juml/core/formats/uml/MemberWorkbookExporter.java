// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * メンバー解析結果 ({@link MemberAnalysis}) を {@code .xlsx} ワークブックとして書き出す。
 *
 * <p>「Members」シートに全列を 1 メンバー=1 行で並べ、見出し行を固定 + オートフィルタを付ける。
 * 数値列は数値セルとして書くので Excel 上で並べ替え・集計できる (空セルは空白)。テキスト列の空セルは
 * {@code -} で埋める。別途「凡例」シートに各列の説明を出力する。</p>
 */
public final class MemberWorkbookExporter {

    private MemberWorkbookExporter() {
    }

    /** クラス一覧を .xlsx として {@code os} に書き出す (ストリームはクローズしない)。 */
    public static void write(List<JavaClassInfo> classes, OutputStream os) throws IOException {
        List<String> headers = MemberAnalysis.headers();
        List<String[]> rows = MemberAnalysis.rows(classes);
        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle headerStyle = boldStyle(wb);
            writeMembersSheet(wb, headerStyle, headers, rows);
            writeLegendSheet(wb, headerStyle);
            wb.write(os);
        }
    }

    private static void writeMembersSheet(Workbook wb, CellStyle headerStyle,
                                          List<String> headers, List<String[]> rows) {
        Sheet sheet = wb.createSheet("Members");
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
        }
        int rowIndex = 1;
        for (String[] cells : rows) {
            Row row = sheet.createRow(rowIndex++);
            for (int i = 0; i < cells.length; i++) {
                writeCell(row.createCell(i), cells[i], MemberAnalysis.isNumeric(i));
            }
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, rows.size(), 0, headers.size() - 1));
        for (int i = 0; i < headers.size(); i++) {
            sheet.setColumnWidth(i, 16 * 256);
        }
    }

    private static void writeCell(Cell cell, String value, boolean numeric) {
        String v = value == null ? "" : value;
        if (numeric) {
            if (v.isEmpty()) {
                return;
            }
            try {
                cell.setCellValue(Double.parseDouble(v));
            } catch (NumberFormatException e) {
                cell.setCellValue(v);
            }
        } else {
            cell.setCellValue(v.isEmpty() ? "-" : v);
        }
    }

    private static final String[] LEGEND_HEADERS = {"列", "対象", "例", "説明"};
    private static final int[] LEGEND_WIDTHS = {18, 16, 32, 70};

    private static void writeLegendSheet(Workbook wb, CellStyle headerStyle) {
        Sheet sheet = wb.createSheet("凡例");
        Row header = sheet.createRow(0);
        for (int i = 0; i < LEGEND_HEADERS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(LEGEND_HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }
        int rowIndex = 1;
        for (String[] entry : MemberAnalysis.legend()) {
            Row row = sheet.createRow(rowIndex++);
            for (int i = 0; i < entry.length; i++) {
                row.createCell(i).setCellValue(entry[i]);
            }
        }
        sheet.createFreezePane(0, 1);
        for (int i = 0; i < LEGEND_WIDTHS.length; i++) {
            sheet.setColumnWidth(i, LEGEND_WIDTHS[i] * 256);
        }
    }

    private static CellStyle boldStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }
}
