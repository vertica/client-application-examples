# Pre-requisites
- Maven (tested with version 3.8.4)
- Java (tested with version 6)

This application has been tested on both MacOS and Linux.

# Running the JDBC sample app

Run the sample app using the following command:
```
mvn compile exec:java -Dexec.mainClass=OAuthSampleApp -Dexec.args="hostname dbname <parameters>"
```

When running the app, specify the Vertica host and database name as the first two parameters. All other parameters are optional. 
Example: `java OAuthSampleApp verticahost mydb --access-token myaccesstoken`

Possible options:
```
-p, --port <port number>
-a, --access-token <access token>
-r, --refresh-token <refresh token>
-i, --client-id <client ID>
-s, --client-secret <client secret>
-t, --token-url <token URL>
```
