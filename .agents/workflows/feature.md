---
description: AI-Driven Feature Development Workflow
---

# Feature Development Workflow

This workflow ensures that all AI-driven changes are isolated, granular, and safe.

## Rules for AI Assistants

### 1. Branching
- **Mandatory Feature Branches**: Never work directly on the `main` branch. 
- **Naming**: Always create a new branch from the latest `main` using the `feat/` prefix (e.g., `git checkout -b feat/new-ui-panel main`).

### 2. Granular Commits
- **Small Commits**: Commit after every single logical change. 
- **Scope**: One commit should ideally touch only one file or one specific logical unit of work. 
- **Isolation**: Do not group unrelated features, refactors, or bug fixes into a single commit. This helps in isolating bugs and simplifies reverts.

### 3. Stability & Validation
// turbo
- **Build First**: Always run `mvn compile` (or the project's build command) before committing any code changes. 
- **Verification**: Verify the change manually or via tests before moving to the next granular step.

### 4. Pushing & Merging
- **Remote Sync**: Push the feature branch to `origin` frequently.
- **Merge Approval**: Once a feature is complete, request the user to review the branch before merging into `main`.
