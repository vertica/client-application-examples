# Pre-requisites

- Maven (tested with version 3.8.4)
- Java (tested with version 6)

This application has been tested on MacOS, Linux, and Windows.

The server must be configured for OAuth as described here: https://www.vertica.com/docs/11.1.x/HTML/Content/Authoring/Security/ClientAuth/OAuth/OAuthJDBC.htm

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
-i, --client-id <client ID>
-s, --client-secret <client secret>
-t, --token-url <token URL>
-sc, --scope <scope>
```
