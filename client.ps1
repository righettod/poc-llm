while($true){
	$userMessage = Read-Host -Prompt "[User Message]🧑‍💻 "
	Write-Host "`n"
	Write-Host -ForegroundColor Cyan "[LLM Response]🤖 : "
	curl -H "Content-Type: text/plain" -d "$userMessage" http://localhost:8080/ask
	Write-Host "`n"
	Write-Host "--------------------"
	Write-Host "`n"
}