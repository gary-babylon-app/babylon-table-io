package app.babylon.table.io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.babylon.lang.Is;
import app.babylon.table.column.ColumnName;
import app.babylon.table.transform.DateFormatInference;
import app.babylon.text.BigDecimals;
import app.babylon.text.Strings;

public class HeaderStrategyExcelAuto implements HeaderStrategy
{
    private static final int DEFAULT_SCAN_LIMIT = 50;
    private static final int LOOKAHEAD_ROW_COUNT = 3;

    private enum CellType
    {
        TEXT, NUM, DATE, BLANK
    }

    private final int scanLimit;

    public HeaderStrategyExcelAuto()
    {
        this(DEFAULT_SCAN_LIMIT);
    }

    public HeaderStrategyExcelAuto(int scanLimit)
    {
        if (scanLimit < 1)
        {
            throw new IllegalArgumentException("Header scan limit must be at least 1.");
        }
        this.scanLimit = scanLimit;
    }

    @Override
    public int getScanLimit()
    {
        return this.scanLimit;
    }

    @Override
    public HeaderDetection detect(RowStreamMarkable rowStream, Set<ColumnName> selectedColumns) throws IOException
    {
        List<Row> rows = new ArrayList<>();
        while (rows.size() < this.scanLimit && rowStream.next())
        {
            rows.add(rowStream.current().copy());
        }
        if (rows.isEmpty())
        {
            return new HeaderDetection(new ColumnName[0]);
        }

        Candidate candidate = detectCandidate(rows, selectedColumns);
        rowStream.mark(candidate.rowIndex());
        return headerDetection(rows.get(candidate.rowIndex()), candidate.startCol(), candidate.endCol(),
                selectedColumns);
    }

    @Override
    public HeaderDetection detectFoundHeaders(RowStreamMarkable rowStream, Set<ColumnName> selectedColumns)
            throws IOException
    {
        return detect(rowStream, selectedColumns);
    }

    private static Candidate detectCandidate(List<Row> rows, Set<ColumnName> selectedColumns)
    {
        Candidate best = null;
        for (int i = 0; i < rows.size(); i++)
        {
            Row row = rows.get(i);
            Window window = window(row);
            if (window.isEmpty())
            {
                continue;
            }
            double headerScore = headerScore(row, window);
            if (headerScore < 1.0d)
            {
                continue;
            }
            double stabilityScore = stabilityScore(rows, i, window);
            double selectedColumnScore = selectedColumnScore(row, window, selectedColumns);
            double score = 3.0d * headerScore + 4.0d * stabilityScore + selectedColumnScore;
            Candidate candidate = new Candidate(i, window.startCol(), window.endCol(), score);
            if (best == null || candidate.score() > best.score())
            {
                best = candidate;
            }
        }
        return best == null ? fallback(rows) : best;
    }

    private static Candidate fallback(List<Row> rows)
    {
        for (int i = 0; i < rows.size(); i++)
        {
            Window window = window(rows.get(i));
            if (!window.isEmpty())
            {
                return new Candidate(i, window.startCol(), window.endCol(), 0.0d);
            }
        }
        return new Candidate(0, 0, -1, 0.0d);
    }

    private static Window window(Row row)
    {
        int startCol = -1;
        int endCol = -1;
        for (int col = 0; col < row.size(); col++)
        {
            if (isBlankField(row, col))
            {
                if (startCol >= 0)
                {
                    break;
                }
                continue;
            }
            if (startCol < 0)
            {
                startCol = col;
            }
            endCol = col;
        }
        return startCol < 0 ? new Window(0, -1) : new Window(startCol, endCol);
    }

    private static double headerScore(Row row, Window window)
    {
        int width = window.width();
        int nonBlank = 0;
        int textCount = 0;
        int dataLikeCount = 0;
        int lengthSum = 0;
        Set<String> distinct = new HashSet<>();
        for (int col = window.startCol(); col <= window.endCol(); col++)
        {
            CharSequence text = strippedFieldText(row, col);
            CellType type = classify(text);
            if (type == CellType.BLANK)
            {
                continue;
            }
            ++nonBlank;
            lengthSum += text.length();
            distinct.add(text.toString());
            if (type == CellType.TEXT)
            {
                ++textCount;
            }
            else
            {
                ++dataLikeCount;
            }
        }
        if (nonBlank == 0)
        {
            return Double.NEGATIVE_INFINITY;
        }

        double score = 0.0d;
        score += 2.0d * textCount / nonBlank;
        score += distinct.size() / (double) nonBlank;
        score -= 1.5d * dataLikeCount / nonBlank;
        score -= 0.02d * Math.max(0.0d, lengthSum / (double) nonBlank - 20.0d);
        score += Math.min(0.8d, 0.2d * Math.max(0, width - 1));
        return score;
    }

    private static double stabilityScore(List<Row> rows, int headerIndex, Window window)
    {
        int checked = 0;
        double score = 0.0d;
        for (int i = headerIndex + 1; i < rows.size() && checked < LOOKAHEAD_ROW_COUNT; i++)
        {
            Row row = rows.get(i);
            if (isEmpty(row, window))
            {
                continue;
            }
            ++checked;
            Window rowWindow = window(row);
            if (!rowWindow.isEmpty() && rowWindow.startCol() >= window.startCol()
                    && rowWindow.startCol() <= window.endCol())
            {
                score += 0.4d;
            }
            if (!rowWindow.isEmpty() && Math.abs(rowWindow.endCol() - window.endCol()) <= 1)
            {
                score += 0.4d;
            }
            score += dataLikeShare(row, window);
        }
        return checked == 0 ? 0.0d : score / checked;
    }

    private static double dataLikeShare(Row row, Window window)
    {
        int dataLike = 0;
        for (int col = window.startCol(); col <= window.endCol(); col++)
        {
            CellType type = classify(strippedFieldText(row, col));
            if (type == CellType.NUM || type == CellType.DATE)
            {
                ++dataLike;
            }
        }
        return dataLike / (double) Math.max(1, window.width());
    }

    private static double selectedColumnScore(Row row, Window window, Set<ColumnName> selectedColumns)
    {
        if (Is.empty(selectedColumns))
        {
            return 0.0d;
        }
        int matched = 0;
        for (int col = window.startCol(); col <= window.endCol(); col++)
        {
            ColumnName columnName = parseColumnName(row, col);
            if (columnName != null && selectedColumns.contains(columnName))
            {
                ++matched;
            }
        }
        return 2.0d * matched / Math.max(1, selectedColumns.size());
    }

    private static HeaderDetection headerDetection(Row headerRow, int startCol, int endCol,
            Set<ColumnName> selectedColumns)
    {
        if (endCol < startCol)
        {
            return new HeaderDetection(new ColumnName[0]);
        }
        int width = endCol - startCol + 1;
        ColumnName[] columnNames = new ColumnName[width];
        int[] positions = new int[width];
        for (int i = 0; i < width; i++)
        {
            ColumnName columnName = parseColumnName(headerRow, startCol + i);
            columnNames[i] = columnName == null ? ColumnName.of("Column" + (i + 1)) : columnName;
            positions[i] = startCol + i;
        }

        if (Is.empty(selectedColumns))
        {
            return new HeaderDetection(columnNames, false, columnNames, positions);
        }

        List<ColumnName> selectedHeaders = new ArrayList<>();
        List<Integer> selectedPositions = new ArrayList<>();
        for (int i = 0; i < columnNames.length; i++)
        {
            if (selectedColumns.contains(columnNames[i]))
            {
                selectedHeaders.add(columnNames[i]);
                selectedPositions.add(positions[i]);
            }
        }
        return new HeaderDetection(columnNames, false, selectedHeaders.toArray(new ColumnName[0]),
                selectedPositions.stream().mapToInt(Integer::intValue).toArray());
    }

    private static boolean isEmpty(Row row, Window window)
    {
        for (int col = window.startCol(); col <= window.endCol(); col++)
        {
            if (!isBlankField(row, col))
            {
                return false;
            }
        }
        return true;
    }

    private static boolean isBlankField(Row row, int fieldIndex)
    {
        if (fieldIndex < 0 || fieldIndex >= row.size())
        {
            return true;
        }
        int length = row.length(fieldIndex);
        if (length == 0)
        {
            return true;
        }
        return Strings.isStripxEmpty(row, row.start(fieldIndex), length);
    }

    private static ColumnName parseColumnName(Row row, int fieldIndex)
    {
        if (fieldIndex < 0 || fieldIndex >= row.size())
        {
            return null;
        }
        int length = row.length(fieldIndex);
        if (length == 0)
        {
            return null;
        }
        return ColumnName.parse(row, row.start(fieldIndex), length);
    }

    private static CharSequence fieldText(Row row, int fieldIndex)
    {
        if (fieldIndex < 0 || fieldIndex >= row.size())
        {
            return "";
        }
        return row.subSequence(row.start(fieldIndex), row.start(fieldIndex) + row.length(fieldIndex));
    }

    private static CharSequence strippedFieldText(Row row, int fieldIndex)
    {
        return Strings.stripx(fieldText(row, fieldIndex));
    }

    private static CellType classify(CharSequence text)
    {
        if (Strings.isEmpty(text))
        {
            return CellType.BLANK;
        }
        if (DateFormatInference.isLikelyDate(text))
        {
            return CellType.DATE;
        }
        if (BigDecimals.isDecimal(text))
        {
            return CellType.NUM;
        }
        return CellType.TEXT;
    }

    private record Candidate(int rowIndex, int startCol, int endCol, double score)
    {
    }

    private record Window(int startCol, int endCol)
    {
        private boolean isEmpty()
        {
            return this.endCol < this.startCol;
        }

        private int width()
        {
            return isEmpty() ? 0 : this.endCol - this.startCol + 1;
        }
    }
}
