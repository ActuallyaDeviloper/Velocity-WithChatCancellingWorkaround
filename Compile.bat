del /Q proxy\build\libs\*
cmd /C "..\Gradle\bin\Gradle.bat" build
rename proxy\build\libs\velocity-proxy-*-SNAPSHOT-all.jar velocity.jar
copy proxy\build\libs\velocity.jar ..\Velocity\velocity.jar
pause