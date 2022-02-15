# Running the JDBC sample app
The sample app can be run as a Maven project using the IntelliJ IDE.

When running the app, specify the Vertica host and database name as the first two parameters. All other parameters are optional. 
Example: `java OAuthSampleApp verticahost mydb --access-token myaccesstoken`

Possible options:
-p, --port <port number>
-a, --access-token <access token>
-r, --refresh-token <refresh token>
-i, --client-id <client ID>
-s, --client-secret <client secret>
-t, --token-url <token URL>

