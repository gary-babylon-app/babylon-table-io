package app.babylon.table.io;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;

import app.babylon.lang.ArgumentCheck;
import app.babylon.table.column.ColumnName;

final class RowCursorExcel implements RowCursor
{
    private final ReadOptionsExcel options;
    private final ReadableWorkbook workbook;
    private final Stream<Row> rowStream;
    private final Iterator<Row> rowIterator;
    private StringArrayRow current;

    RowCursorExcel(InputStream inputStream, ReadOptionsExcel options)
    {
        this.options = ArgumentCheck.nonNull(options);
        try
        {
            this.workbook = new ReadableWorkbook(toBufferedStream(ArgumentCheck.nonNull(inputStream)));
            Sheet sheet = sheet(this.workbook, this.options.specificSheetName());
            this.rowStream = sheet.openStream();
            this.rowIterator = this.rowStream.iterator();
            this.current = null;
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error reading XLSX file: " + e.getMessage(), e);
        }
    }

    public ColumnName getSpecificSheetName()
    {
        return this.options.specificSheetName();
    }

    @Override
    public boolean next()
    {
        if (!this.rowIterator.hasNext())
        {
            this.current = null;
            return false;
        }

        Row sourceRow = this.rowIterator.next();
        String[] values = new String[sourceRow.getCellCount()];
        for (int col = 0; col < values.length; col++)
        {
            values[col] = getCellValue(sourceRow.getOptionalCell(col).orElse(null));
        }
        this.current = new StringArrayRow(values);
        return true;
    }

    @Override
    public RowValues current()
    {
        return ArgumentCheck.nonNull(this.current, "current row is not available until next() succeeds");
    }

    @Override
    public void close() throws Exception
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

    private static Sheet sheet(ReadableWorkbook workbook, ColumnName specificSheetName) throws IOException
    {
        if (specificSheetName == null)
        {
            return workbook.getFirstSheet();
        }

        Map<ColumnName, Sheet> sheets = workbook.getSheets()
                .collect(Collectors.toMap(s -> ColumnName.of(s.getName()), s -> s));
        Sheet sheet = sheets.get(specificSheetName);
        if (sheet == null)
        {
            throw new IllegalArgumentException("No sheet found with name " + specificSheetName + ".");
        }
        return sheet;
    }

    private static String getCellValue(Cell cell)
    {
        return cell == null ? "" : cell.getRawValue();
    }

    private static BufferedInputStream toBufferedStream(InputStream inputStream)
    {
        if (inputStream instanceof BufferedInputStream bufferedInputStream)
        {
            return bufferedInputStream;
        }
        return new BufferedInputStream(inputStream);
    }

}
