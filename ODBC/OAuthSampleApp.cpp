// (c) Copyright [2022] Micro Focus or one of its affiliates.
// Licensed under the Apache License, Version 2.0 (the "License");
// You may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Demonstrate running a query after connecting to Vertica using the OAuth authentication method 
// system table for a list of all tables in the current schema.
// Some standard headers
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <getopt.h>
#include <sstream>
#include <iostream>
#include <assert.h>
// Standard ODBC headers
#include <sql.h>
#include <sqltypes.h>
#include <sqlext.h>
// Helper function to print SQL error messages.
template <typename HandleT>
void reportError(int handleTypeEnum, HandleT hdl)
{
    // Get the status records.
    SQLSMALLINT   i, MsgLen;
    SQLRETURN ret2;
    SQLCHAR       SqlState[6], Msg[SQL_MAX_MESSAGE_LENGTH];
    SQLINTEGER    NativeError;
    i = 1;
    std::cout << std::endl;
    while ((ret2 = SQLGetDiagRec(handleTypeEnum, hdl, i, SqlState, &NativeError,
        Msg, sizeof(Msg), &MsgLen)) != SQL_NO_DATA) {
        std::cout << "error record #" << i++ << std::endl;
        std::cout << "sqlstate: " << SqlState << std::endl;
        std::cout << "detailed msg: " << Msg << std::endl;
        std::cout << "native error code: " << NativeError << std::endl;
    }
}

void setupOdbcEnvironment(SQLRETURN &ret, SQLHENV &hdlEnv, SQLHDBC &hdlDbc) {
    // Set up the ODBC environment
    ret = SQLAllocHandle(SQL_HANDLE_ENV, SQL_NULL_HANDLE, &hdlEnv); 
    assert(SQL_SUCCEEDED(ret)); 
    // Tell ODBC that the application uses ODBC 3.
    ret = SQLSetEnvAttr(hdlEnv, SQL_ATTR_ODBC_VERSION,
    (SQLPOINTER) SQL_OV_ODBC3, SQL_IS_UINTEGER);
    assert(SQL_SUCCEEDED(ret)); 
    // Allocate a database handle.
    ret = SQLAllocHandle(SQL_HANDLE_DBC, hdlEnv, &hdlDbc); 
    assert(SQL_SUCCEEDED(ret)); 
}

void connectToDB(
    SQLRETURN &ret,
    SQLHDBC &hdlDbc,
    std::string accessToken,
    std::string refreshToken,
    std::string clientId,
    std::string clientSecret,
    std::string tokenUrl) {
    // Connect to the database
    std::cout << "Connecting to database." << std::endl;
    ret = SQLDriverConnect(hdlDbc, NULL, (SQLCHAR *) ("DSN=VerticaDSN;PWD=;OAuthAccessToken=" + accessToken + ";OAuthRefreshToken=" + refreshToken +";OAuthClientId=" + clientId + ";OAuthClientSecret=" + clientSecret + ";OAuthTokenUrl=" + tokenUrl).c_str(), SQL_NTS, NULL, 0, NULL, false);
    if(!SQL_SUCCEEDED(ret)) {
        std::cout << "Could not connect to database" << std::endl;
        reportError<SQLHDBC>(SQL_HANDLE_DBC, hdlDbc);
        exit(EXIT_FAILURE);
    }
    std::cout << "Connected to database." << std::endl;
}

void executeQuery(SQLRETURN &ret, SQLHDBC hdlDbc, SQLHSTMT &hdlStmt, SQLBIGINT &table_id, SQLTCHAR (&table_name)[256]) {
    // Set up a statement handle
    SQLAllocHandle(SQL_HANDLE_STMT, hdlDbc, &hdlStmt);
    assert(SQL_SUCCEEDED(ret)); 

    // Execute a query to get the names and IDs of all tables in the schema
    // search path (usually public).
    ret = SQLExecDirect( hdlStmt, (SQLCHAR*)"SELECT table_id, table_name "
        "FROM tables ORDER BY table_name", SQL_NTS );

    if(!SQL_SUCCEEDED(ret)) { 
        std::cout << "Error executing statement." << std::endl;
        reportError<SQLHDBC>(SQL_HANDLE_STMT, hdlStmt);
        exit(EXIT_FAILURE);
    }    
    // Query succeeded, so bind two variables to the two columns in the 
    // result set,
    std::cout << "Fetching results..." << std::endl;
    ret = SQLBindCol(hdlStmt, 1, SQL_C_SBIGINT, (SQLPOINTER)&table_id, 
    sizeof(table_id), NULL);
    ret = SQLBindCol(hdlStmt, 2, SQL_C_TCHAR, (SQLPOINTER)table_name, 
    sizeof(table_name), NULL);
}

void printResults(SQLRETURN &ret, SQLHSTMT &hdlStmt, SQLBIGINT &table_id, SQLTCHAR (&table_name)[256]) {
    // Loop through the results, 
    while( SQL_SUCCEEDED(ret = SQLFetchScroll(hdlStmt, SQL_FETCH_NEXT,1))) {
        // Print the bound variables, which now contain the values from the
        // fetched row.
        std::cout << table_id << " | " << table_name << std::endl;
    }


    // See if loop exited for reasons other than running out of data
    if (ret != SQL_NO_DATA) {
        // Exited for a reason other than no more data... report the error.
        reportError<SQLHDBC>( SQL_HANDLE_STMT, hdlStmt );    
    }
}

void cleanUp(SQLRETURN &ret, SQLHDBC &hdlDbc, SQLHSTMT &hdlStmt, SQLHENV &hdlEnv) {
    // Clean up by shutting down the connection
    std::cout << "Free handles." << std::endl;
    ret = SQLDisconnect( hdlDbc );
    if(!SQL_SUCCEEDED(ret)) {
        std::cout << "Error disconnecting. Transaction still open?" << std::endl;
        exit(EXIT_FAILURE);
    }    
    SQLFreeHandle(SQL_HANDLE_STMT, hdlStmt);
    SQLFreeHandle(SQL_HANDLE_DBC, hdlDbc); 
    SQLFreeHandle(SQL_HANDLE_ENV, hdlEnv);  
    exit(EXIT_SUCCESS);
}

int main(int argc, char** argv)
{
    std::string accessToken;
    std::string refreshToken;
    std::string clientId;
    std::string clientSecret;
    std::string tokenUrl;
    static struct option long_options[] = {
        {"access-token", required_argument, 0, 'a'},
        {"refresh-token", required_argument, 0, 'b'},
        {"client-id", required_argument, 0, 'c'},
        {"client-secret", required_argument, 0, 'd'},
        {"token-url", required_argument, 0, 'e'},
    };
    int c;
    while (1) {
        int option_index = 0;
        c = getopt_long(argc, argv, "a:b:c:d:e:", long_options, &option_index);
        if (c == -1)
            break;
        switch (c) {
            case 'a':
                accessToken = optarg;
                break;
            case 'b':
                refreshToken = optarg;
                break;
            case 'c':
                clientId = optarg;
                break;
            case 'd':
                clientSecret = optarg;
                break;
            case 'e':
                tokenUrl = optarg;
                break;
            default:
                break;
        }
    }
    SQLRETURN ret;
    SQLHENV hdlEnv;
    SQLHDBC hdlDbc;
    setupOdbcEnvironment(ret, hdlEnv, hdlDbc);
    connectToDB(ret, hdlDbc, accessToken, refreshToken, clientId, clientSecret, tokenUrl);

    SQLHSTMT hdlStmt;
    SQLBIGINT table_id;       // Holds the ID of the table.
    SQLTCHAR table_name[256]; // buffer to hold name of table
    executeQuery(ret, hdlDbc, hdlStmt, table_id, table_name);

    printResults(ret, hdlStmt, table_id, table_name);
    cleanUp(ret, hdlDbc, hdlStmt, hdlEnv);
}

