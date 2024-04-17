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
import java.util.Properties;
import java.util.concurrent.Callable;

import java.io.OutputStream;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.DataOutputStream;
import java.net.URL;
import javax.net.ssl.*;
import java.net.HttpURLConnection;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyStore;

import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "OAuthSampleApp", mixinStandardHelpOptions = true, version = "OAuth sample app 1.0", description = "Connects to a Vertica database using OAuth")
public class OAuthSampleApp implements Callable<Integer> {

    @Parameters(index = "0", description = "Host name")
    private String host = "";

    @Parameters(index = "1", description = "Database name")
    private String dbName = "";

    @Option(names = { "-p", "--port" }, description = "Port")
    private String port = "5433";

    @Option(names = { "-a", "--access-token" }, description = "Access token")
    private String accessToken = "";

    @Option(names = { "-r", "--refresh-token" }, description = "Refresh token")
    private String refreshToken = "";

    @Option(names = { "-s", "--client-secret" }, description = "Client Secret")
    private String clientSecret = "";
	
	private static final String OAUTH_CLIENT_ID = System.getenv("OAUTH_CLIENT_ID");
    private static final String OAUTH_REALM = System.getenv("OAUTH_REALM");
    private static final String OAUTH_TOKEN_USER = System.getenv("OAUTH_TOKEN_USER");
    private static final String OAUTH_TOKEN_PASSWORD = System.getenv("OAUTH_TOKEN_PASSWORD");

    private static String OAUTH_SERVER = System.getenv("OAUTH_SERVER"); 
    private static String OAUTH_HTTP_PORT =  System.getenv("OAUTH_HTTP_PORT");

    private static String getTokenUrl()
    {
        return "http://" + OAUTH_SERVER + ":" + OAUTH_HTTP_PORT + "/realms/" + OAUTH_REALM + "/protocol/openid-connect/token";
    }

    private static String getIssuerUri()
    {
        return "http://" + OAUTH_SERVER + ":" + OAUTH_HTTP_PORT + "/realms/" + OAUTH_REALM;
    }

    private static String getHttpsTokenUrl()
    {
        return "http://" + OAUTH_SERVER + ":" + OAUTH_HTTP_PORT + "/realms/" + OAUTH_REALM + "/protocol/openid-connect/token";
    }

    private static String getDiscoveryUrl()
    {
        return "http://" + OAUTH_SERVER + ":" + OAUTH_HTTP_PORT + "/realms/" + OAUTH_REALM + "/.well-known/openid-configuration";
    }

    private void getTokenRefresh(String username, String password, String clientId, String clientSecret, String tokenUrl) throws Exception
    {
		String postOpts = "grant_type=refresh_token&client_secret=" + clientSecret + "&client_id=" + clientId + "&refresh_token=" + refreshToken;
         byte[] postData = postOpts.getBytes("UTF-8");
        int postDataLength = postData.length;
        URL url = new URL(tokenUrl);
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
			accessToken = jObject.get("access_token").getAsString();
			refreshToken = jObject.get("refresh_token").getAsString();
			
			System.out.println("\n\nNew Access Token : " + accessToken + "\n\nNew Refresh Token : " + refreshToken ); 

        } catch (IOException e) {
            String res = "";
            try {
                BufferedInputStream in = new BufferedInputStream(connection.getErrorStream());
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                for (int result = in.read(); result != -1; result = in.read()) {
                    buf.write((byte) result);
                }
                res = buf.toString("UTF-8");
            } catch (IOException inputStreamReadException) {
                //Improper setup. Passes in SF, fails in Devjail. Skip when this happens, but print the error.
                inputStreamReadException.printStackTrace();
                System.out.println("Error when fetching token endpoints. Skipping test.");
            }
			throw e;
        } finally {
            connection.disconnect();
        }
    }
	
    private static Connection connectToDB(String host, String port, String dbName, String accessToken,
            String refreshToken, String clientSecret) throws SQLException {
        Properties jdbcOptions = new Properties();
        jdbcOptions.put("oauthaccesstoken", accessToken);
        jdbcOptions.put("oauthrefreshtoken", refreshToken);
        jdbcOptions.put("oauthclientsecret", clientSecret);

        return DriverManager.getConnection(
                "jdbc:vertica://" + host + ":" + port + "/" + dbName, jdbcOptions);
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

    @Override
    public Integer call() throws Exception {
        try {
            Connection conn = connectToDB(host, port, dbName, accessToken, refreshToken, clientSecret);
            ResultSet rs = executeQuery(conn);
            printResults(rs);
			try{
				getTokenRefresh(OAUTH_TOKEN_USER, OAUTH_TOKEN_PASSWORD, OAUTH_CLIENT_ID, clientSecret, getTokenUrl());
			}catch(Exception e){
				throw e;
			}
            conn.close();
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
        }
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new OAuthSampleApp()).execute(args);
        System.exit(exitCode);
    }

}
