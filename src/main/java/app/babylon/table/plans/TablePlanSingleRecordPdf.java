package app.babylon.table.plans;

import java.io.IOException;
import java.io.OutputStream;

import app.babylon.lang.ArgumentCheck;
import app.babylon.table.TableColumnar;
import app.babylon.table.TableException;
import app.babylon.table.io.pdf.RecordWriterPdf;

public final class TablePlanSingleRecordPdf
{
    private static final String DEFAULT_NAME = "pdf-output-stream";

    private String name;
    private OutputStream outputStream;

    public TablePlanSingleRecordPdf()
    {
        this.name = DEFAULT_NAME;
        this.outputStream = null;
    }

    public TablePlanSingleRecordPdf withOutputStream(OutputStream outputStream)
    {
        this.outputStream = ArgumentCheck.nonNull(outputStream);
        return this;
    }

    public TablePlanSingleRecordPdf withOutputStream(String name, OutputStream outputStream)
    {
        this.name = ArgumentCheck.nonNull(name);
        return withOutputStream(outputStream);
    }

    public String getName()
    {
        return this.name;
    }

    public OutputStream getOutputStream()
    {
        return this.outputStream;
    }

    public void execute(TableColumnar header, TableColumnar main, TableColumnar footer)
    {
        try
        {
            RecordWriterPdf.write(ArgumentCheck.nonNull(main, "main"), header, footer,
                    ArgumentCheck.nonNull(this.outputStream, "outputStream"));
        }
        catch (IOException e)
        {
            throw new TableException("Failed to write single-record PDF to '" + this.name + "'.", e);
        }
    }
}
