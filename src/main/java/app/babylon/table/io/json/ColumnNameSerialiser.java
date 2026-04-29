/*
 * Copyright 2026 Babylon Financial Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package app.babylon.table.io.json;

import java.lang.reflect.Type;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import app.babylon.table.column.ColumnName;

public class ColumnNameSerialiser implements JsonSerializer<ColumnName>
{
    @Override
    public JsonElement serialize(ColumnName src, Type typeOfSrc, JsonSerializationContext context)
    {
        if (src == null)
        {
            return null;
        }
        return new JsonPrimitive(src.getValue());
    }
}
