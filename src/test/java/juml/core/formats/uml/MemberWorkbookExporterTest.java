// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MemberWorkbookExporterTest {

    @Test
    public void write_producesMembersAndLegendSheetsWithNumericMetrics() throws Exception {
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(
                "package p; public class A {"
                + " public int f(int n) { if (n > 0) { return 1; } return 0; } }");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        MemberWorkbookExporter.write(classes, bos);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bos.toByteArray()))) {
            Sheet members = wb.getSheet("Members");
            assertNotNull("Members シートが無い", members);

            Sheet legend = wb.getSheet("凡例");
            assertNotNull("凡例 シートが無い", legend);
            Row legendHeader = legend.getRow(0);
            assertEquals("列", legendHeader.getCell(0).getStringCellValue());
            assertEquals("対象", legendHeader.getCell(1).getStringCellValue());
            assertEquals("例", legendHeader.getCell(2).getStringCellValue());
            assertEquals("説明", legendHeader.getCell(3).getStringCellValue());

            Row header = members.getRow(0);
            assertEquals("class", header.getCell(0).getStringCellValue());

            Row methodRow = findRow(members, "f");
            assertNotNull("method f の行が無い", methodRow);
            Cell branches = methodRow.getCell(MemberAnalysis.Col.BRANCHES.ordinal());
            assertEquals(CellType.NUMERIC, branches.getCellType());
            assertEquals(1.0, branches.getNumericCellValue(), 0.0);
        }
    }

    private static Row findRow(Sheet sheet, String memberName) {
        int nameCol = MemberAnalysis.Col.NAME.ordinal();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            Cell cell = row.getCell(nameCol);
            if (cell != null && memberName.equals(cell.getStringCellValue())) {
                return row;
            }
        }
        return null;
    }
}
