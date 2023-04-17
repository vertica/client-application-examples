# ADO.NET Sample App

## Prerequisites

- .NET Core 3.1+, or .NET Framework 4.6.1+
- The Vertica ADO.NET DLL

Note that for Linux support you will need .NET Core.  It is recommended to use .NET 6.0 since it is a long-term support version.

Note that in the future the Vertica ADO.NET driver will be made available via NuGet.  Until then, you get a copy from the Vertica Windows installer or contact Vertica directly.

## Install .NET

Follow the instructions for [Windows](https://learn.microsoft.com/en-us/dotnet/core/install/windows?tabs=net60) or [Linux](https://learn.microsoft.com/en-us/dotnet/core/install/linux).

For example, do the following to install .NET 6.0 on CentOS 7:
```sh
sudo rpm -Uvh https://packages.microsoft.com/config/centos/7/packages-microsoft-prod.rpm
sudo yum install -y dotnet-sdk-6.0

dotnet --version
```

## Running the ADO.NET sample app

1. Copy the Vertica ADO.NET driver DLL (`Vertica.Data.dll`) to the `lib` folder.
2. Modify the `app.config` connection string and query as needed (note that `app.config` is copied to the build directory as `AdoDotNetSampleApp.dll.config`)
3. Build the application:
```sh
# Windows
dotnet build

# Linux
# Note that you may need to use sudo on Linux, depending on how dotnet was installed
dotnet build -property:RuntimeIdentifier=linux-x64
```
4. Run the application:
```sh
# Windows
# Note that other .NET builds are also generated, this example just runs the .NET 6.0 build
.\bin\Release\net6.0\win-x64\AdoDotNetSampleApp.exe

# Linux
./bin/Release/net6.0/linux-x64/AdoDotNetSampleApp
```
