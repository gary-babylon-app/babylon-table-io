package app.babylon.table.io;

import java.io.BufferedInputStream;
import java.io.InputStream;

import app.babylon.lang.ArgumentCheck;
import app.babylon.table.column.ColumnName;

final class RowCursorExcel extends RowCursorLineReaderCommon
{
    private final ReadOptionsExcel options;

    RowCursorExcel(InputStream inputStream, ReadOptionsExcel options)
    {
        super(createLineReader(ArgumentCheck.nonNull(inputStream), ArgumentCheck.nonNull(options).specificSheetName()));
        this.options = options;
    }

    public ColumnName getSpecificSheetName()
    {
        return this.options.specificSheetName();
    }

    private static LineReader createLineReader(InputStream inputStream, ColumnName specificSheetName)
    {
        return new LineReaderFastExcel(toBufferedStream(inputStream), specificSheetName);
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
