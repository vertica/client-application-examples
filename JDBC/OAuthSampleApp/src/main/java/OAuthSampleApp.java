// (c) Copyright [2022] Micro Focus or one of its affiliates.
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

    @Option(names = { "-i", "--client-id" }, description = "Client ID")
    private String clientId = "";

    @Option(names = { "-s", "--client-secret" }, description = "Client Secret")
    private String clientSecret = "";

    @Option(names = { "-t", "--token-url" }, description = "Token URL")
    private String tokenUrl = "";

    @Option(names = { "-sc", "--scope" }, description = "Scope")
    private String scope = "";

    @Option(names = { "-v", "--validate-hostname" }, description = "Validate Hostname")
    private String validateHost = "";

    private static Connection connectToDB(String host, String port, String dbName, String accessToken,
            String clientSecret, String refreshToken, String clientId, String tokenUrl, String scope, String validateHost) throws SQLException {
        Properties jdbcOptions = new Properties();
        jdbcOptions.put("oauthaccesstoken", accessToken);
        jdbcOptions.put("oauthrefreshtoken", refreshToken);

	// Put these options into static json config
	String jsonConfig = "{\"oauthtokenurl\" : \"" + tokenUrl + "\", " +  
	"\"oauthclientid\" : \"" + clientId + "\", " + 
	"\"oauthclientsecret\" : \"" + clientSecret + "\", " +
	"\"oauthvalidatehostname\" : \"" + validateHost + "\", " +
	"\"oauthscope\" : \"" + scope + "\"" +
	"}";
	System.out.println(jsonConfig);
	jdbcOptions.put("oauthjsonconfig", jsonConfig);

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
            Connection conn = connectToDB(host, port, dbName, accessToken, clientSecret, refreshToken, clientId,
                    tokenUrl, scope, validateHost);
            ResultSet rs = executeQuery(conn);
            printResults(rs);
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
