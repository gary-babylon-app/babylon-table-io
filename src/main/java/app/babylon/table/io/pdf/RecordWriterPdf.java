package app.babylon.table.io.pdf;

import app.babylon.table.TableColumnar;
import app.babylon.table.ToStringSettings;
import app.babylon.table.column.Column;
import app.babylon.table.column.ColumnName;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

public final class RecordWriterPdf
{
    private static final PDRectangle PAGE_SIZE = PDRectangle.A4;
    private static final PDFont LABEL_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont VALUE_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont TITLE_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final float MARGIN = 54.0f;
    private static final float GAP = 12.0f;
    private static final float BODY_GAP = 24.0f;
    private static final float TITLE_FONT_SIZE = 14.0f;
    private static final float DESCRIPTION_FONT_SIZE = 13.0f;
    private static final float FONT_SIZE = 12.0f;
    private static final float LINE_HEIGHT = 14.5f;
    private static final float METADATA_FONT_SIZE = 8.5f;
    private static final float METADATA_LINE_HEIGHT = 12.0f;
    private static final ToStringSettings SETTINGS = ToStringSettings.standard();

    private RecordWriterPdf()
    {
    }

    public static void write(TableColumnar table, Path path)
    {
        write(table, null, path);
    }

    public static void write(TableColumnar table, TableColumnar footer, Path path)
    {
        write(table, null, footer, path);
    }

    public static void write(TableColumnar table, TableColumnar header, TableColumnar footer, Path path)
    {
        try (PDDocument document = create(table, header, footer))
        {
            document.save(path.toFile());
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException("Unable to write table PDF: " + path, e);
        }
    }

    public static void write(TableColumnar table, OutputStream outputStream) throws IOException
    {
        write(table, null, outputStream);
    }

    public static void write(TableColumnar table, TableColumnar footer, OutputStream outputStream) throws IOException
    {
        write(table, null, footer, outputStream);
    }

    public static void write(TableColumnar table, TableColumnar header, TableColumnar footer, OutputStream outputStream)
            throws IOException
    {
        try (PDDocument document = create(table, header, footer))
        {
            document.save(outputStream);
        }
    }

    public static byte[] toBytes(TableColumnar table)
    {
        return toBytes(table, null);
    }

    public static byte[] toBytes(TableColumnar table, TableColumnar footer)
    {
        return toBytes(table, null, footer);
    }

    public static byte[] toBytes(TableColumnar table, TableColumnar header, TableColumnar footer)
    {
        try (PDDocument document = create(table, header, footer);
                ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            document.save(out);
            return out.toByteArray();
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException("Unable to render table PDF", e);
        }
    }

    public static PDDocument create(TableColumnar table)
    {
        return create(table, null);
    }

    public static PDDocument create(TableColumnar table, TableColumnar footer)
    {
        return create(table, null, footer);
    }

    public static PDDocument create(TableColumnar table, TableColumnar header, TableColumnar footer)
    {
        try
        {
            PDDocument document = new PDDocument();
            ZonedDateTime createdAt = ZonedDateTime.now();
            document.getDocumentInformation().setCreationDate(GregorianCalendar.from(createdAt));
            document.getDocumentInformation().setTitle(table.getName().toString());
            if (table.getDescription() != null)
            {
                document.getDocumentInformation().setSubject(clean(table.getDescription().getValue()));
            }
            String company = firstValue(footer, "Company");
            if (company.isEmpty())
            {
                company = firstValue(footer, "Pdf Created By");
            }
            if (!company.isEmpty())
            {
                document.getDocumentInformation().setCustomMetadataValue("Company", companyName(company));
            }
            String documentId = firstValue(header, "Document id");
            if (documentId.isEmpty())
            {
                documentId = firstValue(header, "DocumentId");
            }
            if (documentId.isEmpty())
            {
                documentId = firstValue(header, "Invoice Number");
            }
            if (documentId.isEmpty())
            {
                documentId = firstValue(header, "InvoiceNumber");
            }
            if (documentId.isEmpty())
            {
                documentId = firstValue(footer, "Document id");
            }
            if (documentId.isEmpty())
            {
                documentId = firstValue(footer, "DocumentId");
            }
            if (!documentId.isEmpty())
            {
                document.getDocumentInformation().setCustomMetadataValue("DocumentId", documentId);
            }
            for (int row = 0; row < table.getRowCount(); ++row)
            {
                addRowPage(document, table, header, footer, row);
            }
            return document;
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException("Unable to render table PDF", e);
        }
    }

    private static void addRowPage(PDDocument document, TableColumnar table, TableColumnar header, TableColumnar footer,
            int row) throws IOException
    {
        PDPage page = new PDPage(PAGE_SIZE);
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page))
        {
            float y = PAGE_SIZE.getHeight() - MARGIN;
            int footerLineCount = footerLineCount(footer, row);
            float footerTop = MARGIN + METADATA_LINE_HEIGHT * (footerLineCount + 2.0f);
            y = drawHeader(content, header, row, y);
            String title = table.getName() + (table.getRowCount() == 1 ? "" : " row " + (row + 1));
            drawCenteredText(content, TITLE_FONT, TITLE_FONT_SIZE, y, title);
            y -= LINE_HEIGHT * 1.8f;
            String description = table.getDescription() == null ? "" : clean(table.getDescription().getValue());
            if (!description.isEmpty())
            {
                float lastDescriptionY = y;
                for (String line : wrap(description, VALUE_FONT, DESCRIPTION_FONT_SIZE,
                        PAGE_SIZE.getWidth() - 2.0f * MARGIN))
                {
                    lastDescriptionY = y;
                    drawCenteredText(content, VALUE_FONT, DESCRIPTION_FONT_SIZE, y, line);
                    y -= LINE_HEIGHT * 1.2f;
                }
                float separatorY = lastDescriptionY - LINE_HEIGHT * 0.55f;
                drawSeparator(content, separatorY);
                y = separatorY - LINE_HEIGHT * 6.2f;
            }

            ColumnName[] columnNames = table.getColumnNames();
            Column[] columns = table.getColumns();
            Set<ColumnName> skippedColumnNames = skippedColumnNames(header, footer);
            float valueLeft = valueLeft(columnNames, skippedColumnNames);
            for (int i = 0; i < columns.length; ++i)
            {
                if (skippedColumnNames.contains(columnNames[i]))
                {
                    continue;
                }
                if (y < footerTop + LINE_HEIGHT)
                {
                    break;
                }
                y = drawKeyValue(content, columnNames[i].getValue(), columns[i].toString(row, SETTINGS), valueLeft, y);
            }
            drawFooter(content, footer, row);
        }
    }

    private static Set<ColumnName> skippedColumnNames(TableColumnar header, TableColumnar footer)
    {
        Set<ColumnName> result = new HashSet<>();
        addColumnNames(result, header);
        addColumnNames(result, footer);
        return result;
    }

    private static void addColumnNames(Set<ColumnName> result, TableColumnar table)
    {
        if (table == null)
        {
            return;
        }
        for (ColumnName columnName : table.getColumnNames())
        {
            result.add(columnName);
        }
    }

    private static float drawHeader(PDPageContentStream content, TableColumnar header, int row, float y)
            throws IOException
    {
        if (header == null || header.getRowCount() == 0)
        {
            return y;
        }
        int headerRow = Math.min(row, header.getRowCount() - 1);
        ColumnName[] columnNames = header.getColumnNames();
        Column[] columns = header.getColumns();
        float maxLabelWidth = 0.0f;
        float maxValueWidth = 0.0f;
        for (int i = 0; i < columns.length; ++i)
        {
            String value = clean(columns[i].toString(headerRow, SETTINGS));
            if (!value.isEmpty())
            {
                maxLabelWidth = Math.max(maxLabelWidth,
                        textWidth(VALUE_FONT, METADATA_FONT_SIZE, label(headerLabel(columnNames[i].getValue()))));
                maxValueWidth = Math.max(maxValueWidth, textWidth(VALUE_FONT, METADATA_FONT_SIZE, value));
            }
        }
        float valueX = PAGE_SIZE.getWidth() - MARGIN - maxValueWidth;
        float labelRight = valueX - GAP;
        for (int i = 0; i < columns.length; ++i)
        {
            String value = clean(columns[i].toString(headerRow, SETTINGS));
            if (!value.isEmpty())
            {
                String label = label(headerLabel(columnNames[i].getValue()));
                float labelX = labelRight - textWidth(VALUE_FONT, METADATA_FONT_SIZE, label);
                drawText(content, VALUE_FONT, METADATA_FONT_SIZE, Math.max(MARGIN, labelX), y, label);
                drawText(content, VALUE_FONT, METADATA_FONT_SIZE, valueX, y, value);
                y -= METADATA_LINE_HEIGHT;
            }
        }
        float separatorY = y + METADATA_LINE_HEIGHT * 0.45f;
        drawSeparator(content, separatorY);
        return separatorY - METADATA_LINE_HEIGHT * 1.6f;
    }

    private static void drawFooter(PDPageContentStream content, TableColumnar footer, int row) throws IOException
    {
        List<String> lines = footerLines(footer, row);
        if (lines.isEmpty())
        {
            return;
        }
        drawSeparator(content, MARGIN + METADATA_LINE_HEIGHT * (lines.size() + 0.75f));
        float y = MARGIN + METADATA_LINE_HEIGHT * (lines.size() - 1);
        for (String line : lines)
        {
            drawText(content, VALUE_FONT, METADATA_FONT_SIZE, MARGIN, y, line);
            y -= METADATA_LINE_HEIGHT;
        }
    }

    private static void drawSeparator(PDPageContentStream content, float y) throws IOException
    {
        content.saveGraphicsState();
        content.setLineWidth(0.5f);
        content.setStrokingColor(0.75f);
        content.moveTo(MARGIN, y);
        content.lineTo(PAGE_SIZE.getWidth() - MARGIN, y);
        content.stroke();
        content.restoreGraphicsState();
    }

    private static int footerLineCount(TableColumnar footer, int row) throws IOException
    {
        return footerLines(footer, row).size();
    }

    private static List<String> footerLines(TableColumnar footer, int row) throws IOException
    {
        return metadataLines(footer, row);
    }

    private static List<String> metadataLines(TableColumnar table, int row) throws IOException
    {
        List<String> lines = new ArrayList<>();
        if (table != null)
        {
            if (table.getRowCount() == 0)
            {
                return lines;
            }
        }
        else
        {
            return lines;
        }
        int tableRow = Math.min(row, table.getRowCount() - 1);
        float width = PAGE_SIZE.getWidth() - 2.0f * MARGIN;
        ColumnName[] columnNames = table.getColumnNames();
        Column[] columns = table.getColumns();
        for (int i = 0; i < columns.length; ++i)
        {
            String value = clean(columns[i].toString(tableRow, SETTINGS));
            if (!value.isEmpty())
            {
                lines.addAll(wrap(label(footerLabel(columnNames[i].getValue())) + " " + value, VALUE_FONT,
                        METADATA_FONT_SIZE, width));
            }
        }
        return lines;
    }

    private static float valueLeft(ColumnName[] columnNames, Set<ColumnName> skippedColumns) throws IOException
    {
        float labelWidth = 0.0f;
        for (ColumnName columnName : columnNames)
        {
            if (!skippedColumns.contains(columnName))
            {
                labelWidth = Math.max(labelWidth, textWidth(LABEL_FONT, FONT_SIZE, label(columnName.getValue())));
            }
        }
        return MARGIN + labelWidth + BODY_GAP;
    }

    private static float drawKeyValue(PDPageContentStream content, String label, String value, float valueLeft, float y)
            throws IOException
    {
        float valueRight = PAGE_SIZE.getWidth() - MARGIN;
        float valueWidth = valueRight - valueLeft;
        List<String> lines = wrap(clean(value), VALUE_FONT, FONT_SIZE, valueWidth);

        drawText(content, LABEL_FONT, FONT_SIZE, MARGIN, y, label(label));
        for (int i = 0; i < lines.size(); ++i)
        {
            drawText(content, VALUE_FONT, FONT_SIZE, valueLeft, y - (i * LINE_HEIGHT), lines.get(i));
        }
        return y - Math.max(1, lines.size()) * LINE_HEIGHT;
    }

    private static List<String> wrap(String value, PDFont font, float fontSize, float maxWidth) throws IOException
    {
        List<String> lines = new ArrayList<>();
        if (value.isEmpty())
        {
            lines.add("");
            return lines;
        }

        String[] words = value.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words)
        {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (textWidth(font, fontSize, candidate) <= maxWidth)
            {
                line.setLength(0);
                line.append(candidate);
            }
            else
            {
                if (!line.isEmpty())
                {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                if (textWidth(font, fontSize, word) <= maxWidth)
                {
                    line.append(word);
                }
                else
                {
                    lines.addAll(splitLongWord(word, font, fontSize, maxWidth));
                }
            }
        }
        if (!line.isEmpty())
        {
            lines.add(line.toString());
        }
        return lines;
    }

    private static List<String> splitLongWord(String word, PDFont font, float fontSize, float maxWidth)
            throws IOException
    {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < word.length(); ++i)
        {
            String candidate = line.toString() + word.charAt(i);
            if (!line.isEmpty() && textWidth(font, fontSize, candidate) > maxWidth)
            {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(word.charAt(i));
        }
        if (!line.isEmpty())
        {
            lines.add(line.toString());
        }
        return lines;
    }

    private static void drawText(PDPageContentStream content, PDFont font, float fontSize, float x, float y,
            String text) throws IOException
    {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    private static void drawCenteredText(PDPageContentStream content, PDFont font, float fontSize, float y, String text)
            throws IOException
    {
        float width = textWidth(font, fontSize, text);
        drawText(content, font, fontSize, (PAGE_SIZE.getWidth() - width) / 2.0f, y, text);
    }

    private static float textWidth(PDFont font, float fontSize, String text) throws IOException
    {
        return font.getStringWidth(text) / 1000.0f * fontSize;
    }

    private static String firstValue(TableColumnar table, String name)
    {
        if (table == null || table.getRowCount() == 0)
        {
            return "";
        }
        ColumnName columnName = ColumnName.of(name);
        if (!table.contains(columnName))
        {
            return "";
        }
        return clean(table.get(columnName).toString(0, SETTINGS));
    }

    private static String companyName(String value)
    {
        int index = value.indexOf('@');
        if (index < 0)
        {
            return value;
        }
        return clean(value.substring(0, index)).strip();
    }

    private static String clean(String text)
    {
        return text == null ? "" : text.replace('\n', ' ').replace('\r', ' ');
    }

    private static String footerLabel(String text)
    {
        String cleanText = clean(text);
        StringBuilder builder = new StringBuilder(cleanText.length() + 4);
        for (int i = 0; i < cleanText.length(); ++i)
        {
            char c = cleanText.charAt(i);
            if (i > 0 && Character.isUpperCase(c)
                    && (Character.isLowerCase(cleanText.charAt(i - 1)) || Character.isDigit(cleanText.charAt(i - 1))))
            {
                builder.append(' ');
                builder.append(Character.toLowerCase(c));
            }
            else
            {
                builder.append(c);
            }
        }
        return titleCaseFooterLabel(builder.toString());
    }

    private static String titleCaseFooterLabel(String text)
    {
        String[] words = text.split(" ");
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < words.length; ++i)
        {
            if (i > 0)
            {
                builder.append(' ');
            }
            String word = words[i];
            if ("at".equals(word))
            {
                builder.append(word);
            }
            else if (!word.isEmpty())
            {
                builder.append(Character.toUpperCase(word.charAt(0)));
                builder.append(word.substring(1));
            }
        }
        return builder.toString();
    }

    private static String headerLabel(String text)
    {
        String label = footerLabel(text);
        StringBuilder builder = new StringBuilder(label.length());
        boolean startOfWord = true;
        for (int i = 0; i < label.length(); ++i)
        {
            char c = label.charAt(i);
            if (Character.isWhitespace(c))
            {
                startOfWord = true;
                builder.append(c);
            }
            else if (startOfWord)
            {
                builder.append(Character.toUpperCase(c));
                startOfWord = false;
            }
            else
            {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static String label(String text)
    {
        return clean(text) + ":";
    }
}
