package app.babylon.table.io.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class RecordWriterPdfTest
{
    @Test
    void toBytesWritesOnePagePerRecordWithMetadataHeaderFooterAndBodyFields() throws IOException
    {
        TableColumnar records = records();
        TableColumnar header = oneValueTable("InvoiceNumber", "#1001");
        TableColumnar footer = oneValueTable("PdfCreatedBy", "Babylon Financial Technology @ 2026-05-23T15:00:00Z");

        byte[] bytes = RecordWriterPdf.toBytes(records, header, footer);

        try (PDDocument document = Loader.loadPDF(bytes))
        {
            String text = new PDFTextStripper().getText(document);

            assertEquals(2, document.getNumberOfPages());
            assertEquals("Broker records", document.getDocumentInformation().getTitle());
            assertEquals("Records converted from broker mail.", document.getDocumentInformation().getSubject());
            assertEquals("#1001", document.getDocumentInformation().getCustomMetadataValue("DocumentId"));
            assertEquals("Babylon Financial Technology",
                    document.getDocumentInformation().getCustomMetadataValue("Company"));

            assertTrue(text.contains("Broker records row 1"));
            assertTrue(text.contains("Broker records row 2"));
            assertTrue(text.contains("Account: GIA"));
            assertTrue(text.contains("Account: TFSA"));
            assertTrue(text.contains("Amount: 12.34"));
            assertTrue(text.contains("Amount: 56.78"));
            assertTrue(text.contains("Invoice Number: #1001"));
            assertTrue(text.contains("Pdf Created By: Babylon Financial Technology @ 2026-05-23T15:00:00Z"));
            assertFalse(text.contains("InvoiceNumber: #1001"));
            assertFalse(text.contains("PdfCreatedBy: Babylon Financial Technology"));
        }
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
}
