package app.babylon.table.io;

import java.io.BufferedInputStream;
import java.io.InputStream;

import app.babylon.lang.ArgumentCheck;
import app.babylon.table.column.ColumnName;

public final class RowCursorExcel extends RowCursorLineReaderCommon
{
    private static final int EMPTY_ROW_LIMIT = 3;

    private final ColumnName specificSheetName;
    private Row currentRow;
    private int emptyRowCount;

    RowCursorExcel(InputStream inputStream, Builder builder)
    {
        super(createLineReader(ArgumentCheck.nonNull(inputStream), builder), builder);
        this.specificSheetName = builder.specificSheetName;
        this.currentRow = null;
        this.emptyRowCount = 0;
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public HeaderStrategy getHeaderStrategy()
    {
        return super.getHeaderStrategy();
    }

    public boolean isStripping()
    {
        return super.isStripping();
    }

    public ColumnName getSpecificSheetName()
    {
        return this.specificSheetName;
    }

    @Override
    public boolean next()
    {
        while (super.next())
        {
            Row row = super.current();
            if (row.isEmpty())
            {
                ++this.emptyRowCount;
                if (this.emptyRowCount >= EMPTY_ROW_LIMIT)
                {
                    this.currentRow = null;
                    return false;
                }
                continue;
            }
            this.emptyRowCount = 0;
            this.currentRow = row;
            return true;
        }

        this.currentRow = null;
        return false;
    }

    @Override
    public Row current()
    {
        return ArgumentCheck.nonNull(this.currentRow, "current row is not available until next() succeeds");
    }

    private static LineReader createLineReader(InputStream inputStream, Builder builder)
    {
        return new LineReaderFastExcel(toBufferedStream(inputStream), builder.specificSheetName);
    }

    private static BufferedInputStream toBufferedStream(InputStream inputStream)
    {
        if (inputStream instanceof BufferedInputStream bufferedInputStream)
        {
            return bufferedInputStream;
        }
        return new BufferedInputStream(inputStream);
    }

    public static final class Builder extends RowCursorLineReaderCommon.BuilderBase<Builder>
    {
        private ColumnName specificSheetName;

        private Builder()
        {
            super();
            this.headerStrategy = new HeaderStrategyExcelAuto();
            this.specificSheetName = null;
        }

        public Builder withSpecificSheetName(ColumnName specificSheetName)
        {
            this.specificSheetName = specificSheetName;
            return this;
        }

        Builder copy()
        {
            Builder copy = new Builder();
            copyCommonTo(copy);
            copy.specificSheetName = this.specificSheetName;
            return copy;
        }

        @Override
        protected Builder self()
        {
            return this;
        }

        public RowCursorExcel build(InputStream inputStream)
        {
            return new RowCursorExcel(inputStream, this);
        }
    }
}
