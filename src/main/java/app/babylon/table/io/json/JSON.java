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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
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
import app.babylon.table.transform.Transform;
import app.babylon.table.transform.TransformStringToType;
import app.babylon.text.Strings;

public final class JSON
{
    public enum Format
    {
        ROW_ORIENTED, COLUMNAR
    }

    private static final String COLUMN_TYPES = "columnTypes";
    private static final String COLUMNS = "columns";
    private static final String DECIMAL = "Decimal";
    private static final String DESCRIPTION = "description";
    private static final String NAME = "name";
    private static final String ROWS = "rows";

    private JSON()
    {
    }

    public static String toJsonColumnar(TableColumnar table, ToStringSettings settings)
    {
        return gson().toJson(toJsonObjectColumnar(table, settings));
    }

    protected static JsonObject toJsonObjectColumnar(TableColumnar table, ToStringSettings settings)
    {
        JsonObject jsonObject = new JsonObject();
        jsonObject.add(COLUMNS, toJsonColumnsColumnar(table, settings));
        jsonObject.addProperty(DESCRIPTION, table.getDescription().getValue());
        jsonObject.addProperty(NAME, table.getName().getOriginal());
        return jsonObject;
    }

    public static String toJson(TableColumnar table, ToStringSettings settings)
    {
        return toJsonRowOriented(table, settings);
    }

    public static String toJsonRowOriented(TableColumnar table, ToStringSettings settings)
    {
        return gson().toJson(toJsonObjectRowOriented(table, settings));
    }

    protected static JsonObject toJsonObjectRowOriented(TableColumnar table, ToStringSettings settings)
    {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty(NAME, table.getName().getOriginal());
        jsonObject.addProperty(DESCRIPTION, table.getDescription().getValue());

        Column[] columns = table.getColumns();
        jsonObject.add(COLUMNS, toJsonColumnNames(table));
        jsonObject.add(COLUMN_TYPES, toJsonColumnTypes(columns));
        jsonObject.add(ROWS, toJsonRows(table, columns, settings));
        return jsonObject;
    }

    private static Gson gson()
    {
        return new GsonBuilder().create();
    }

    private static JsonArray toJsonColumnsColumnar(TableColumnar table, ToStringSettings settings)
    {
        JsonArray jsonColumns = new JsonArray();
        for (Column column : table.columns())
        {
            JsonObject jsonColumn = new JsonObject();
            jsonColumn.add(columnName(column), toJsonValuesColumnar(column, settings));
            jsonColumns.add(jsonColumn);
        }
        return jsonColumns;
    }

    private static JsonArray toJsonValuesColumnar(Column column, ToStringSettings settings)
    {
        JsonArray values = new JsonArray();
        for (int j = 0; j < column.size(); ++j)
        {
            values.add(column.toString(j, settings));
        }
        return values;
    }

    private static JsonArray toJsonRows(TableColumnar table, Column[] columns, ToStringSettings settings)
    {
        JsonArray jsonRows = new JsonArray();
        for (int i = 0; i < table.getRowCount(); ++i)
        {
            JsonObject jsonRow = new JsonObject();
            for (int j = 0; j < columns.length; ++j)
            {
                addRowValue(jsonRow, columns[j], i, settings);
            }
            jsonRows.add(jsonRow);
        }
        return jsonRows;
    }

    private static JsonArray toJsonColumnNames(TableColumnar table)
    {
        JsonArray jsonColumns = new JsonArray();
        ColumnName[] columnNames = table.getColumnNames();
        for (int j = 0; j < columnNames.length; ++j)
        {
            jsonColumns.add(columnNames[j].toCamelCase());
        }
        return jsonColumns;
    }

    private static JsonObject toJsonColumnTypes(Column[] columns)
    {
        JsonObject jsonColumnTypes = new JsonObject();
        for (int j = 0; j < columns.length; ++j)
        {
            Column column = columns[j];
            if (column instanceof ColumnObject<?> && BigDecimal.class.equals(column.getType().getValueClass()))
            {
                jsonColumnTypes.addProperty(columnName(column), DECIMAL);
            }
        }
        return jsonColumnTypes;
    }

    private static void addRowValue(JsonObject jsonRow, Column column, int rowIndex, ToStringSettings settings)
    {
        String name = columnName(column);
        if (!column.isSet(rowIndex))
        {
            jsonRow.add(name, JsonNull.INSTANCE);
        }
        else if (column instanceof ColumnObject<?>)
        {
            jsonRow.addProperty(name, column.toString(rowIndex, settings));
        }
        else if (column instanceof ColumnInt ci)
        {
            jsonRow.addProperty(name, Integer.valueOf(ci.get(rowIndex)));
        }
        else if (column instanceof ColumnLong cl)
        {
            jsonRow.addProperty(name, Long.valueOf(cl.get(rowIndex)));
        }
        else if (column instanceof ColumnDouble cd)
        {
            jsonRow.addProperty(name, Double.valueOf(cd.get(rowIndex)));
        }
        else if (column instanceof ColumnByte cb)
        {
            jsonRow.addProperty(name, Byte.valueOf(cb.get(rowIndex)));
        }
        else
        {
            jsonRow.addProperty(name, column.toString(rowIndex, settings));
        }
    }

    private static String columnName(Column column)
    {
        return column.getName().toCamelCase();
    }

    @Deprecated
    protected static JsonObject toJsonByRow(TableColumnar table, ToStringSettings settings)
    {
        return toJsonObjectRowOriented(table, settings);
    }

    public static TableColumnar fromJsonColumnar(String s)
    {
        JsonObject jsonObject = JsonParser.parseString(s).getAsJsonObject();
        return fromJsonColumnar(jsonObject);
    }

    public static String toJson(ColumnLong column, ToStringSettings settings)
    {
        JsonArray values = new JsonArray();
        for (int j = 0; j < column.size(); ++j)
        {
            if (column.isSet(j))
            {
                values.add(Long.valueOf(column.get(j)));
            }
            else
            {
                values.add((Long) null);
            }
        }

        JsonObject jsonColumn = new JsonObject();
        jsonColumn.add(columnName(column), values);

        return gson().toJson(jsonColumn);
    }

    public static ColumnLong toColumnLong(String jsonString)
    {
        JsonObject jsonObject = JsonParser.parseString(jsonString).getAsJsonObject();
        return toColumnLong(jsonObject);
    }

    private static ColumnLong toColumnLong(JsonObject jsonObject)
    {
        for (Entry<String, JsonElement> e : jsonObject.entrySet())
        {
            ColumnName cn = ColumnName.parse(e.getKey());
            ColumnLong.Builder cl = ColumnLong.builder(cn);

            JsonElement je = e.getValue();
            if (je.isJsonNull())
            {
                cl.addNull();
            }
            if (je.isJsonPrimitive())
            {
                addLongValue(cl, je);
            }
            if (je.isJsonArray())
            {
                JsonArray ja = je.getAsJsonArray();
                for (JsonElement je2 : ja)
                {
                    addLongValue(cl, je2);
                }
            }
            else
            {
                throw new RuntimeException("Badly formed JSON.");
            }
            return cl.build();
        }

        return null;
    }

    private static void addLongValue(ColumnLong.Builder builder, JsonElement element)
    {
        if (element == null || element.isJsonNull())
        {
            builder.addNull();
        }
        else if (element.isJsonPrimitive())
        {
            String text = element.getAsString();
            builder.add(text, 0, text.length());
        }
    }

    public static TableColumnar toTableRowOriented(String s)
    {
        JsonObject jsonObject = JsonParser.parseString(s).getAsJsonObject();
        return fromJsonRowOriented(jsonObject);
    }

    public static TableColumnar fromJsonColumnar(JsonObject jsonObject)
    {
        List<JsonColumn> columns = readJsonColumns(jsonObject.get(COLUMNS).getAsJsonArray());
        validateColumnarLengths(columns);
        ColumnObject.Builder<String>[] columnBuilders = newStringBuilders(columns);
        addColumnarRows(columnBuilders, columns);

        TableColumnar table = newTable(jsonObject, columnBuilders);
        return applyColumnTypeTransformations(jsonObject, table);
    }

    private static ColumnObject.Builder<String>[] newStringBuilders(List<JsonColumn> columns)
    {
        @SuppressWarnings("unchecked")
        ColumnObject.Builder<String>[] columnBuilders = new ColumnObject.Builder[columns.size()];
        for (int i = 0; i < columns.size(); ++i)
        {
            columnBuilders[i] = ColumnObject.builder(ColumnName.of(columns.get(i).name()));
        }
        return columnBuilders;
    }

    private static void validateColumnarLengths(List<JsonColumn> columns)
    {
        for (int i = 1; i < columns.size(); ++i)
        {
            JsonColumn column = columns.get(i);
            if (column.values().size() != columns.get(i - 1).values().size())
            {
                throw new RuntimeException(
                        "Expected table with columns of same length, fail on column " + i + " " + column.name());
            }
        }
    }

    private static void addColumnarRows(ColumnObject.Builder<String>[] columnBuilders, List<JsonColumn> columns)
    {
        if (columns.isEmpty())
        {
            return;
        }

        int size = columns.get(0).values().size();
        String[] rowValues = new String[columnBuilders.length];
        for (int rowIndex = 0; rowIndex < size; ++rowIndex)
        {
            populateColumnarRowValues(rowValues, columns, rowIndex);
            if (!isEmptyRow(rowValues))
            {
                addRowValues(columnBuilders, rowValues);
            }
        }
    }

    private static void populateColumnarRowValues(String[] rowValues, List<JsonColumn> columns, int rowIndex)
    {
        for (int i = 0; i < rowValues.length; ++i)
        {
            JsonElement element = columns.get(i).values().get(rowIndex);
            rowValues[i] = jsonStringOrNull(element);
        }
    }

    private static List<JsonColumn> readJsonColumns(JsonArray jsonColumns)
    {
        List<JsonColumn> columns = new ArrayList<>();
        for (JsonElement element : jsonColumns)
        {
            JsonObject jsonColumn = element.getAsJsonObject();
            for (Entry<String, JsonElement> entry : jsonColumn.entrySet())
            {
                columns.add(new JsonColumn(entry.getKey(), entry.getValue().getAsJsonArray()));
            }
        }
        return columns;
    }

    public static TableColumnar fromJsonRowOriented(JsonObject jsonObject)
    {
        JsonArray jsonColumns = jsonObject.get(COLUMNS).getAsJsonArray();
        JsonArray jsonRows = jsonObject.get(ROWS).getAsJsonArray();

        String[] headers = readHeaders(jsonColumns);
        ColumnObject.Builder<String>[] columnBuilders = newStringBuilders(headers);
        String[] rowValues = new String[jsonColumns.size()];

        for (int i = 0; i < jsonRows.size(); ++i)
        {
            populateRowOrientedRowValues(rowValues, jsonRows.get(i).getAsJsonObject(), headers);
            if (!isEmptyRow(rowValues))
            {
                addRowValues(columnBuilders, rowValues);
            }
        }

        TableColumnar table = newTable(jsonObject, columnBuilders);
        return applyColumnTypeTransformations(jsonObject, table);
    }

    private static TableColumnar newTable(JsonObject jsonObject, ColumnObject.Builder<String>[] columnBuilders)
    {
        return Tables.newTable(parseName(jsonObject), parseDescription(jsonObject), columnBuilders);
    }

    private static TableName parseName(JsonObject jsonObject)
    {
        return TableName.of(jsonObject.get(NAME).getAsString());
    }

    private static String[] readHeaders(JsonArray jsonColumns)
    {
        String[] headers = new String[jsonColumns.size()];
        for (int i = 0; i < jsonColumns.size(); ++i)
        {
            headers[i] = jsonColumns.get(i).getAsString();
        }
        return headers;
    }

    @SuppressWarnings("unchecked")
    private static ColumnObject.Builder<String>[] newStringBuilders(String[] headers)
    {
        ColumnObject.Builder<String>[] columnBuilders = new ColumnObject.Builder[headers.length];
        for (int i = 0; i < headers.length; ++i)
        {
            columnBuilders[i] = ColumnObject.builder(ColumnName.of(headers[i]));
        }
        return columnBuilders;
    }

    private static void populateRowOrientedRowValues(String[] rowValues, JsonObject jsonRow, String[] headers)
    {
        for (int j = 0; j < headers.length; ++j)
        {
            rowValues[j] = jsonStringOrNull(jsonRow.get(headers[j]));
        }
    }

    private static String jsonStringOrNull(JsonElement element)
    {
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static boolean isEmptyRow(String[] rowValues)
    {
        for (int i = 0; i < rowValues.length; ++i)
        {
            if (!Strings.isEmpty(rowValues[i]))
            {
                return false;
            }
        }
        return true;
    }

    private static void addRowValues(ColumnObject.Builder<String>[] columnBuilders, String[] rowValues)
    {
        for (int i = 0; i < columnBuilders.length; ++i)
        {
            columnBuilders[i].add(rowValues[i]);
        }
    }

    @Deprecated
    public static TableColumnar fromJson(JsonObject jsonObject)
    {
        return fromJsonColumnar(jsonObject);
    }

    private static TableColumnar applyColumnTypeTransformations(JsonObject jsonObject, TableColumnar table)
    {
        JsonObject columnTypes = jsonObject.getAsJsonObject(COLUMN_TYPES);
        if (columnTypes == null)
        {
            return table;
        }

        List<ColumnName> decimalColumns = new ArrayList<>();
        for (Entry<String, JsonElement> entry : columnTypes.entrySet())
        {
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonPrimitive() && DECIMAL.equalsIgnoreCase(value.getAsString()))
            {
                decimalColumns.add(ColumnName.of(entry.getKey()));
            }
        }
        if (decimalColumns.isEmpty())
        {
            return table;
        }

        List<Transform> transforms = new ArrayList<>();
        for (ColumnName decimalColumn : decimalColumns)
        {
            transforms.add(TransformStringToType.builder(ColumnTypes.DECIMAL, decimalColumn).build());
        }
        return table.apply(transforms);
    }

    private static TableDescription parseDescription(JsonObject jsonObject)
    {
        JsonElement descriptionElement = jsonObject.get(DESCRIPTION);
        if (descriptionElement == null || descriptionElement.isJsonNull())
        {
            return new TableDescription();
        }
        return new TableDescription(descriptionElement.getAsString());
    }

    private record JsonColumn(String name, JsonArray values)
    {
    }
}
