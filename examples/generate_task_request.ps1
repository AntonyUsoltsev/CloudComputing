# PowerShell скрипт для генерации запроса задачи

$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptPath

Write-Host "Working directory: $scriptPath"

# Компилируем класс
javac SimpleCalculator.java
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to compile SimpleCalculator.java"
    exit 1
}

# Конвертируем байткод в base64
$classFile = Join-Path $scriptPath "SimpleCalculator.class"
if (-not (Test-Path $classFile)) {
    Write-Error "SimpleCalculator.class not found at $classFile"
    exit 1
}
$classBytes = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($classFile))

# Создаем Java файл для сериализации аргументов
$serializeArgsCode = @"
import java.io.*;

public class SerializeArgs {
    public static void main(String[] args) throws Exception {
        Object[] arguments = {32, 44};
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(arguments);
        oos.close();
        
        String base64 = java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
        System.out.println(base64);
    }
}
"@
$serializeArgsFile = Join-Path $scriptPath "SerializeArgs.java"
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($serializeArgsFile, $serializeArgsCode, $utf8NoBom)

# Компилируем и запускаем сериализацию
Write-Host "Compiling SerializeArgs.java..."
javac SerializeArgs.java
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to compile SerializeArgs.java"
    exit 1
}

Write-Host "Serializing arguments..."
$argsBytes = java SerializeArgs 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to serialize arguments: $argsBytes"
    exit 1
}
$argsBytes = $argsBytes.Trim()

# Генерируем UUID
$taskId = [guid]::NewGuid().ToString()

# Создаем JSON запрос
$json = @{
    taskId = $taskId
    className = "SimpleCalculator"
    methodName = "sum"
    classBytes = $classBytes
    arguments = $argsBytes
    codeHash = "simple-calc-v1"
    metadata = @{
        priority = 1
        timeoutMs = 5000
    }
} | ConvertTo-Json -Depth 10

$outputFile = Join-Path $scriptPath "task_request.json"
$json | Out-File -FilePath $outputFile -Encoding UTF8

Write-Host ""
Write-Host "Task request saved to $outputFile"
Write-Host "Class bytes length: $($classBytes.Length)"
Write-Host "Arguments bytes length: $($argsBytes.Length)"
