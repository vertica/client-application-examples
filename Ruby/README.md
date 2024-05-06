# Overview

As of now, there is no native Ruby driver for Vertica. Here we provide a sample script that uses [JRuby](https://www.jruby.org/) (a Java implementation of Ruby on the JVM) and Vertica JDBC to connect to the database.

# Pre-requisites

- JRuby (tested with version 9.3.8.0 on Linux)
- Vertica JDBC

# Running the Ruby sample app

Run the sample app using the following command:
```
jruby RubySampleApp.rb
```

For more instructions about JDBC, please refer to [Vertica documentation](https://docs.vertica.com/latest/en/connecting-to/client-libraries/accessing/java/).
