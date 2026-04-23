package app.babylon.table.io;

import java.util.Map;

import app.babylon.io.StreamSource;
import app.babylon.lang.ArgumentCheck;
import app.babylon.table.column.Column;
import app.babylon.table.column.ColumnName;

public final class RowSourceExcel implements RowSource
{
    private final StreamSource streamSource;
    private final RowCursorExcel.Builder rowCursorBuilder;

    private RowSourceExcel(Builder builder)
    {
        this.streamSource = ArgumentCheck.nonNull(builder.streamSource);
        this.rowCursorBuilder = builder.rowCursorBuilder.copy();
    }

    public static Builder builder()
    {
        return new Builder();
    }

    @Override
    public String getName()
    {
        return this.streamSource.getName();
    }

    @Override
    public RowCursor openRows()
    {
        return this.rowCursorBuilder.build(this.streamSource.openStream());
    }

    public static final class Builder
    {
        private StreamSource streamSource;
        private final RowCursorExcel.Builder rowCursorBuilder;

        private Builder()
        {
            this.streamSource = null;
            this.rowCursorBuilder = RowCursorExcel.builder();
        }

        public Builder withStreamSource(StreamSource streamSource)
        {
            this.streamSource = ArgumentCheck.nonNull(streamSource);
            return this;
        }

        public Builder withHeaderStrategy(HeaderStrategy headerStrategy)
        {
            this.rowCursorBuilder.withHeaderStrategy(headerStrategy);
            return this;
        }

        public Builder withSelectedColumn(ColumnName columnName)
        {
            this.rowCursorBuilder.withSelectedColumn(columnName);
            return this;
        }

        public Builder withSelectedColumns(ColumnName... columnNames)
        {
            this.rowCursorBuilder.withSelectedColumns(columnNames);
            return this;
        }

        public Builder withColumnRename(ColumnName original, ColumnName newName)
        {
            this.rowCursorBuilder.withColumnRename(original, newName);
            return this;
        }

        public Builder withColumnRenames(Map<ColumnName, ColumnName> renames)
        {
            this.rowCursorBuilder.withColumnRenames(renames);
            return this;
        }

        public Builder withRowFilter(RowFilter rowFilter)
        {
            this.rowCursorBuilder.withRowFilter(rowFilter);
            return this;
        }

        public Builder withStripping(boolean stripping)
        {
            this.rowCursorBuilder.withStripping(stripping);
            return this;
        }

        public Builder withSpecificSheetName(ColumnName specificSheetName)
        {
            this.rowCursorBuilder.withSpecificSheetName(specificSheetName);
            return this;
        }

        public Builder withColumnType(ColumnName columnName, Column.Type columnType)
        {
            this.rowCursorBuilder.withColumnType(columnName, columnType);
            return this;
        }

        public Builder withColumnTypes(Map<ColumnName, Column.Type> columnTypes)
        {
            this.rowCursorBuilder.withColumnTypes(columnTypes);
            return this;
        }

        public RowSourceExcel build()
        {
            return new RowSourceExcel(this);
        }
    }
}
