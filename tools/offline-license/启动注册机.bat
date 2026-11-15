@echo off
setlocal
cd /d "%~dp0"
python --version >nul 2>nul
if errorlevel 1 (
  echo 未检测到 Python。请先安装 Python 3.10 或更高版本，并勾选 Add Python to PATH。
  pause
  exit /b 1
)
python -c "import cryptography" >nul 2>nul
if errorlevel 1 (
  echo 正在安装注册机依赖...
  python -m pip install -r requirements.txt
  if errorlevel 1 (
    echo 依赖安装失败，请检查网络或手动运行：python -m pip install -r requirements.txt
    pause
    exit /b 1
  )
)
python register_gui.py
if errorlevel 1 pause
