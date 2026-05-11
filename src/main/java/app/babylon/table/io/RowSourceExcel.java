package app.babylon.table.io;

import app.babylon.io.StreamSource;
import app.babylon.lang.ArgumentCheck;

final class RowSourceExcel implements RowSource
{
    private final StreamSource streamSource;
    private final ReadOptionsExcel options;

    RowSourceExcel(StreamSource streamSource, ReadOptionsExcel options)
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
