# ADO.NET Example

## Prerequisites

- .NET Core 2.0+, or .NET Framework 4.6.1+
- The Vertica ADO.NET DLL

Note that for Linux support you will need .NET Core.  It is recommended to use .NET 6.0 since it is a long-term support version.

Note that in the future the Vertica ADO.NET driver will be made available via NuGet.  However, until then get a copy from the Vertica Windows installer or contact Vertica directly.

## Install .NET

The following is an example of how to install .NET 6.0 on CentOS 7:
```sh
sudo rpm -Uvh https://packages.microsoft.com/config/centos/7/packages-microsoft-prod.rpm
sudo yum install -y dotnet-sdk-6.0

dotnet --version
```

## Running the ADO.NET sample app

The following example is for Linux, the same instructions also apply to Windows (simply adjust the file paths as needed).

1. Copy the Vertica ADO.NET driver DLL (`Vertica.Data.dll`) to the `lib` folder.

2. Modify the `app.config`:
```
# Update the connection string and query (the default is for Vertica CE in Docker)
vi app.config

# If the application has already been built, modify the copy under the build dir
sudo vi ./bin/Release/net6.0/linux-x64/Test.dll.config
```

3. Build the application:
```sh
sudo donet build
```

4. Run the application:
```sh
./bin/Release/net6.0/linux-x64/Test
```
