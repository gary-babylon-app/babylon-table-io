package app.babylon.table.io;

import java.io.IOException;
import java.io.OutputStream;

import org.dhatim.fastexcel.Workbook;

import app.babylon.lang.ArgumentCheck;
import app.babylon.table.TableColumnar;

public final class TableSinkExcel implements TableSink
{
    private final SinkStream sinkStream;
    private final OutputStream outputStream;
    private final ExcelContext context;

    private TableSinkExcel(Builder builder)
    {
        this.sinkStream = builder.sinkStream;
        this.outputStream = builder.outputStream;
        if (this.sinkStream == null && this.outputStream == null)
        {
            throw new IllegalArgumentException("Expected sinkStream or outputStream.");
        }
        this.context = resolveContext(builder.context);
    }

    public static TableSinkExcel of(SinkStream sinkStream)
    {
        return builder().withSinkStream(sinkStream).build();
    }

    public static TableSinkExcel of(OutputStream outputStream)
    {
        return builder().withOutputStream(outputStream).build();
    }

    public static Builder builder()
    {
        return new Builder();
    }

    @Override
    public String getName()
    {
        return this.sinkStream == null ? "excel-output-stream" : this.sinkStream.getName();
    }

    @Override
    public void write(TableColumnar table) throws IOException
    {
        TableColumnar checkedTable = ArgumentCheck.nonNull(table);
        if (this.outputStream != null)
        {
            writeWorkbook(checkedTable, this.outputStream);
            return;
        }

        try (OutputStream out = this.sinkStream.openStream())
        {
            writeWorkbook(checkedTable, out);
        }
    }

    private void writeWorkbook(TableColumnar table, OutputStream out) throws IOException
    {
        String author = Workbooks.resolveWorkbookAuthor(this.context);
        Workbook workbook = new Workbook(out, author, "1.0");
        Workbooks.applyWorkbookProperties(workbook, table, this.context);
        Workbooks.tableToWorkBook(table, workbook, this.context, 1, 1, false);
        workbook.finish();
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

    public static final class Builder
    {
        private SinkStream sinkStream;
        private OutputStream outputStream;
        private ExcelContext context;

        private Builder()
        {
            this.sinkStream = null;
            this.outputStream = null;
            this.context = ExcelContext.defaultContext();
        }

        public Builder withSinkStream(SinkStream sinkStream)
        {
            this.sinkStream = ArgumentCheck.nonNull(sinkStream);
            this.outputStream = null;
            return this;
        }

        public Builder withOutputStream(OutputStream outputStream)
        {
            this.outputStream = ArgumentCheck.nonNull(outputStream);
            this.sinkStream = null;
            return this;
        }

        public Builder withContext(ExcelContext context)
        {
            this.context = resolveContext(context);
            return this;
        }

        public TableSinkExcel build()
        {
            return new TableSinkExcel(this);
        }
    }
}
