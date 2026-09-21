# Usable evaluation system — 2026-09-07

User authorizes implementation and full architecture/UI reorganization. Target confirmed: easy-performance-management; easy-mra owns multi-rater feedback, easy-job-management owns job evaluation, hcm owns employee master.

Coordinator Astra; Sol backend architecture/integration; Terra frontend implementation and tests; Luna source catalog/documentation. Root repo clean baseline 1d9d282. Preserve existing _workspace and other sessions; all new audit materials in this subdirectory. No external service/DB writes, deployments or cost actions. Local synthetic data only.

Slices:
1. Standards mapping + reference workflow and current gap audit.
2. Secure evaluation operations API: participant/reviewer identity, workflow lifecycle, completion guards and feedback.
3. Role-specific usable workspace and employee/team flows with names, status, errors/loading/empty states.
4. Fresh isolated PostgreSQL + reproducible synthetic demo, API workflow/permissions/tenant negative tests, browser flow.
5. Review changes and final run instructions/evidence/limitations.

User update: existing system poorly arranged; entirely new architecture/screens are allowed. Preserve validated internals when helpful, no obligation to preserve confusing UI.
