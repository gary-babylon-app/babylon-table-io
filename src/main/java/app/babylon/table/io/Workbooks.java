package app.babylon.table.io;

import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Locale;

import org.dhatim.fastexcel.BorderSide;
import org.dhatim.fastexcel.BorderStyle;
import org.dhatim.fastexcel.Color;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

import app.babylon.table.TableColumnar;
import app.babylon.table.column.Column;
import app.babylon.table.column.ColumnByte;
import app.babylon.table.column.ColumnDouble;
import app.babylon.table.column.ColumnInt;
import app.babylon.table.column.ColumnLong;
import app.babylon.table.column.ColumnObject;
import app.babylon.table.column.Columns;
import app.babylon.text.Strings;

public class Workbooks
{
    private static final String EXCEL_DATE_FORMAT = "yyyy-mm-dd";
    private static final BorderStyle TABLE_BORDER_STYLE = BorderStyle.MEDIUM;
    private static final String HEADER_FILL_COLOR = Color.GRAY2;
    private static final int DEFAULT_START_ROW_IDX = 1;
    private static final int DEFAULT_START_COL_IDX = 1;
    private static final String FOOTER_TEXT = "Powered by Babylon Financial Technology.";

    public static void tableToWorkBook(TableColumnar table, Workbook workbook)
    {
        tableToWorkBook(table, workbook, ExcelContext.defaultContext());
    }

    public static void tableToWorkBook(TableColumnar table, Workbook workbook, ExcelContext context)
    {
        tableToWorkBook(table, workbook, context, DEFAULT_START_ROW_IDX, DEFAULT_START_COL_IDX);
    }

    public static void tableToWorkBook(TableColumnar table, Workbook workbook, ExcelContext context, int startRowIdx,
            int startColIdx)
    {
        tableToWorkBook(table, workbook, context, startRowIdx, startColIdx, true);
    }

    public static void tableToWorkBook(TableColumnar table, Workbook workbook, ExcelContext context, int startRowIdx,
            int startColIdx, boolean includeFooter)
    {
        Worksheet sheet = workbook.newWorksheet(sanitizeSheetName(table.getName().getOriginal()));
        sheet.hideGridLines();
        Locale userLocale = resolveLocale(context);

        int firstColIdx = startColIdx;
        int headerRowIdx = startRowIdx;
        int dataRowStartIdx = startRowIdx + 1;
        int lastColIdx = table.getColumnCount() - 1;
        int lastColIdxAbs = firstColIdx + lastColIdx;
        String description = table.getDescription() == null ? null : table.getDescription().getValue();
        if (lastColIdx >= 0 && description != null && !description.isBlank())
        {
            int descriptionRowIdx = startRowIdx;
            sheet.value(descriptionRowIdx, firstColIdx, description);
            sheet.range(descriptionRowIdx, firstColIdx, descriptionRowIdx, lastColIdxAbs).style().merge()
                    .horizontalAlignment("center").verticalAlignment("center").wrapText(true)
                    .fillColor(HEADER_FILL_COLOR).borderStyle(TABLE_BORDER_STYLE).set();

            headerRowIdx = startRowIdx + 1;
            dataRowStartIdx = startRowIdx + 2;
        }

        int j = 0;
        for (Column column : table.columns())
        {
            sheet.value(headerRowIdx, firstColIdx + j, column.getName().getValue());
            ++j;
        }
        if (lastColIdx >= 0)
        {
            sheet.range(headerRowIdx, firstColIdx, headerRowIdx, lastColIdxAbs).style().bold().italic()
                    .fillColor(HEADER_FILL_COLOR).borderStyle(TABLE_BORDER_STYLE).set();
        }

        j = 0;
        for (Column column : table.columns())
        {
            if (column instanceof ColumnObject<?> co
                    && java.time.LocalDate.class.equals(column.getType().getValueClass()))
            {
                @SuppressWarnings("unchecked")
                ColumnObject<java.time.LocalDate> cld = (ColumnObject<java.time.LocalDate>) co;
                for (int i = 0; i < column.size(); ++i)
                {
                    if (column.isSet(i))
                    {
                        LocalDate d = cld.get(i);
                        sheet.value(dataRowStartIdx + i, firstColIdx + j, d);
                    }
                }
                int firstDataRow = dataRowStartIdx;
                int lastDataRow = dataRowStartIdx + column.size() - 1;
                if (lastDataRow >= firstDataRow)
                {
                    sheet.range(firstDataRow, firstColIdx + j, lastDataRow, firstColIdx + j).style()
                            .format(EXCEL_DATE_FORMAT).set();
                }
            }
            else if (column instanceof ColumnObject<?> co && BigDecimal.class.equals(column.getType().getValueClass()))
            {
                @SuppressWarnings("unchecked")
                ColumnObject<BigDecimal> cd = (ColumnObject<BigDecimal>) co;
                String decimalFormat = decimalFormat(userLocale, Columns.decimalPlaces(cd));
                for (int i = 0; i < column.size(); ++i)
                {
                    if (column.isSet(i))
                    {
                        BigDecimal bd = cd.get(i);
                        if (bd != null)
                        {
                            sheet.value(dataRowStartIdx + i, firstColIdx + j, bd);
                        }
                    }
                }
                int firstDataRow = dataRowStartIdx;
                int lastDataRow = dataRowStartIdx + column.size() - 1;
                if (lastDataRow >= firstDataRow)
                {
                    sheet.range(firstDataRow, firstColIdx + j, lastDataRow, firstColIdx + j).style()
                            .format(decimalFormat).set();
                }
            }
            else if (column instanceof ColumnByte cb)
            {
                for (int i = 0; i < column.size(); ++i)
                {
                    if (column.isSet(i))
                    {
                        sheet.value(dataRowStartIdx + i, firstColIdx + j, (int) cb.get(i));
                    }
                }
            }
            else if (column instanceof ColumnInt ci)
            {
                for (int i = 0; i < column.size(); ++i)
                {
                    if (column.isSet(i))
                    {
                        sheet.value(dataRowStartIdx + i, firstColIdx + j, ci.get(i));
                    }
                }
            }
            else if (column instanceof ColumnLong cl)
            {
                for (int i = 0; i < column.size(); ++i)
                {
                    if (column.isSet(i))
                    {
                        sheet.value(dataRowStartIdx + i, firstColIdx + j, cl.get(i));
                    }
                }
            }
            else if (column instanceof ColumnDouble cdb)
            {
                for (int i = 0; i < column.size(); ++i)
                {
                    if (column.isSet(i))
                    {
                        sheet.value(dataRowStartIdx + i, firstColIdx + j, cdb.get(i));
                    }
                }
            }
            else
            {
                for (int i = 0; i < column.size(); ++i)
                {
                    if (column.isSet(i))
                    {
                        sheet.value(dataRowStartIdx + i, firstColIdx + j, column.toString(i));
                    }
                }
            }
            ++j;
        }

        int tableLastRow = Math.max(headerRowIdx, dataRowStartIdx + table.getRowCount() - 1);
        if (lastColIdx >= 0)
        {
            applyPerimeterBorder(sheet, headerRowIdx, firstColIdx, tableLastRow, lastColIdxAbs);
            applyPerimeterBorder(sheet, headerRowIdx, firstColIdx, headerRowIdx, lastColIdxAbs);
        }

        if (includeFooter && lastColIdx >= 0)
        {
            int footerRowIdx = tableLastRow + 2;
            sheet.value(footerRowIdx, firstColIdx, FOOTER_TEXT);
            sheet.range(footerRowIdx, firstColIdx, footerRowIdx, lastColIdxAbs).style().merge().italic()
                    .horizontalAlignment("left").verticalAlignment("center").wrapText(true).set();
        }
    }

    public static void applyWorkbookProperties(Workbook workbook, TableColumnar table, ExcelContext context)
    {
        if (workbook == null)
        {
            return;
        }

        if (table != null)
        {
            String title = table.getName() == null ? null : table.getName().getOriginal();
            if (!isBlank(title))
            {
                workbook.properties().setTitle(title);
            }

            String subject = table.getDescription() == null ? null : table.getDescription().getValue();
            if (!isBlank(subject))
            {
                workbook.properties().setSubject(subject);
            }
        }

        String companyName = context == null ? null : context.companyName();
        if (!Strings.isEmpty(companyName))
        {
            workbook.properties().setCompany(companyName);
        }

        workbook.properties().setDescription("Powered by Babylon Financial Technology");
    }

    public static String resolveWorkbookAuthor(ExcelContext context)
    {
        String userName = context == null ? null : context.userName();
        if (!Strings.isEmpty(userName))
        {
            return userName;
        }
        return "Babylon";
    }

    public static void tableToWorkBook(String sheetName, Collection<TableColumnar> tables, Workbook workbook)
    {
        tableToWorkBook(sheetName, tables, workbook, ExcelContext.defaultContext());
    }

    public static void tableToWorkBook(String sheetName, Collection<TableColumnar> tables, Workbook workbook,
            ExcelContext context)
    {
        Worksheet sheet = workbook.newWorksheet(sanitizeSheetName(sheetName));
        sheet.hideGridLines();
        Locale userLocale = resolveLocale(context);

        int rowIdx = 0;
        for (TableColumnar table : tables)
        {
            int j = 0;
            for (Column column : table.columns())
            {
                sheet.value(rowIdx, j, column.getName().getValue());
                ++j;
            }
            ++rowIdx;

            j = 0;
            for (Column column : table.columns())
            {
                if (column instanceof ColumnObject<?> co
                        && java.time.LocalDate.class.equals(column.getType().getValueClass()))
                {
                    @SuppressWarnings("unchecked")
                    ColumnObject<java.time.LocalDate> cld = (ColumnObject<java.time.LocalDate>) co;
                    for (int i = 0; i < column.size(); ++i)
                    {
                        if (column.isSet(i))
                        {
                            java.time.LocalDate d = cld.get(i);
                            LocalDate d2 = LocalDate.from(d);
                            sheet.value(rowIdx + i, j, d2);
                        }
                    }
                    int firstDataRow = rowIdx;
                    int lastDataRow = rowIdx + column.size() - 1;
                    if (lastDataRow >= firstDataRow)
                    {
                        sheet.range(firstDataRow, j, lastDataRow, j).style().format(EXCEL_DATE_FORMAT).set();
                    }
                }
                else if (column instanceof ColumnObject<?> co
                        && BigDecimal.class.equals(column.getType().getValueClass()))
                {
                    @SuppressWarnings("unchecked")
                    ColumnObject<BigDecimal> cd = (ColumnObject<BigDecimal>) co;
                    String decimalFormat = decimalFormat(userLocale, Columns.decimalPlaces(cd));
                    for (int i = 0; i < column.size(); ++i)
                    {
                        if (column.isSet(i))
                        {
                            BigDecimal bd = cd.get(i);
                            if (bd != null)
                            {
                                sheet.value(rowIdx + i, j, bd);
                            }
                        }
                    }
                    int firstDataRow = rowIdx;
                    int lastDataRow = rowIdx + column.size() - 1;
                    if (lastDataRow >= firstDataRow)
                    {
                        sheet.range(firstDataRow, j, lastDataRow, j).style().format(decimalFormat).set();
                    }
                }
                else if (column instanceof ColumnByte cb)
                {
                    for (int i = 0; i < column.size(); ++i)
                    {
                        if (column.isSet(i))
                        {
                            sheet.value(rowIdx + i, j, (int) cb.get(i));
                        }
                    }
                }
                else if (column instanceof ColumnInt ci)
                {
                    for (int i = 0; i < column.size(); ++i)
                    {
                        if (column.isSet(i))
                        {
                            sheet.value(rowIdx + i, j, ci.get(i));
                        }
                    }
                }
                else if (column instanceof ColumnLong cl)
                {
                    for (int i = 0; i < column.size(); ++i)
                    {
                        if (column.isSet(i))
                        {
                            sheet.value(rowIdx + i, j, cl.get(i));
                        }
                    }
                }
                else if (column instanceof ColumnDouble cdb)
                {
                    for (int i = 0; i < column.size(); ++i)
                    {
                        if (column.isSet(i))
                        {
                            sheet.value(rowIdx + i, j, cdb.get(i));
                        }
                    }
                }
                else
                {
                    for (int i = 0; i < column.size(); ++i)
                    {
                        if (column.isSet(i))
                        {
                            sheet.value(rowIdx + i, j, column.toString(i));
                        }
                    }
                }
                ++j;
            }
            rowIdx += table.getRowCount();
            ++rowIdx;
        }
    }

    private static String decimalFormat(Locale locale, int decimalPlaces)
    {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(locale);
        String format = "#" + symbols.getGroupingSeparator() + "##0";
        if (decimalPlaces <= 0)
        {
            return format;
        }

        return format + symbols.getDecimalSeparator() + "0".repeat(decimalPlaces);
    }

    private static Locale resolveLocale(ExcelContext context)
    {
        if (context == null || context.userLocale() == null)
        {
            return ExcelContext.DEFAULT_LOCALE;
        }
        return context.userLocale();
    }

    private static String sanitizeSheetName(String input)
    {
        String name = input == null ? "" : input;
        name = name.replaceAll("[\\\\/:*?\\[\\]]", "_").strip();
        if (name.isEmpty())
        {
            name = "Sheet1";
        }
        if (name.length() > 31)
        {
            name = name.substring(0, 31);
        }
        return name;
    }

    private static boolean isBlank(String s)
    {
        return s == null || s.isBlank();
    }

    private static void applyPerimeterBorder(Worksheet sheet, int topRow, int leftCol, int bottomRow, int rightCol)
    {
        for (int c = leftCol; c <= rightCol; ++c)
        {
            applyBorderForCell(sheet, topRow, c, true, topRow == bottomRow, c == leftCol, c == rightCol);
        }

        if (bottomRow > topRow)
        {
            for (int c = leftCol; c <= rightCol; ++c)
            {
                applyBorderForCell(sheet, bottomRow, c, false, true, c == leftCol, c == rightCol);
            }
        }

        for (int r = topRow + 1; r < bottomRow; ++r)
        {
            applyBorderForCell(sheet, r, leftCol, false, false, true, leftCol == rightCol);
            if (rightCol > leftCol)
            {
                applyBorderForCell(sheet, r, rightCol, false, false, false, true);
            }
        }
    }

    private static void applyBorderForCell(Worksheet sheet, int row, int col, boolean top, boolean bottom, boolean left,
            boolean right)
    {
        var style = sheet.range(row, col, row, col).style();
        if (top)
        {
            style.borderStyle(BorderSide.TOP, TABLE_BORDER_STYLE);
        }
        if (bottom)
        {
            style.borderStyle(BorderSide.BOTTOM, TABLE_BORDER_STYLE);
        }
        if (left)
        {
            style.borderStyle(BorderSide.LEFT, TABLE_BORDER_STYLE);
        }
        if (right)
        {
            style.borderStyle(BorderSide.RIGHT, TABLE_BORDER_STYLE);
        }
        style.set();
    }
}
