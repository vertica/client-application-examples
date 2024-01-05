# Pre-requisites

- g++ compiler (tested with version 7.3.1)

This sample app was tested on Linux and Windows.

The server must be configured for OAuth as described in the [Vertica documentation](https://docs.vertica.com/23.4.x/en/security-and-authentication/client-authentication/oauth-2-0-authentication/configuring-oauth-authentication/).

# Running the ODBC sample app

1. Run `sudo yum install unixODBC-devel` (allows include `sql.h`)
2. Install the Vertica ODBC driver:
```
wget https://www.vertica.com/client_drivers/23.4.x/23.4.0-0/vertica-client-23.4.0-0.x86_64.rpm
sudo yum localinstall vertica-client-23.4.0-0.x86_64.rpm
```
3. Create `~/.odbc.ini`
4. Put this in the `odbc.ini` file:
```
[ODBC Data Sources]
VerticaDSN = "My Vertica DSN"

[VerticaDSN]
Description = Vertica
Driver = $LIBVERTICAODBC_DIR/libverticaodbc.so
Database = <your db name>
Servername = <your server name>
UID = <your db user ID>
PWD = 
Port = <your db port>
ConnSettings = 
AutoCommit = 0
Locale = en_US@collation=binary
```
5. Run `export LIBVERTICAODBC_DIR=<folder containing libverticaodbc.so>`. For example, if your `libverticaodbc.so` is located at `/opt/vertica/lib64/libverticaodbc.so`, then you should use `/opt/vertica/lib64`
6. Run `g++ OAuthSampleApp.cpp -L$LIBVERTICAODBC_DIR -lverticaodbc -Wl,-rpath=$LIBVERTICAODBC_DIR`
7. Execute the generated `a.out` binary by running `./a.out --access-token <access token>`

Note that for Windows and MacOS, rather than use the `.odbc.ini` file the [Vertica ODBC client](https://www.vertica.com/download/vertica/client-drivers/) must be installed and a Vertica ODBC data source must be created named `VerticaDSN` using the ODBC Data Source Administrator.

Also note that the command to compile the sample app may differ slightly depending on the platform.  The following is how to compile on Windows, assuming an ODBC driver is already installed and available (such as through MinGW):
```
g++ OAuthSampleApp.cpp -lodbc32 -Wall
./a.exe --access-token <access token>
```

Possible options:
```
--access-token <access token>
--refresh-token <refresh token>
--client-secret <client secret>
```
