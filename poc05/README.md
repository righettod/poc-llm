# POC n°5

## General goals

🎯 The goal of this POC is to explore the capability to leverage Claude code feature to enrich a [User Story](https://www.atlassian.com/agile/project-management/user-stories) with possible attacks that can affect the feature represented by the user story.

🤔 I want to explore the way to do threat modeling with Claude code. Precisely, I want to explore such combination leveraging Claude code:

1. Define the list of possible attacks (represented by a collection of [evil user story](https://dzone.com/articles/adding-appsec-agile-security)) to enrich the user story.
2. Use a collection of [security-oriented skills](https://github.com/righettod/code-assistant-skills-security-utils) to generate "secure code by default" for the target feature.
3. Validate that the generated code handles the attacks listed in the user story.

🔬 My final objective is to make *security aspect transparent for a DevOps team* by leveraging the power of code specialized language models and a coding assistant.

## Content

> [!IMPORTANT]
> For the **reference** files, when possible, a GitHub reference was added pointing directly to the file (ideally a markdown one) to help the web search tool of claude code to extract useful data.

📄 All files of the POC are stored in the folder `.claude/skills/evil-user-stories` ([ref](../.claude/skills/evil-user-stories/)).

📖 Assuming this compact user story content (represented by `[USER_STORY_CONTENT]` in following instructions): `As a registered user, I want to upload a file from my device to the platform, So that I can share necessary documentation for my account processing. File type supported is 'PDF'. The target technology is 'PYTHON'`.

🧑‍💻 Example of usage from within a **claude code** session:

```text
/evil-user-stories [USER_STORY_CONTENT].

● I'll start by reading the reference files and fetching the external resources needed for this analysis.
...

● Good, I have the reference data. Now let me compile the complete threat model analysis.
...

● Now saving the full content to "IdentifiedEvilUserStories.md".
```

🧑‍💻 Same from the **claude code** command line:

```bash
claude --verbose --output-format stream-json --max-turns 10 --allowedTools "Read,Grep,Glob,WebSearch,WebFetch,Write,Bash,Skill" -p "[USER_STORY_CONTENT]"
```
