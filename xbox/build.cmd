@echo off
REM RGBTv Xbox — builds a sideloadable .msix (Release x64) with a self-signed test certificate.
REM Requirements: Windows 10/11, Visual Studio 2022 with "Universal Windows Platform development" workload
REM (Windows 11 SDK 10.0.26100 + .NET Native), Node.js. Run from a "Developer Command Prompt for VS 2022".
setlocal
cd /d "%~dp0"
node sync.js || exit /b 1
if not exist RGBTv.Xbox\RGBTv_Test.pfx (
  echo Creating self-signed test certificate (RGBTv_Test.pfx, password: rgbtv) ...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$c=New-SelfSignedCertificate -Type Custom -Subject 'CN=RGBTv' -KeyUsage DigitalSignature -FriendlyName 'RGBTv Test' -CertStoreLocation 'Cert:\CurrentUser\My' -TextExtension @('2.5.29.37={text}1.3.6.1.5.5.7.3.3','2.5.29.19={text}'); $p=ConvertTo-SecureString -String 'rgbtv' -Force -AsPlainText; Export-PfxCertificate -Cert $c -FilePath 'RGBTv.Xbox\RGBTv_Test.pfx' -Password $p | Out-Null" || exit /b 1
)
msbuild RGBTv.Xbox.sln /restore /p:Configuration=Release /p:Platform=x64 /p:AppxBundle=Never /p:UapAppxPackageBuildMode=SideloadOnly /p:AppxPackageSigningEnabled=true /p:PackageCertificateKeyFile=RGBTv_Test.pfx /p:PackageCertificatePassword=rgbtv /p:AppxPackageDir="%~dp0release\" /m || exit /b 1
echo.
echo Done. Package folder: %~dp0release\  (upload the .msix + Dependencies\x64\*.appx in Xbox Device Portal)
endlocal
