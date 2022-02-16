# Pre-requisites
g++ compiler (tested with version 7.3.1)

This sample app was tested on Linux.

# Running the ODBC sample app
1. Run `sudo yum install unixODBC-devel` (allows include sql.h)
2. Install the Vertica ODBC driver:
```
wget https://www.vertica.com/client_drivers/11.0.x/11.0.1-0/vertica-client-11.0.1-0.x86_64.rpm
sudo yum localinstall vertica-client-11.0.1-0.x86_64.rpm
```
3. Create ~/.odbc.ini
4. Put this in the odbc.ini:
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

5. Run `export LIBVERTICAODBC_DIR=<folder containing libverticaodbc.so>`. For example, if your libverticaodbc.so is located at /opt/vertica/lib64/libverticaodbc.so, then you should use /opt/vertica/lib64
6. Run `g++ OAuthSampleApp.cpp -L$LIBVERTICAODBC_DIR -lverticaodbc -Wl,-rpath=$LIBVERTICAODBC_DIR`
7. Execute the generated a.out binary by running `./a.out --access-token <access token>`

Possible options:
--access-token <access token>
--refresh-token <refresh token>
--client-id <client ID>
--client-secret <client secret>
--token-url <token URL>

