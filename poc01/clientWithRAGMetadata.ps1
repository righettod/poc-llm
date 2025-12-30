Clear-Host
$base = "http://localhost:8080"
$requestHeaders = @{ "Content-Type" = "text/plain" }
Write-Host -ForegroundColor Magenta "[User Role   ]:"
$userRole = Read-Host
while ($true) {
    Write-Host -ForegroundColor Yellow "[User Message]:"
    $userMessage = Read-Host
    Write-Host -ForegroundColor Cyan "[LLM  Response]:"
    $response = Invoke-RestMethod -Uri "$base/askWithUserRole/$userRole" -Method Post -Headers $requestHeaders -Body $userMessage -ResponseHeadersVariable responseHeaders
    Write-Host $response
    Write-Host "--------------------"
}