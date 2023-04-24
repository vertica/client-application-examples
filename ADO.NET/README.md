# ADO.NET Sample App

## Overview

The Vertica ADO.NET driver provides a data source for Vertica, so a client can connect and read/write data.  The driver is written in C# and built on .NET.

See the available data types, commands, and other information [here](https://docs.vertica.com/latest/en/connecting-to/client-libraries/accessing/c/).

## Prerequisites

- .NET Core 3.1+, or .NET Framework 4.6.1+
- A running Vertica database

:information_source: Note that you will need to use .NET Core, not .NET Framework, if using Linux or Mac.  It is recommended to use .NET 6.0 since it is a long-term support version.

## Install .NET

Follow the instructions for [Windows](https://learn.microsoft.com/en-us/dotnet/core/install/windows?tabs=net60), [Linux](https://learn.microsoft.com/en-us/dotnet/core/install/linux), or [macOS](https://learn.microsoft.com/en-us/dotnet/core/install/macos).

## Start a Vertica server

If you don't already have a Vertica instance running, it is recommended to use the Vertica Community Edition (CE) Docker image.

Start Vertica CE in Docker:
```sh
docker run -d \
    -p 5433:5433 -p 5444:5444 \
    -v vertica-data:/data \
    --name vertica-ce \
    --restart=unless-stopped \
    vertica/vertica-ce:latest
```

## Build and run the ADO.NET sample app

1. Modify the `app.config` connection string and query as needed (note that `app.config` is copied to the build directory as `AdoDotNetSampleApp.dll.config` during the build)
2. Build the application:
```sh
dotnet build
```
3. Run the application:
```sh
# Windows
.\bin\Release\net6.0\win-x64\AdoDotNetSampleApp.exe

# Linux
./bin/Release/net6.0/linux-x64/AdoDotNetSampleApp

# Mac
./bin/Release/net6.0/osx-x64/AdoDotNetSampleApp
```

:information_source: Note that the sample app produces binaries for multiple target .NET versions: .NET 6.0, .NET Core 3.1, and .NET Framework 4.6.2 (Windows only).  The above example only shows .NET 6.0.

### Referencing a local NuGet package

If you have the Vertica ADO.NET driver NuGet package, you can update the project to use the package directly:

1. Copy the Vertica ADO.NET driver NuGet package (`Vertica.Data.<VERSION>.nupkg`) to the `packages` folder
2. Add the following to a `nuget.config` file in this directory:
```xml
<?xml version="1.0" encoding="utf-8" ?>
<configuration>
  <packageSources>
    <add key="LocalPackages" value="packages" />
  </packageSources>
</configuration>
```
3. Update the Vertica.Data package reference version in the project file as needed
4. Build and run the application, same as above

### Referencing a DLL

If you have the Vertica ADO.NET driver DLL, you can update the project to use the DLL directly:

1. Copy the Vertica ADO.NET driver DLL (`Vertica.Data.dll`) to the `lib` folder
2. Replace the existing packages reference in the project file with the following:
```xml
<ItemGroup>
  <Reference Include="Vertica.Data">
    <HintPath>lib\Vertica.Data.dll</HintPath>
  </Reference>
</ItemGroup>

<ItemGroup>
  <PackageReference Include="Microsoft.Win32.Registry" Version="5.0.0" />
  <PackageReference Include="System.Configuration.ConfigurationManager" Version="6.0.0" />
</ItemGroup>
```
3. Build and run the application, same as above
