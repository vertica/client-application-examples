// (c) Copyright [2022-2023] Open Text.
// Licensed under the Apache License, Version 2.0 (the "License");
// You may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.SQLTransientConnectionException;
import java.sql.SQLInvalidAuthorizationSpecException;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.net.URL;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vertica.jdbc.VerticaConnection;

public class OAuthSampleApp
{
	private static Properties prop;
	private static String OAUTH_ACCESS_TOKEN_VAR_STRING = "OAUTH_SAMPLE_ACCESS_TOKEN";
	private static String OAUTH_REFRESH_TOKEN_VAR_STRING = "OAUTH_SAMPLE_REFRESH_TOKEN";
	private static Connection vConnection;
	// Get jdbc connection string from provided configuration
	private static String getConnectionString(String args) 
	{
		Map<String, String> kv=new HashMap<String, String>();
		String[] entries = args.split(";");
		for (String entry : entries) 
		{
			String[] pair = entry.split("=");
			kv.put(pair[0], pair[1]);
		}
		return "jdbc:vertica://" + kv.get("Host") + ":" 
								 + kv.get("Port") + "/" 
								 + kv.get("Database") + "?user=" 
								 + kv.get("User") + "&password=" 
								 + kv.get("Password");
	}
	// Get the parameters from the connection String
	private static String getParam(String paramName) 
	{
		String args = prop.getProperty("ConnectionString");
		Map<String, String> kv=new HashMap<String, String>();
		String[] entries = args.split(";");
		for (String entry : entries) 
		{
			String[] pair = entry.split("=");
			kv.put(pair[0], pair[1]);
		}
		return kv.get(paramName);
	}
	// Add/Create Authentication record in database. Create use and grant the permissions for User
	private static void SetUpDbForOAuth() throws Exception 
	{
		String connectionString = getConnectionString(prop.getProperty("ConnectionString"));
		vConnection = DriverManager.getConnection(connectionString);
		String CONNECTION_STRING = prop.getProperty("ConnectionString");    
		String USER = prop.getProperty("User");
		String CLIENT_ID = prop.getProperty("ClientId");
		String CLIENT_SECRET = prop.getProperty("ClientSecret");
		String INTROSPECT_URL = prop.getProperty("TokenUrl") + "introspect";
		Statement st = vConnection.createStatement();
		st.executeUpdate("DROP USER IF EXISTS " + USER + " CASCADE;");
		st.executeUpdate("DROP AUTHENTICATION IF EXISTS v_oauth CASCADE;");
		st.executeUpdate("CREATE AUTHENTICATION v_oauth METHOD 'oauth' HOST '0.0.0.0/0';");
		st.executeUpdate("ALTER AUTHENTICATION v_oauth SET client_id= '" + CLIENT_ID + "';");
		st.executeUpdate("ALTER AUTHENTICATION v_oauth SET client_secret= '" + CLIENT_SECRET + "';");
		st.executeUpdate("ALTER AUTHENTICATION v_oauth SET introspect_url = '" + INTROSPECT_URL + "';");
		st.executeUpdate("CREATE USER " + USER + ";");
		st.executeUpdate("GRANT AUTHENTICATION v_oauth TO " + USER + ";");
		st.executeUpdate("GRANT ALL ON SCHEMA PUBLIC TO " + USER +";");
		st.close();
	}
	// Dispose the authentication record from database
	private static void TearDown() 
	{
		try
		{
			Statement st = vConnection.createStatement(); 
			String USER = prop.getProperty("User");
			st.executeUpdate("DROP USER IF EXISTS " + USER + " CASCADE");
			st.executeUpdate("DROP AUTHENTICATION IF EXISTS v_oauth CASCADE");
			vConnection.close();
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	// Connect to Database using access Token
	private static Connection connectToDB(String accessToken) throws SQLException 
	{
		Properties jdbcOptions = new Properties();
		jdbcOptions.put("oauthaccesstoken", accessToken);
		return DriverManager.getConnection(
				"jdbc:vertica://" + getParam("Host") + ":" + getParam("Port") + "/" + getParam("Database") , jdbcOptions);
    }
	// Test connection using access token and test database query result
	private static void ConnectToDatabase() throws SQLException 
	{
		int connAttemptCount = 0;
		while (connAttemptCount <= 1)
        {
            try
            {
            	String accessToken = System.getProperty(OAUTH_ACCESS_TOKEN_VAR_STRING);
        		if( null == accessToken || accessToken.isEmpty())
        		{
					throw new Exception("Access Token is not available.");
        		}else
        		{
        			Connection conn = connectToDB(accessToken);
        			ResultSet rs = executeQuery(conn);
        			printResults(rs);
					break;
        		}
            }catch(Exception ex) 
            {
            	if (connAttemptCount > 0) 
            	{ 
            		break;
            	}
            	try {
					ex.printStackTrace();
            		GetTokensByRefreshGrant();
            	}catch (Exception e1)
                {
            		e1.printStackTrace();
                    try
					{
						GetTokensByPasswordGrant();
					}catch(Exception e2)
					{
						e2.printStackTrace();
					}
                }
            	++connAttemptCount;
            }
        }
	}
	// execute Simple query on database connection
	private static ResultSet executeQuery(Connection conn) throws SQLException 
	{
		Statement stmt = conn.createStatement();
		return stmt.executeQuery("SELECT user_id, user_name FROM USERS ORDER BY user_id");
	}
	private static void printResults(ResultSet rs) throws SQLException 
	{
		int rowIdx = 1;
		while (rs.next()) 
		{
			System.out.println(rowIdx + ". " + rs.getString(1).trim() + " " + rs.getString(2).trim());
			rowIdx++;
		}
	}
	// Get encoded URL from parameters
	private static String getEncodedParamsString(Map<String, String> params) throws UnsupportedEncodingException 
	{
		StringBuilder result = new StringBuilder();
		for (Map.Entry<String, String> entry : params.entrySet()) 
		{
			result.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name()));
			result.append("=");
			result.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
			result.append("&");
		}
		result.setLength(result.length()-1);
		return result.toString();
	}
    // password grant logs into the IDP using credentials in app.config
    public static void GetTokensByPasswordGrant()  throws Exception 
    {
    	Map<String, String> formData = new HashMap<String, String>();
		formData.put("grant_type",   "password");
		formData.put("client_id",     prop.getProperty("ClientId"));
		formData.put("client_secret", prop.getProperty("ClientSecret"));
		formData.put("username",      prop.getProperty("User"));
		formData.put("password",      prop.getProperty("Password"));
        GetAndSetTokens(formData);
    }
    // refresh grant uses the refresh token to get a new access and refresh token
    public static void GetTokensByRefreshGrant() throws Exception {
    	Map<String, String> formData = new HashMap<String, String>();
   		formData.put("grant_type",		"refresh_token");
		formData.put("client_id",		prop.getProperty("ClientId"));
		formData.put("client_secret",	prop.getProperty("ClientSecret"));
		formData.put("refresh_token",	System.getProperty(OAUTH_REFRESH_TOKEN_VAR_STRING));
		GetAndSetTokens(formData);
    }
    // read result from Buffered input stream
	private static ByteArrayOutputStream readResult(BufferedInputStream in, ByteArrayOutputStream buf) {
		try
		{
			for (int result = in.read(); result != -1; result = in.read()) 
			{
				buf.write((byte) result);
			}
		}catch(Exception e)
		{
			e.printStackTrace();
		}
		return buf;
	}
    // get and set tokens from IDP 
	private static void GetAndSetTokens(Map<String, String> formData) throws Exception
    {
    	try {
	    	String postOpts = getEncodedParamsString(formData);
	        byte[] postData = postOpts.getBytes("UTF-8");
	        int postDataLength = postData.length;
	        URL url = new URL(prop.getProperty("TokenUrl"));
	        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	        try {
	            connection.setDoOutput(true);
	            connection.setUseCaches(false);
	            connection.setRequestMethod("POST");
	            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
	            connection.setRequestProperty("Content-Length", Integer.toString(postDataLength));
	            connection.setRequestProperty("Accept", "application/json");
				connection.getOutputStream().write(postData);
	           	BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
	    	    ByteArrayOutputStream buf = new ByteArrayOutputStream();
	            readResult(in, buf);
	    	    String res = buf.toString("UTF-8");
	        	JsonElement jElement = new JsonParser().parse(res);
	            JsonObject jObject = jElement.getAsJsonObject();
				// set Tokens as System Properties - new values to access_token and refresh_token
	            String accessToken = jObject.has("access_token") ? jObject.get("access_token").getAsString() : null;
	            String refreshToken = jObject.has("refresh_token") ? jObject.get("refresh_token").getAsString() : null;           
	            if (null == accessToken || null == refreshToken)
	            {
	            	throw new Exception("Access/refresh token is null, Please verify the config.properties for proper inputs.");
	            }
				System.setProperty(OAUTH_ACCESS_TOKEN_VAR_STRING, accessToken);
				System.setProperty(OAUTH_REFRESH_TOKEN_VAR_STRING, refreshToken);
	        }catch(UnsupportedEncodingException uee)
			{
				uee.printStackTrace();
	        } catch (Exception e) 
	        {
	            String res = "";
	            try 
	            {
	                BufferedInputStream in = new BufferedInputStream(connection.getErrorStream());
	                ByteArrayOutputStream buf = new ByteArrayOutputStream();
	                readResult(in, buf);
	                res = buf.toString("UTF-8");
	            } catch (Exception ex) 
	            {
	                //Improper setup. Passes in SF, fails in Devjail. Skip when this happens, but print the error.
	                ex.printStackTrace();
	            }
				throw e;
	        } finally 
	        {
	            connection.disconnect();
	        }
    	}catch (IOException unreportedex) 
    	{
    		unreportedex.printStackTrace();
        }
    }
	// if no access token is set, obtains and sets first access and refresh tokens
	// uses the password grant flow
	private static void EnsureAccessToken() throws Exception 
	{
		try 
		{
			String accessToken = System.getenv(OAUTH_ACCESS_TOKEN_VAR_STRING);
			if(null == accessToken || accessToken.isEmpty()) 
			{
				// Obtain first access token
				GetTokensByPasswordGrant();
			}
		}catch(Exception e)
		{
			throw e;
		}
	}
	// load configuration properties
	public static void loadProperties()
	{
		prop = new Properties();
		try (InputStream input = new FileInputStream("src/main/java/config.properties")) {
			// load a properties file
			prop.load(input);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
    // main function, Call starts from here
	public static void main(String[] args) 
	{    	
		try
        {
			loadProperties();
            //SetUpDbForOAuth();// Commeted this Call due to issues with "Add/Create Authentication record"
            EnsureAccessToken();
            ConnectToDatabase();
        }catch (SQLTransientConnectionException connException)
        {
			connException.printStackTrace();
        }catch (SQLInvalidAuthorizationSpecException authException)
        {
            authException.printStackTrace();
        }catch (SQLException e)
        {
            e.printStackTrace();
        }catch (Exception unreportedEx)
		{
			unreportedEx.printStackTrace();
		}finally
        {
            //TearDown(); // Commeted this Call due to issues with "Add/Create Authentication record"
        }
		System.exit(0);
	}
}
