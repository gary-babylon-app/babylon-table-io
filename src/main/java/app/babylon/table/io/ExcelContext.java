package app.babylon.table.io;

import java.util.Locale;

public record ExcelContext(String userName, String companyName, Locale userLocale)
{
    public static final Locale DEFAULT_LOCALE = Locale.US;

    public static ExcelContext defaultContext()
    {
        return new ExcelContext(null, null, DEFAULT_LOCALE);
    }
}
