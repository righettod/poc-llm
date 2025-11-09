# POC n°1

## Topology

App using a local LLM with RAG.

## Technology stack

* [langchain4j](https://github.com/langchain4j/langchain4j) for the integration with a local LLM and have access to low level exchange with the LLM.
  * Every time it is possible I stick to the way proposed in [samples](https://github.com/langchain4j/langchain4j-examples/tree/main) in order to use the framework.
* [springboot](https://spring.io/projects/spring-boot) for exposing simple services.

## Run the POC

💻 Step 1 - Execute in a shell window:

```powershell
# ollama pull llama3.1:latest
PS> ollama run llama3.1:latest
```

💻 Step 2 - Execute the run configuration **StartApp** from Intellij IDEA.

💻 Now you can use the script [client.ps1](client.ps1) to exchange with the model.

## Pending test of attack vectors

✅ None.

## Notes about attack vectors

### 😈 Insert a file with malicious content for which the content will be returned to the app via the LLM on a specific user prompt causing the content to be retrieved via RAG

"Malicious" document inserted into the documents store and user prompt used to retrieve its content via the app + LLM:

![prt00](images/prt00.png)

### 😈 Sensitive information disclosure via RAG retrieval due to a file used to fill the embedding store and that was not expected to be because it contains sensitive or PII information


"Sensitive" document inserted into the documents store and user prompt used to retrieve its content via the app + LLM:

![prt01](images/prt01.png)

### 😈 Insert one or several files with false content to cause the LLM to return false information

Document inserted into the documents store  with "fake information" and user prompt used to retrieve its content via the app + LLM:

![prt02](images/prt02.png)

## List of attack vectors asked to ChatGPT

> [!WARNING]
> I only keep ones in my mind map that I was thinking as relevant for the context of my study.

My question:

```text
Give me a list of threats when developing an app using a LLM with RAG.

I have already identified these:

1) Insert a file with malicious content for which the content 
will be returned to the app via the LLM on a specific user prompt causing
 the content to be retrieved via RAG.

2) Insert a file with malicious content that exploit a vulnerability 
present into a library used to parse the file when the embedding store
is filled by an app.

Filter all your results to only keep RAG-Specific ones.
```

Its reply:

Below I expand that into a focused, RAG-only threat list (each entry explains *why* it’s RAG-specific and a one-line mitigation you can apply). I'm *only* listing threats that arise because a retrieval/augmentation step exists.

1. **Malicious content returned via retrieval that performs prompt-injection / jailbreak**
   * *Why RAG-specific:* content retrieved from the store becomes part of the model prompt/context, so attackers can place instructions that override system/user intent.
   * *Short mitigation:* sanitize/scrub retrieved text, apply instruction-filtering, and treat retrieved content as untrusted (use classifiers or metadata checks before concatenation).

2. **Malicious file exploiting a parser/library when embeddings/indexing are created** *(your #2)*
   * *Why RAG-specific:* the RAG pipeline often ingests and parses user documents to create chunks/embeddings, exposing parsers to adversarial inputs.
   * *Short mitigation:* sandbox parsing, use safe parsers, validate file types and run malware scanning before indexing.

3. **Data-poisoning of the vector store (poisoned documents/embeddings influence answers)**
   * *Why RAG-specific:* RAG depends on the store’s corpus; adding poisoned documents alters what’s retrieved and thus model output.
   * *Short mitigation:* vet uploaders, maintain write controls, version snapshots, and monitor retrieval drift/metrics.

4. **Embedding-space manipulation (crafted documents that change similarity ranking / cause hijacking)**
   * *Why RAG-specific:* attackers craft content whose embeddings are artificially close to many queries to be returned inappropriately.
   * *Short mitigation:* use anomaly detection on embedding distributions, restrict public writes, and combine multiple retrieval signals (metadata + dense + sparse).

5. **Index integrity / unauthorized modification (attacker modifies/deletes/reorders indexed docs)**
   * *Why RAG-specific:* attackers who can alter the index can control which documents the retriever returns.
   * *Short mitigation:* RBAC, immutable snapshots, audit logs, and signed/timestamped index updates.

6. **Cross-tenant data leakage in shared vector stores / multi-tenant setups**
   * *Why RAG-specific:* retrieval may surface documents from other tenants if isolation is misconfigured.
   * *Short mitigation:* strict logical/physical separation, per-tenant namespaces, and retrieval tenancy checks.

7. **Sensitive information leakage via retrieval (PII/credentials returned by RAG)**
   * *Why RAG-specific:* the retriever may return documents containing secrets that the LLM will reprint.
   * *Short mitigation:* secret scanning on ingestion, redact sensitive fields, and enforce retrieval filters for confidential content.

8. **Over-exposure via long or uncontrolled context assembly (excessive concatenation of retrieved docs)**
   * *Why RAG-specific:* RAG concatenates retrieved pieces; without limits this leaks more than needed and increases risk surface.
   * *Short mitigation:* cap total tokens/char length, rank by relevance+trust, and prefer summarized vs full text.

9. **Stale / revoked content still served from cache or snapshots**
   * *Why RAG-specific:* retrieval can return outdated or legally/ethically revoked documents that the model then repeats.
   * *Short mitigation:* support TTLs, revocation lists, and invalidate caches on content removal.

10. **Conflicting or adversarial context that causes hallucinations / misleading completions**
    * *Why RAG-specific:* contradictory retrieved documents can push the LLM into fabricating or picking wrong facts.
    * *Short mitigation:* detect contradictory retrievals, surface provenance/confidence, and ask the model to cite sources.

11. **Index metadata leakage (filenames, paths, uploader IDs revealed via retrieval)**
    * *Why RAG-specific:* metadata often becomes part of retrieval context or provenance and can leak internal structure.
    * *Short mitigation:* minimize metadata returned to the model; redact internal identifiers.

12. **Retrieval-triggered exfiltration (craft queries that cause the model to assemble and leak secret content)**
    * *Why RAG-specific:* combined retrieval + LLM reasoning can be used to stitch secrets from multiple shards into a single answer.
    * *Short mitigation:* detect and block high-risk retrieval patterns, apply rate limits, and redact sensitive shards before use.

13. **Chaining attacks using auxiliary retrieved content (external links, code, or commands included in docs)**
    * *Why RAG-specific:* retrieved content may contain actionable commands/URLs that the model echoes or the app executes.
    * *Short mitigation:* disallow auto-execution of returned commands/links, and sanitize or neutralize actionable content.

14. **Cache poisoning (poisoned cached retrieval results used later for many queries)**
    * *Why RAG-specific:* caches speed RAG; if an attacker poisons cache entries, many users get the malicious context.
    * *Short mitigation:* secure cache write path, validate cached entries, and use cache signing/expiration.

15. **Denial-of-service via large or expensive retrieval payloads**
    * *Why RAG-specific:* attackers add huge documents or force retrieval of many shards, inflating compute and costs.
    * *Short mitigation:* enforce size limits on documents/shards, throttle retrieval, and charge/limit uploads per user.

16. **Provenance tampering (fake source attribution to make bad content appear trustworthy)**
    * *Why RAG-specific:* RAG often surfaces provenance (source names/URLs). Attackers can insert forged provenance to increase trust in malicious content.
    * *Short mitigation:* cryptographically sign trusted sources, validate ingestion provenance, and show provenance confidence.

17. **Embedding model compromise / upstream poisoning (bad embeddings produced by a compromised embedding service)**
    * *Why RAG-specific:* embeddings are central to retrieval; if the embedding model/service is compromised, the whole retrieval ranking can be controlled.
    * *Short mitigation:* monitor embedding drift, use multiple embedding models for cross-validation, and isolate embedding provider trust boundaries.

18. **Chunking/splitting vulnerabilities that leak context or meaning when chunks are reassembled**
    * *Why RAG-specific:* how you split documents affects what the LLM sees; inappropriate chunk boundaries can leak secrets or make malicious instructions more effective.
    * *Short mitigation:* choose chunking logic that avoids splitting sensitive fragments, and apply additional redaction after chunking.

19. **Search/semantic ranking disclosure (attackers infer what’s in the store by probing retrieval behavior)**
    * *Why RAG-specific:* an attacker can issue many queries and observe what gets returned to reconstruct or map the corpus.
    * *Short mitigation:* rate-limit, add noise to low-confidence retrievals, and monitor probing patterns.

20. **Legal/IP/licensing violations through retrieval of copyrighted or restricted content**
    * *Why RAG-specific:* RAG may surface copyrighted material verbatim in responses, creating legal risk for the app.
    * *Short mitigation:* track content licensing on ingestion, prefer summarization, and block returns of high-risk copyrighted content.
