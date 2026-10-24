# POC n°9

## General goals

🎯 Explore how a coding assistant can be leveraged to ensure that a project is always compatible with the latest version of all direct external dependencies.

🤔 With AI, I think more and more vulnerabilities will be detected in libraries. The development team can only fix these vulnerabilities by updating the libraries, but to do so, the project must support the latest version of all the libraries it uses (I am referring here to direct dependencies, not transitive dependencies). So I thought of creating a skill that would instruct the system to update the project's dependency management file with the latest version of all libraries, and then run all unit tests. If the tests fail, the skill would provide the code to modify to ensure compatibility.

🔬 A dedicated skill called [dependency-upgrader](../.claude/skills/dependency-upgrader/SKILL.md) to explore the concepts, idea and possbilities.

🧑‍💻 Use the following command to execute the skill against the [sandbox project](sandbox/): `/dependency-upgrader poc09/sandbox`

📦 In the [sandbox project](sandbox/), use the version **3.0.0** of the following artefacts to use the version vulnerable to CVE-2025-66516:

```xml
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parser-pdf-module</artifactId>
    <version>3.0.0</version>
</dependency>
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>3.0.0</version>
</dependency>
```
