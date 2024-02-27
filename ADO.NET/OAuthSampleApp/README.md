## Overview

The Vertica ADO.NET driver now allows connecting through OAuth access tokens. 

This sample application was built in .NET 6.0 can be run in .NET Framework 4.6+ or .NET Core 3.1+.

# Prerequisites

For information on installing .NET, starting a Vertica docker container, and how to reference a NuGet package, see the README.md in the ADO.NET base directory.

# Adding OAuth to the server with VSQL

You must first configure the Vertica server for OAuth as described here: https://docs.vertica.com/24.1.x/en/security-and-authentication/client-authentication/oauth-2-0-authentication/configuring-oauth-authentication/

The doc describes how to get an access token through Curl, but the sample app has a function for getting access tokens if you prefer a programmatic approach.

# Running the app

To run the sample app, you need to fill out the app.config file, which contains the connection string and query. The file has the default host, port, database, and user with an empty password and access token. To connect using OAuth, add your access token to the connection string, leaving the password blank.
