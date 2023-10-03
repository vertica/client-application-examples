using System;
using System.Configuration;
using System.Data.Common;
using Vertica.Data.VerticaClient;
using System.Collections.ObjectModel;
using System.Linq;

internal class ResultSetPrinter {
        private const string TRUNCATION_SYMBOL = "...";

        private int  m_maxColumnWidth   = Int32.Parse(ConfigurationManager.AppSettings["MaxColumnWidth"]);
        private int  m_widestColumnRead = 0;
        private bool m_ValuesTruncated  = false;
        private VerticaDataReader m_reader;
        private ReadOnlyCollection<DbColumn> m_columns;

        public ResultSetPrinter(VerticaDataReader reader) {
            m_reader = reader;
            m_columns = m_reader.GetColumnSchema();
        }

        public void printResults() 
        {
            printColumnHeaders();

            // Write the data
            while (m_reader.Read()) {
                for (int i = 0; i < m_reader.FieldCount; i++) {
                    string Value = m_reader.GetValue(i).ToString();
                    Console.Write("{0} ", formatColumnValue(m_columns[i], Value));
                }
                Console.WriteLine();
            }

            if (m_ValuesTruncated) {
                Console.WriteLine();
                Console.Write("Warning: Some data values have been truncated.  Truncated values end in '{0}'. Increase MaxColumnWidth to {1} to eliminate truncation with this data.", TRUNCATION_SYMBOL, m_widestColumnRead);
                Console.WriteLine();
            }
        }
        
        private void printColumnHeaders() 
        {
            printColumnSeparator();

            // Write the column headers
            foreach (DbColumn column in m_columns) {
                int columnWidth = getPrintedColumnWidth(column);
                checkColumnWidth(columnWidth);
                Console.Write("{0} ", column.ColumnName.PadRight(columnWidth));
            }
            Console.WriteLine();

            printColumnSeparator();
        }

        private string formatColumnValue(DbColumn column, string Value)
        {
            checkColumnWidth(Value.Length);
            int columnWidth = getPrintedColumnWidth(column);
            if(Value.Length > columnWidth) {
                m_ValuesTruncated = true;
                // Truncate value if its too long
                return Value.Substring(0, columnWidth - TRUNCATION_SYMBOL.Length) + TRUNCATION_SYMBOL;
            }
            return Value.PadRight(columnWidth);
        }

        private void printColumnSeparator() {
            // Write a column separator
            foreach (DbColumn col in m_columns) {
                int columnWidth = getPrintedColumnWidth(col);
                Console.Write("{0} ", pad('-', columnWidth));
            }
            Console.WriteLine();
        }

        private void checkColumnWidth(int columnWidth) 
        {
            if (m_widestColumnRead < columnWidth) {
                m_widestColumnRead = columnWidth;  
            }          
        }

        private int getPrintedColumnWidth(DbColumn column)
        { 
            int columnMinWidth = Math.Max(column.ColumnName.Length, TRUNCATION_SYMBOL.Length);
            if(m_maxColumnWidth < 0) {
                return Math.Max(columnMinWidth, (int)column.ColumnSize);
            }
            return Math.Max(columnMinWidth, m_maxColumnWidth);  
        }

        private string pad(char value, int length)
        {
            string paddedString = "".PadRight(length);
            if (value == ' ') {
                return paddedString;
            }
            return paddedString.Replace(' ', value);
        }

        /* 
        Utility function to prevent showing the password in the program output
        */
        public static string printableConnectionString (String connectionString)
        {
            String printableConnectionString = "";
            String [] properties = connectionString.Split(';');
            foreach (String property in properties) {
                if (printableConnectionString.Length > 0) {
                    printableConnectionString+=";";
                }
                if (property.StartsWith("Password=")) {
                    printableConnectionString+="Password=***";
                } else {
                    printableConnectionString+=property;
                    Console.WriteLine(property);
                }
            }
            return printableConnectionString;
        }
    }
