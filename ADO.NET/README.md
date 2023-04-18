# ADO.NET Sample App

## Prerequisites

- .NET Core 3.1+, or .NET Framework 4.6.1+
- The Vertica ADO.NET DLL

:information_source: Note that you will need to use .NET Core, not .NET Framework, if using Linux or Mac.  It is recommended to use .NET 6.0 since it is a long-term support version.

:information_source: Note that in the future the Vertica ADO.NET driver will be made available via NuGet.  Until then, you get contact Vertica for a copy.

## Install .NET

Follow the instructions for [Windows](https://learn.microsoft.com/en-us/dotnet/core/install/windows?tabs=net60), [Linux](https://learn.microsoft.com/en-us/dotnet/core/install/linux), or [macOS](https://learn.microsoft.com/en-us/dotnet/core/install/macos).

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
dotnet build -property:RuntimeIdentifier=linux-x64
```
4. Run the application (for each supported target .NET version):
```sh
# Windows
.\bin\Release\net6.0\win-x64\AdoDotNetSampleApp.exe
.\bin\Release\netcoreapp3.1\win-x64\AdoDotNetSampleApp.exe
.\bin\Release\net462\win-x64\AdoDotNetSampleApp.exe

# Linux
./bin/Release/net6.0/linux-x64/AdoDotNetSampleApp
./bin/Release/netcoreapp3.1/linux-x64/AdoDotNetSampleApp
```

:warning: When using Linux, if you run into permissions issues you can run `dotnet build` and other commands with `sudo`.