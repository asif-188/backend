@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"
if "%SPRING_DATA_MONGODB_URI%"=="" set "SPRING_DATA_MONGODB_URI=mongodb+srv://b.net/globalisor?appName=smartcosec"
call mvnw.cmd spring-boot:run
