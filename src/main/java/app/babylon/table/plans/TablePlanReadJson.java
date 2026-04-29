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
import app.babylon.table.io.json.JSON;

public class TablePlanReadJson
{
    private JSON.Format format;

    public TablePlanReadJson()
    {
        this.format = JSON.Format.ROW_ORIENTED;
    }

    public TablePlanReadJson withFormat(JSON.Format format)
    {
        this.format = ArgumentCheck.nonNull(format);
        return this;
    }

    public JSON.Format getFormat()
    {
        return this.format;
    }

    public TableColumnar execute(String json)
    {
        String checkedJson = ArgumentCheck.nonNull(json);
        if (JSON.Format.COLUMNAR.equals(this.format))
        {
            return JSON.fromJsonColumnar(checkedJson);
        }
        return JSON.toTableRowOriented(checkedJson);
    }
}
