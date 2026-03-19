FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src

COPY backend/CourseSync.Api/CourseSync.Api.csproj backend/CourseSync.Api/
RUN dotnet restore backend/CourseSync.Api/CourseSync.Api.csproj

COPY . .
RUN dotnet publish backend/CourseSync.Api/CourseSync.Api.csproj -c Release -o /app/publish

FROM mcr.microsoft.com/dotnet/aspnet:8.0
WORKDIR /app
COPY --from=build /app/publish .

EXPOSE 8080
ENV ASPNETCORE_URLS=http://+:8080

ENTRYPOINT ["dotnet", "CourseSync.Api.dll"]
