create index if not exists user_reports_reporter_id_idx
  on public.user_reports (reporter_id);

create index if not exists user_reports_room_id_idx
  on public.user_reports (room_id);
