package app.babylon.table.io;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;

import app.babylon.table.column.ColumnName;

public class LineReaderFastExcel implements LineReader
{
    private final ColumnName specificSheetName;
    private final ReadableWorkbook workbook;
    private final Stream<Row> rowStream;
    private final Iterator<Row> rowIterator;
    private final List<Row> replayRows;
    private final RowBuffer current;
    private TableWindow tableWindow;
    private int replayIndex;
    private int previousRowNum;
    private boolean finished;

    public LineReaderFastExcel(BufferedInputStream instream, ColumnName specificSheetName)
    {
        this(instream, specificSheetName, new HeaderStrategyAuto(), Set.of());
    }

    LineReaderFastExcel(BufferedInputStream instream, ColumnName specificSheetName, HeaderStrategy headerStrategy,
            Set<ColumnName> selectedColumns)
    {
        this.specificSheetName = specificSheetName;

        try
        {
            this.workbook = new ReadableWorkbook(instream);
            Sheet sheet = this.workbook.getFirstSheet();
            if (this.specificSheetName != null)
            {
                Map<ColumnName, Sheet> sheets = this.workbook.getSheets()
                        .collect(Collectors.toMap(s -> ColumnName.of(s.getName()), s -> s));
                sheet = sheets.get(this.specificSheetName);
                if (sheet == null)
                {
                    throw new IllegalArgumentException("No sheet found with name " + this.specificSheetName + ".");
                }
            }
            this.rowStream = sheet.openStream();
            this.rowIterator = this.rowStream.iterator();
            this.replayRows = new ArrayList<>();
            this.current = new RowBuffer();
            this.tableWindow = null;
            this.replayIndex = 0;
            this.previousRowNum = Integer.MIN_VALUE;
            this.finished = false;
            detectTableWindow(headerStrategy, selectedColumns);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error reading XLSX file: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean next() throws IOException
    {
        RowBuffer row = this.current;
        row.clear();
        if (this.finished)
        {
            return false;
        }

        Row sourceRow = nextSourceRow();
        if (sourceRow == null)
        {
            this.finished = true;
            return false;
        }
        if (isRowGap(sourceRow) || isEmptyInTableWindow(sourceRow))
        {
            this.finished = true;
            return false;
        }

        for (int col = this.tableWindow.startCol; col <= this.tableWindow.endCol; col++)
        {
            appendCell(row, getCellValue(sourceRow.getOptionalCell(col).orElse(null)));
        }
        this.previousRowNum = sourceRow.getRowNum();
        return true;
    }

    @Override
    public app.babylon.table.io.Row current()
    {
        return this.current;
    }

    private static String getCellValue(Cell cell)
    {
        return cell == null ? "" : cell.getRawValue();
    }

    private void detectTableWindow(HeaderStrategy headerStrategy, Set<ColumnName> selectedColumns)
    {
        ExcelRowStream rowStream = new ExcelRowStream();
        try
        {
            headerStrategy.detect(rowStream, selectedColumns);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to detect Excel header row.", e);
        }

        if (this.replayRows.isEmpty())
        {
            this.finished = true;
            this.tableWindow = new TableWindow(0, -1);
            return;
        }

        Row headerRow = this.replayRows.get(rowStream.getMarkedIndex());
        this.tableWindow = tableWindow(headerRow);
        this.replayIndex = rowStream.getMarkedIndex();
        this.previousRowNum = headerRow.getRowNum() - 1;
    }

    private TableWindow tableWindow(Row headerRow)
    {
        Optional<Cell> firstNonEmptyCell = headerRow.getFirstNonEmptyCell();
        if (firstNonEmptyCell.isEmpty())
        {
            return new TableWindow(0, -1);
        }
        int startCol = firstNonEmptyCell.get().getColumnIndex();
        return new TableWindow(startCol, contiguousEndCol(headerRow, startCol));
    }

    private static int contiguousEndCol(Row row, int startCol)
    {
        int endCol = startCol;
        for (int col = startCol + 1; col < row.getCellCount(); ++col)
        {
            Cell cell = row.getOptionalCell(col).orElse(null);
            if (cell == null || cell.getText().isEmpty())
            {
                break;
            }
            endCol = col;
        }
        return endCol;
    }

    private static void appendCell(RowBuffer row, String value)
    {
        if (value != null)
        {
            for (int i = 0; i < value.length(); ++i)
            {
                row.append(value.charAt(i));
            }
        }
        row.finishField();
    }

    private boolean isRowGap(Row sourceRow)
    {
        return this.previousRowNum != Integer.MIN_VALUE && sourceRow.getRowNum() > this.previousRowNum + 1;
    }

    private boolean isEmptyInTableWindow(Row sourceRow)
    {
        for (int col = this.tableWindow.startCol; col <= this.tableWindow.endCol; col++)
        {
            Cell cell = sourceRow.getOptionalCell(col).orElse(null);
            if (cell != null && !cell.getText().isEmpty())
            {
                return false;
            }
        }
        return true;
    }

    @Override
    public void close() throws IOException
    {
        IOException closeError = null;
        try
        {
            this.rowStream.close();
        }
        catch (RuntimeException e)
        {
            if (e.getCause() instanceof IOException io)
            {
                closeError = io;
            }
            else
            {
                throw e;
            }
        }

        this.workbook.close();
        if (closeError != null)
        {
            throw closeError;
        }
    }

    private Row nextSourceRow()
    {
        if (this.replayIndex < this.replayRows.size())
        {
            return this.replayRows.get(this.replayIndex++);
        }
        if (this.rowIterator.hasNext())
        {
            return this.rowIterator.next();
        }
        return null;
    }

    private record TableWindow(int startCol, int endCol)
    {
    }

    private final class ExcelRowStream implements RowStreamMarkable
    {
        private int index;
        private int markedIndex;
        private RowBuffer current;

        private ExcelRowStream()
        {
            this.index = -1;
            this.markedIndex = 0;
            this.current = null;
        }

        @Override
        public void mark(int index)
        {
            this.markedIndex = index;
        }

        private int getMarkedIndex()
        {
            return markedIndex;
        }

        @Override
        public void reset()
        {
            this.index = -1;
            this.current = null;
        }

        @Override
        public boolean next()
        {
            this.index++;
            Row row = nextHeaderCandidate(this.index);
            if (row == null)
            {
                this.current = null;
                return false;
            }
            this.current = toRowBuffer(row);
            return true;
        }

        @Override
        public app.babylon.table.io.Row current()
        {
            return this.current;
        }

        private Row nextHeaderCandidate(int index)
        {
            if (index < replayRows.size())
            {
                return replayRows.get(index);
            }
            if (!rowIterator.hasNext())
            {
                return null;
            }
            Row row = rowIterator.next();
            replayRows.add(row);
            return row;
        }

        private RowBuffer toRowBuffer(Row sourceRow)
        {
            RowBuffer row = new RowBuffer();
            TableWindow window = tableWindow(sourceRow);
            for (int col = window.startCol; col <= window.endCol; col++)
            {
                appendCell(row, getCellValue(sourceRow.getOptionalCell(col).orElse(null)));
            }
            return row;
        }
    }
}
