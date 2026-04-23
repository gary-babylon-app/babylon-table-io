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


public class LineReaderFastExcel implements LineReader
{
    private final ColumnName specificSheetName;
    private final ReadableWorkbook workbook;
    private final Stream<Row> rowStream;
    private final Iterator<Row> rowIterator;
    private final RowBuffer current;

    public LineReaderFastExcel(BufferedInputStream instream, ColumnName specificSheetName)
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
            this.current = new RowBuffer();
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error reading XLSX file: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean next() throws IOException
    {
        RowBuffer row = this.current;
        row.clear();
        if (!this.rowIterator.hasNext())
        {
            return false;
        }
        Row sourceRow = this.rowIterator.next();

        if (sourceRow == null)
        {
            return true;
        }

        for (int col = 0; col < sourceRow.getCellCount(); col++)
        {
            appendCell(row, getCellValue(sourceRow.getCell(col)));
        }
        return true;
    }

    @Override
    public app.babylon.table.io.Row current()
    {
        return this.current;
    }

    private static String getCellValue(Cell cell)
    {
        return cell == null ? "" : cell.getRawValue();
    }

    private static void appendCell(RowBuffer row, String value)
    {
        if (value != null)
        {
            for (int i = 0; i < value.length(); ++i)
            {
                row.append(value.charAt(i));
            }
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

        try
        {
            this.workbook.close();
        }
        catch (IOException e)
        {
            if (closeError == null)
            {
                closeError = e;
            }
            else
            {
                closeError.addSuppressed(e);
            }
        }

        if (closeError != null)
        {
            throw closeError;
        }
    }
}
