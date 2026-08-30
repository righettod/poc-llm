# POC n°7

## General goals

🎯 The goal of this POC is to explore for an SLDC every step in which GenIA can be used and possibility how?

🤔 My real goal is to have a visual representation of possibilities even if it's far-fetched or paranoid.

ℹ️ Note about choice and way of working:

* I use many times a dedicated **skill** in order to leverage as much as possible, the capabilities of a coding assistant as it can easily access to the codebase.
* I used Claude to validate/challenge/explore my base ideas, from Claude code perspective, as it is the coding assistant I used.

## Ideas

```mermaid
kanban
    step00[Planning/Requirements]
        step00-task00["Use a skill to create the collection of evil user stories for each user story."]
    step01[Design/Development]
        step01-task00["Use skills with predefined security checks for specific type of features. The goal is to have any code generated with the expected security check in place."]
        step01-task01["Use a skill to generate a test case for each evil user stories to ensure that the code base handle it."]
        step01-task02["Use a skill that will ensure that every access point of the codebase exposed to users is protected by an authorization constraint.<br/>⚠️ **also Enforce this in CI during the Testing phase.**"] 
        step01-task03["Use a skill that will ensure the configuration properties used into the app do not contain unsafe value or default value that is unsafe.<br/>⚠️ **also Enforce this in CI during the Testing phase.**"]       
        step01-task04["Use a skill that will ensure that every access point of the codebase exposed to users defines the explicit HTTP method used, the media type consumed, the media type produced.<br/>⚠️ **also Enforce this in CI during the Testing phase.**"]                  
        step01-task05["Use a skill that set up the development environment from scratch for a newcomer in the DevOps teams using all teams and company conventions including security ones."]                          
    step02[Testing]    
        step02-task00["In CI use a skill to review the findings of a SAST scan to remove false positive."]
        step02-task01["In CI use a skill to analyze the code from user input perspective to identify flaws reachable by a user."]
        step02-task02["In CI use a skill to update the project documentation based on the real state of the code base.<br/>💡 Generate it in markdown format and store it into the SCM."]     
        step02-task03["In CI, in a sandbox (docker container) to handle compromised release, try to use the latest release of every library, if something fails then ask to the code assistant to propose a PR with the code to update."]     
    step03[Deploy]              
        step03-task00["Use a skill to generate a list of files from the code base that must never be present in the deployed app in order to use such file in a fuzzing tool to check they are not present."]
        step03-task01["Leverage the project documentation, RAG (local db) and a local model to create a small chatbot to easily explore the documentation of the project. Useful to integrate newcomers. See the 'docs-chatbot' reference for an example."]        
        step03-task02["Integrate the content of the SBOM in the RAG data to allow searching for dependencies in the chatbot."]                
```

## References

* <https://www.headmind.com/evil-user-stories/>
* <https://mermaid.js.org/syntax/kanban.html>
* <https://github.com/righettod/toolbox-pentest-web#docs-chatbot>
* <https://github.com/righettod/toolbox-pentest-web/tree/master/docs-chatbot>
* <https://github.com/resources/articles/what-is-an-sbom-software-bill-of-materials>
