/*
 * Copyright 2026 Babylon Financial Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package app.babylon.table.io;

import java.io.InputStream;

import app.babylon.io.DataResource;
import app.babylon.lang.ArgumentCheck;
import app.babylon.table.column.ColumnName;

public record ReadOptionsExcel(ColumnName specificSheetName)
{
    public static ReadOptionsExcel standard()
    {
        return builder().build();
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public RowCursor createCursor(InputStream inputStream)
    {
        return new RowCursorExcel(ArgumentCheck.nonNull(inputStream), this);
    }

    public RowSource createSource(DataResource streamSource)
    {
        return new RowSourceExcel(ArgumentCheck.nonNull(streamSource), this);
    }

    public static final class Builder
    {
        private ColumnName specificSheetName;

        private Builder()
        {
            this.specificSheetName = null;
        }

        public Builder withSpecificSheetName(ColumnName specificSheetName)
        {
            this.specificSheetName = specificSheetName;
            return this;
        }

        public ReadOptionsExcel build()
        {
            return new ReadOptionsExcel(this.specificSheetName);
        }
    }
}
