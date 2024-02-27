using Vertica.Data.VerticaClient;
using Newtonsoft.Json.Linq;
using System.Configuration;

internal class OAuthSampleApp
{
    static void Main(string[] args)
    {
        VerticaConnectionStringBuilder vcsb = new VerticaConnectionStringBuilder();
        vcsb.ConnectionString = ConfigurationManager.ConnectionStrings["ConnectionString"].ConnectionString;

        ConnectToDatabase(vcsb.ConnectionString);
    }

    public static void ConnectToDatabase(string connectionString)
    {
        try
        {
            using (VerticaConnection conn = new VerticaConnection(connectionString))
            {
                Console.WriteLine("Attempting to connect with connection string: " + ResultSetPrinter.printableConnectionString(connectionString));
                conn.Open();

                using (VerticaCommand command = conn.CreateCommand())
                {
                    Console.WriteLine("Connection Successful");
                    command.CommandText = ConfigurationManager.AppSettings["Query"];
                    using (VerticaDataReader reader = command.ExecuteReader())
                    {
                        Console.WriteLine("Executing Query: " + command.CommandText);
                        ResultSetPrinter printer = new ResultSetPrinter(reader);
                        printer.printResults();
                        Console.WriteLine("Query Executed. Exiting.");
                    }
                }
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine(ex.Message);
        }
    }

    // returns an access token that you can add to the connection string
    public static async Task<string> GetToken(string tokenType, string clientID, string clientSecret, string user, string password, string tokenUrl)
    {
        HttpClient httpClient = new HttpClient();

        try
        {
            var content = new FormUrlEncodedContent(new Dictionary<string, string>
            {
                {"grant_type", "password"},
                {"client_id", clientID},
                {"client_secret", clientSecret},
                {"username", user},
                {"password", password}
            });

            var response = await httpClient.PostAsync(tokenUrl, content);

            response.EnsureSuccessStatusCode(); // Ensure the request was successful

            var result = await response.Content.ReadAsStringAsync();
            JObject jObject = JObject.Parse(result);
            return jObject["access_token"].ToString();
        }
        catch (HttpRequestException ex)
        {
            Console.WriteLine("Error getting access token: " + ex.Message);
            throw;
        }
    }
}

