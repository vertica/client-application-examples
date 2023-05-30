using System;
using System.Configuration;
using System.Data.Common;
using Vertica.Data.VerticaClient;

class ResultSetPrinter {

    private static int maxColumnWidth = Int32.Parse (ConfigurationManager.AppSettings["MaxColumnWidth"]); 
    
    public void printResults (VerticaDataReader reader) 
    {        
        var columns = reader.GetColumnSchema();
        printColumnHeader (columns);

        // Write the data
        while (reader.Read())
        {
            for (int i = 0; i < reader.FieldCount; i++)
            {
                int columnWidth = getPrintedColumnWidth (columns[i]);
                string Value = reader.GetValue(i).ToString ();
                Console.Write("{0} ", Value.PadRight (columnWidth));
            }
            Console.WriteLine();
        }
    }
    
    private void printColumnHeader (System.Collections.ObjectModel.ReadOnlyCollection<DbColumn> columns) 
    {
        // Write the column headers
        foreach (DbColumn col in columns)
        {
            int columnWidth = getPrintedColumnWidth (col);
            Console.Write("{0} ", col.ColumnName.PadRight (columnWidth));
        }
        Console.WriteLine();

        // Write a column separator
        foreach (DbColumn col in columns)
        {
            int columnWidth = getPrintedColumnWidth (col);
            Console.Write("{0} ", pad ('-', columnWidth));
        }
        Console.WriteLine();
    }

    private int getPrintedColumnWidth (DbColumn column)
    {
        int width = column.ColumnName.Length > column.ColumnSize ? column.ColumnName.Length : (int)column.ColumnSize;
        if (maxColumnWidth != -1) {
            if (width > maxColumnWidth) return maxColumnWidth;
        }

        return width;
    }

    private string pad (char value, int length)
    {
        string paddedString = "".PadRight (length);
        if (value == ' ')
        {
            return paddedString;
        }
        return paddedString.Replace (' ', value);
    }
}