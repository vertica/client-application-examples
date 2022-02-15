import java.sql.*;
import java.util.Properties;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "OAuthSampleApp", mixinStandardHelpOptions = true, version = "OAuth sample app 1.0",
        description = "Connects to a Vertica database using OAuth")
public class OAuthSampleApp implements Callable<Integer> {

    @Parameters(index = "0", description = "Host name")
    private String host = "";

    @Parameters(index = "1", description = "Database name")
    private String dbName = "";

    @Option(names = {"-p", "--port"}, description = "Port")
    private String port = "5433";

    @Option(names = {"-a", "--access-token"}, description = "Access token")
    private String accessToken = "";

    @Option(names = {"-r", "--refresh-token"}, description = "Refresh token")
    private String refreshToken = "";

    @Option(names = {"-i", "--client-id"}, description = "Client ID")
    private String clientId = "";

    @Option(names = {"-s", "--client-secret"}, description = "Client Secret")
    private String clientSecret = "";

    @Option(names = {"-t", "--token-url"}, description = "Token URL")
    private String tokenUrl = "";

    private static Connection connectToDB(String host, String port, String dbName, String accessToken, String clientSecret, String refreshToken, String clientId, String tokenUrl) throws SQLException {
        Properties jdbcOptions = new Properties();
        jdbcOptions.put("user", "");
        jdbcOptions.put("password", "");
        jdbcOptions.put("oauthaccesstoken", accessToken);
        jdbcOptions.put("oauthrefreshtoken", refreshToken);
        jdbcOptions.put("oauthtokenurl", tokenUrl);
        jdbcOptions.put("oauthclientid", clientId);
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
            Connection conn = connectToDB(host, port, dbName, accessToken, clientSecret, refreshToken, clientId, tokenUrl);
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
