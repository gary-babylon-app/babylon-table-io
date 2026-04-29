/*
 * Copyright 2026 Babylon Financial Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package app.babylon.table.plans;

import app.babylon.lang.ArgumentCheck;
import app.babylon.table.TableColumnar;
import app.babylon.table.ToStringSettings;
import app.babylon.table.io.json.JSON;

public class TablePlanWriteJson
{
    private JSON.Format format;
    private ToStringSettings toStringSettings;

    public TablePlanWriteJson()
    {
        this.format = JSON.Format.ROW_ORIENTED;
        this.toStringSettings = ToStringSettings.standard();
    }

    public TablePlanWriteJson withFormat(JSON.Format format)
    {
        this.format = ArgumentCheck.nonNull(format);
        return this;
    }

    public JSON.Format getFormat()
    {
        return this.format;
    }

    public TablePlanWriteJson withToStringSettings(ToStringSettings toStringSettings)
    {
        this.toStringSettings = ArgumentCheck.nonNull(toStringSettings);
        return this;
    }

    public ToStringSettings getToStringSettings()
    {
        return this.toStringSettings;
    }

    public String execute(TableColumnar table)
    {
        TableColumnar checkedTable = ArgumentCheck.nonNull(table);
        if (JSON.Format.COLUMNAR.equals(this.format))
        {
            return JSON.toJsonColumnar(checkedTable, this.toStringSettings);
        }
        return JSON.toJsonRowOriented(checkedTable, this.toStringSettings);
    }
}
