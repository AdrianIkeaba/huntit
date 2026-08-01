-- Release order 5: account safety, moderation, blocking, and retention.

alter table public.profiles
  add column age_confirmed_at timestamptz,
  add column terms_accepted_at timestamptz;

create table public.user_blocks (
  blocker_id uuid not null references public.profiles(id) on delete cascade,
  blocked_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  constraint user_blocks_no_self_block check (blocker_id <> blocked_id)
);

create index user_blocks_blocked_id_idx
  on public.user_blocks (blocked_id, blocker_id);

alter table public.user_blocks enable row level security;

revoke all on public.user_blocks from public, anon, authenticated;
grant select on public.user_blocks to authenticated;
grant all on public.user_blocks to service_role;

create policy "user_blocks_select_own"
on public.user_blocks
for select
to authenticated
using (blocker_id = (select auth.uid()));

create table public.user_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid references public.profiles(id) on delete set null,
  reported_user_id uuid references public.profiles(id) on delete set null,
  room_id uuid references public.game_rooms(id) on delete set null,
  reason text not null check (
    reason in (
      'harassment',
      'hate_speech',
      'sexual_content',
      'dangerous_behavior',
      'spam',
      'inappropriate_name',
      'other'
    )
  ),
  details text check (details is null or char_length(details) <= 500),
  status text not null default 'open' check (
    status in ('open', 'reviewing', 'actioned', 'dismissed')
  ),
  created_at timestamptz not null default now(),
  reviewed_at timestamptz
);

create index user_reports_status_created_at_idx
  on public.user_reports (status, created_at);
create index user_reports_reported_user_idx
  on public.user_reports (reported_user_id, created_at desc);

alter table public.user_reports enable row level security;

revoke all on public.user_reports from public, anon, authenticated;
grant select on public.user_reports to authenticated;
grant all on public.user_reports to service_role;

create policy "user_reports_select_own"
on public.user_reports
for select
to authenticated
using (reporter_id = (select auth.uid()));

create or replace function private.users_have_block_relation(
  p_first_user_id uuid,
  p_second_user_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.user_blocks ub
    where (ub.blocker_id = p_first_user_id and ub.blocked_id = p_second_user_id)
       or (ub.blocker_id = p_second_user_id and ub.blocked_id = p_first_user_id)
  );
$$;

revoke all on function private.users_have_block_relation(uuid, uuid)
from public, anon, authenticated;
grant execute on function private.users_have_block_relation(uuid, uuid)
to authenticated, service_role;

create or replace function public.block_player(p_blocked_user_id uuid)
returns json
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_caller_id uuid := (select auth.uid());
begin
  if v_caller_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_blocked_user_id is null or p_blocked_user_id = v_caller_id then
    return json_build_object('success', false, 'error', 'Invalid player');
  end if;

  if not exists (
    select 1 from public.profiles p where p.id = p_blocked_user_id
  ) then
    return json_build_object('success', false, 'error', 'Player not found');
  end if;

  insert into public.user_blocks (blocker_id, blocked_id)
  values (v_caller_id, p_blocked_user_id)
  on conflict (blocker_id, blocked_id) do nothing;

  return json_build_object('success', true);
end;
$$;

revoke all on function public.block_player(uuid)
from public, anon, authenticated, service_role;
grant execute on function public.block_player(uuid) to authenticated;

create or replace function public.unblock_player(p_blocked_user_id uuid)
returns json
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_caller_id uuid := (select auth.uid());
begin
  if v_caller_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  delete from public.user_blocks
  where blocker_id = v_caller_id
    and blocked_id = p_blocked_user_id;

  return json_build_object('success', true);
end;
$$;

revoke all on function public.unblock_player(uuid)
from public, anon, authenticated, service_role;
grant execute on function public.unblock_player(uuid) to authenticated;

create or replace function public.report_player(
  p_reported_user_id uuid,
  p_room_id uuid,
  p_reason text,
  p_details text default null
)
returns json
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_caller_id uuid := (select auth.uid());
  v_details text := nullif(btrim(p_details), '');
begin
  if v_caller_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_reported_user_id is null or p_reported_user_id = v_caller_id then
    return json_build_object('success', false, 'error', 'Invalid player');
  end if;

  if p_reason is null or p_reason not in (
    'harassment',
    'hate_speech',
    'sexual_content',
    'dangerous_behavior',
    'spam',
    'inappropriate_name',
    'other'
  ) then
    return json_build_object('success', false, 'error', 'Choose a valid report reason');
  end if;

  if v_details is not null and char_length(v_details) > 500 then
    return json_build_object(
      'success', false, 'error', 'Report details must be 500 characters or fewer'
    );
  end if;

  if not exists (
    select 1
    from public.game_rooms gr
    where gr.id = p_room_id
      and (
        (
          gr.is_public is true
          and gr.status = 'lobby'::public.game_status
          and gr.host_id = p_reported_user_id
        )
        or (
          exists (
            select 1
            from public.game_participants reporter_gp
            where reporter_gp.room_id = gr.id
              and reporter_gp.user_id = v_caller_id
          )
          and exists (
            select 1
            from public.game_participants reported_gp
            where reported_gp.room_id = gr.id
              and reported_gp.user_id = p_reported_user_id
          )
        )
      )
  ) then
    return json_build_object(
      'success', false, 'error', 'You can only report a player from a visible game'
    );
  end if;

  if (
    select count(*)
    from public.user_reports ur
    where ur.reporter_id = v_caller_id
      and ur.created_at >= now() - interval '1 hour'
  ) >= 5 then
    return json_build_object(
      'success', false, 'error', 'Too many reports. Please try again later'
    );
  end if;

  insert into public.user_reports (
    reporter_id,
    reported_user_id,
    room_id,
    reason,
    details
  )
  values (
    v_caller_id,
    p_reported_user_id,
    p_room_id,
    p_reason,
    v_details
  );

  return json_build_object('success', true);
end;
$$;

revoke all on function public.report_player(uuid, uuid, text, text)
from public, anon, authenticated, service_role;
grant execute on function public.report_player(uuid, uuid, text, text)
to authenticated;

-- A block prevents the two accounts from entering the same room. The generic
-- response avoids disclosing who blocked whom.
create or replace function public.join_game_room(p_room_code text)
returns json
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_caller_id uuid := (select auth.uid());
  v_normalized_code text := btrim(p_room_code);
  v_room_id uuid;
  v_max_players integer;
  v_current_players integer;
  v_room_status public.game_status;
  v_join_order integer;
  v_existing_is_playing boolean;
begin
  if v_caller_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if v_normalized_code is null or v_normalized_code !~ '^[0-9]{6}$' then
    return json_build_object('success', false, 'error', 'Enter a valid 6-digit room code');
  end if;

  select gr.id, gr.max_players, gr.status
  into v_room_id, v_max_players, v_room_status
  from public.game_rooms gr
  where gr.room_code = v_normalized_code
  for update;

  if not found then
    return json_build_object('success', false, 'error', 'Room not found');
  end if;

  if v_room_status <> 'lobby'::public.game_status then
    return json_build_object('success', false, 'error', 'Game already started');
  end if;

  select gp.is_playing
  into v_existing_is_playing
  from public.game_participants gp
  where gp.room_id = v_room_id
    and gp.user_id = v_caller_id;

  if found and v_existing_is_playing is true then
    return json_build_object(
      'success', true,
      'room_id', v_room_id,
      'room_code', v_normalized_code,
      'message', 'Already in room'
    );
  end if;

  if exists (
    select 1
    from public.game_participants gp
    where gp.room_id = v_room_id
      and gp.is_playing is true
      and private.users_have_block_relation(v_caller_id, gp.user_id)
  ) then
    return json_build_object(
      'success', false, 'error', 'This room is not available'
    );
  end if;

  select count(*)
  into v_current_players
  from public.game_participants gp
  where gp.room_id = v_room_id
    and gp.is_playing is true;

  if v_max_players is not null and v_current_players >= v_max_players then
    return json_build_object('success', false, 'error', 'Room is full');
  end if;

  if v_existing_is_playing is not null then
    update public.game_participants
    set is_playing = true
    where room_id = v_room_id
      and user_id = v_caller_id;
  else
    select coalesce(max(gp.join_order), 0) + 1
    into v_join_order
    from public.game_participants gp
    where gp.room_id = v_room_id;

    insert into public.game_participants (
      room_id,
      user_id,
      is_host,
      current_score,
      join_order,
      is_playing
    )
    values (
      v_room_id,
      v_caller_id,
      false,
      0,
      v_join_order,
      true
    );
  end if;

  return json_build_object(
    'success', true,
    'room_id', v_room_id,
    'room_code', v_normalized_code
  );
end;
$$;

revoke all on function public.join_game_room(text)
from public, anon, authenticated, service_role;
grant execute on function public.join_game_room(text) to authenticated;

drop policy if exists "game_rooms_select_visible" on public.game_rooms;
create policy "game_rooms_select_visible"
on public.game_rooms
for select
to authenticated
using (
  host_id = (select auth.uid())
  or (select private.is_room_participant(id))
  or (
    is_public is true
    and status = 'lobby'::public.game_status
    and not private.users_have_block_relation((select auth.uid()), host_id)
  )
);

drop policy if exists "player_profiles_select_visible" on public.player_profiles;
create policy "player_profiles_select_visible"
on public.player_profiles
for select
to authenticated
using (
  id = (select auth.uid())
  or (
    (select private.can_view_player(id))
    and not private.users_have_block_relation((select auth.uid()), id)
  )
);

-- Used only by the authenticated account-deletion Edge Function. It returns
-- paths for the Storage API; it never mutates the storage schema directly.
create or replace function public.list_submission_paths_for_account_deletion(
  p_user_id uuid
)
returns table (object_name text)
language sql
stable
security definer
set search_path = ''
as $$
  select so.name
  from storage.objects so
  where so.bucket_id = 'submissions'
    and (storage.foldername(so.name))[2] = p_user_id::text
  order by so.name;
$$;

revoke all on function public.list_submission_paths_for_account_deletion(uuid)
from public, anon, authenticated, service_role;
grant execute on function public.list_submission_paths_for_account_deletion(uuid)
to service_role;

-- Abuse reports are retained for a maximum of 24 months for safety review,
-- then permanently removed. Account-linked gameplay already cascades on
-- deletion; the existing cleanup-old-games job removes finished games.
select cron.schedule(
  'purge-expired-user-reports',
  '23 3 * * *',
  $$delete from public.user_reports where created_at < now() - interval '24 months'$$
)
where not exists (
  select 1 from cron.job where jobname = 'purge-expired-user-reports'
);
