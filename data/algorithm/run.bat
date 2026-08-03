@echo off
setlocal

set "PYTHON_EXE=python"
set "INPUT_FILE=steel_data_cleaned.csv"
set "OUTPUT_FILE=output_sample.json"

if not "%~1"=="" set "INPUT_FILE=%~1"
if not "%~2"=="" set "OUTPUT_FILE=%~2"

echo Running production-energy optimization with Python...
"%PYTHON_EXE%" "%~dp0generate_plan.py" "%~dp0%INPUT_FILE%" "%~dp0%OUTPUT_FILE%"
echo Done. Please check %OUTPUT_FILE%.
pause
