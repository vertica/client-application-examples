# Overview

The JDBC driver allows you to connect to your Vertica database with an access token.

This sample application will describe how to enable and use the "browser flow" to retrieve a token and login.

## Prerequisites

- An IDP (tested with Keycloak)
- Maven (tested with version 3.9.6)
- Java (tested with version 8)
- JDBC driver (at least 24.3)
- Vertica (at least 12.1)

You need to be in an environment that can open a web browser.

## Using OAuth to login

You must first configure the Vertica server for OAuth as described in the [Vertica documentation](https://docs.vertica.com/latest/en/security-and-authentication/client-authentication/oauth-2-0-authentication/configuring-oauth-authentication/).
The steps can also be done programatically as shown in the setupDbForOAuth() method.

The application uses two configuration files. One is properties.example, which allows the database admin to login and setup another user for OAuth. It is optional.
Required is the OAuthJsonConfig.json. It requires the "OAuthClientId" and "OAuthDiscoveryUrl". If your IDP utilizes secrets, then "OAuthClientSecret" is also needed. The setup method assumes that you are using OIDC 'confidential access type' and uses client secret for authentication. You can also setup 'public access type' to use a jwt to connect.

Whether you are using a secret or not, PKCE is implemented in the client for greater security. You can read about PKCE here [Vertica documentation](https://oauth.net/2/pkce/).

## Running the app

It's easiest to use VS Code or another IDE but you can run the program through the command line with:
mvn clean install
mvn dependency:copy-dependencies
java -cp "target/jarname.jar;target/dependency/*" OAuthHandler
