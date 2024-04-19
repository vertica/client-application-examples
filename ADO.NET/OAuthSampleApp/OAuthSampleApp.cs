using Newtonsoft.Json.Linq;
using System.Configuration;
using System.Runtime.InteropServices;
using Vertica.Data.VerticaClient;

internal class OAuthSampleApp
{
    private static VerticaConnectionStringBuilder connectionStringBuilder;
    private static VerticaConnection vConnection;
    private const string OAUTH_ACCESS_TOKEN_VAR_STRING = "OAUTH_SAMPLE_ACCESS_TOKEN";
    private const string OAUTH_REFRESH_TOKEN_VAR_STRING = "OAUTH_SAMPLE_REFRESH_TOKEN";

    static async Task Main(string[] args)
    {
        connectionStringBuilder = new VerticaConnectionStringBuilder();
        connectionStringBuilder.ConnectionString = ConfigurationManager.ConnectionStrings["ConnectionString"].ConnectionString;

        await SetUpDbForOAuth();
        await EnsureAccessToken();
        await ConnectToDatabase();
        await TearDown();
    }

    // admin must log in and setup the user for OAuth
    // steps can also be followed in vsql
    static async Task SetUpDbForOAuth()
    {
        vConnection = new VerticaConnection(connectionStringBuilder.ConnectionString);
        vConnection.Open();

        string USER = getSetting("User");
        string CLIENT_ID = getSetting("ClientId");
        string CLIENT_SECRET = getSetting("ClientSecret");
        string INTROSPECT_URL = getSetting("TokenUrl") + "introspect";

        VerticaCommand cmd = new VerticaCommand("", vConnection);
        string[] sqlCommands =
        {
            "DROP USER IF EXISTS " + USER + " CASCADE",
            "DROP AUTHENTICATION IF EXISTS adooauth CASCADE",
            "CREATE USER " + USER,
            "CREATE AUTHENTICATION adooauth METHOD 'oauth'" + GetAuthHost(),
            "GRANT AUTHENTICATION adooauth TO " + USER,
            "GRANT ALL ON SCHEMA PUBLIC TO " + USER,
            "ALTER AUTHENTICATION adooauth SET client_id = '" + CLIENT_ID + "'",
            "ALTER AUTHENTICATION adooauth SET client_secret = '" + CLIENT_SECRET + "'",
            "ALTER AUTHENTICATION adooauth SET introspect_url = '" + INTROSPECT_URL + "'",
        };

        // Execute each SQL command in the array
        foreach (string commandText in sqlCommands)
        {
            cmd.CommandText = commandText;
            cmd.ExecuteNonQuery();
        }
    }

    // removes the oauth user and closes the admin connection
    public static async Task TearDown()
    {
        string USER = getSetting("User");
        VerticaCommand cmd = new VerticaCommand("DROP USER IF EXISTS " + USER + " CASCADE", vConnection);
        cmd.ExecuteNonQuery();
        cmd.CommandText = "DROP AUTHENTICATION IF EXISTS adooauth CASCADE";
        cmd.ExecuteNonQuery();

        vConnection.Close();
    }

    // if no access token is set, obtains and sets first access and refresh tokens
    // uses the password grant flow
    static async Task EnsureAccessToken()
    {
        string refreshToken = Environment.GetEnvironmentVariable(OAUTH_REFRESH_TOKEN_VAR_STRING, EnvironmentVariableTarget.User);
        string accessToken = Environment.GetEnvironmentVariable(OAUTH_ACCESS_TOKEN_VAR_STRING, EnvironmentVariableTarget.User);

        if (string.IsNullOrEmpty(accessToken))
        {
            if (string.IsNullOrEmpty(refreshToken))
            {
                // Obtain first access token
                Console.WriteLine("Access token not found. Obtaining first access token from IDP.");
                await GetTokensByPasswordGrant();
            }
            else
            {
                try
                {
                    // attempt to use given refresh token
                    Console.WriteLine("Attempting to use given refresh token.");
                    await GetTokensByRefreshGrant();
                }
                catch (HttpRequestException hre)
                {
                    Console.WriteLine(hre.Message);
                    Console.WriteLine("Initial refresh token has expired. Getting new access and refresh tokens.");
                    await GetTokensByPasswordGrant();
                }
            }
        } else
        {
            Console.WriteLine("Access token found in environment variable.");
        }

        SetAccessToken();
    }

    // the access token must be set in the connection string
    static void SetAccessToken()
    {
        string accessToken = Environment.GetEnvironmentVariable(OAUTH_ACCESS_TOKEN_VAR_STRING, EnvironmentVariableTarget.User);
        connectionStringBuilder["OAuthAccessToken"] = accessToken;
    }

    // OAuth user attempts to connect with the access token
    // If it fails, we try to get a new access token with the refresh token
    // If the refresh token is expired, we get new access and refresh tokens
    public static async Task ConnectToDatabase()
    {
        // ADO.NET requires a user in the connection string
        // It is ignored by the driver, and only the access token is used
        // The user can be set to any value (except the admin) and it will connect
        connectionStringBuilder.User = getSetting("User");
        int connAttemptCount = 0;

        while (connAttemptCount <= 1)
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

                        // change the query in app.config or make the command text a string literal
                        command.CommandText = getSetting("Query");
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
                if (connAttemptCount > 0) { break; }
                try
                {
                    Console.WriteLine("Access token invalid or expired. Attempting token refresh.");
                    await GetTokensByRefreshGrant();
                }
                catch (HttpRequestException hre)
                {
                    Console.WriteLine(hre.Message);
                    Console.WriteLine("Refresh token invalid or expired. Attempting to get new access and refresh tokens");
                    await GetTokensByPasswordGrant();
                }

                SetAccessToken();
                connAttemptCount++;
            }
        }
    }

    // password grant logs into the IDP using credentials in app.config
    public static async Task GetTokensByPasswordGrant()
    {
        Dictionary<string, string> formData = new Dictionary<string, string>
        {
            {"grant_type",   "password"},
            {"client_id",     getSetting("ClientId")},
            {"client_secret", getSetting("ClientSecret")},
            {"username",      getSetting("User")},
            {"password",      getSetting("Password")}
        };

        await GetAndSetTokens(formData);
    }

    // refresh grant uses the refresh token to get a new access and refresh token
    public static async Task GetTokensByRefreshGrant()
    {
        Dictionary<string, string> formData = new Dictionary<string, string>
        {
            {"grant_type",   "refresh_token"},
            {"client_id",     getSetting("ClientId")},
            {"client_secret", getSetting("ClientSecret")},
            {"refresh_token", Environment.GetEnvironmentVariable(OAUTH_REFRESH_TOKEN_VAR_STRING, EnvironmentVariableTarget.User)}
        };

        await GetAndSetTokens(formData);
    }

    // uses the token url from the config to make an http request
    // will always always set new access and refresh tokens, even if currently valid
    private static async Task GetAndSetTokens(Dictionary<string, string> formData)
    {
        HttpClient httpClient = new HttpClient();

        try
        {
            var content = new FormUrlEncodedContent(formData);

            string tokenUrl = getSetting("TokenUrl");
            var response = await httpClient.PostAsync(tokenUrl, content);

            response.EnsureSuccessStatusCode();

            var result = await response.Content.ReadAsStringAsync();
            JObject jObject = JObject.Parse(result);

            // Set the access token as an environment variable
            Environment.SetEnvironmentVariable(OAUTH_ACCESS_TOKEN_VAR_STRING, jObject["access_token"].ToString(), EnvironmentVariableTarget.User);

            // Set the refresh token as an environment variable if IDP returns one
            if (jObject["refresh_token"] != null)
            {
                Environment.SetEnvironmentVariable(OAUTH_REFRESH_TOKEN_VAR_STRING, jObject["refresh_token"].ToString(), EnvironmentVariableTarget.User);
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine("Error getting refresh and/or access tokens: " + ex.Message);
            throw;
        }
    }

    static string getSetting(String setting)
    {
        return ConfigurationManager.AppSettings[setting] ?? "";
    }

    // default hosts depending on OS
    static string GetAuthHost()
    {
        return RuntimeInformation.IsOSPlatform(OSPlatform.Linux) ? "LOCAL" : "HOST '0.0.0.0/0'";
    }
}

