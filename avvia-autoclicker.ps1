# Avvia l'autoclicker passando le opzioni, es.:
#   powershell -NoProfile -ExecutionPolicy Bypass -File avvia-autoclicker.ps1 --cps 0
# Se autoclicker.exe non e' accanto allo script lo scarica dall'ultima Release.
# --aggiorna forza il download della versione piu' recente.
$ErrorActionPreference = 'Stop'
$exe = Join-Path $PSScriptRoot 'autoclicker.exe'
$url = 'https://github.com/l13291221-cmyk/registratore-lezioni-/releases/download/autoclicker-latest/autoclicker.exe'

$opzioni = @($args | Where-Object { $_ -ne '--aggiorna' })
if ($opzioni.Count -ne $args.Count -or -not (Test-Path $exe)) {
    Write-Host "Scarico autoclicker.exe..."
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $url -OutFile $exe -UseBasicParsing
    Unblock-File $exe
}

if ($opzioni.Count) { Start-Process -FilePath $exe -ArgumentList $opzioni }
else { Start-Process -FilePath $exe }
Write-Host "Autoclicker avviato. F6 = avvia/ferma, Esc = stop."
