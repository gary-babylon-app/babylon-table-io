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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import app.babylon.table.TableColumnar;
import app.babylon.table.TableDescription;
import app.babylon.table.TableName;
import app.babylon.table.Tables;
import app.babylon.table.column.ColumnInt;
import app.babylon.table.column.ColumnName;
import app.babylon.table.column.ColumnObject;
import app.babylon.table.io.json.JSON;

public class TablePlanJsonTest
{
    private static TableColumnar sampleTable()
    {
        ColumnObject.Builder<BigDecimal> amountBuilder = ColumnObject.builderDecimal(ColumnName.of("Amount"));
        amountBuilder.add(new BigDecimal("12.34"));

        ColumnInt.Builder quantity = ColumnInt.builder(ColumnName.of("Quantity"));
        quantity.add(5);

        ColumnObject.Builder<String> note = ColumnObject.builder(ColumnName.of("Note"));
        note.add("Hello");

        return Tables.newTable(TableName.of("TestTable"), new TableDescription("Test Description"),
                amountBuilder.build(), quantity.build(), note.build());
    }

    @Test
    public void writeJsonDefaultsToRowOrientedFormat()
    {
        String json = new TablePlanWriteJson().execute(sampleTable());

        assertEquals(compact("""
                {
                  "name": "TestTable",
                  "description": "Test Description",
                  "columns": ["amount", "quantity", "note"],
                  "columnTypes": {
                    "amount": "Decimal"
                  },
                  "rows": [
                    {
                      "amount": "12.34",
                      "quantity": 5,
                      "note": "Hello"
                    }
                  ]
                }
                """), json);
    }

    @Test
    public void writeJsonCanUseColumnarFormat()
    {
        String json = new TablePlanWriteJson().withFormat(JSON.Format.COLUMNAR).execute(sampleTable());

        assertEquals(compact("""
                {
                  "columns": [
                    { "amount": ["12.34"] },
                    { "quantity": ["5"] },
                    { "note": ["Hello"] }
                  ],
                  "description": "Test Description",
                  "name": "TestTable"
                }
                """), json);
    }

    @Test
    public void readJsonDefaultsToRowOrientedFormat()
    {
        String json = new TablePlanWriteJson().execute(sampleTable());

        TableColumnar table = new TablePlanReadJson().execute(json);

        assertEquals(3, table.getColumnCount());
        assertEquals(1, table.getRowCount());
        assertEquals("Test Description", table.getDescription().getValue());
        assertEquals(new BigDecimal("12.34"), table.getDecimal(ColumnName.of("amount")).get(0));
        assertEquals("5", table.getString(ColumnName.of("quantity")).get(0));
        assertEquals("Hello", table.getString(ColumnName.of("note")).get(0));
    }

    @Test
    public void readJsonCanUseColumnarFormat()
    {
        String json = new TablePlanWriteJson().withFormat(JSON.Format.COLUMNAR).execute(sampleTable());

        TableColumnar table = new TablePlanReadJson().withFormat(JSON.Format.COLUMNAR).execute(json);

        assertEquals(3, table.getColumnCount());
        assertEquals(1, table.getRowCount());
        assertEquals("Test Description", table.getDescription().getValue());
        assertEquals("12.34", table.getString(ColumnName.of("amount")).get(0));
        assertEquals("5", table.getString(ColumnName.of("quantity")).get(0));
        assertEquals("Hello", table.getString(ColumnName.of("note")).get(0));
    }

    private static String compact(String json)
    {
        return JsonParser.parseString(json).toString();
    }
}
