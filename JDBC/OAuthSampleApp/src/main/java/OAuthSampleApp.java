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
import java.util.concurrent.Callable;

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

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.vertica.jdbc.VerticaConnection;

@Command(name = "OAuthSampleApp", mixinStandardHelpOptions = true, version = "OAuth sample app 1.0", description = "Connects to a Vertica database using OAuth")
public class OAuthSampleApp implements Callable<Integer> {

	private static Properties prop;
	private static String OAUTH_ACCESS_TOKEN_VAR_STRING = "OAUTH_SAMPLE_ACCESS_TOKEN";
    private static String OAUTH_REFRESH_TOKEN_VAR_STRING = "OAUTH_SAMPLE_REFRESH_TOKEN";
   
	private static Connection vConnection;
	
	private static String getConnectionString(String args) {
		Map<String, String> kv=new HashMap<String, String>();
        String[] entries = args.split(";");

        for (String entry : entries) {
            String[] pair = entry.split("=");
            kv.put(pair[0], pair[1]);
        }
        return "jdbc:vertica://" + kv.get("Host") + ":" 
								 + kv.get("Port") + "/" 
								 + kv.get("Database") + "?user=" 
								 + kv.get("User") + "&password=" 
								 + kv.get("Password");
    }

	private static void SetUpDbForOAuth() throws Exception
    {
		String connectionString = getConnectionString(prop.getProperty("ConnectionString"));
    	vConnection = DriverManager.getConnection(connectionString);
		System.out.println("SetUpDbForOAuth");
    	String CONNECTION_STRING = prop.getProperty("ConnectionString");    
        String USER = prop.getProperty("User");
        String CLIENT_ID = prop.getProperty("ClientId");
        String CLIENT_SECRET = prop.getProperty("ClientSecret");
        String INTROSPECT_URL = prop.getProperty("TokenUrl") + "introspect";
       	Statement st = vConnection.createStatement(); 
       	
		st.execute("DROP USER IF EXISTS " + USER + " CASCADE;");
		st.execute("DROP AUTHENTICATION IF EXISTS v_oauth CASCADE;");
		st.execute("CREATE AUTHENTICATION v_oauth METHOD 'oauth' LOCAL;");//HOST '0.0.0.0/0';");//getOAuthHost());
		st.execute("ALTER AUTHENTICATION v_oauth SET client_id= '" + CLIENT_ID + "';");
		st.execute("ALTER AUTHENTICATION v_oauth SET client_secret= '" + CLIENT_SECRET + "';");
		st.execute("ALTER AUTHENTICATION v_oauth SET introspect_url = '" + INTROSPECT_URL + "';");
		st.execute("CREATE USER " + USER + ";");
		st.execute("GRANT AUTHENTICATION v_oauth TO " + USER + ";");
		st.execute("GRANT ALL ON SCHEMA PUBLIC TO " + USER +";");
		st.close();
    }
    
    
    private static void TearDown() throws Exception
    {
		System.out.println("TearDown");
    	Statement st = vConnection.createStatement(); 
    	String USER = prop.getProperty("User");
    //    st.execute("DROP USER IF EXISTS " + USER + " CASCADE");
    //    st.execute("DROP AUTHENTICATION IF EXISTS v_oauth CASCADE");
        vConnection.close();
    }

	private static Connection connectToDB(String accessToken, String clientId, String clientSecret, String tokenUrl) throws SQLException 
    {
        Properties jdbcOptions = new Properties();
        jdbcOptions.put("oauthaccesstoken", accessToken);
        jdbcOptions.put("oauthtokenurl", tokenUrl);
        jdbcOptions.put("oauthclientid", clientId);
        jdbcOptions.put("oauthclientsecret", clientSecret);

		return DriverManager.getConnection(
                "jdbc:vertica://" + "172.17.0.45" + ":" + "5433" + "/" + "verticadb21451", jdbcOptions);
    }

    private static void ConnectToDatabase() throws SQLException {
		System.out.println("ConnectToDatabase");
		String accessToken = System.getProperty(OAUTH_ACCESS_TOKEN_VAR_STRING);

		if( null == accessToken || accessToken.isEmpty())
		{
			System.out.println("Access Token is not available.");
		}else
		{
			Connection conn = connectToDB( accessToken,
										prop.getProperty("ClientId"),
										prop.getProperty("ClientSecret"),
										prop.getProperty("TokenUrl"));
			ResultSet rs = executeQuery(conn);
	        printResults(rs);
       	}
    }

    private static ResultSet executeQuery(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        return stmt.executeQuery("SELECT user_id, user_name FROM USERS ORDER BY user_id");
    }

    private static void printResults(ResultSet rs) throws SQLException {
        int rowIdx = 1;
        while (rs.next()) {
            System.out.println(rowIdx + ". " + rs.getString(1).trim() + " " + rs.getString(2).trim());
            rowIdx++;
        }
    }
    
    private static String getEncodedParamsString(Map<String, String> params) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (first) {
                first = false;
            } else {
                result.append("&");
            }
            result.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name()));
            result.append("=");
            result.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
        }
        return result.toString();
    }

    // password grant logs into the IDP using credentials in app.config
    public static void GetTokensByPasswordGrant()  throws Exception {
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
	            for (int result = in.read(); result != -1; result = in.read()) {
	               	buf.write((byte) result);
		        }
	    	    String res = buf.toString("UTF-8");
	        	JsonElement jElement = new JsonParser().parse(res);
	            JsonObject jObject = jElement.getAsJsonObject();
	            
				// set Tokens as System Properties - new values to access_token and refresh_token
				System.setProperty(OAUTH_ACCESS_TOKEN_VAR_STRING, jObject.get("access_token").getAsString());
				System.setProperty(OAUTH_REFRESH_TOKEN_VAR_STRING, jObject.get("refresh_token").getAsString());

	        } catch (Exception e) {
	            String res = "";
	            try {
	                BufferedInputStream in = new BufferedInputStream(connection.getErrorStream());
	                ByteArrayOutputStream buf = new ByteArrayOutputStream();
	                for (int result = in.read(); result != -1; result = in.read()) {
	                    buf.write((byte) result);
	                }
	                res = buf.toString("UTF-8");
	            } catch (Exception ex) {
	                //Improper setup. Passes in SF, fails in Devjail. Skip when this happens, but print the error.
	                ex.printStackTrace();
	                System.out.println("Error when fetching token endpoints. Skipping test.");
	            }
				throw e;
	        } finally {
	            connection.disconnect();
	        }
    	}catch (IOException unreportedex) {
    		unreportedex.printStackTrace();
        }
    }
    
    // if no access token is set, obtains and sets first access and refresh tokens
    // uses the password grant flow
    private void EnsureAccessToken() throws Exception {
		System.out.println("EnsureAccessToken");
		try {
			String accessToken = System.getenv(OAUTH_ACCESS_TOKEN_VAR_STRING);
			if(null == accessToken || accessToken.isEmpty()) {
				// Obtain first access token
				System.out.println("Access token not found. Obtaining first access token from IDP.");
				GetTokensByPasswordGrant();
			}
		}catch(Exception e)
		{
			throw e;
		}
	}
    
    @Override
    public Integer call() throws Exception {
        try {
       		SetUpDbForOAuth();
        	EnsureAccessToken();
        	ConnectToDatabase();
            
           	try{
            	GetTokensByRefreshGrant();
				System.out.println("New Access token is set by refreshToken");
			}catch(Exception e){
				throw e;
			}
        } catch (SQLTransientConnectionException connException) {
            System.out.print("Network connection issue: ");
            System.out.print(connException.getMessage());
            System.out.println(" Try again later!");
        } catch (SQLInvalidAuthorizationSpecException authException) {
            System.out.print("Could not log into database: ");
            System.out.print(authException.getMessage());
            System.out.println(" Check the login credentials and try again.");
        } catch (SQLException e) {
            e.printStackTrace();
        }finally {
			TearDown();
        }
        return 0;
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
    
    public static void main(String[] args) {
    	
    	loadProperties();
    	int exitCode = new CommandLine(new OAuthSampleApp()).execute(args);
        System.exit(exitCode);
    }

}
