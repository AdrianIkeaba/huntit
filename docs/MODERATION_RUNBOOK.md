# Hunt.it moderation runbook

Owner: ghost.dev
Review cadence: at least once every business day, and before every production release.

## Queue

Review open reports in `public.user_reports`, oldest first. The table is not exposed for client writes; reports enter through the authenticated `report_player` function. Only trusted backend or dashboard operators should have service-role access.

For each report:

1. Review the reason, room context, prior reports for the reported account, and any relevant display or room name.
2. Never download or expose challenge photos for routine moderation. They are deleted after automated verification and are not user-facing content.
3. Set the report status to `reviewing` while investigating.
4. Choose and record one outcome:
   - `dismissed`: no policy violation or insufficient evidence.
   - `actioned`: warning, content/profile change, room removal, temporary ban, or permanent ban.
5. Set `reviewed_at` when the decision is complete.

## Severity and response targets

- Credible threats, exploitation, sexual content, or imminent danger: review immediately; preserve only the minimum necessary evidence and escalate to the appropriate authority when legally required.
- Hate speech, targeted harassment, or repeated dangerous behavior: review within 24 hours.
- Spam, inappropriate names, and other lower-risk reports: review within 3 business days.

## Enforcement

Use Supabase Authentication user management for temporary or permanent bans. Do not share the service-role key with the app or moderators who do not need infrastructure access. Where a name or room is the violation, remove or replace it and document the action.

Blocking is user-controlled and separate from reporting. A block prevents the two accounts from joining the same future room, hides blocked hosts from public room discovery, and must not be removed by moderators unless the blocking user requests it.

## Retention and privacy

Reports expire after 24 months through the scheduled `purge-expired-user-reports` job. Deleting an account removes its identifiers from retained reports. Do not copy reports into untracked documents or personal accounts.

## Operational check

Before release and weekly thereafter:

- Confirm the report queue is accessible to the designated operator.
- Confirm the purge job is active.
- Confirm recent reports can move through `open`, `reviewing`, and a terminal state.
- Confirm banned accounts cannot authenticate.
- Record the reviewer, date, number of open reports, and oldest report age.
