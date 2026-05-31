package app.babylon.table.io;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;

import app.babylon.table.column.ColumnName;

public class ExcelTableRowReader implements LineReader
{
    private final ColumnName specificSheetName;
    private final ReadableWorkbook workbook;
    private final Stream<Row> rowStream;
    private final Iterator<Row> rowIterator;
    private ByteStringSlices current;

    public ExcelTableRowReader(BufferedInputStream instream, ColumnName specificSheetName)
    {
        this.specificSheetName = specificSheetName;

        try
        {
            this.workbook = new ReadableWorkbook(instream);
            Sheet sheet = this.workbook.getFirstSheet();
            if (this.specificSheetName != null)
            {
                Map<ColumnName, Sheet> sheets = this.workbook.getSheets()
                        .collect(Collectors.toMap(s -> ColumnName.of(s.getName()), s -> s));
                sheet = sheets.get(this.specificSheetName);
                if (sheet == null)
                {
                    throw new IllegalArgumentException("No sheet found with name " + this.specificSheetName + ".");
                }
            }
            this.rowStream = sheet.openStream();
            this.rowIterator = this.rowStream.iterator();
            this.current = null;
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error reading XLSX file: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean next() throws IOException
    {
        if (!this.rowIterator.hasNext())
        {
            this.current = null;
            return false;
        }

        Row sourceRow = this.rowIterator.next();
        StringSlices.Builder row = new StringSlices.Builder();
        for (int col = 0; col < sourceRow.getCellCount(); col++)
        {
            appendCell(row, getCellValue(sourceRow.getOptionalCell(col).orElse(null)));
        }
        this.current = row.build().toByteStringSlices();
        return true;
    }

    @Override
    public ByteStringSlices current()
    {
        return this.current;
    }

    private static String getCellValue(Cell cell)
    {
        return cell == null ? "" : cell.getRawValue();
    }

    private static void appendCell(StringSlices.Builder row, String value)
    {
        if (value != null)
        {
            row.append(value);
        }
        row.finishField();
    }

    @Override
    public void close() throws IOException
    {
        IOException closeError = null;
        try
        {
            this.rowStream.close();
        }
        catch (RuntimeException e)
        {
            if (e.getCause() instanceof IOException io)
            {
                closeError = io;
            }
            else
            {
                throw e;
            }
        }

        this.workbook.close();
        if (closeError != null)
        {
            throw closeError;
        }
    }

}
