package app.babylon.table.io.pdf;

import java.io.IOException;
import java.io.OutputStream;

import app.babylon.lang.ArgumentCheck;
import app.babylon.table.TableColumnar;
import app.babylon.table.io.TableSink;

public final class TableSinkPdf implements TableSink
{
    private static final String DEFAULT_NAME = "pdf-output-stream";

    private final String name;
    private final OutputStream outputStream;

    private TableSinkPdf(Builder builder)
    {
        this.name = ArgumentCheck.nonNull(builder.name, "name");
        this.outputStream = ArgumentCheck.nonNull(builder.outputStream, "outputStream");
    }

    public static TableSinkPdf of(OutputStream outputStream)
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
        return this.name;
    }

    @Override
    public void write(TableColumnar table) throws IOException
    {
        RecordWriterPdf.write(ArgumentCheck.nonNull(table), this.outputStream);
    }

    public static final class Builder
    {
        private String name;
        private OutputStream outputStream;

        private Builder()
        {
            this.name = DEFAULT_NAME;
            this.outputStream = null;
        }

        public Builder withOutputStream(OutputStream outputStream)
        {
            this.outputStream = ArgumentCheck.nonNull(outputStream);
            return this;
        }

        public Builder withOutputStream(String name, OutputStream outputStream)
        {
            this.name = ArgumentCheck.nonNull(name);
            return withOutputStream(outputStream);
        }

        public TableSinkPdf build()
        {
            return new TableSinkPdf(this);
        }
    }
}
