@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup batch script, version 3.3.2
@REM ----------------------------------------------------------------------------
@IF "%__MVNW_ARG0_NAME__%"=="" (SET "MVN_CMD=mvn") ELSE (SET "MVN_CMD=%__MVNW_ARG0_NAME__%")
@SET MAVEN_PROJECTBASEDIR=%~dp0
@SET MAVEN_WRAPPER_DIR=%MAVEN_PROJECTBASEDIR%.mvn\wrapper
@SET MAVEN_WRAPPER_PROPERTIES=%MAVEN_WRAPPER_DIR%\maven-wrapper.properties
@SET MVNW_REPOURL=
@SET MVNW_USERNAME=
@SET MVNW_PASSWORD=

@FOR /F "usebackq tokens=1,2 delims==" %%a IN ("%MAVEN_WRAPPER_PROPERTIES%") DO (
  IF "%%a"=="distributionUrl" SET DISTRIBUTION_URL=%%b
)

@SET MAVEN_DIST_NAME=apache-maven-3.9.9
@SET MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\%MAVEN_DIST_NAME%-bin\%MAVEN_DIST_NAME%
@SET MVN_EXE=%MAVEN_HOME%\bin\mvn.cmd

@IF NOT EXIST "%MVN_EXE%" (
  @ECHO Downloading Maven 3.9.9...
  @SET DL_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip
  @SET DEST_DIR=%USERPROFILE%\.m2\wrapper\dists\%MAVEN_DIST_NAME%-bin
  @MKDIR "%DEST_DIR%" 2>NUL
  @powershell -Command "Invoke-WebRequest -Uri '%DL_URL%' -OutFile '%DEST_DIR%\%MAVEN_DIST_NAME%-bin.zip'"
  @powershell -Command "Expand-Archive -Path '%DEST_DIR%\%MAVEN_DIST_NAME%-bin.zip' -DestinationPath '%DEST_DIR%' -Force"
)

@SET JAVA_HOME_CMD=%JAVA_HOME%
@IF "%JAVA_HOME_CMD%"=="" (
  @SET JAVA_HOME_CMD=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot
)

@SET JAVA_EXE=%JAVA_HOME_CMD%\bin\java.exe
@IF NOT EXIST "%JAVA_EXE%" (
  @ECHO Java not found at %JAVA_EXE%
  @EXIT /B 1
)

@"%MVN_EXE%" %*
