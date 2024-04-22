## Overview

The Vertica ADO.NET driver now allows connecting through OAuth access tokens. 

This application simulates what you would want your application to do when connecting with access and refresh tokens. We store the tokens as environment variables but they can be stored in any fashion. The logic of the program is:
1. If the access token is set, attempt to connect.
2. If the access token is not set and there is a refresh token, attempt to get a new access token.
3. If neither is set (i.e. running the app for the first time), set the access and refresh tokens through the direct access (password) grant.
4. Attempt to connect with the access token in the connection string.
5. If the access token is invalid/expired, use the refresh token to get a new one and try again.
6. If the refresh token is expired, use the direct access flow to get new tokens and try again.

# Prerequisites

For information on installing .NET, starting a Vertica docker container, and how to reference a NuGet package, see the [README.md](https://github.com/vertica/client-application-examples/blob/main/ADO.NET/README.md) in the ADO.NET base directory.

This sample application was built in .NET 6.0 and can be run in .NET Framework 4.6+ or .NET Core 3.1+.
You'll need Vertica (at least 12.1) and the ADO.NET driver (at least 24.1).
An IDP (like Keycloak or Okta) will need to be configured to handle OAuth.

# Using OAuth to login

You must first configure the Vertica server for OAuth as described here: https://docs.vertica.com/latest/en/security-and-authentication/client-authentication/oauth-2-0-authentication/configuring-oauth-authentication/.
The steps can also be done programatically as shown in the SetUp method.

The doc describes how to get an access token through Curl, but the sample app has functions for getting access and refresh tokens.

This app stores confidential information like the client secret in the app.config file for ease of use. Use proper secret management tools in production.

Note: The token url (if using Keycloak) should be in the form of "http://192.168.0.255:8080/realms/test/protocol/openid-connect/token/"

# Running the app

To run the sample app, fill out the app.config file. It has two sections.
The 'connection string' section is for the default superuser. This is for logging in to setup the OAuth user. If you have already setup OAuth in Vertica, ignore it.
The 'app settings' section is for the OAuth user.  To connect using OAuth, add your client ID, client secret (if needed), username, password, and token url that you configured with your IDP.

The first time you connect, user environment variables 'OAUTH_SAMPLE_ACCESS_TOKEN' and 'OAUTH_SAMPLE_REFRESH_TOKEN' are set. You could also set the tokens values yourself if you don't want to acquire them through this application.