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
            connection.Open();

            // Query the database
            string query = ConfigurationManager.AppSettings["Query"];
            using (VerticaCommand command = connection.CreateCommand())
            {
                command.CommandText = query;

                // Read the query results
                using (VerticaDataReader reader = command.ExecuteReader())
                {
                    while (reader.Read())
                    {
                        for (int i = 0; i < reader.FieldCount; i++)
                        {
                            Console.Write(reader.GetValue(i) + "\t");
                        }
                        Console.WriteLine();
                    }
                }
            }
        }
    }
}
