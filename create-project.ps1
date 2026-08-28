$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "zeus"
$psi.Arguments = "create sleep-test"
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.UseShellExecute = $false
$proc = [System.Diagnostics.Process]::Start($psi)
$proc.StandardInput.WriteLine("APP")
Start-Sleep -Seconds 1
$proc.StandardInput.WriteLine("4.0")
Start-Sleep -Seconds 1
$proc.StandardInput.WriteLine("n")
Start-Sleep -Seconds 1
$proc.StandardInput.WriteLine("n")
Start-Sleep -Seconds 3
$proc.StandardOutput.ReadToEnd()
$proc.WaitForExit()
