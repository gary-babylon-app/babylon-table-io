package app.babylon.table.io.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkedContentReference;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDMarkedContent;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.StandardStructureTypes;
import org.apache.pdfbox.text.PDFMarkedContentExtractor;
import org.apache.pdfbox.text.TextPosition;

import app.babylon.io.DataResource;
import app.babylon.lang.ArgumentCheck;
import app.babylon.table.TableColumnar;
import app.babylon.table.TableDescription;
import app.babylon.table.TableName;
import app.babylon.table.Tables;
import app.babylon.table.column.ColumnName;
import app.babylon.table.column.ColumnObject;
import app.babylon.text.Strings;

public final class TableExtractorTaggedPdf
{
    public Collection<TableColumnar> extract(DataResource dataResource)
    {
        DataResource checked = ArgumentCheck.nonNull(dataResource);
        try (InputStream inputStream = checked.openStream())
        {
            return extract(inputStream);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Unable to read tagged PDF '" + checked.getName() + "'.", e);
        }
    }

    public Collection<TableColumnar> extract(InputStream inputStream)
    {
        try (PDDocument document = Loader.loadPDF(ArgumentCheck.nonNull(inputStream).readAllBytes()))
        {
            return extract(document);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Unable to read tagged PDF stream.", e);
        }
    }

    private Collection<TableColumnar> extract(PDDocument document) throws IOException
    {
        PDStructureTreeRoot root = ArgumentCheck.nonNull(document).getDocumentCatalog().getStructureTreeRoot();
        if (root == null)
        {
            return List.of();
        }

        TableCollector collector = new TableCollector(new TextByPage(document));
        collector.collect(root.getKids(), null);
        return collector.tables();
    }

    private static final class TableCollector
    {
        private final TextByPage textByPage;
        private final List<TableColumnar> tables = new ArrayList<>();
        private final Set<TableName> tableNames = new LinkedHashSet<>();
        private int unnamedTableCount;

        TableCollector(TextByPage textByPage)
        {
            this.textByPage = ArgumentCheck.nonNull(textByPage);
            this.unnamedTableCount = 0;
        }

        void collect(List<Object> kids, String sectionTitle) throws IOException
        {
            for (Object kid : kids)
            {
                if (kid instanceof PDStructureElement element)
                {
                    String childSectionTitle = sectionTitle;
                    if (StandardStructureTypes.SECT.equals(element.getStandardStructureType()))
                    {
                        childSectionTitle = title(element);
                    }
                    if (StandardStructureTypes.TABLE.equals(element.getStandardStructureType()))
                    {
                        TableColumnar table = table(element, childSectionTitle);
                        if (table != null)
                        {
                            this.tables.add(table);
                        }
                    }
                    collect(element.getKids(), childSectionTitle);
                }
            }
        }

        Collection<TableColumnar> tables()
        {
            return List.copyOf(this.tables);
        }

        private TableColumnar table(PDStructureElement tableElement, String sectionTitle) throws IOException
        {
            List<String> headers = tableHeaders(tableElement);
            List<List<String>> rows = bodyRows(tableElement);
            int columnCount = columnCount(headers, rows);
            if (columnCount == 0)
            {
                return null;
            }

            List<ColumnObject.Builder<String>> builders = columnBuilders(headers, columnCount);
            for (List<String> row : rows)
            {
                for (int i = 0; i < builders.size(); ++i)
                {
                    if (i < row.size())
                    {
                        builders.get(i).add(row.get(i));
                    }
                    else
                    {
                        builders.get(i).addNull();
                    }
                }
            }
            return Tables.newTable(tableName(tableElement, sectionTitle),
                    descriptionFromBuilders(builders, rows.size()), builders.toArray(ColumnObject.Builder[]::new));
        }

        private TableName tableName(PDStructureElement tableElement, String sectionTitle)
        {
            String title = title(tableElement);
            String base = title == null ? sectionTitle : title;
            if (base == null)
            {
                base = "Table" + ++this.unnamedTableCount;
            }
            return uniqueTableName(base);
        }

        private TableName uniqueTableName(String base)
        {
            TableName candidate = TableName.of(base);
            if (this.tableNames.add(candidate))
            {
                return candidate;
            }

            for (int suffix = 2;; ++suffix)
            {
                candidate = TableName.of(base + suffix);
                if (this.tableNames.add(candidate))
                {
                    return candidate;
                }
            }
        }

        private List<ColumnObject.Builder<String>> columnBuilders(List<String> headers, int columnCount)
        {
            List<ColumnObject.Builder<String>> builders = new ArrayList<>();
            Set<ColumnName> names = new LinkedHashSet<>();
            for (int i = 0; i < columnCount; ++i)
            {
                String header = i < headers.size() ? headers.get(i) : null;
                ColumnName name = uniqueColumnName(header == null || header.isBlank() ? "Column" + (i + 1) : header,
                        names);
                builders.add(ColumnObject.builder(name));
            }
            return builders;
        }

        private static TableDescription descriptionFromBuilders(List<ColumnObject.Builder<String>> builders,
                int rowCount)
        {
            List<ColumnName> columnNames = new ArrayList<>();
            for (ColumnObject.Builder<String> builder : builders)
            {
                columnNames.add(builder.getName());
            }
            return description(columnNames, rowCount);
        }

        private static TableDescription description(List<ColumnName> columnNames, int rowCount)
        {
            if (columnNames.isEmpty())
            {
                return new TableDescription("No data.");
            }

            String columns = join(columnNames);
            if (rowCount == 0)
            {
                return new TableDescription("No data. Columns: " + columns + ".");
            }
            if (rowCount == 1)
            {
                return new TableDescription("Fields: " + columns + ".");
            }
            return new TableDescription("Columns: " + columns + ".");
        }

        private static String join(List<ColumnName> columnNames)
        {
            if (columnNames.size() == 1)
            {
                return columnNames.get(0).getValue();
            }
            if (columnNames.size() == 2)
            {
                return columnNames.get(0).getValue() + " and " + columnNames.get(1).getValue();
            }

            StringBuilder out = new StringBuilder();
            for (int i = 0; i < columnNames.size(); ++i)
            {
                if (i > 0)
                {
                    out.append(i + 1 == columnNames.size() ? " and " : ", ");
                }
                out.append(columnNames.get(i).getValue());
            }
            return out.toString();
        }

        private static ColumnName uniqueColumnName(String base, Set<ColumnName> names)
        {
            ColumnName candidate = ColumnName.of(base);
            if (names.add(candidate))
            {
                return candidate;
            }

            for (int suffix = 2;; ++suffix)
            {
                candidate = ColumnName.of(base + suffix);
                if (names.add(candidate))
                {
                    return candidate;
                }
            }
        }

        private List<String> tableHeaders(PDStructureElement table) throws IOException
        {
            List<String> headers = new ArrayList<>();
            PDStructureElement head = firstChild(table, StandardStructureTypes.T_HEAD);
            List<PDStructureElement> rows = head == null ? directRows(table) : directRows(head);
            for (PDStructureElement row : rows)
            {
                headers.addAll(cellTexts(row, StandardStructureTypes.TH));
            }
            return headers;
        }

        private List<List<String>> bodyRows(PDStructureElement table) throws IOException
        {
            List<List<String>> rows = new ArrayList<>();
            PDStructureElement body = firstChild(table, StandardStructureTypes.T_BODY);
            List<PDStructureElement> sourceRows = body == null ? directRows(table) : directRows(body);
            for (PDStructureElement row : sourceRows)
            {
                List<String> values = cellTexts(row, StandardStructureTypes.TD);
                if (!values.isEmpty())
                {
                    rows.add(values);
                }
            }
            return rows;
        }

        private List<String> cellTexts(PDStructureElement row, String cellType) throws IOException
        {
            List<String> texts = new ArrayList<>();
            for (Object kid : row.getKids())
            {
                if (kid instanceof PDStructureElement cell && cellType.equals(cell.getStandardStructureType()))
                {
                    String text = this.textByPage.text(cell).trim();
                    if (StandardStructureTypes.TH.equals(cellType))
                    {
                        int strippedEnd = Strings.stripEnd(text, 0, text.length(), ":");
                        text = strippedEnd == text.length() ? text : text.substring(0, strippedEnd);
                    }
                    texts.add(text);
                }
            }
            return texts;
        }

        private static int columnCount(List<String> headers, List<List<String>> rows)
        {
            int columnCount = headers.size();
            for (List<String> row : rows)
            {
                columnCount = Math.max(columnCount, row.size());
            }
            return columnCount;
        }

        private static PDStructureElement firstChild(PDStructureElement element, String type)
        {
            for (Object kid : element.getKids())
            {
                if (kid instanceof PDStructureElement child && type.equals(child.getStandardStructureType()))
                {
                    return child;
                }
            }
            return null;
        }

        private static List<PDStructureElement> directRows(PDStructureElement element)
        {
            List<PDStructureElement> rows = new ArrayList<>();
            for (Object kid : element.getKids())
            {
                if (kid instanceof PDStructureElement child
                        && StandardStructureTypes.TR.equals(child.getStandardStructureType()))
                {
                    rows.add(child);
                }
            }
            return rows;
        }

        private static String title(PDStructureElement element)
        {
            String title = element.getTitle();
            return title == null || title.isBlank() ? null : title.trim();
        }
    }

    private static final class TextByPage
    {
        private final Map<COSDictionary, Map<Integer, String>> pageTextByMcid = new java.util.HashMap<>();

        TextByPage(PDDocument document) throws IOException
        {
            for (PDPage page : document.getPages())
            {
                PDFMarkedContentExtractor extractor = new PDFMarkedContentExtractor();
                extractor.processPage(page);

                Map<Integer, String> textByMcid = new java.util.HashMap<>();
                for (PDMarkedContent content : extractor.getMarkedContents())
                {
                    putMarkedContent(textByMcid, content);
                }
                this.pageTextByMcid.put(page.getCOSObject(), textByMcid);
            }
        }

        String text(PDStructureElement element) throws IOException
        {
            StringBuilder out = new StringBuilder();
            appendText(out, element.getPage(), element.getKids());
            return out.toString().replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        }

        private void appendText(StringBuilder out, PDPage page, List<Object> kids) throws IOException
        {
            for (Object kid : kids)
            {
                if (kid instanceof COSInteger mcid)
                {
                    appendMcid(out, page, mcid.intValue());
                }
                else if (kid instanceof Number mcid)
                {
                    appendMcid(out, page, mcid.intValue());
                }
                else if (kid instanceof PDMarkedContentReference reference)
                {
                    appendMcid(out, reference.getPage(), reference.getMCID());
                }
                else if (kid instanceof PDStructureElement element)
                {
                    appendText(out, element.getPage() == null ? page : element.getPage(), element.getKids());
                }
            }
        }

        private void appendMcid(StringBuilder out, PDPage page, int mcid)
        {
            String text = page == null
                    ? null
                    : this.pageTextByMcid.getOrDefault(page.getCOSObject(), Map.of()).get(mcid);
            if (text == null || text.isBlank())
            {
                return;
            }
            if (!out.isEmpty())
            {
                out.append(' ');
            }
            out.append(text);
        }

        private static void putMarkedContent(Map<Integer, String> textByMcid, PDMarkedContent content)
        {
            String text = markedContentText(content).trim();
            if (!text.isEmpty() && content.getMCID() >= 0)
            {
                textByMcid.put(content.getMCID(), text);
            }
            for (Object value : content.getContents())
            {
                if (value instanceof PDMarkedContent nested)
                {
                    putMarkedContent(textByMcid, nested);
                }
            }
        }

        private static String markedContentText(PDMarkedContent content)
        {
            TextCollector collector = new TextCollector();
            collector.append(content);
            return collector.toString();
        }

        private static final class TextCollector
        {
            private final StringBuilder out = new StringBuilder();
            private TextPosition previous;

            void append(PDMarkedContent content)
            {
                for (Object value : content.getContents())
                {
                    if (value instanceof TextPosition text)
                    {
                        append(text);
                    }
                    else if (value instanceof PDMarkedContent nested)
                    {
                        append(nested);
                    }
                }
            }

            private void append(TextPosition text)
            {
                if (this.previous != null && startsNewTextRun(text))
                {
                    this.out.append(' ');
                }
                this.out.append(text.getUnicode());
                this.previous = text;
            }

            private boolean startsNewTextRun(TextPosition text)
            {
                float yDelta = Math.abs(text.getYDirAdj() - this.previous.getYDirAdj());
                float xGap = text.getXDirAdj() - this.previous.getEndX();
                return yDelta > Math.max(1.0f, text.getFontSizeInPt() / 2.0f)
                        || xGap > Math.max(1.0f, text.getWidthOfSpace() / 2.0f);
            }

            @Override
            public String toString()
            {
                return this.out.toString();
            }
        }
    }
}
