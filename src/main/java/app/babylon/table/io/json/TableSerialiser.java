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
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import app.babylon.table.TableColumnar;
import app.babylon.table.ToStringSettings;

public class TableSerialiser implements JsonSerializer<TableColumnar>
{
    @Override
    public JsonElement serialize(TableColumnar src, Type typeOfSrc, JsonSerializationContext context)
    {
        return JSON.toJsonObjectRowOriented(src, ToStringSettings.standard());
    }
}
