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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import app.babylon.table.TableColumnar;
import app.babylon.table.TableDescription;
import app.babylon.table.TableName;
import app.babylon.table.Tables;
import app.babylon.table.ToStringSettings;
import app.babylon.table.column.Column;
import app.babylon.table.column.ColumnByte;
import app.babylon.table.column.ColumnDouble;
import app.babylon.table.column.ColumnInt;
import app.babylon.table.column.ColumnLong;
import app.babylon.table.column.ColumnName;
import app.babylon.table.column.ColumnObject;
import app.babylon.table.column.ColumnTypes;

public class JSONTest
{
    private TableColumnar sampleTable()
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
    public void toJsonByRow_includesDecimalColumnTypes()
    {
        TableColumnar table = sampleTable();

        String json = JSON.toJson(table, ToStringSettings.standard());
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        JsonObject columnTypes = jsonObject.getAsJsonObject("columnTypes");

        assertTrue(jsonObject.has("columnTypes"));
        assertEquals("Decimal", columnTypes.get("amount").getAsString());
        assertFalse(columnTypes.has("quantity"));
        assertFalse(columnTypes.has("note"));
    }

    @Test
    public void toJsonRowOriented_writesExpectedShape()
    {
        TableColumnar table = sampleTable();

        String json = JSON.toJsonRowOriented(table, ToStringSettings.standard());

        String expected = compact("""
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
                """);

        assertEquals(expected, json);
    }

    @Test
    public void toJsonColumnar_writesExpectedShape()
    {
        TableColumnar table = sampleTable();

        String json = JSON.toJsonColumnar(table, ToStringSettings.standard());

        String expected = compact("""
                {
                  "columns": [
                    { "amount": ["12.34"] },
                    { "quantity": ["5"] },
                    { "note": ["Hello"] }
                  ],
                  "description": "Test Description",
                  "name": "TestTable"
                }
                """);

        assertEquals(expected, json);
    }

    @Test
    public void toJsonRowOriented_andBackToTableRowOriented_roundTrips()
    {
        TableColumnar table = sampleTable();

        String json = JSON.toJsonRowOriented(table, ToStringSettings.standard());
        TableColumnar roundTripped = JSON.toTableRowOriented(json);

        assertTableJsonEquivalent(table, roundTripped);
        assertEquals(3, roundTripped.getColumnCount());
        assertEquals(1, roundTripped.getRowCount());
        assertEquals("Test Description", roundTripped.getDescription().getValue());
        assertEquals(new BigDecimal("12.34"), roundTripped.getDecimal(ColumnName.of("amount")).get(0));
        assertEquals("5", roundTripped.getString(ColumnName.of("quantity")).get(0));
        assertEquals("Hello", roundTripped.getString(ColumnName.of("note")).get(0));
    }

    @Test
    public void fromJsonRowOriented_parsesJsonObject()
    {
        TableColumnar table = sampleTable();
        JsonObject jsonObject = JSON.toJsonObjectRowOriented(table, ToStringSettings.standard());
        TableColumnar parsed = JSON.fromJsonRowOriented(jsonObject);

        assertTableJsonEquivalent(table, parsed);
        assertEquals(3, parsed.getColumnCount());
        assertEquals(1, parsed.getRowCount());
        assertEquals("Test Description", parsed.getDescription().getValue());
        assertEquals(new BigDecimal("12.34"), parsed.getDecimal(ColumnName.of("amount")).get(0));
        assertEquals("5", parsed.getString(ColumnName.of("quantity")).get(0));
        assertEquals("Hello", parsed.getString(ColumnName.of("note")).get(0));
    }

    @Test
    public void toJsonColumnar_andBackToTableColumnar_roundTrips()
    {
        TableColumnar table = sampleTable();

        String json = JSON.toJsonColumnar(table, ToStringSettings.standard());
        TableColumnar roundTripped = JSON.fromJsonColumnar(json);

        assertTableTextEquivalent(table, roundTripped);
        assertEquals(3, roundTripped.getColumnCount());
        assertEquals(1, roundTripped.getRowCount());
        assertEquals("Test Description", roundTripped.getDescription().getValue());
        assertEquals("12.34", roundTripped.getString(ColumnName.of("amount")).get(0));
        assertEquals("5", roundTripped.getString(ColumnName.of("quantity")).get(0));
        assertEquals("Hello", roundTripped.getString(ColumnName.of("note")).get(0));
    }

    @Test
    public void fromJsonColumnar_parsesJsonObject()
    {
        TableColumnar table = sampleTable();
        String json = JSON.toJsonColumnar(table, ToStringSettings.standard());
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        TableColumnar parsed = JSON.fromJsonColumnar(jsonObject);

        assertTableTextEquivalent(table, parsed);
        assertEquals(3, parsed.getColumnCount());
        assertEquals(1, parsed.getRowCount());
        assertEquals("Test Description", parsed.getDescription().getValue());
        assertEquals("12.34", parsed.getString(ColumnName.of("amount")).get(0));
        assertEquals("5", parsed.getString(ColumnName.of("quantity")).get(0));
        assertEquals("Hello", parsed.getString(ColumnName.of("note")).get(0));
    }

    @Test
    public void fromJsonColumnar_treatsExplicitJsonNullAsUnset()
    {
        JsonObject jsonObject = JsonParser.parseString("""
                {
                  "columns": [
                    { "name": ["Alice", null] },
                    { "city": ["London", "Paris"] }
                  ],
                  "description": "People",
                  "name": "People"
                }
                """).getAsJsonObject();

        TableColumnar table = JSON.fromJsonColumnar(jsonObject);

        assertEquals(2, table.getRowCount());
        assertEquals("Alice", table.getString(ColumnName.of("name")).get(0));
        assertFalse(table.getString(ColumnName.of("name")).isSet(1));
        assertEquals("Paris", table.getString(ColumnName.of("city")).get(1));
    }

    @Test
    public void fromJsonColumnar_rejectsMismatchedColumnLengths()
    {
        JsonObject jsonObject = JsonParser.parseString("""
                {
                  "columns": [
                    { "name": ["Alice", "Bob"] },
                    { "city": ["London"] }
                  ],
                  "description": "People",
                  "name": "People"
                }
                """).getAsJsonObject();

        assertThrows(RuntimeException.class, () -> JSON.fromJsonColumnar(jsonObject));
    }

    @Test
    public void fromJsonColumnar_skipsEmptyRows()
    {
        JsonObject jsonObject = JsonParser.parseString("""
                {
                  "columns": [
                    { "name": ["Alice", "", "Bob"] },
                    { "city": ["London", null, "Paris"] }
                  ],
                  "description": "People",
                  "name": "People"
                }
                """).getAsJsonObject();

        TableColumnar table = JSON.fromJsonColumnar(jsonObject);

        assertEquals(2, table.getRowCount());
        assertEquals("Alice", table.getString(ColumnName.of("name")).get(0));
        assertEquals("London", table.getString(ColumnName.of("city")).get(0));
        assertEquals("Bob", table.getString(ColumnName.of("name")).get(1));
        assertEquals("Paris", table.getString(ColumnName.of("city")).get(1));
    }

    @Test
    public void fromJsonRowOriented_skipsEmptyRows()
    {
        JsonObject jsonObject = JsonParser.parseString("""
                {
                  "name": "People",
                  "description": "People",
                  "columns": ["name", "city"],
                  "columnTypes": {},
                  "rows": [
                    { "name": "Alice", "city": "London" },
                    { "name": "", "city": null },
                    { "name": "Bob", "city": "Paris" }
                  ]
                }
                """).getAsJsonObject();

        TableColumnar table = JSON.fromJsonRowOriented(jsonObject);

        assertEquals(2, table.getRowCount());
        assertEquals("Alice", table.getString(ColumnName.of("name")).get(0));
        assertEquals("London", table.getString(ColumnName.of("city")).get(0));
        assertEquals("Bob", table.getString(ColumnName.of("name")).get(1));
        assertEquals("Paris", table.getString(ColumnName.of("city")).get(1));
    }

    @Test
    public void toColumnLong_readsArrayValuesAndNulls()
    {
        ColumnLong column = JSON.toColumnLong("""
                {
                  "amount": [12, null, "-3"]
                }
                """);

        assertEquals(ColumnName.of("amount"), column.getName());
        assertEquals(3, column.size());
        assertEquals(12L, column.get(0));
        assertFalse(column.isSet(1));
        assertEquals(-3L, column.get(2));
    }

    @Test
    public void toJsonRowOriented_writesUnsetPrimitiveValuesAsJsonNull()
    {
        ColumnInt.Builder ints = ColumnInt.builder(ColumnName.of("IntValue"));
        ints.add(1);
        ints.addNull();

        ColumnLong.Builder longs = ColumnLong.builder(ColumnName.of("LongValue"));
        longs.add(2L);
        longs.addNull();

        ColumnDouble.Builder doubles = ColumnDouble.builder(ColumnName.of("DoubleValue"));
        doubles.add(3.5d);
        doubles.addNull();

        ColumnByte.Builder bytes = ColumnByte.builder(ColumnName.of("ByteValue"));
        bytes.add((byte) 4);
        bytes.addNull();

        TableColumnar table = Tables.newTable(TableName.of("PrimitiveNulls"), new TableDescription(), ints.build(),
                longs.build(), doubles.build(), bytes.build());

        JsonObject jsonObject = JSON.toJsonObjectRowOriented(table, ToStringSettings.standard());
        JsonObject row = jsonObject.getAsJsonArray("rows").get(1).getAsJsonObject();

        assertTrue(row.get("intValue").isJsonNull());
        assertTrue(row.get("longValue").isJsonNull());
        assertTrue(row.get("doubleValue").isJsonNull());
        assertTrue(row.get("byteValue").isJsonNull());
    }

    private static String compact(String json)
    {
        return JsonParser.parseString(json).toString();
    }

    private static void assertTableJsonEquivalent(TableColumnar expected, TableColumnar actual)
    {
        assertTableTextEquivalent(expected, actual);
        for (Column expectedColumn : expected.getColumns())
        {
            Column actualColumn = actual.getColumns(expectedColumn.getName())[0];
            if (ColumnTypes.DECIMAL.equals(expectedColumn.getType()))
            {
                assertEquals(ColumnTypes.DECIMAL, actualColumn.getType());
            }
        }
    }

    private static void assertTableTextEquivalent(TableColumnar expected, TableColumnar actual)
    {
        ToStringSettings settings = ToStringSettings.standard();
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getDescription().getValue(), actual.getDescription().getValue());
        assertEquals(expected.getColumnCount(), actual.getColumnCount());
        assertEquals(expected.getRowCount(), actual.getRowCount());

        ColumnName[] expectedNames = expected.getColumnNames();
        ColumnName[] actualNames = actual.getColumnNames();
        assertEquals(expectedNames.length, actualNames.length);
        for (int columnIndex = 0; columnIndex < expectedNames.length; ++columnIndex)
        {
            assertEquals(expectedNames[columnIndex], actualNames[columnIndex]);
            assertColumnTextEquivalent(expected.getColumns(expectedNames[columnIndex])[0],
                    actual.getColumns(actualNames[columnIndex])[0], settings);
        }
    }

    private static void assertColumnTextEquivalent(Column expected, Column actual, ToStringSettings settings)
    {
        assertEquals(expected.size(), actual.size());
        for (int rowIndex = 0; rowIndex < expected.size(); ++rowIndex)
        {
            assertEquals(expected.isSet(rowIndex), actual.isSet(rowIndex));
            assertEquals(expected.toString(rowIndex, settings), actual.toString(rowIndex, settings));
        }
    }
}
