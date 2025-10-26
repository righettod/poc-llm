Clear-Host
$base = "http://localhost:8080"
$sessionHeaderName = "X-Chat-Session-Id"
$requestHeaders = @{ "Content-Type" = "text/plain" }
$sessionId = "000000000000"
while ($true)
{
    Write-Host -ForegroundColor Yellow "[$sessionId][User Message]:"
    $userMessage = Read-Host
    Write-Host -ForegroundColor Cyan "[$sessionId][LLM  Response]:"
    $response = Invoke-RestMethod -Uri "$base/ask" -Method Post -Headers $requestHeaders -Body $userMessage -ResponseHeadersVariable responseHeaders
    $requestHeaders[$sessionHeaderName] = $responseHeaders[$sessionHeaderName][0]
    $sessionId = $requestHeaders[$sessionHeaderName]
    Write-Host $response
    Write-Host "--------------------"
}