package app.babylon.table.io.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import app.babylon.table.TableColumnar;
import app.babylon.table.TableDescription;
import app.babylon.table.TableName;
import app.babylon.table.Tables;
import app.babylon.table.column.ColumnName;
import app.babylon.table.column.ColumnObject;
import app.babylon.table.plans.TablePlanWrite;

class TableSinkPdfTest
{
    private static final ColumnName ACCOUNT = ColumnName.of("Account");
    private static final ColumnName AMOUNT = ColumnName.of("Amount");

    @Test
    void shouldWriteSingleTablePdfSink() throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TableSinkPdf sink = TableSinkPdf.builder().withOutputStream("records.pdf", out).build();

        sink.write(records());

        try (PDDocument document = Loader.loadPDF(out.toByteArray()))
        {
            String text = new PDFTextStripper().getText(document);

            assertEquals("records.pdf", sink.getName());
            assertEquals(2, document.getNumberOfPages());
            assertEquals("Broker records", document.getDocumentInformation().getTitle());
            assertTrue(text.contains("Broker records row 1"));
            assertTrue(text.contains("Account: GIA"));
            assertTrue(text.contains("Amount: 12.34"));
        }
    }

    @Test
    void genericWritePlanCanWriteSelectedColumnsToPdfSink() throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TableSinkPdf sink = TableSinkPdf.of(out);

        new TablePlanWrite().withSelectedColumns(ACCOUNT).withSink(sink).execute(records());

        try (PDDocument document = Loader.loadPDF(out.toByteArray()))
        {
            String text = new PDFTextStripper().getText(document);

            assertTrue(text.contains("Account: GIA"));
            assertFalse(text.contains("Amount: 12.34"));
        }
    }

    @Test
    void directOutputStreamIsNotClosed() throws IOException
    {
        CloseTrackingOutputStream out = new CloseTrackingOutputStream();

        TableSinkPdf.of(out).write(records());

        assertFalse(out.closed);
    }

    private static TableColumnar records()
    {
        ColumnObject.Builder<String> account = ColumnObject.builder(ACCOUNT);
        account.add("GIA");
        account.add("TFSA");
        ColumnObject.Builder<String> amount = ColumnObject.builder(AMOUNT);
        amount.add("12.34");
        amount.add("56.78");
        return Tables.newTable(TableName.of("Broker records"),
                new TableDescription("Records converted from broker mail."), account.build(), amount.build());
    }

    private static final class CloseTrackingOutputStream extends ByteArrayOutputStream
    {
        private boolean closed;

        @Override
        public void close()
        {
            this.closed = true;
        }
    }
}
