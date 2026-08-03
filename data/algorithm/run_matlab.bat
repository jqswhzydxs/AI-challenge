@echo off
setlocal

set "MATLAB_EXE=matlab"
if exist "D:\MATLAB\R2026a\bin\matlab.exe" set "MATLAB_EXE=D:\MATLAB\R2026a\bin\matlab.exe"

echo Running production-energy optimization with MATLAB legacy entrypoint...
"%MATLAB_EXE%" -batch "main('steel_data_cleaned.csv','output_sample.json')"
echo Done. Please check output_sample.json.
pause
