Clear-Host
$base = "http://localhost:8080"
$sessionId = Invoke-RestMethod -Uri "$base/start" -Method Get
$customHeaders = @{
    "Content-Type" = "text/plain"
    "X-Chat-Session-Id" = $sessionId
}
Write-Host "🧑‍💻 Chat session: " -NoNewline
Write-Host "$sessionId" -ForegroundColor Green
while ($true)
{
    Write-Host -ForegroundColor Yellow "[User Message]:"
    $userMessage = Read-Host
    Write-Host -ForegroundColor Cyan "[LLM  Response]: "
    $response = Invoke-RestMethod -Uri "$base/ask" -Method Post -Headers $customHeaders -Body $userMessage
    Write-Host $response
    Write-Host "--------------------"
}