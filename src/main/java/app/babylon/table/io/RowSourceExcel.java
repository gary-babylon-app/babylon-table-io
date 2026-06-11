package app.babylon.table.io;

import app.babylon.io.DataResource;
import app.babylon.lang.ArgumentCheck;

final class RowSourceExcel implements RowSource
{
    private final DataResource streamSource;
    private final ReadOptionsExcel options;

    RowSourceExcel(DataResource streamSource, ReadOptionsExcel options)
    {
        this.streamSource = ArgumentCheck.nonNull(streamSource);
        this.options = ArgumentCheck.nonNull(options);
    }

    @Override
    public String getName()
    {
        return this.streamSource.getName();
    }

    @Override
    public RowCursor openRows()
    {
        return this.options.createCursor(this.streamSource.openStream());
    }
}
