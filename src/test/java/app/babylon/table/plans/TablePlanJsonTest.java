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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import app.babylon.io.DataResource;
import app.babylon.io.DataResources;
import app.babylon.table.TableColumnar;
import app.babylon.table.TableDescription;
import app.babylon.table.TableName;
import app.babylon.table.Tables;
import app.babylon.table.column.ColumnInt;
import app.babylon.table.column.ColumnName;
import app.babylon.table.column.ColumnObject;
import app.babylon.table.column.ColumnTypes;
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

        TableColumnar table = new TablePlanReadJson().execute(jsonSource(json));

        assertEquals(3, table.getColumnCount());
        assertEquals(1, table.getRowCount());
        assertEquals("Test Description", table.getDescription().getValue());
        assertEquals("12.34", table.getString(ColumnName.of("amount")).get(0));
        assertEquals("5", table.getString(ColumnName.of("quantity")).get(0));
        assertEquals("Hello", table.getString(ColumnName.of("note")).get(0));
    }

    @Test
    public void readJsonDefaultsToIgnoringColumnTypes()
    {
        String json = new TablePlanWriteJson().execute(sampleTable());

        TableColumnar table = new TablePlanReadJson().execute(jsonSource(json));

        assertEquals(ColumnTypes.STRING, table.getType(ColumnName.of("amount")));
    }

    @Test
    public void readJsonCanApplyColumnTypes()
    {
        String json = new TablePlanWriteJson().execute(sampleTable());

        TableColumnar table = new TablePlanReadJson().withApplyColumnTypes(true).execute(jsonSource(json));

        assertEquals(ColumnTypes.DECIMAL, table.getType(ColumnName.of("amount")));
        assertEquals(new BigDecimal("12.34"), table.getDecimal(ColumnName.of("amount")).get(0));
    }

    @Test
    public void readJsonCanSelectRowOrientedColumns()
    {
        String json = new TablePlanWriteJson().execute(sampleTable());

        TableColumnar table = new TablePlanReadJson()
                .withSelectedColumns(ColumnName.of("amount"), ColumnName.of("note")).execute(jsonSource(json));

        assertEquals(2, table.getColumnCount());
        assertArrayEquals(new ColumnName[]
        {ColumnName.of("amount"), ColumnName.of("note")}, table.getColumnNames());
        assertEquals(ColumnTypes.STRING, table.getType(ColumnName.of("amount")));
        assertEquals("12.34", table.getString(ColumnName.of("amount")).get(0));
        assertEquals("Hello", table.getString(ColumnName.of("note")).get(0));
    }

    @Test
    public void readJsonCanSelectRowOrientedColumnsFromInputStream()
    {
        String json = new TablePlanWriteJson().execute(sampleTable());
        ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        TableColumnar table = new TablePlanReadJson().withSelectedColumn(ColumnName.of("note")).execute(inputStream);

        assertEquals(1, table.getColumnCount());
        assertArrayEquals(new ColumnName[]
        {ColumnName.of("note")}, table.getColumnNames());
        assertEquals("Hello", table.getString(ColumnName.of("note")).get(0));
    }

    @Test
    public void readJsonColumnarCanSkipColumnTypes()
    {
        String json = """
                {
                  "columns": [
                    { "amount": ["12.34"] },
                    { "quantity": ["5"] },
                    { "note": ["Hello"] }
                  ],
                  "columnTypes": {
                    "amount": "Decimal"
                  },
                  "description": "Test Description",
                  "name": "TestTable"
                }
                """;

        TableColumnar table = new TablePlanReadJson().withFormat(JSON.Format.COLUMNAR).execute(jsonSource(json));

        assertEquals(ColumnTypes.STRING, table.getType(ColumnName.of("amount")));
        assertEquals("12.34", table.getString(ColumnName.of("amount")).get(0));
    }

    @Test
    public void readJsonColumnarCanSelectColumns()
    {
        String json = columnarJsonWithColumnTypes();

        TableColumnar table = new TablePlanReadJson().withFormat(JSON.Format.COLUMNAR)
                .withSelectedColumns(ColumnName.of("amount"), ColumnName.of("note")).execute(jsonSource(json));

        assertEquals(2, table.getColumnCount());
        assertArrayEquals(new ColumnName[]
        {ColumnName.of("amount"), ColumnName.of("note")}, table.getColumnNames());
        assertEquals(ColumnTypes.STRING, table.getType(ColumnName.of("amount")));
        assertEquals("12.34", table.getString(ColumnName.of("amount")).get(0));
        assertEquals("Hello", table.getString(ColumnName.of("note")).get(0));
    }

    @Test
    public void readJsonColumnarSkipsUnselectedColumnArrays()
    {
        String json = """
                {
                  "columns": [
                    { "amount": ["12.34", "56.78"] },
                    { "quantity": ["5"] },
                    { "note": ["Hello", "World"] }
                  ],
                  "description": "Test Description",
                  "name": "TestTable"
                }
                """;

        TableColumnar table = new TablePlanReadJson().withFormat(JSON.Format.COLUMNAR)
                .withSelectedColumns(ColumnName.of("amount"), ColumnName.of("note")).execute(jsonSource(json));

        assertEquals(2, table.getColumnCount());
        assertEquals(2, table.getRowCount());
        assertArrayEquals(new ColumnName[]
        {ColumnName.of("amount"), ColumnName.of("note")}, table.getColumnNames());
        assertEquals("12.34", table.getString(ColumnName.of("amount")).get(0));
        assertEquals("56.78", table.getString(ColumnName.of("amount")).get(1));
        assertEquals("Hello", table.getString(ColumnName.of("note")).get(0));
        assertEquals("World", table.getString(ColumnName.of("note")).get(1));
    }

    @Test
    public void readJsonCanUseColumnarFormat()
    {
        String json = new TablePlanWriteJson().withFormat(JSON.Format.COLUMNAR).execute(sampleTable());

        TableColumnar table = new TablePlanReadJson().withFormat(JSON.Format.COLUMNAR).execute(jsonSource(json));

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

    private static DataResource jsonSource(String json)
    {
        return DataResources.fromString(json, "json");
    }

    private static String columnarJsonWithColumnTypes()
    {
        return """
                {
                  "columns": [
                    { "amount": ["12.34"] },
                    { "quantity": ["5"] },
                    { "note": ["Hello"] }
                  ],
                  "columnTypes": {
                    "amount": "Decimal"
                  },
                  "description": "Test Description",
                  "name": "TestTable"
                }
                """;
    }
}
