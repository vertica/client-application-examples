
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

public class OAuthSampleApp {
	private static final String OAUTH_ACCESS_TOKEN_VAR_STRING = "OAUTH_SAMPLE_ACCESS_TOKEN";
	private static final String OAUTH_REFRESH_TOKEN_VAR_STRING = "OAUTH_SAMPLE_REFRESH_TOKEN";

	private static Properties prop;
	private static Map<String, String> csProp = new HashMap<String, String>();
	private static Connection vConnection;

	// main function, Call starts from here
	public static void main(String[] args) {
		try {
			loadProperties();
			setUpDbForOAuth();
			ensureAccessToken();
			connectToDatabase();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (Exception unreportedEx) {
			unreportedEx.printStackTrace();
		} finally {
			tearDown();
		}
		System.exit(0);
	}

	// admin must log in and setup the user for OAuth
	// steps can also be followed in vsql
	private static void setUpDbForOAuth() throws Exception{
		String connectionString = getConnectionString();
		String user = prop.getProperty("User");
		String clientId = prop.getProperty("ClientId");
		String clientSecret = prop.getProperty("ClientSecret");
		String discoveryUrl = prop.getProperty("DiscoveryUrl");
		String queries[]= {
				"DROP USER IF EXISTS " + user + " CASCADE;",
				"DROP AUTHENTICATION IF EXISTS v_oauth CASCADE;",
				"CREATE AUTHENTICATION v_oauth METHOD 'oauth' LOCAL;",
				"ALTER AUTHENTICATION v_oauth SET client_id = '" + clientId + "';",
				"ALTER AUTHENTICATION v_oauth SET client_secret = '" + clientSecret + "';",
				"ALTER AUTHENTICATION v_oauth SET discovery_url = '" + discoveryUrl + "';",
				"CREATE USER " + user + ";",
				"GRANT AUTHENTICATION v_oauth TO " + user + ";",
				"GRANT ALL ON SCHEMA PUBLIC TO " + user + ";"
				};
		vConnection = DriverManager.getConnection(connectionString);
		Statement st = vConnection.createStatement();
		
		for (String query: queries) {
			st.execute(query);
		}
		st.close();
	}

	// removes the oauth user and closes the admin connection
	private static void tearDown() {
		try {
			Statement st = vConnection.createStatement();
			String user = prop.getProperty("User");
			st.executeUpdate("DROP USER IF EXISTS " + user + " CASCADE");
			st.executeUpdate("DROP AUTHENTICATION IF EXISTS v_oauth CASCADE");
			vConnection.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// If access token is Invalid/Expired, Get new tokens using password/refresh grant
	private static void ensureAccessToken() throws Exception {
		String accessToken = System.getenv(OAUTH_ACCESS_TOKEN_VAR_STRING);
		String refreshToken = System.getenv(OAUTH_REFRESH_TOKEN_VAR_STRING);
		try {
			if (null == accessToken || accessToken.isEmpty()) {
				if (null == refreshToken || refreshToken.isEmpty()) {
					// Obtain first access token and refresh Tokens
					System.out.println("Access token is invalid/expired, trying to do refresh");
					getTokensByPasswordGrant();
				} else {
					try {
						System.out.println("Attempting to use given refresh token.");
						getTokensByRefreshGrant();
					} catch (Exception e) {
						System.out.println("Initial refresh token has expired. Getting new access and refresh tokens.");
						getTokensByPasswordGrant();
					}
				}
			} else {
				System.setProperty(OAUTH_ACCESS_TOKEN_VAR_STRING, accessToken);
			}
		} catch (Exception e) {
			throw e;
		}
	}

	// Test connection using access token and test database query result
	private static void connectToDatabase() throws SQLException {
		int connAttemptCount = 0;
		while (connAttemptCount <= 1) {
			try {
				String accessToken = System.getProperty(OAUTH_ACCESS_TOKEN_VAR_STRING);
				if (null == accessToken || accessToken.isEmpty()) {
					throw new Exception("Access Token is not available.");
				} else {
					System.out.println("Attempting to connect with OAuth access token");
					Connection conn = getConnection(accessToken);
					ResultSet rs = executeQuery(conn);
					printResults(rs);
					System.out.println("Query Executed. Exiting.");
					break;
				}
			} catch (Exception ex) {
				if (connAttemptCount > 0) {
					break;
				}
				try {
					getTokensByRefreshGrant();
				} catch (Exception e1) {
					try {
						System.out.println("Refresh token is invalid/Expired, Getting new tokens");
						getTokensByPasswordGrant();
					} catch (Exception e2) {
						e2.printStackTrace();
					}
				}
				++connAttemptCount;
			}
		}
	}

	// password grant logs into the IDP using credentials in app.config
	public static void getTokensByPasswordGrant() throws Exception {
		Map<String, String> formData = new HashMap<String, String>();
		formData.put("grant_type", "password");
		formData.put("client_id", prop.getProperty("ClientId"));
		formData.put("client_secret", prop.getProperty("ClientSecret"));
		formData.put("username", prop.getProperty("User"));
		formData.put("password", prop.getProperty("Password"));
		getAndSetTokens(formData);
	}

	// refresh grant uses the refresh token to get the new access and refresh token
	public static void getTokensByRefreshGrant() throws Exception {
		Map<String, String> formData = new HashMap<String, String>();
		formData.put("grant_type", "refresh_token");
		formData.put("client_id", prop.getProperty("ClientId"));
		formData.put("client_secret", prop.getProperty("ClientSecret"));
		formData.put("refresh_token", System.getProperty(OAUTH_REFRESH_TOKEN_VAR_STRING));
		getAndSetTokens(formData);
	}

	// get and set tokens from IDP
	private static void getAndSetTokens(Map<String, String> formData) throws Exception {
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
				JsonElement jElement = JsonParser.parseString(res);
				JsonObject jObject = jElement.getAsJsonObject();
				// set Tokens as System Properties - new values to access_token and
				// refresh_token
				String accessToken = jObject.has("access_token") ? jObject.get("access_token").getAsString() : null;
				String refreshToken = jObject.has("refresh_token") ? jObject.get("refresh_token").getAsString() : null;
				if (null == accessToken) {
					throw new Exception(
							"Access/refresh token is null, Please verify the config.properties for proper inputs.");
				}
				System.setProperty(OAUTH_ACCESS_TOKEN_VAR_STRING, accessToken);
				if (null != refreshToken && !refreshToken.isEmpty()) {
					System.setProperty(OAUTH_REFRESH_TOKEN_VAR_STRING, refreshToken);
				}
			} catch (UnsupportedEncodingException uee) {
				uee.printStackTrace();
			} catch (Exception e) {
				String res = "";
				try {
					BufferedInputStream in = new BufferedInputStream(connection.getErrorStream());
					ByteArrayOutputStream buf = new ByteArrayOutputStream();
					readResult(in, buf);
					res = buf.toString("UTF-8");
				} catch (Exception ex) {
					// Skip when this happens, but print the error.
					ex.printStackTrace();
				}
				throw e;
			} finally {
				connection.disconnect();
			}
		} catch (IOException unreportedex) {
			unreportedex.printStackTrace();
		}
	}

	// Get jdbc connection string from provided configuration
	private static void getcsProp(String args) {
		String[] entries = args.split(";");
		for (String entry : entries) {
			String[] pair = entry.split("=");
			csProp.put(pair[0], pair[1]);
		}
	}
	
	// get connection string for admin connection to setup OAuth Record
	private static String getConnectionString() {
		return "jdbc:vertica://" + csProp.get("Host") + ":" + csProp.get("Port") + "/" + csProp.get("Database")
				+ "?user=" + csProp.get("User") + "&password=" + csProp.get("Password");
	}

	// Get the parameters from the connection String
	private static String getParam(String paramName) {
		return csProp.get(paramName);
	}

	// Connect to Database using access Token
	private static Connection getConnection(String accessToken) throws SQLException {
		Properties jdbcOptions = new Properties();
		jdbcOptions.put("oauthaccesstoken", accessToken);
		return DriverManager.getConnection(
				"jdbc:vertica://" + getParam("Host") + ":" + getParam("Port") + "/" + getParam("Database"),
				jdbcOptions);
	}

	// execute Simple query on database connection
	private static ResultSet executeQuery(Connection conn) throws SQLException {
		Statement stmt = conn.createStatement();
		String query = "SELECT user_id, user_name FROM USERS ORDER BY user_id";
		System.out.println("Executing query:" + query);
		return stmt.executeQuery(query);
	}

	private static void printResults(ResultSet rs) throws SQLException {
		int rowIdx = 1;
		while (rs.next()) {
			System.out.println(rowIdx + ". " + rs.getString(1).trim() + " " + rs.getString(2).trim());
			rowIdx++;
		}
	}

	// Get encoded URL from parameters
	private static String getEncodedParamsString(Map<String, String> params) throws UnsupportedEncodingException {
		StringBuilder result = new StringBuilder();
		for (Map.Entry<String, String> entry : params.entrySet()) {
			result.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name()));
			result.append("=");
			result.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
			result.append("&");
		}
		result.setLength(result.length() - 1);
		return result.toString();
	}

	// read result from Buffered input stream
	private static ByteArrayOutputStream readResult(BufferedInputStream in, ByteArrayOutputStream buf) {
		try {
			for (int result = in.read(); result != -1; result = in.read()) {
				buf.write((byte) result);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return buf;
	}

	// load the configuration properties
	public static void loadProperties() {
		prop = new Properties();
		try (InputStream input = new FileInputStream("src/main/java/config.properties")) {
			// load the properties file
			prop.load(input);
			// Get the connectionString parameters from properties prop
			getcsProp(prop.getProperty("ConnectionString"));
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}
