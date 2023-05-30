using System;
using System.Configuration;
using Vertica.Data.VerticaClient;

class SampleApp
{
    static void Main(string[] args)
    {
        // Create a connection
        using (VerticaConnection connection = new VerticaConnection())
        {
            connection.ConnectionString = ConfigurationManager.ConnectionStrings["ConnectionString"].ConnectionString;
            
            try {
                connection.Open();
            } 
            catch (Exception e)
            {
                Console.WriteLine("Could not connect to {0}", connection.ConnectionString);
                Console.WriteLine (e);
                return;
            }

            try {               
                // Query the database
                using (VerticaCommand command = connection.CreateCommand())
                {
                    command.CommandText = ConfigurationManager.AppSettings["Query"];
   
                    // Read the query results
                    using (VerticaDataReader reader = command.ExecuteReader())
                    {
                        ResultSetPrinter printer = new ResultSetPrinter ();
                        printer.printResults (reader);
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
