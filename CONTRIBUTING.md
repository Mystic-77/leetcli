# Contributing to LeetCLI

## Important for AI Coding Assistants (Mandatory Rules)

To maintain high code quality, system stability, and fast bug isolation, all AI models or coding assistants interacting with this repository MUST follow these rules:

### 1. Development on Feature Branches
- **Never work directly on the `main` branch**. Always create a new branch from the latest `main` using the `feat/` prefix (e.g., `git checkout -b feat/task-name main`).
- Changes should be pushed to the remote repository under this feature branch for visibility and tracking.

### 2. Granular Commits
- **Make commits often and keep them small**. Each commit should address a single focused task, bug fix, or refactor step.
- Do not group unrelated changes into a single massive commit. This practice allows for faster bug isolation and easier reverts if a specific change introduces a regression.
- Use descriptive commit messages following the Conventional Commits style (e.g., `feat:`, `fix:`, `refactor:`, `docs:`).

### 3. Build Validation
- **Always run `mvn compile`** (or the project's primary build command) before each commit to verify that the project remains in a stable and compilable state.
- If a change causes a build failure, fix the issue within the same granular step before proceeding.

### 4. Documentation & Verification
- Keep any project walkthroughs, task lists, or status documents updated as you work.
- Provide a summary of work done and any verification steps performed (tests run, UI manual checks, etc.).
- Request explicit user approval before merging a completed feature branch into `main`.
