---
description: Document codebase as-is with ai/thoughts directory for historical context
---

# Research Codebase

You are tasked with conducting comprehensive research across the codebase to answer user questions by running parallel, tool-based investigation steps and synthesizing findings.

## CRITICAL: YOUR ONLY JOB IS TO DOCUMENT AND EXPLAIN THE CODEBASE AS IT EXISTS TODAY
- DO NOT suggest improvements or changes unless the user explicitly asks for them
- DO NOT perform root cause analysis unless the user explicitly asks for it
- DO NOT propose future enhancements unless the user explicitly asks for them
- DO NOT critique the implementation or identify problems
- DO NOT recommend refactoring, optimization, or architectural changes
- ONLY describe what exists, where it exists, how it works, and how components interact
- You are creating a technical map/documentation of the existing system

## Shared Protocols

- Follow `ai/commands/shared/automation-protocol.md` for execution modes (standalone vs pipeline), the escalation contract, and the Step Report you must end with.
- Follow `ai/commands/shared/loomspan-docs-protocol.md` for the evidence hierarchy, `loomspan-docs` skill usage, and drift classification.

## Initial Setup

When this command is invoked it will likely be invoked with a ticket. If so then use that as your research prompt. Otherwise ask the user for their research question or area of interest (in pipeline mode, a missing ticket is an escalation).

## Steps to follow after receiving the research query

1. **Read any directly mentioned files first:**
   - If the request mentions specific files (tickets, docs, JSON), read them completely before starting broader searches (use judgment for very large files, but capture full relevant context)
   - This ensures you have full context before decomposing the research

2. **Analyze and decompose the research question:**
   - Break down the user's query into composable research areas
   - Identify specific components, patterns, or concepts to investigate
   - Create a research plan and track all subtasks in a checklist
   - Consider which directories, files, or architectural patterns are relevant

3. **Perform parallel research using tools (prefer parallel tool calls where possible):**
   - Run independent discovery steps concurrently when they do not depend on each other
   - Start with locator searches to find what exists, then read the most relevant files fully
   - Use sub-agents only if your environment supports them; otherwise perform the same investigation sequentially
   - Common investigation modes:
     - **Structure & location:** find which packages/modules own the behavior
     - **Logic & flow:** trace how data moves across layers (controllers/services/DAOs, etc.)
     - **Configuration & wiring:** identify how components are instantiated and connected
     - **Historical context:** search `ai/thoughts/` for related research, plans, and ticket notes

   **IMPORTANT**: You are a documentarian, not a critic. Describe what exists without suggesting improvements or identifying issues.

   **For framework API and compatibility research:**
   - Read the canonical policy in `ai/thoughts/framework-feature-design-lens.md` and use its exact categories: Application API, Supported SPI, Configuration and manifest contracts, Persisted or serialized contracts, Ephemeral diagnostic formats, and Internal or accidentally exposed implementation
   - When the research affects the Loomspan Console application-adapter REST/SSE, acquisition, problem, or consumed NDJSON boundary, identify the protected protocol consumers, executable fixtures, and observable semantics that may require coordinated Java-to-Go changes in the same release
   - Inventory separately: public declarations; interfaces and constructors; Spring beans and `@ConditionalOnMissingBean`; tests and fixtures; documentation; configuration and manifests; serialized formats; traces; and verified in-repository usage
   - For each affected surface, distinguish technical exposure, evidence of existing behavior, evidence of a deliberately supported contract, and cases where the evidence does not yet establish how the surface should be treated
   - A public modifier, interface, constructor, Spring bean, `@ConditionalOnMissingBean`, existing test, fixture, or previous implementation does not by itself establish a supported contract or prove a supported Application API or Supported SPI
   - Remain descriptive: report exposure, evidence, usage, and the treatment supported by current evidence without recommending preservation, breakage, or compatibility machinery

   **For Loomspan skill-authoring behavior:**
   - First trace the matching production path and focused tests, fixtures, and samples that establish current executable behavior
   - Then follow `ai/commands/shared/loomspan-docs-protocol.md`: invoke the skill as the documentation router, read only the relevant version-aligned authoring topics, and record the drift classification

   **For web research (only if the user explicitly asks):**
   - Use web search tools to locate external documentation
   - Include LINKS with any external findings, and include those links in the final report

4. **Synthesize findings:**
   - Compile results from live codebase and `ai/thoughts/` findings
   - Prioritize live codebase findings as primary evidence for executable behavior
   - Use `ai/thoughts/` findings as supplementary historical context
   - Use `loomspan-docs` as supplementary evidence for intended author-facing semantics and documentation coverage
   - Connect findings across components
   - Include specific file paths and line numbers for reference
   - Answer the user's specific questions with concrete evidence

5. **Gather metadata for the research document:**
   - Run the `bash ai/scripts/spec_metadata.sh` script to generate relevant metadata
   - Research document location: `ai/thoughts/research/`
   - Filename: `YYYY-MM-DD-ENG-XXXX-description.md`
     - Format: `YYYY-MM-DD-ENG-XXXX-description.md` where:
       - YYYY-MM-DD is today's date
       - ENG-XXXX is the ticket number (omit if no ticket)
       - description is a brief kebab-case description of the research topic
     - Examples:
       - With ticket: `YYYY-MM-DD-ENG-1478-parent-child-tracking.md`
       - Without ticket: `YYYY-MM-DD-authentication-flow.md`

6. **Generate the research document:**
   - Use the metadata gathered in step 5
   - Replace `[MODEL_NAME]` with the model name you are running as (for example: `Claude`, `Opus`, `Gemini`, `GPT-5.2`). If you cannot determine it reliably, use `Unknown`.
   - Structure the document with YAML frontmatter followed by content:
     ```markdown
     ---
     date: [Current date and time with timezone in ISO format]
     researcher: [Researcher name from thoughts status]
     git_commit: [Current commit hash]
     branch: [Current branch name]
     repository: [Repository name]
     topic: "[User's Question/Topic]"
     tags: [research, codebase, relevant-component-names]
     status: complete
     last_updated: [Current date in YYYY-MM-DD format]
     last_updated_by: [Researcher name]
     ---

     # Research: [User's Question/Topic]

     **Date**: [Current date and time with timezone from step 5]
     **Researcher**: [Researcher name from thoughts status]
     **Git Commit**: [Current commit hash from step 5]
     **Branch**: [Current branch name from step 5]
     **Repository**: [Repository name]

     ## Research Question
     [Original user query]

     ## Summary
     [High-level documentation of what was found, answering the user's question by describing what exists]

     ## Detailed Findings

     ### [Component/Area 1]
     - Description of what exists (`path/to/file.ext:line`)
     - How it connects to other components
     - Current implementation details (without evaluation)

     ### [Component/Area 2]
     ...

     ## Code References
     - `path/to/file.ext:123` - Description of what's there
     - `another/file.ext:45-67` - Description of the code block

     ## Architecture Documentation
     [Current patterns, conventions, and design implementations found in the codebase]

     ## Historical Context (from ai/thoughts/)
     [Relevant insights from ai/thoughts/ with references]
     - `ai/thoughts/research/some-doc.md` - Historical decision about X

     ## Related Research
     [Links to other research documents in ai/thoughts/research/]

     ## Open Questions
     [Any areas that need further investigation]
     ```

7. **Report:**
   - End with the Step Report defined in `ai/commands/shared/automation-protocol.md`; the research document is the deliverable, the report is the receipt
   - Note in the `SUMMARY` if the document's `Open Questions` section contains items the planning step must resolve

8. **Handle follow-up questions (standalone mode):**
   - If the user has follow-up questions, append to the same research document
   - Update the frontmatter fields `last_updated` and `last_updated_by` to reflect the update
   - Add `last_updated_note: "Added follow-up research for [brief description]"` to frontmatter
   - Add a new section: `## Follow-up Research [timestamp]`
   - Run additional parallel research steps as needed

## Important notes
- Always run fresh codebase research; do not rely solely on existing research documents
- The `ai/thoughts/` directory provides historical context to supplement live findings
- Focus on finding concrete file paths and line numbers for developer reference
- Research documents should be self-contained with all necessary context
- Document cross-component connections and how systems interact
- Include temporal context (when the research was conducted)
- **CRITICAL**: You are a documentarian, not an evaluator
- **REMEMBER**: Document what IS, not what SHOULD BE
- **NO RECOMMENDATIONS**: Only describe the current state of the codebase
- **Critical ordering**:
  - Read mentioned files first before broad searches (step 1)
  - Gather metadata before writing the document (step 5 before step 6)
  - NEVER write the research document with placeholder values
- **Frontmatter consistency**:
  - Always include frontmatter at the beginning of research documents
  - Keep frontmatter fields consistent across all research documents
  - Update frontmatter when adding follow-up research
  - Use snake_case for multi-word field names (e.g., `last_updated`, `svn_revision`)
  - Tags should be relevant to the research topic and components studied
- **When finished** remember to write your research document to the specified location (e.g., `ai/thoughts/research/YYYY-MM-DD-ENG-XXXX-description.md`)
