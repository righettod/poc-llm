while($true){
    Write-Host -ForegroundColor Yellow "[User Message]:"
    $userMessage = Read-Host
    Write-Host -ForegroundColor Cyan "[LLM Response]: "
    curl --silent -H "Content-Type: text/plain" -d "$userMessage" http://localhost:8080/ask | jq
    Write-Host "--------------------"
}