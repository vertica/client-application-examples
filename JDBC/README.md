# Pre-requisites

- Maven (tested with version 3.8.4)
- Java (tested with version 6)

This application has been tested on MacOS, Linux, and Windows.

The server must be configured for OAuth as described in the [Vertica documentation](https://docs.vertica.com/24.2.x/en/security-and-authentication/client-authentication/oauth-2-0-authentication/configuring-oauth-authentication/).

# Running the JDBC sample app

Run the sample app using the following command:
```
mvn compile exec:java -Dexec.mainClass=OAuthSampleApp -Dexec.args="hostname dbname <parameters>"
```

When running the app, specify the Vertica host and database name as the first two parameters. All other parameters are optional. For example:
```
mvn compile exec:java -Dexec.mainClass=OAuthSampleApp -Dexec.args="verticahost mydb --access-token myaccesstoken"
```

Possible options:
```
-p, --port <port number>
-a, --access-token <access token>
-r, --refresh-token <refresh token>
-s, --client-secret <client secret>
```
