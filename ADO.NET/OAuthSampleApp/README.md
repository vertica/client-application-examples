## Overview

The Vertica ADO.NET driver now allows connecting through OAuth access tokens. 

This sample application was built in .NET 6.0 and can be run in .NET Framework 4.6+ or .NET Core 3.1+.

# Prerequisites

For information on installing .NET, starting a Vertica docker container, and how to reference a NuGet package, see the [README.md](https://github.com/vertica/client-application-examples/blob/main/ADO.NET/README.md) in the ADO.NET base directory.

# Using OAuth to login

You must first configure the Vertica server for OAuth as described here: https://docs.vertica.com/latest/en/security-and-authentication/client-authentication/oauth-2-0-authentication/configuring-oauth-authentication/.
The steps can also be done programatically as shown in the SetUp method.

The doc describes how to get an access token through Curl, but the sample app has functions for getting access and refresh tokens.
The authentication steps are:
1. Use the OAuth credentials from the app.config to set an access and refresh token.
2. Attempt to login with the access token. If it's expired, use the refresh token to get a new access token and login with that.
3. If the refresh token is expired, repeat step 1.

This app stores confidential information like the client secret in the app.config file for ease of use. Use proper secret management tools in production.

Note: The token url (if using Keycloak) should be in the form of "http://192.168.0.255:8080/realms/test/protocol/openid-connect/token/"

# Running the app

To run the sample app, fill out the app.config file. The connection string is for the default superuser.
To connect using OAuth, add your client ID, client secret, username, password, and token url that you configured with your IDP.
