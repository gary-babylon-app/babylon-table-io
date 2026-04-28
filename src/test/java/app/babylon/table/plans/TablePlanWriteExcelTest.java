package app.babylon.table.plans;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.ReadingOptions;
import org.dhatim.fastexcel.reader.Row;
import org.junit.jupiter.api.Test;

import app.babylon.io.StreamSource;
import app.babylon.table.TableColumnar;
import app.babylon.table.TableName;
import app.babylon.table.Tables;
import app.babylon.table.column.ColumnByte;
import app.babylon.table.column.ColumnDouble;
import app.babylon.table.column.ColumnInt;
import app.babylon.table.column.ColumnLong;
import app.babylon.table.column.ColumnName;
import app.babylon.table.column.ColumnObject;
import app.babylon.table.column.ColumnTypes;
import app.babylon.table.io.RowSourceExcel;
import app.babylon.table.io.SinkStream;

class TablePlanWriteExcelTest
{
    private static final TableName MIXED_TYPES = TableName.of("MixedTypes");
    private static final ColumnName BYTE_VALUE = ColumnName.of("ByteValue");
    private static final ColumnName INT_VALUE = ColumnName.of("IntValue");
    private static final ColumnName LONG_VALUE = ColumnName.of("LongValue");
    private static final ColumnName DOUBLE_VALUE = ColumnName.of("DoubleValue");
    private static final ColumnName DECIMAL_VALUE = ColumnName.of("DecimalValue");
    private static final ColumnName LOCAL_DATE_VALUE = ColumnName.of("LocalDateValue");
    private static final ColumnName STRING_VALUE = ColumnName.of("StringValue");

    @Test
    void shouldRoundTripTypedColumnsThroughExcel()
    {
        TableColumnar table = mixedTypesTable();
        byte[] bytes = writeToExcel(table);

        TableColumnar actual = readFromExcel(bytes);

        assertEquals(table.getRowCount(), actual.getRowCount());
        assertPrimitiveValues(actual);
        assertObjectValues(actual);
        assertCellFormats(bytes);
    }

    private static TableColumnar mixedTypesTable()
    {
        ColumnByte byteValues = ColumnByte.builder(BYTE_VALUE).add((byte) 7).add((byte) -3).build();
        ColumnInt intValues = ColumnInt.builder(INT_VALUE).add(123).add(-456).build();
        ColumnLong longValues = ColumnLong.builder(LONG_VALUE).add(1234567890123L).add(-9876543210L).build();
        ColumnDouble doubleValues = ColumnDouble.builder(DOUBLE_VALUE).add(12.5).add(-0.25).build();
        ColumnObject<BigDecimal> decimalValues = ColumnObject.builderDecimal(DECIMAL_VALUE)
                .add(new BigDecimal("1.2300")).add(new BigDecimal("-45.6000")).build();
        ColumnObject<LocalDate> localDateValues = ColumnObject
                .<LocalDate>builder(LOCAL_DATE_VALUE, ColumnTypes.LOCALDATE).add(LocalDate.of(2026, 4, 26))
                .add(LocalDate.of(2027, 1, 2)).build();
        ColumnObject<String> stringValues = ColumnObject.builder(STRING_VALUE).add("alpha").add("beta").build();

        return Tables.newTable(MIXED_TYPES, byteValues, intValues, longValues, doubleValues, decimalValues,
                localDateValues, stringValues);
    }

    private static byte[] writeToExcel(TableColumnar table)
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SinkStream sink = new SinkStream()
        {
            @Override
            public String getName()
            {
                return "mixed-types.xlsx";
            }

            @Override
            public OutputStream openStream()
            {
                return out;
            }
        };

        new TablePlanWriteExcel().withSink(sink).execute(table);
        return out.toByteArray();
    }

    private static TableColumnar readFromExcel(byte[] bytes)
    {
        RowSourceExcel rowSource = RowSourceExcel.builder().withStreamSource(streamSource(bytes))
                .withSpecificSheetName(ColumnName.of(MIXED_TYPES.getOriginal())).build();

        TablePlanRead plan = new TablePlanRead().withTableName(MIXED_TYPES).withColumnType(BYTE_VALUE, ColumnTypes.BYTE)
                .withColumnType(INT_VALUE, ColumnTypes.INT).withColumnType(LONG_VALUE, ColumnTypes.LONG)
                .withColumnType(DOUBLE_VALUE, ColumnTypes.DOUBLE).withColumnType(DECIMAL_VALUE, ColumnTypes.DECIMAL)
                .withColumnType(LOCAL_DATE_VALUE, ColumnTypes.LOCALDATE)
                .withColumnType(STRING_VALUE, ColumnTypes.STRING);

        return plan.execute(rowSource).select(BYTE_VALUE, INT_VALUE, LONG_VALUE, DOUBLE_VALUE, DECIMAL_VALUE,
                LOCAL_DATE_VALUE, STRING_VALUE);
    }

    private static StreamSource streamSource(byte[] bytes)
    {
        return new StreamSource()
        {
            @Override
            public String getName()
            {
                return "mixed-types.xlsx";
            }

            @Override
            public InputStream openStream()
            {
                return new ByteArrayInputStream(bytes);
            }
        };
    }

    private static void assertPrimitiveValues(TableColumnar actual)
    {
        ColumnByte byteValues = (ColumnByte) actual.get(BYTE_VALUE);
        assertEquals((byte) 7, byteValues.get(0));
        assertEquals((byte) -3, byteValues.get(1));

        ColumnInt intValues = actual.getInt(INT_VALUE);
        assertEquals(123, intValues.get(0));
        assertEquals(-456, intValues.get(1));

        ColumnLong longValues = actual.getLong(LONG_VALUE);
        assertEquals(1234567890123L, longValues.get(0));
        assertEquals(-9876543210L, longValues.get(1));

        ColumnDouble doubleValues = actual.getDouble(DOUBLE_VALUE);
        assertEquals(12.5, doubleValues.get(0));
        assertEquals(-0.25, doubleValues.get(1));
    }

    private static void assertObjectValues(TableColumnar actual)
    {
        ColumnObject<BigDecimal> decimalValues = actual.getDecimal(DECIMAL_VALUE);
        assertEquals(0, new BigDecimal("1.2300").compareTo(decimalValues.get(0)));
        assertEquals(0, new BigDecimal("-45.6000").compareTo(decimalValues.get(1)));

        ColumnObject<LocalDate> localDateValues = actual.getObject(LOCAL_DATE_VALUE, ColumnTypes.LOCALDATE);
        assertEquals(LocalDate.of(2026, 4, 26), localDateValues.get(0));
        assertEquals(LocalDate.of(2027, 1, 2), localDateValues.get(1));

        ColumnObject<String> stringValues = actual.getString(STRING_VALUE);
        assertEquals("alpha", stringValues.get(0));
        assertEquals("beta", stringValues.get(1));
    }

    private static void assertCellFormats(byte[] bytes)
    {
        try (ReadableWorkbook workbook = new ReadableWorkbook(new ByteArrayInputStream(bytes),
                new ReadingOptions(true, false)))
        {
            Row firstDataRow = workbook.getFirstSheet().read().get(2);
            Cell decimalCell = firstDataRow.getCell(5);
            Cell localDateCell = firstDataRow.getCell(6);

            assertEquals("#,##0.0000", decimalCell.getDataFormatString());
            assertEquals("yyyy-mm-dd", localDateCell.getDataFormatString());
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to read workbook formats.", e);
        }
    }
}
