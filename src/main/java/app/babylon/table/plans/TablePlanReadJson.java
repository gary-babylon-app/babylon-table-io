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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import app.babylon.io.StreamSource;
import app.babylon.lang.ArgumentCheck;
import app.babylon.table.TableColumnar;
import app.babylon.table.TableDescription;
import app.babylon.table.TableName;
import app.babylon.table.Tables;
import app.babylon.table.column.ColumnName;
import app.babylon.table.column.ColumnObject;
import app.babylon.table.column.ColumnTypes;
import app.babylon.table.io.json.JSON;
import app.babylon.table.transform.Transform;
import app.babylon.table.transform.TransformStringToType;

public class TablePlanReadJson
{
    private static final String COLUMN_TYPES = "columnTypes";
    private static final String COLUMNS = "columns";
    private static final String DECIMAL = "Decimal";
    private static final String DESCRIPTION = "description";
    private static final String NAME = "name";
    private static final String ROWS = "rows";

    private JSON.Format format;
    private boolean applyColumnTypes;
    private final Set<ColumnName> selectedColumns = new LinkedHashSet<>();

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

    public TablePlanReadJson withApplyColumnTypes(boolean applyColumnTypes)
    {
        this.applyColumnTypes = applyColumnTypes;
        return this;
    }

    public boolean getApplyColumnTypes()
    {
        return this.applyColumnTypes;
    }

    public TablePlanReadJson withSelectedColumn(ColumnName columnName)
    {
        this.selectedColumns.add(ArgumentCheck.nonNull(columnName));
        return this;
    }

    public TablePlanReadJson withSelectedColumns(ColumnName... columnNames)
    {
        if (columnNames != null)
        {
            this.selectedColumns.addAll(Arrays.asList(columnNames));
        }
        return this;
    }

    public TablePlanReadJson withSelectedColumns(Collection<ColumnName> columnNames)
    {
        return withSelectedColumns((Iterable<ColumnName>) columnNames);
    }

    public TablePlanReadJson withSelectedColumns(Iterable<ColumnName> columnNames)
    {
        if (columnNames != null)
        {
            for (ColumnName columnName : columnNames)
            {
                this.selectedColumns.add(columnName);
            }
        }
        return this;
    }

    public Set<ColumnName> getSelectedColumns()
    {
        return Collections.unmodifiableSet(this.selectedColumns);
    }

    public TableColumnar execute(InputStream inputStream)
    {
        return read(ArgumentCheck.nonNull(inputStream));
    }

    public TableColumnar execute(StreamSource streamSource)
    {
        StreamSource checkedStreamSource = ArgumentCheck.nonNull(streamSource);
        try (InputStream inputStream = checkedStreamSource.openStream())
        {
            return execute(inputStream);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Unable to read JSON stream.", e);
        }
    }

    private TableColumnar read(InputStream inputStream)
    {
        return read(new InputStreamReader(ArgumentCheck.nonNull(inputStream), StandardCharsets.UTF_8));
    }

    private TableColumnar read(Reader reader)
    {
        return read(new JsonReader(ArgumentCheck.nonNull(reader)));
    }

    private TableColumnar read(JsonReader reader)
    {
        try
        {
            if (JSON.Format.COLUMNAR.equals(this.format))
            {
                return fromJsonColumnar(ArgumentCheck.nonNull(reader));
            }
            return toTableRowOriented(ArgumentCheck.nonNull(reader));
        }
        catch (IOException e)
        {
            throw new RuntimeException("Badly formed JSON.", e);
        }
    }

    private TableColumnar fromJsonColumnar(JsonReader reader) throws IOException
    {
        List<ColumnName> decimalColumns = new ArrayList<>();
        List<ColumnObject.Builder<String>> builders = new ArrayList<>();
        TableName tableName = null;
        TableDescription description = new TableDescription();

        reader.beginObject();
        while (reader.hasNext())
        {
            String name = reader.nextName();
            if (NAME.equals(name))
            {
                tableName = TableName.of(nextStringOrNull(reader));
            }
            else if (DESCRIPTION.equals(name))
            {
                description = description(nextStringOrNull(reader));
            }
            else if (COLUMN_TYPES.equals(name))
            {
                readColumnTypesOrSkip(reader, decimalColumns);
            }
            else if (COLUMNS.equals(name))
            {
                readColumnarColumns(reader, builders);
            }
            else
            {
                reader.skipValue();
            }
        }
        reader.endObject();

        TableColumnar table = Tables.newTable(tableName, description, buildersArray(builders));
        return applyColumnTypeTransformations(table, decimalColumns);
    }

    private void readColumnarColumns(JsonReader reader, List<ColumnObject.Builder<String>> builders) throws IOException
    {
        int columnarLength = -1;
        reader.beginArray();
        while (reader.hasNext())
        {
            reader.beginObject();
            while (reader.hasNext())
            {
                String columnName = reader.nextName();
                if (includes(columnName))
                {
                    ColumnObject.Builder<String> builder = ColumnObject.builder(ColumnName.of(columnName));
                    int size = readColumnarValues(reader, builder);
                    columnarLength = validateColumnarLength(columnName, size, columnarLength);
                    builders.add(builder);
                }
                else
                {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }
        reader.endArray();
    }

    private int readColumnarValues(JsonReader reader, ColumnObject.Builder<String> builder) throws IOException
    {
        int size = 0;
        reader.beginArray();
        while (reader.hasNext())
        {
            builder.add(nextStringOrNull(reader));
            ++size;
        }
        reader.endArray();
        return size;
    }

    private int validateColumnarLength(String columnName, int size, int previousSize)
    {
        if (previousSize < 0)
        {
            return size;
        }
        if (previousSize != size)
        {
            throw new RuntimeException("Expected table with columns of same length, fail on column " + columnName);
        }
        return previousSize;
    }

    private TableColumnar toTableRowOriented(JsonReader reader) throws IOException
    {
        List<ColumnObject.Builder<String>> builders = new ArrayList<>();
        Map<ColumnName, Integer> indexes = new LinkedHashMap<>();
        List<ColumnName> decimalColumns = new ArrayList<>();
        TableName tableName = null;
        TableDescription description = new TableDescription();

        reader.beginObject();
        while (reader.hasNext())
        {
            String name = reader.nextName();
            if (NAME.equals(name))
            {
                tableName = TableName.of(nextStringOrNull(reader));
            }
            else if (DESCRIPTION.equals(name))
            {
                description = description(nextStringOrNull(reader));
            }
            else if (COLUMN_TYPES.equals(name))
            {
                readColumnTypesOrSkip(reader, decimalColumns);
            }
            else if (COLUMNS.equals(name))
            {
                readRowOrientedColumns(reader, builders, indexes);
            }
            else if (ROWS.equals(name))
            {
                readRowOrientedRows(reader, builders, indexes);
            }
            else
            {
                reader.skipValue();
            }
        }
        reader.endObject();

        TableColumnar table = Tables.newTable(tableName, description, buildersArray(builders));
        return applyColumnTypeTransformations(table, decimalColumns);
    }

    private void readColumnTypesOrSkip(JsonReader reader, List<ColumnName> decimalColumns) throws IOException
    {
        if (!this.applyColumnTypes)
        {
            reader.skipValue();
            return;
        }

        reader.beginObject();
        while (reader.hasNext())
        {
            String columnName = reader.nextName();
            String columnType = nextStringOrNull(reader);
            if (includes(columnName) && DECIMAL.equalsIgnoreCase(columnType))
            {
                decimalColumns.add(ColumnName.of(columnName));
            }
        }
        reader.endObject();
    }

    private void readRowOrientedColumns(JsonReader reader, List<ColumnObject.Builder<String>> builders,
            Map<ColumnName, Integer> indexes) throws IOException
    {
        reader.beginArray();
        while (reader.hasNext())
        {
            String columnName = nextStringOrNull(reader);
            if (includes(columnName))
            {
                addBuilder(columnName, builders, indexes);
            }
        }
        reader.endArray();
    }

    private void readRowOrientedRows(JsonReader reader, List<ColumnObject.Builder<String>> builders,
            Map<ColumnName, Integer> indexes) throws IOException
    {
        reader.beginArray();
        while (reader.hasNext())
        {
            readRowOrientedRow(reader, builders, indexes);
        }
        reader.endArray();
    }

    private void readRowOrientedRow(JsonReader reader, List<ColumnObject.Builder<String>> builders,
            Map<ColumnName, Integer> indexes) throws IOException
    {
        String[] rowValues = new String[builders.size()];

        reader.beginObject();
        while (reader.hasNext())
        {
            String columnName = reader.nextName();
            Integer columnIndex = indexes.get(ColumnName.of(columnName));
            if (columnIndex == null)
            {
                reader.skipValue();
            }
            else
            {
                rowValues[columnIndex.intValue()] = nextStringOrNull(reader);
            }
        }
        reader.endObject();

        addRowValues(buildersArray(builders), rowValues);
    }

    private String nextStringOrNull(JsonReader reader) throws IOException
    {
        JsonToken token = reader.peek();
        if (JsonToken.NULL.equals(token))
        {
            reader.nextNull();
            return null;
        }
        if (JsonToken.BOOLEAN.equals(token))
        {
            return Boolean.toString(reader.nextBoolean());
        }
        if (JsonToken.BEGIN_ARRAY.equals(token) || JsonToken.BEGIN_OBJECT.equals(token))
        {
            reader.skipValue();
            return null;
        }
        return reader.nextString();
    }

    private TableDescription description(String description)
    {
        return description == null ? new TableDescription() : new TableDescription(description);
    }

    private boolean includes(String columnName)
    {
        return columnName != null
                && (this.selectedColumns.isEmpty() || this.selectedColumns.contains(ColumnName.of(columnName)));
    }

    private ColumnObject.Builder<String> addBuilder(String columnName, List<ColumnObject.Builder<String>> builders,
            Map<ColumnName, Integer> indexes)
    {
        ColumnName name = ColumnName.of(columnName);
        Integer existingIndex = indexes.get(name);
        if (existingIndex != null)
        {
            return builders.get(existingIndex.intValue());
        }

        ColumnObject.Builder<String> builder = ColumnObject.builder(name);
        indexes.put(name, Integer.valueOf(builders.size()));
        builders.add(builder);
        return builder;
    }

    private ColumnObject.Builder<String>[] buildersArray(List<ColumnObject.Builder<String>> builders)
    {
        @SuppressWarnings("unchecked")
        ColumnObject.Builder<String>[] columnBuilders = builders.toArray(new ColumnObject.Builder[0]);
        return columnBuilders;
    }

    private void addRowValues(ColumnObject.Builder<String>[] columnBuilders, String[] rowValues)
    {
        for (int i = 0; i < columnBuilders.length; ++i)
        {
            columnBuilders[i].add(rowValues[i]);
        }
    }

    private TableColumnar applyColumnTypeTransformations(TableColumnar table, List<ColumnName> decimalColumns)
    {
        if (!this.applyColumnTypes || decimalColumns.isEmpty())
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
}
