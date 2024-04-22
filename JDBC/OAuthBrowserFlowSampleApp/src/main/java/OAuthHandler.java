import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;


public class OAuthHandler {
    // set these in the properties file to match your configuration
    String host = "";
    String port = "";
    String dbName = "";
    String adminUser = "";
    String adminUserPassword = "";
    String oAuthUser = "";
    String authName = "";
    String clientId = "";
    String clientSecret = "";
    String httpsDiscoveryUrl = "";

    Connection adminConnection;

    public static void main(String[] args) {
        try {
            OAuthHandler oauthHandler = new OAuthHandler();
            oauthHandler.doAuth();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void doAuth() throws RuntimeException, URISyntaxException, IOException, SQLException {
        try {
            loadStartProperties("properties.example");
            setupDbForOAuth();
            loginWithBrowserOAuth();
        } finally {
            tearDownForOAuth();
        }
    }

    private void loadStartProperties(String filename) throws IOException {
        Properties props = new Properties();
        props.load(new FileReader(filename));
        host = props.getProperty("host");
        port = props.getProperty("port");
        dbName = props.getProperty("dbName");
        adminUser = props.getProperty("adminUser");
        adminUserPassword = props.getProperty("adminUserPassword");
        oAuthUser = props.getProperty("oAuthUser");
        authName = props.getProperty("authName");
        clientId = props.getProperty("clientId");
        clientSecret = props.getProperty("clientSecret");
        httpsDiscoveryUrl = props.getProperty("httpsDiscoveryUrl");
    }

    // Login as admin and setup OAuth user and authentication
    private void setupDbForOAuth() throws SQLException {
        adminConnection = getAdminConnection();
        Statement stmt = adminConnection.createStatement();
        
        // Array of SQL commands
        String[] sqlCommands = {
            "DROP USER IF EXISTS " + oAuthUser + " CASCADE",
            "DROP AUTHENTICATION IF EXISTS " + authName + " CASCADE",
            "CREATE USER " + oAuthUser,
            "CREATE AUTHENTICATION " + authName + " METHOD 'oauth' HOST '0.0.0.0/0'",
            "GRANT AUTHENTICATION " + authName + " TO " + oAuthUser,
            "GRANT ALL ON SCHEMA PUBLIC TO " + oAuthUser,
            "ALTER AUTHENTICATION " + authName + " SET client_id = '" + clientId + "'",
            "ALTER AUTHENTICATION " + authName + " SET client_secret = '" + clientSecret + "'",
            "ALTER AUTHENTICATION " + authName + " SET discovery_url = '" + httpsDiscoveryUrl + "'"
        };

        // Execute each SQL command in the array
        for (String sqlCommand : sqlCommands) {
            stmt.execute(sqlCommand);
        }
    }
    
    // password auth for admin user
    private Connection getAdminConnection() throws SQLException {
        Properties jdbcOptions = new Properties();
        jdbcOptions.put("user", adminUser);
        jdbcOptions.put("password", adminUserPassword);
        Connection conn = DriverManager.getConnection("jdbc:vertica://" + host + ":" + port + "/" + dbName, jdbcOptions);
        return conn;
    }

    // connect and run SELECT CURRENT_USER to verify browser OAuth succeeded
    public void loginWithBrowserOAuth() throws SQLException, IOException {
        String jsonConfigFromFile = loadJsonConfig("OAuthJsonConfig.json");
        Properties jdbcOptions = new Properties();
        jdbcOptions.put("oauthauthenticator", "browser");
        jdbcOptions.put("oauthjsonconfig", jsonConfigFromFile);

        Connection conn = DriverManager.getConnection("jdbc:vertica://" + host + ":" + port + "/" + dbName, jdbcOptions);
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT CURRENT_USER;");
        while (rs.next()) {
            System.out.println("Successfully connected to Vertica. SELECT CURRENT_USER returned: " + rs.getString(1));
        }
    }

    // load JSON configuration from file
    private String loadJsonConfig(String filePath) throws IOException {
        String jsonConfig = "";
        try{
            Gson gson = new Gson();
            FileReader reader = new FileReader(filePath);
            JsonObject jObect = gson.fromJson(reader, JsonObject.class);
            reader.close();
            jsonConfig = jObect.toString();
        } catch (IOException e) {
            System.out.println("Unable to parse json config from file: " + e.getMessage());
        }
        
        return jsonConfig;
    }

    // remove oauth user and authentication
    private void tearDownForOAuth() throws SQLException
    {
        if (adminConnection != null)
        {
            Statement stmt = adminConnection.createStatement();
            stmt.execute("DROP USER IF EXISTS " + oAuthUser + " CASCADE");
            stmt.execute("DROP AUTHENTICATION IF EXISTS " +  authName + " CASCADE");
        }
    }
}
