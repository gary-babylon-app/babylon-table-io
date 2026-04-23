package app.babylon.table.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.Currency;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.junit.jupiter.api.Test;

import app.babylon.io.StreamSource;
import app.babylon.io.StreamSources;
import app.babylon.table.TableColumnar;
import app.babylon.table.TableName;
import app.babylon.table.column.ColumnName;
import app.babylon.table.column.ColumnObject;
import app.babylon.table.column.ColumnTypes;
import app.babylon.table.plans.TablePlanRead;

class RowSourceExcelTest {

    private static final ColumnName CATEGORY = ColumnName.of("Category");
    private static final ColumnName NOTIONAL = ColumnName.of("Notional");
    private static final ColumnName FREQUENCY = ColumnName.of("Frequency");
    private static final ColumnName AMOUNT = ColumnName.of("Amount");
    private static final ColumnName CURRENCY = ColumnName.of("Currency");
    private static final ColumnName PAYMENT_DATE = ColumnName.of("PaymentDate");
    private static final TableName CASHFLOWS = TableName.of("Cashflows");

    @Test
    void shouldReadTypedCashflowsFromInMemoryWorkbook() throws Exception {
        TableColumnar table = readCashflows(inMemoryWorkbookStreamSource());

        assertCashflows(table);
    }

    @Test
    void shouldReadTypedCashflowsFromClassPathWorkbook() {
        TableColumnar table = readCashflows(StreamSources.fromClass(RowSourceExcelTest.class, "Cashflows.xlsx"));

        assertCashflows(table);
    }

    private static TableColumnar readCashflows(StreamSource streamSource) {
        RowSourceExcel rowSource = RowSourceExcel.builder().withStreamSource(streamSource)
                .withHeaderStrategy(new HeaderStrategyExplicitRow(0))
                .withSpecificSheetName(ColumnName.of(CASHFLOWS.getOriginal())).build();

        TablePlanRead plan = new TablePlanRead().withTableName(CASHFLOWS).withColumnType(CATEGORY, ColumnTypes.STRING)
                .withColumnType(FREQUENCY, ColumnTypes.PERIOD).withColumnTypes(ColumnTypes.DECIMAL, NOTIONAL, AMOUNT)
                .withColumnType(CURRENCY, ColumnTypes.CURRENCY).withColumnType(PAYMENT_DATE, ColumnTypes.LOCALDATE);

        return plan.execute(rowSource);
    }

    private static void assertCashflows(TableColumnar table) {
        assertEquals(CASHFLOWS, table.getName());
        assertEquals(ColumnTypes.STRING, table.getType(CATEGORY));
        assertEquals(ColumnTypes.DECIMAL, table.getType(NOTIONAL));
        assertEquals(ColumnTypes.PERIOD, table.getType(FREQUENCY));
        assertEquals(ColumnTypes.DECIMAL, table.getType(AMOUNT));
        assertEquals(ColumnTypes.CURRENCY, table.getType(CURRENCY));
        assertEquals(ColumnTypes.LOCALDATE, table.getType(PAYMENT_DATE));
        assertEquals(5, table.getRowCount());

        ColumnObject<String> categories = table.getString(CATEGORY);
        ColumnObject<BigDecimal> notionals = table.getDecimal(NOTIONAL);
        ColumnObject<Period> frequencies = table.getObject(FREQUENCY, ColumnTypes.PERIOD);
        ColumnObject<BigDecimal> amounts = table.getDecimal(AMOUNT);
        ColumnObject<Currency> currencies = table.getObject(CURRENCY, ColumnTypes.CURRENCY);
        ColumnObject<LocalDate> paymentDates = table.getObject(PAYMENT_DATE, ColumnTypes.LOCALDATE);

        assertTrue(categories.isConstant());
        assertTrue(notionals.isConstant());
        assertTrue(frequencies.isConstant());
        assertTrue(currencies.isConstant());
        assertFalse(amounts.isConstant());
        assertFalse(paymentDates.isConstant());

        assertEquals("Pay", categories.get(0));
        assertEquals("Pay", categories.get(4));
        assertEquals(0, new BigDecimal("10000000.00").compareTo(notionals.get(0)));
        assertEquals(0, new BigDecimal("10000000.00").compareTo(notionals.get(4)));
        assertEquals(Period.ofYears(1), frequencies.get(0));
        assertEquals(Period.ofYears(1), frequencies.get(4));
        assertEquals(0, new BigDecimal("100000.00").compareTo(amounts.get(0)));
        assertEquals(0, new BigDecimal("150000.00").compareTo(amounts.get(1)));
        assertEquals(0, new BigDecimal("200000.00").compareTo(amounts.get(2)));
        assertEquals(0, new BigDecimal("250000.00").compareTo(amounts.get(3)));
        assertEquals(0, new BigDecimal("300000.00").compareTo(amounts.get(4)));
        assertEquals(Currency.getInstance("GBP"), currencies.get(0));
        assertEquals(LocalDate.of(2027, 1, 1), paymentDates.get(0));
        assertEquals(LocalDate.of(2028, 1, 1), paymentDates.get(1));
        assertEquals(LocalDate.of(2029, 1, 1), paymentDates.get(2));
        assertEquals(LocalDate.of(2030, 1, 1), paymentDates.get(3));
        assertEquals(LocalDate.of(2031, 1, 1), paymentDates.get(4));
    }

    private static StreamSource inMemoryWorkbookStreamSource() throws Exception {
        byte[] workbookBytes = createWorkbookBytes();
        return new StreamSource() {
            @Override
            public String getName() {
                return "cashflows.xlsx";
            }

            @Override
            public InputStream openStream() {
                return new ByteArrayInputStream(workbookBytes);
            }
        };
    }

    private static byte[] createWorkbookBytes() throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                Workbook workbook = new Workbook(outputStream, "babylon-table-io", "1.2026")) {
            Worksheet sheet = workbook.newWorksheet("Cashflows");
            sheet.value(0, 0, "Category");
            sheet.value(0, 1, "Notional");
            sheet.value(0, 2, "Frequency");
            sheet.value(0, 3, "Amount");
            sheet.value(0, 4, "Currency");
            sheet.value(0, 5, "PaymentDate");

            writeCashflowRow(sheet, 1, new BigDecimal("10000000.00"), new BigDecimal("100000.00"),
                    LocalDate.of(2027, 1, 1));
            writeCashflowRow(sheet, 2, new BigDecimal("10000000.00"), new BigDecimal("150000.00"),
                    LocalDate.of(2028, 1, 1));
            writeCashflowRow(sheet, 3, new BigDecimal("10000000.00"), new BigDecimal("200000.00"),
                    LocalDate.of(2029, 1, 1));
            writeCashflowRow(sheet, 4, new BigDecimal("10000000.00"), new BigDecimal("250000.00"),
                    LocalDate.of(2030, 1, 1));
            writeCashflowRow(sheet, 5, new BigDecimal("10000000.00"), new BigDecimal("300000.00"),
                    LocalDate.of(2031, 1, 1));

            workbook.finish();
            return outputStream.toByteArray();
        }
    }

    private static void writeCashflowRow(Worksheet sheet, int rowIndex, BigDecimal notional, BigDecimal amount,
            LocalDate paymentDate) {
        sheet.value(rowIndex, 0, "Pay");
        sheet.value(rowIndex, 1, notional);
        sheet.value(rowIndex, 2, "1Y");
        sheet.value(rowIndex, 3, amount);
        sheet.value(rowIndex, 4, "GBP");
        sheet.value(rowIndex, 5, paymentDate);
    }
}
