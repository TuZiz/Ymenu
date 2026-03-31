@ECHO OFF
SETLOCAL
SET WRAPPER_DIR=%~dp0.gradle\wrapper
IF NOT EXIST "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
IF NOT EXIST "%WRAPPER_DIR%\gradle-wrapper.jar" (
  curl -L -o "%WRAPPER_DIR%\gradle-wrapper.jar" https://raw.githubusercontent.com/gradle/gradle/v8.10.2/gradle/wrapper/gradle-wrapper.jar
)
IF NOT EXIST "%WRAPPER_DIR%\gradle-wrapper.properties" (
  > "%WRAPPER_DIR%\gradle-wrapper.properties" (
    echo distributionBase=GRADLE_USER_HOME
    echo distributionPath=wrapper/dists
    echo distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.2-bin.zip
    echo networkTimeout=10000
    echo validateDistributionUrl=true
    echo zipStoreBase=GRADLE_USER_HOME
    echo zipStorePath=wrapper/dists
  )
)
java -classpath "%WRAPPER_DIR%\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
