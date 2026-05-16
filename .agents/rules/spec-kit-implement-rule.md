---
trigger: always_on
---

# SpecKit Execution Rules in Antigravity

* Whenever the `/speckit.implement` command is invoked, read the technical plan generated in `specs/`.
* Do not attempt to code all tasks within the same chat session.
* Divide the technical plan into isolated tasks and execute branches (tasks) in parallel when there is no direct dependency.
* Create separate artifacts for validation before merging into the main codebase.