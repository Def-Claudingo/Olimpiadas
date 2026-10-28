param([string]$Archivo = (Join-Path $PSScriptRoot ("../datos/demo-" + [guid]::NewGuid().ToString("N") + ".mapa")))

$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $false
$proyecto = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $proyecto "target/olimpiadas-java-1.0.0.jar"
$ejemplo = Join-Path $proyecto "ejemplos/aceptacion.mapa"
$exportado = $Archivo + ".exportado"
$recargado = $Archivo + ".recargado"

if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
    throw "Primero ejecute mvn verify desde OlimpiadasJava."
}
if (Test-Path -LiteralPath $Archivo) {
    throw "El archivo de demostracion ya existe. Use otra ruta para conservar sus datos."
}

function Invoke-Mapa {
    param([string[]]$Argumentos, [int]$CodigoEsperado = 0, [string]$Estado = $Archivo)
    & java -jar $jar --archivo $Estado @Argumentos
    if ($LASTEXITCODE -ne $CodigoEsperado) {
        throw "Codigo inesperado: $LASTEXITCODE; esperado: $CodigoEsperado."
    }
}

Invoke-Mapa -Argumentos @("importar", $ejemplo)
Invoke-Mapa -Argumentos @("ruta", "A", "B")
Invoke-Mapa -Argumentos @("ruta", "A", "AISLADA")
Invoke-Mapa -Argumentos @("ruta", "A", "A")
$hashAnterior = (Get-FileHash -LiteralPath $Archivo -Algorithm SHA256).Hash
Invoke-Mapa -Argumentos @("conexion", "B", "A", "-1") -CodigoEsperado 2
if ((Get-FileHash -LiteralPath $Archivo -Algorithm SHA256).Hash -ne $hashAnterior) {
    throw "La conexion negativa modifico el archivo."
}
Invoke-Mapa -Argumentos @("mapa")
Invoke-Mapa -Argumentos @("historial")
Invoke-Mapa -Argumentos @("exportar", $exportado)
Invoke-Mapa -Argumentos @("importar", $exportado) -Estado $recargado
Invoke-Mapa -Argumentos @("ruta", "A", "B") -Estado $recargado
Write-Output "DEMOSTRACION_COMPLETA: cada comando se ejecuto en un proceso Java nuevo."
Write-Output "Estado original: $Archivo"
Write-Output "Exportacion: $exportado"
Write-Output "Estado importado: $recargado"
