package app.babylon.table.plans;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import app.babylon.table.TableColumnar;
import app.babylon.table.TableDescription;
import app.babylon.table.TableException;
import app.babylon.table.TableName;
import app.babylon.table.Tables;
import app.babylon.table.column.ColumnName;
import app.babylon.table.column.ColumnObject;

class TablePlanSingleRecordPdfTest
{
    @Test
    void shouldWriteHeaderMainAndFooterTables() throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TablePlanSingleRecordPdf plan = new TablePlanSingleRecordPdf().withOutputStream("records.pdf", out);

        plan.execute(oneValueTable("InvoiceNumber", "#1001"), records(),
                oneValueTable("PdfCreatedBy", "Babylon Financial Technology @ 2026-05-23T15:00:00Z"));

        try (PDDocument document = Loader.loadPDF(out.toByteArray()))
        {
            String text = new PDFTextStripper().getText(document);

            assertSame(out, plan.getOutputStream());
            assertEquals("records.pdf", plan.getName());
            assertEquals(2, document.getNumberOfPages());
            assertTrue(text.contains("Broker records row 1"));
            assertTrue(text.contains("Invoice Number: #1001"));
            assertTrue(text.contains("Account: GIA"));
            assertTrue(text.contains("Pdf Created By: Babylon Financial Technology @ 2026-05-23T15:00:00Z"));
        }
    }

    @Test
    void shouldIncludeOutputNameInFailureMessage()
    {
        TablePlanSingleRecordPdf plan = new TablePlanSingleRecordPdf().withOutputStream("records.pdf",
                new ThrowingOutputStream());

        TableException exception = assertThrows(TableException.class, () -> plan.execute(null, records(), null));

        assertTrue(exception.getMessage().contains("records.pdf"));
    }

    private static TableColumnar records()
    {
        ColumnObject.Builder<String> account = ColumnObject.builder(ColumnName.of("Account"));
        account.add("GIA");
        account.add("TFSA");
        ColumnObject.Builder<String> amount = ColumnObject.builder(ColumnName.of("Amount"));
        amount.add("12.34");
        amount.add("56.78");
        return Tables.newTable(TableName.of("Broker records"),
                new TableDescription("Records converted from broker mail."), account.build(), amount.build());
    }

    private static TableColumnar oneValueTable(String columnName, String value)
    {
        ColumnObject.Builder<String> builder = ColumnObject.builder(ColumnName.of(columnName));
        builder.add(value);
        return Tables.newTable(TableName.of("Metadata"), builder.build());
    }

    private static final class ThrowingOutputStream extends OutputStream
    {
        @Override
        public void write(int b) throws IOException
        {
            throw new IOException("closed");
        }
    }
}
