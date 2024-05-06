#!/usr/bin/env jruby

require 'java'

# There are a couple of ways to tell JRuby where to find classes.  The first
# is with the JVM CLASSPATH environmental variable:
#
#   CLASSPATH=/path/to/vertica/jdbc.jar:$CLASSPATH ./RubySampleApp.rb
#
# The other, which we're going to use here, is to require the jar file in
# the program itself:
require '/path/to/vertica-jdbc-x.x.x-x.jar'

# import Java libraries
java_import 'java.lang.System'
java_import 'java.sql.DriverManager'
java_import 'java.sql.SQLException'
java_import 'java.sql.SQLInvalidAuthorizationSpecException'
java_import 'java.util.Properties'


begin
    myProp = Properties.new()
    myProp.put("user", "dbadmin")
    myProp.put("loginTimeout", "35")

    conn = DriverManager.getConnection("jdbc:vertica://localhost:5433/dbname", myProp)
    System.out.println('Connected!')

    # Execute queries through JDBC
    stmt = conn.createStatement()
    sql = "SELECT node_name, start_time, end_time, average_cpu_usage_percent FROM v_monitor.cpu_usage LIMIT 5"
    System.out.println("Query: " + sql)
    rs = stmt.executeQuery(sql)
    System.out.println("Query result: ")
    while rs.next()
        System.out.println(rs.getString(1) + " | " + rs.getString(2) + " | " + 
                           rs.getString(3) + " | " + rs.getString(4))
    end
    conn.close()
rescue SQLInvalidAuthorizationSpecException => e
    System.out.print("Could not log into database: ")
    System.out.println(e)
    System.out.println("Check the login credentials and try again.")
rescue SQLException => e
    System.out.println(e)
end
