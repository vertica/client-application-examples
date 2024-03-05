using Vertica.Data.VerticaClient;
using Newtonsoft.Json.Linq;
using System.Configuration;
using System.Runtime.InteropServices;

internal class OAuthSampleApp
{
    private static VerticaConnectionStringBuilder connectionStringBuilder;
    private static VerticaConnection vConnection;
    private const string OAUTH_ACCESS_TOKEN = "OAUTH_SAMPLE_ACCESS_TOKEN";
    private const string OAUTH_REFRESH_TOKEN = "OAUTH_SAMPLE_REFRESH_TOKEN";

    static async Task Main(string[] args)
    {
        connectionStringBuilder = new VerticaConnectionStringBuilder();
        connectionStringBuilder.ConnectionString = ConfigurationManager.ConnectionStrings["ConnectionString"].ConnectionString;

        await SetUp();

        // change the user from the dbadmin to OAuth user
        connectionStringBuilder.User = ConfigurationManager.AppSettings["User"];

        await EnsureAccessToken();
        await ConnectToDatabase();
        await TearDown();
    }

    // admin must log in and setup the user for OAuth
    // steps can also be followed in vsql
    static async Task SetUp()
    {
        vConnection = new VerticaConnection(connectionStringBuilder.ConnectionString);
        vConnection.Open();

        string USER = ConfigurationManager.AppSettings["User"];
        string CLIENT_ID = ConfigurationManager.AppSettings["ClientId"];
        string CLIENT_SECRET = ConfigurationManager.AppSettings["ClientSecret"];
        string INTROSPECT_URL = ConfigurationManager.AppSettings["TokenUrl"] + "introspect";

        VerticaCommand cmd = new VerticaCommand("DROP USER IF EXISTS " + USER + " CASCADE", vConnection);
        cmd.ExecuteNonQuery();
        cmd.CommandText = "DROP AUTHENTICATION IF EXISTS adooauth CASCADE";
        cmd.ExecuteNonQuery();
        cmd.CommandText = "CREATE USER " + USER;
        cmd.ExecuteNonQuery();
        cmd.CommandText = "CREATE AUTHENTICATION adooauth METHOD 'oauth'" + GetAuthHost();
        cmd.ExecuteNonQuery();
        cmd.CommandText = "GRANT AUTHENTICATION adooauth TO " + USER;
        cmd.ExecuteNonQuery();
        cmd.CommandText = "GRANT ALL ON SCHEMA PUBLIC TO " + USER;
        cmd.ExecuteNonQuery();
        cmd.CommandText = "ALTER AUTHENTICATION adooauth SET client_id = '" + CLIENT_ID + "'";
        cmd.ExecuteNonQuery();
        cmd.CommandText = "ALTER AUTHENTICATION adooauth SET client_secret = '" + CLIENT_SECRET + "'";
        cmd.ExecuteNonQuery();
        cmd.CommandText = "ALTER AUTHENTICATION adooauth SET introspect_url = '" + INTROSPECT_URL + "'";
        cmd.ExecuteNonQuery();
    }

    // removes the oauth user and closes the connection for the admin
    public static async Task TearDown()
    {
        string USER = ConfigurationManager.AppSettings["User"];
        VerticaCommand cmd = new VerticaCommand("DROP USER IF EXISTS " + USER + " CASCADE", vConnection);
        cmd.ExecuteNonQuery();
        cmd.CommandText = "DROP AUTHENTICATION IF EXISTS adooauth CASCADE";
        cmd.ExecuteNonQuery();

        vConnection.Close();
    }

    // if no access token is set, obtains and sets first access and refresh tokens
    static async Task EnsureAccessToken()
    {
        string accessToken = Environment.GetEnvironmentVariable(OAUTH_ACCESS_TOKEN, EnvironmentVariableTarget.User);

        if (string.IsNullOrEmpty(accessToken))
        {
            // Obtain first access token
            Console.WriteLine("Access token not found. Obtaining new token...");
            await GetAndSetTokens("password");
        }

        SetAccessToken();
    }

    // only the access token is set in the connection string
    static void SetAccessToken()
    {
        string accessToken = Environment.GetEnvironmentVariable(OAUTH_ACCESS_TOKEN, EnvironmentVariableTarget.User);
        connectionStringBuilder["OAuthAccessToken"] = accessToken;
    }

    // OAuth user attempts to connect with the access token
    // If it fails, we try to get a new access token with the refresh token
    // If the refresh token is expired, we get new access and refresh tokens
    public static async Task ConnectToDatabase()
    {
        int retryCount = 0;

        while (retryCount <= 1)
        {
            try
            {
                using (VerticaConnection conn = new VerticaConnection(connectionStringBuilder.ConnectionString))
                {
                    Console.WriteLine("Attempting to connect with connection string: " + ResultSetPrinter.printableConnectionString(connectionStringBuilder.ConnectionString));
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

                            break;
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine(ex.Message);
                if (retryCount > 0) { break; }
                try
                {
                    await GetAndSetTokens("refresh_token");
                }
                catch (HttpRequestException hre)
                {
                    Console.WriteLine(hre.Message);
                    Console.WriteLine("Attempting to get new access and refresh tokens");
                    await GetAndSetTokens("password");
                }

                SetAccessToken();
                retryCount++;
            }
        }
    }
    public static async Task GetAndSetTokens(string grantType)
    {
        // Read OAuth parameters from app.config
        string clientId = ConfigurationManager.AppSettings["ClientId"];
        string clientSecret = ConfigurationManager.AppSettings["ClientSecret"];
        string user = ConfigurationManager.AppSettings["User"];
        string password = ConfigurationManager.AppSettings["Password"];
        string tokenUrl = ConfigurationManager.AppSettings["TokenUrl"];

        HttpClient httpClient = new HttpClient();

        try
        {
            Dictionary<string, string> formData;

            if (grantType == "password")
            {
                formData = new Dictionary<string, string>
            {
                {"grant_type", "password"},
                {"client_id", clientId},
                {"client_secret", clientSecret},
                {"username", user},
                {"password", password}
            };
            }
            else if (grantType == "refresh_token")
            {
                string refreshToken = Environment.GetEnvironmentVariable(OAUTH_REFRESH_TOKEN, EnvironmentVariableTarget.User);

                formData = new Dictionary<string, string>
            {
                {"grant_type", "refresh_token"},
                {"client_id", clientId},
                {"client_secret", clientSecret},
                {"refresh_token", refreshToken}
            };
            }
            else
            {
                throw new ArgumentException("Invalid grant type");
            }

            var content = new FormUrlEncodedContent(formData);

            var response = await httpClient.PostAsync(tokenUrl, content);

            response.EnsureSuccessStatusCode();

            var result = await response.Content.ReadAsStringAsync();
            JObject jObject = JObject.Parse(result);

            // Set the access token as an environment variable
            Environment.SetEnvironmentVariable(OAUTH_ACCESS_TOKEN, jObject["access_token"].ToString(), EnvironmentVariableTarget.User);

            // Set the refresh token as an environment variable
            Environment.SetEnvironmentVariable(OAUTH_REFRESH_TOKEN, jObject["refresh_token"].ToString(), EnvironmentVariableTarget.User);
        }
        catch (Exception ex)
        {
            Console.WriteLine("Error getting access token: " + ex.Message);
            throw;
        }
    }

    static string GetAuthHost()
    {
        return RuntimeInformation.IsOSPlatform(OSPlatform.Linux) ? "LOCAL" : "HOST '0.0.0.0/0'";
    }
}

