package app.babylon.table.io.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.babylon.table.TableColumnar;
import app.babylon.table.TableDescription;
import app.babylon.table.TableName;
import app.babylon.table.Tables;
import app.babylon.table.column.ColumnName;
import app.babylon.table.column.ColumnObject;
class TableExtractorTaggedPdfTest
{
    @Test
    void executeReadsTablesWrittenByRecordWriterPdf()
    {
        byte[] pdf = RecordWriterPdf.toBytes(trade(), header(), footer());

        Collection<TableColumnar> extracted = new TableExtractorTaggedPdf().extract(new ByteArrayInputStream(pdf));
        List<TableColumnar> tables = List.copyOf(extracted);

        assertEquals(List.of("Header", "Trade", "Footer"),
                tables.stream().map(table -> table.getName().getOriginal()).toList());

        assertHeader(tables.get(0));
        assertTrade(tables.get(1));
        assertFooter(tables.get(2));
    }

    private static void assertHeader(TableColumnar table)
    {
        assertEquals(TableName.of("Header"), table.getName());
        assertEquals("Fields: InvoiceNumber and CreatedBy.", table.getDescription().getValue());
        assertEquals(List.of(ColumnName.of("InvoiceNumber"), ColumnName.of("CreatedBy")),
                List.of(table.getColumnNames()));
        assertEquals("#1001", table.getString(ColumnName.of("InvoiceNumber")).get(0));
        assertEquals("Babylon Financial Technology", table.getString(ColumnName.of("CreatedBy")).get(0));
    }

    private static void assertTrade(TableColumnar table)
    {
        assertEquals(TableName.of("Trade"), table.getName());
        assertEquals("Fields: Account, Symbol and Amount.", table.getDescription().getValue());
        assertEquals(List.of(ColumnName.of("Account"), ColumnName.of("Symbol"), ColumnName.of("Amount")),
                List.of(table.getColumnNames()));
        assertEquals("GIA", table.getString(ColumnName.of("Account")).get(0));
        assertEquals("R2040", table.getString(ColumnName.of("Symbol")).get(0));
        assertEquals("12.34", table.getString(ColumnName.of("Amount")).get(0));
    }

    private static void assertFooter(TableColumnar table)
    {
        assertEquals(TableName.of("Footer"), table.getName());
        assertEquals("Fields: PdfCreatedBy.", table.getDescription().getValue());
        assertEquals(List.of(ColumnName.of("PdfCreatedBy")), List.of(table.getColumnNames()));
        assertEquals("Babylon Financial Technology @ 2026-05-23T15:00:00Z",
                table.getString(ColumnName.of("PdfCreatedBy")).get(0));
    }

    private static TableColumnar header()
    {
        return Tables.newTable(TableName.of("Header"), field("InvoiceNumber", "#1001"),
                field("CreatedBy", "Babylon Financial Technology"));
    }

    private static TableColumnar trade()
    {
        return Tables.newTable(TableName.of("Trade"), new TableDescription("A single trade record."),
                field("Account", "GIA"), field("Symbol", "R2040"), field("Amount", "12.34"));
    }

    private static TableColumnar footer()
    {
        return Tables.newTable(TableName.of("Footer"),
                field("PdfCreatedBy", "Babylon Financial Technology @ 2026-05-23T15:00:00Z"));
    }

    private static ColumnObject<String> field(String columnName, String value)
    {
        ColumnObject.Builder<String> builder = ColumnObject.builder(ColumnName.of(columnName));
        builder.add(value);
        return builder.build();
    }
}
