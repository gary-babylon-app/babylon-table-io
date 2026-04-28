package app.babylon.table.plans;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

import org.dhatim.fastexcel.Workbook;

import app.babylon.table.TableColumnar;
import app.babylon.table.TableName;
import app.babylon.table.Tables;
import app.babylon.table.io.ExcelContext;
import app.babylon.table.io.SinkStream;
import app.babylon.table.io.Workbooks;

public class TablePlanWriteExcel
{
    private SinkStream sink;
    private ExcelContext context = ExcelContext.defaultContext();

    public TablePlanWriteExcel withSink(SinkStream sink)
    {
        this.sink = sink;
        return this;
    }

    public SinkStream getSink()
    {
        return sink;
    }

    public TablePlanWriteExcel withContext(ExcelContext context)
    {
        this.context = context;
        return this;
    }

    public ExcelContext getContext()
    {
        return context;
    }

    public void execute(TableColumnar table)
    {
        execute(new TableColumnar[]
        {table});
    }

    public void execute(TableColumnar... tables)
    {
        if (sink == null)
        {
            throw new RuntimeException("Missing sink.");
        }
        if (tables == null || tables.length == 0 || tables[0] == null)
        {
            throw new RuntimeException("Missing table.");
        }

        ExcelContext excelContext = resolveContext(context);
        String author = Workbooks.resolveWorkbookAuthor(excelContext);
        try (OutputStream out = sink.openStream())
        {
            Workbook workbook = new Workbook(out, author, "1.0");
            Workbooks.applyWorkbookProperties(workbook, tables[0], excelContext);
            writeTablesToWorkbook(workbook, excelContext, tables);
            workbook.finish();
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to write workbook.", e);
        }
    }

    private static ExcelContext resolveContext(ExcelContext context)
    {
        if (context == null)
        {
            return ExcelContext.defaultContext();
        }
        if (context.userLocale() == null)
        {
            return new ExcelContext(context.userName(), context.companyName(), ExcelContext.DEFAULT_LOCALE);
        }
        return context;
    }

    private static void writeTablesToWorkbook(Workbook workbook, ExcelContext context, TableColumnar... tables)
    {
        Set<String> usedSheetNames = new HashSet<>();
        for (TableColumnar table : tables)
        {
            if (table != null)
            {
                Workbooks.tableToWorkBook(withUniqueSheetName(table, usedSheetNames), workbook, context, 1, 1, false);
            }
        }
    }

    private static TableColumnar withUniqueSheetName(TableColumnar table, Set<String> usedSheetNames)
    {
        String baseName = sanitizeSheetName(table.getName().getOriginal());
        String candidate = baseName;
        int index = 2;
        while (usedSheetNames.contains(candidate))
        {
            String suffix = "-" + index;
            int maxBaseLength = Math.max(1, 31 - suffix.length());
            String truncatedBase = baseName.length() > maxBaseLength ? baseName.substring(0, maxBaseLength) : baseName;
            candidate = truncatedBase + suffix;
            index++;
        }
        usedSheetNames.add(candidate);

        if (candidate.equals(table.getName().getOriginal()))
        {
            return table;
        }
        return Tables.newTable(TableName.of(candidate), table.getDescription(), table.getColumns());
    }

    private static String sanitizeSheetName(String input)
    {
        String name = input == null ? "" : input;
        name = name.replaceAll("[\\\\/:*?\\[\\]]", "_").strip();
        if (name.isEmpty())
        {
            name = "Sheet1";
        }
        if (name.length() > 31)
        {
            name = name.substring(0, 31);
        }
        return name;
    }
}
