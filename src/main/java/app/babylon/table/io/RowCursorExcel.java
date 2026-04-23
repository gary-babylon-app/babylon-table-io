package app.babylon.table.io;

import java.io.BufferedInputStream;
import java.io.InputStream;

import app.babylon.lang.ArgumentCheck;
import app.babylon.table.column.ColumnName;

public final class RowCursorExcel extends RowCursorLineReaderCommon
{
    private final HeaderStrategy headerStrategy;
    private final boolean stripping;
    private final ColumnName specificSheetName;

    RowCursorExcel(InputStream inputStream, Builder builder)
    {
        super(createLineReader(ArgumentCheck.nonNull(inputStream), builder), builder);
        this.headerStrategy = ArgumentCheck.nonNull(builder.headerStrategy);
        this.stripping = builder.stripping;
        this.specificSheetName = builder.specificSheetName;
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public HeaderStrategy getHeaderStrategy()
    {
        return this.headerStrategy;
    }

    public boolean isStripping()
    {
        return this.stripping;
    }

    public ColumnName getSpecificSheetName()
    {
        return this.specificSheetName;
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
