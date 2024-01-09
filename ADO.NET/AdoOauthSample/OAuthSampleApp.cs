using Vertica.Data.VerticaClient;
using Newtonsoft.Json.Linq;
using Newtonsoft.Json;
using System.Configuration;

internal class AdoOauthSample
{
    static void Main(string[] args)
    {
        VerticaConnectionStringBuilder vcsb = new VerticaConnectionStringBuilder();
        vcsb.ConnectionString = ConfigurationManager.ConnectionStrings["ConnectionString"].ConnectionString;

      //  vcsb.OAuthJsonConfig = GetTokenRefreshJsonConfig();
        ConnectToDatabase(vcsb.ConnectionString);
    }

    public static void ConnectToDatabase(string connectionString)
    {
        try
        {
            using (VerticaConnection conn = new VerticaConnection(connectionString))
            {
                conn.Open();

                using (VerticaCommand command = conn.CreateCommand())
                {
                    Console.WriteLine("Connection Successful");
                    command.CommandText = "SELECT user_id, user_name FROM USERS ORDER BY user_id";
                    using (VerticaDataReader dataReader = command.ExecuteReader())
                    {
                        Console.WriteLine("Executing Query");
                        int rowIdx = 1;
                        while (dataReader.Read())
                        {
                            Console.WriteLine(rowIdx + ". " + dataReader.GetString(0).Trim() + " " + dataReader.GetString(1).Trim());
                            rowIdx++;
                        }
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

    // Client ID, client secret, and one of token or discovery url must be set in order to do token refresh
    private static string GetTokenRefreshJsonConfig()
    {
        string jsonContent = File.ReadAllText("OAuthRefreshSettings.json");
        Dictionary<string, string> settings = JsonConvert.DeserializeObject<Dictionary<string, string>>(jsonContent);

        return @$"
    {{
        ""OAuthTokenUrl"": ""{settings.GetValueOrDefault("OAuthTokenUrl", string.Empty)}"",
        ""OAuthDiscoveryUrl"": ""{settings.GetValueOrDefault("OAuthDiscoveryUrl", string.Empty)}"",
        ""OAuthClientId"": ""{settings.GetValueOrDefault("OAuthClientId", string.Empty)}"",
        ""OAuthClientSecret"": ""{settings.GetValueOrDefault("OAuthClientSecret", string.Empty)}""
    }}";
    }

    // helper function to get token types of 'access' or 'refresh'
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
            return jObject[tokenType].ToString();
        }
        catch (HttpRequestException ex)
        {
            Console.WriteLine("Error getting access token: " + ex.Message);
            throw;
        }
    }
}

