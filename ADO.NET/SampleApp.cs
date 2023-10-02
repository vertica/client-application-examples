using System;
using System.Configuration;
using Vertica.Data.VerticaClient;

internal class SampleApp
{
    static void Main(string[] args)
    {
        // Create a connection
        using (VerticaConnection connection = new VerticaConnection())
        {
            connection.ConnectionString = ConfigurationManager.ConnectionStrings["ConnectionString"].ConnectionString;
            Console.WriteLine("Vertica ADO.NET sample application.");
            Console.WriteLine("-----------------------------------");
            Console.WriteLine("Running on {0}.", System.Environment.MachineName);
            Console.WriteLine("Using connection string: {0}", connection.ConnectionString);

            try {
                connection.Open();
            } 
            catch (Exception e) {
                Console.WriteLine("Could not connect to {0}", connection.ConnectionString);
                Console.WriteLine(e);
                return;
            }

            try {               
                // Query the database
                using (VerticaCommand command = connection.CreateCommand())
                {
                    command.CommandText = ConfigurationManager.AppSettings["Query"];
                    Console.WriteLine("Query: {0}", command.CommandText);
                    // Read the query results
                    using (VerticaDataReader reader = command.ExecuteReader()) {
                        ResultSetPrinter printer = new ResultSetPrinter(reader);
                        printer.printResults();
                    }
                }
            }             
            catch (Exception e) {
                Console.WriteLine("Something went wrong with the sample app!");
                Console.WriteLine(e);
                return;
            }
        }
    }
}
