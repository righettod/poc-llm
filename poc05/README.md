# POC n°5

## General goals

🎯 The goals of this POC is explore the capability to leverage Claude code feature to enrich a [User Story](https://www.atlassian.com/agile/project-management/user-stories) with possible attacks that can affect the feature represented by the user story.

🤔 I want to explore the way to do threat modeling with Claude code. Precisely, I want to explore such combination leveraging Claude code:

1. Define the list possible attacks (represented by a collection of [evil user story](https://dzone.com/articles/adding-appsec-agile-security)) to enrich the user story.
2. Use a collection of [security oriented skills](https://github.com/righettod/code-assistant-skills-security-utils) to generate "secure code by default" for the target feature.
3. Validate that the generated code handle the attacks listed in the user story.

🔬 My final objective is to make *security aspect transparent for a DevOps team* by leveraging the power of code specialized language models and a coding assistant.

## Content

📄 All files of the POC are stored in folder `.claude/skills/evil-user-stories` ([ref](../.claude/skills/evil-user-stories/)).
