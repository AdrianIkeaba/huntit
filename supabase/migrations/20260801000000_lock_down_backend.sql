-- Hunt.it release security baseline.
-- Locks down exposed tables, Storage, and RPCs while preserving the current
-- authenticated game flows.

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

-- Security-definer helpers live outside the exposed public schema. They are
-- only referenced by RLS policies and always bind authorization to auth.uid().
create or replace function private.is_room_participant(p_room_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select (select auth.uid()) is not null
    and exists (
      select 1
      from public.game_participants gp
      where gp.room_id = p_room_id
        and gp.user_id = (select auth.uid())
    );
$$;

create or replace function private.is_room_host(p_room_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select (select auth.uid()) is not null
    and exists (
      select 1
      from public.game_rooms gr
      where gr.id = p_room_id
        and gr.host_id = (select auth.uid())
    );
$$;

create or replace function private.is_public_lobby_room(p_room_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.game_rooms gr
    where gr.id = p_room_id
      and gr.is_public is true
      and gr.status = 'lobby'::public.game_status
  );
$$;

create or replace function private.can_view_player(p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select (select auth.uid()) is not null
    and (
      p_user_id = (select auth.uid())
      or exists (
        select 1
        from public.game_participants caller_gp
        join public.game_participants target_gp
          on target_gp.room_id = caller_gp.room_id
        where caller_gp.user_id = (select auth.uid())
          and target_gp.user_id = p_user_id
      )
    );
$$;

create or replace function private.can_upload_submission_object(p_name text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select (select auth.uid()) is not null
    and p_name ~ '^[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}/round_[0-9]+_[0-9]+[.]jpg$'
    and split_part(p_name, '/', 2) = (select auth.uid())::text
    and exists (
      select 1
      from public.game_participants gp
      where gp.room_id = split_part(p_name, '/', 1)::uuid
        and gp.user_id = (select auth.uid())
        and gp.is_playing is true
    );
$$;

create or replace function private.can_read_submission_object(p_name text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select (select auth.uid()) is not null
    and p_name ~ '^[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}/round_[0-9]+_[0-9]+[.]jpg$'
    and exists (
      select 1
      from public.game_participants gp
      where gp.room_id = split_part(p_name, '/', 1)::uuid
        and gp.user_id = (select auth.uid())
    );
$$;

revoke execute on all functions in schema private
from public, anon, authenticated, service_role;

-- Public player data is separated from private profile/email data.
create table if not exists public.player_profiles (
  id uuid primary key references public.profiles(id) on delete cascade,
  email text not null default '',
  display_name text not null,
  avatar_id integer not null default 1,
  total_games_played integer not null default 0,
  created_at timestamptz,
  updated_at timestamptz
);

create or replace function private.sync_player_profile()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.player_profiles (
    id,
    email,
    display_name,
    avatar_id,
    total_games_played,
    created_at,
    updated_at
  )
  values (
    new.id,
    '',
    new.display_name,
    new.avatar_id,
    coalesce(new.total_games_played, 0),
    new.created_at,
    new.updated_at
  )
  on conflict (id) do update
  set display_name = excluded.display_name,
      avatar_id = excluded.avatar_id,
      total_games_played = excluded.total_games_played,
      updated_at = excluded.updated_at;

  return new;
end;
$$;

revoke execute on function private.sync_player_profile()
from public, anon, authenticated, service_role;

drop trigger if exists sync_player_profile_from_profiles on public.profiles;
create trigger sync_player_profile_from_profiles
after insert or update of display_name, avatar_id, total_games_played
on public.profiles
for each row execute function private.sync_player_profile();

insert into public.player_profiles (
  id,
  email,
  display_name,
  avatar_id,
  total_games_played,
  created_at,
  updated_at
)
select
  p.id,
  '',
  p.display_name,
  p.avatar_id,
  coalesce(p.total_games_played, 0),
  p.created_at,
  p.updated_at
from public.profiles p
on conflict (id) do update
set display_name = excluded.display_name,
    avatar_id = excluded.avatar_id,
    total_games_played = excluded.total_games_played,
    updated_at = excluded.updated_at;

alter table public.profiles enable row level security;
alter table public.player_profiles enable row level security;
alter table public.game_rooms enable row level security;
alter table public.game_participants enable row level security;
alter table public.game_challenges enable row level security;
alter table public.round_submissions enable row level security;

-- Replace all existing application policies with an explicit access model.
drop policy if exists "Enable read access for all users" on public.profiles;
drop policy if exists "Profiles are viewable by everyone" on public.profiles;
drop policy if exists "Users can insert own profile" on public.profiles;
drop policy if exists "Users can update own profile" on public.profiles;

create policy "profiles_select_own"
on public.profiles for select
to authenticated
using (id = (select auth.uid()));

create policy "profiles_insert_own"
on public.profiles for insert
to authenticated
with check (id = (select auth.uid()));

create policy "profiles_update_own"
on public.profiles for update
to authenticated
using (id = (select auth.uid()))
with check (id = (select auth.uid()));

drop policy if exists "player_profiles_select_visible" on public.player_profiles;
create policy "player_profiles_select_visible"
on public.player_profiles for select
to authenticated
using ((select private.can_view_player(id)));

drop policy if exists "Enable read access for all users" on public.game_rooms;
drop policy if exists "Anyone can create game room" on public.game_rooms;
drop policy if exists "Only host can delete game room" on public.game_rooms;
drop policy if exists "Only host can update game room" on public.game_rooms;

create policy "game_rooms_select_visible"
on public.game_rooms for select
to authenticated
using (
  (is_public is true and status = 'lobby'::public.game_status)
  or host_id = (select auth.uid())
  or (select private.is_room_participant(id))
);

create policy "game_rooms_insert_host"
on public.game_rooms for insert
to authenticated
with check (
  host_id = (select auth.uid())
  and status = 'lobby'::public.game_status
  and current_round = 0
  and current_phase = 'lobby'
);

create policy "game_rooms_update_host"
on public.game_rooms for update
to authenticated
using (host_id = (select auth.uid()))
with check (host_id = (select auth.uid()));

create policy "game_rooms_delete_host"
on public.game_rooms for delete
to authenticated
using (host_id = (select auth.uid()));

drop policy if exists "game_participants_select_visible" on public.game_participants;
drop policy if exists "game_participants_insert_host" on public.game_participants;
drop policy if exists "game_participants_update_own_status" on public.game_participants;
drop policy if exists "game_participants_delete_self_or_host" on public.game_participants;

create policy "game_participants_select_visible"
on public.game_participants for select
to authenticated
using (
  (select private.is_room_participant(room_id))
  or (select private.is_room_host(room_id))
  or (select private.is_public_lobby_room(room_id))
);

create policy "game_participants_insert_host"
on public.game_participants for insert
to authenticated
with check (
  user_id = (select auth.uid())
  and is_host is true
  and current_score = 0
  and join_order = 1
  and is_playing is true
  and (select private.is_room_host(room_id))
);

create policy "game_participants_update_own_status"
on public.game_participants for update
to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

create policy "game_participants_delete_self_or_host"
on public.game_participants for delete
to authenticated
using (
  user_id = (select auth.uid())
  or (select private.is_room_host(room_id))
);

drop policy if exists "Participants can insert challenges" on public.game_challenges;
drop policy if exists "Participants can view challenges" on public.game_challenges;

create policy "game_challenges_select_participant"
on public.game_challenges for select
to authenticated
using (
  (select private.is_room_participant(room_id))
  or (select private.is_room_host(room_id))
);

drop policy if exists "Users can insert own submissions" on public.round_submissions;
drop policy if exists "Users can update own submissions" on public.round_submissions;
drop policy if exists "View submissions in same game" on public.round_submissions;

create policy "round_submissions_select_participant"
on public.round_submissions for select
to authenticated
using (
  (select private.is_room_participant(room_id))
  or (select private.is_room_host(room_id))
);

-- Remove broad default Data API grants, then grant only the operations used by
-- the authenticated app. service_role retains full access.
revoke all privileges on table public.profiles
from anon, authenticated;
revoke all privileges on table public.player_profiles
from anon, authenticated;
revoke all privileges on table public.game_rooms
from anon, authenticated;
revoke all privileges on table public.game_participants
from anon, authenticated;
revoke all privileges on table public.game_challenges
from anon, authenticated;
revoke all privileges on table public.round_submissions
from anon, authenticated;

grant select, insert, update on table public.profiles to authenticated;
grant select on table public.player_profiles to authenticated;
grant select, insert, update, delete on table public.game_rooms to authenticated;
grant select, insert, delete on table public.game_participants to authenticated;
grant update (is_playing) on table public.game_participants to authenticated;
grant select on table public.game_challenges to authenticated;
grant select on table public.round_submissions to authenticated;

grant all privileges on table public.player_profiles to service_role;

create index if not exists game_participants_user_id_room_id_idx
on public.game_participants (user_id, room_id);

create index if not exists game_participants_room_id_is_playing_idx
on public.game_participants (room_id, is_playing);

create index if not exists game_rooms_public_lobby_created_idx
on public.game_rooms (created_at desc)
where is_public is true and status = 'lobby'::public.game_status;

create index if not exists round_submissions_room_id_user_id_idx
on public.round_submissions (room_id, user_id);

-- Storage is private. Object paths must be:
-- <room UUID>/<authenticated user UUID>/round_<n>_<timestamp>.jpg
update storage.buckets
set public = false,
    file_size_limit = 1048576,
    allowed_mime_types = array['image/jpeg']::text[]
where id in ('submissions', 'submission-images');

drop policy if exists "Allow authenticated users to upload files" on storage.objects;
drop policy if exists "enable_read_for_all_users" on storage.objects;
drop policy if exists "submission_objects_insert_own_path" on storage.objects;
drop policy if exists "submission_objects_select_room_member" on storage.objects;
drop policy if exists "submission_objects_update_own_path" on storage.objects;
drop policy if exists "submission_objects_delete_owner" on storage.objects;

create policy "submission_objects_insert_own_path"
on storage.objects for insert
to authenticated
with check (
  bucket_id = 'submissions'
  and (select private.can_upload_submission_object(name))
);

create policy "submission_objects_select_room_member"
on storage.objects for select
to authenticated
using (
  bucket_id = 'submissions'
  and (select private.can_read_submission_object(name))
);

create policy "submission_objects_update_own_path"
on storage.objects for update
to authenticated
using (
  bucket_id = 'submissions'
  and owner_id = (select auth.uid())::text
  and (select private.can_upload_submission_object(name))
)
with check (
  bucket_id = 'submissions'
  and owner_id = (select auth.uid())::text
  and (select private.can_upload_submission_object(name))
);

create policy "submission_objects_delete_owner"
on storage.objects for delete
to authenticated
using (
  bucket_id = 'submissions'
  and owner_id = (select auth.uid())::text
);

-- RPCs keep their existing signatures for client compatibility, but now bind
-- supplied user IDs to the authenticated caller, validate membership/state,
-- set an immutable search_path, and make retries idempotent.
create or replace function public.generate_room_code()
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_code text;
begin
  if (select auth.uid()) is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  loop
    v_code := lpad(floor(random() * 1000000)::text, 6, '0');
    exit when not exists (
      select 1 from public.game_rooms gr where gr.room_code = v_code
    );
  end loop;

  return v_code;
end;
$$;

create or replace function public.get_server_time()
returns timestamptz
language sql
stable
security invoker
set search_path = ''
as $$
  select now();
$$;

create or replace function public.join_game_room(
  p_room_code text,
  p_user_id uuid
)
returns json
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_caller_id uuid := (select auth.uid());
  v_room_id uuid;
  v_max_players integer;
  v_current_players integer;
  v_room_status public.game_status;
  v_join_order integer;
begin
  if v_caller_id is null or v_caller_id <> p_user_id then
    raise exception 'Not authorized to join as this user' using errcode = '42501';
  end if;

  select gr.id, gr.max_players, gr.status
  into v_room_id, v_max_players, v_room_status
  from public.game_rooms gr
  where gr.room_code = p_room_code
  for update;

  if not found then
    return json_build_object('success', false, 'error', 'Room not found');
  end if;

  if v_room_status <> 'lobby'::public.game_status then
    return json_build_object('success', false, 'error', 'Game already started');
  end if;

  if exists (
    select 1
    from public.game_participants gp
    where gp.room_id = v_room_id and gp.user_id = v_caller_id
  ) then
    update public.game_participants
    set is_playing = true
    where room_id = v_room_id and user_id = v_caller_id;

    return json_build_object(
      'success', true,
      'room_id', v_room_id,
      'message', 'Already in room'
    );
  end if;

  select count(*), coalesce(max(gp.join_order), 0) + 1
  into v_current_players, v_join_order
  from public.game_participants gp
  where gp.room_id = v_room_id;

  if v_max_players is not null and v_current_players >= v_max_players then
    return json_build_object('success', false, 'error', 'Room is full');
  end if;

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

  return json_build_object('success', true, 'room_id', v_room_id);
end;
$$;

create or replace function public.start_game(
  p_room_id uuid,
  p_user_id uuid
)
returns json
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_caller_id uuid := (select auth.uid());
  v_room public.game_rooms%rowtype;
  v_player_count integer;
  v_challenge_count integer;
begin
  if v_caller_id is null or v_caller_id <> p_user_id then
    raise exception 'Not authorized to start as this user' using errcode = '42501';
  end if;

  select *
  into v_room
  from public.game_rooms gr
  where gr.id = p_room_id
  for update;

  if not found then
    return json_build_object('success', false, 'error', 'Room not found');
  end if;

  if v_room.host_id <> v_caller_id then
    return json_build_object('success', false, 'error', 'Only host can start game');
  end if;

  if v_room.status <> 'lobby'::public.game_status then
    return json_build_object('success', false, 'error', 'Game already started');
  end if;

  select count(*)
  into v_player_count
  from public.game_participants gp
  where gp.room_id = p_room_id and gp.is_playing is true;

  if v_player_count < 2 then
    return json_build_object('success', false, 'error', 'Need at least 2 players');
  end if;

  select count(*)
  into v_challenge_count
  from public.game_challenges gc
  where gc.room_id = p_room_id;

  if v_challenge_count < v_room.total_rounds then
    return json_build_object('success', false, 'error', 'Challenges are not ready');
  end if;

  update public.game_rooms
  set status = 'in_progress'::public.game_status,
      current_round = 1,
      current_phase = case
        when v_room.cooldown_seconds > 0 then 'cooldown'
        else 'round_active'
      end,
      phase_ends_at = case
        when v_room.cooldown_seconds > 0
          then now() + make_interval(secs => v_room.cooldown_seconds)
        else now() + make_interval(secs => v_room.round_duration_seconds)
      end,
      updated_at = now()
  where id = p_room_id;

  return json_build_object('success', true);
end;
$$;

create or replace function public.skip_round(
  p_room_id uuid,
  p_user_id uuid,
  p_round_number integer
)
returns json
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_caller_id uuid := (select auth.uid());
  v_existing_status public.submission_status;
begin
  if v_caller_id is null or v_caller_id <> p_user_id then
    raise exception 'Not authorized to skip as this user' using errcode = '42501';
  end if;

  if not exists (
    select 1
    from public.game_participants gp
    join public.game_rooms gr on gr.id = gp.room_id
    where gp.room_id = p_room_id
      and gp.user_id = v_caller_id
      and gp.is_playing is true
      and gr.status = 'in_progress'::public.game_status
      and gr.current_phase = 'round_active'
      and gr.current_round = p_round_number
      and gr.phase_ends_at > now()
  ) then
    return json_build_object('success', false, 'error', 'Round is not accepting skips');
  end if;

  select rs.status
  into v_existing_status
  from public.round_submissions rs
  where rs.room_id = p_room_id
    and rs.user_id = v_caller_id
    and rs.round_number = p_round_number
  for update;

  if found then
    return json_build_object(
      'success', v_existing_status = 'skipped'::public.submission_status,
      'error', case
        when v_existing_status = 'skipped'::public.submission_status then null
        else 'Round already submitted'
      end
    );
  end if;

  insert into public.round_submissions (
    room_id,
    user_id,
    round_number,
    status,
    points_earned
  )
  values (
    p_room_id,
    v_caller_id,
    p_round_number,
    'skipped'::public.submission_status,
    0
  );

  return json_build_object('success', true, 'points', 0);
end;
$$;

create or replace function public.submit_round(
  p_room_id uuid,
  p_user_id uuid,
  p_round_number integer,
  p_image_url text,
  p_is_success boolean
)
returns json
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_caller_id uuid := (select auth.uid());
  v_new_status public.submission_status;
  v_new_points integer;
  v_existing_status public.submission_status;
  v_existing_points integer;
begin
  if v_caller_id is null or v_caller_id <> p_user_id then
    raise exception 'Not authorized to submit as this user' using errcode = '42501';
  end if;

  if p_image_url is null
     or p_image_url not like p_room_id::text || '/' || v_caller_id::text || '/%' then
    return json_build_object('success', false, 'error', 'Invalid submission image path');
  end if;

  if not exists (
    select 1
    from public.game_participants gp
    join public.game_rooms gr on gr.id = gp.room_id
    where gp.room_id = p_room_id
      and gp.user_id = v_caller_id
      and gp.is_playing is true
      and gr.status = 'in_progress'::public.game_status
      and gr.current_phase = 'round_active'
      and gr.current_round = p_round_number
      and gr.phase_ends_at > now()
  ) then
    return json_build_object('success', false, 'error', 'Round is not accepting submissions');
  end if;

  v_new_status := case
    when p_is_success then 'success'::public.submission_status
    else 'failed'::public.submission_status
  end;
  v_new_points := case when p_is_success then 2 else -1 end;

  select rs.status, rs.points_earned
  into v_existing_status, v_existing_points
  from public.round_submissions rs
  where rs.room_id = p_room_id
    and rs.user_id = v_caller_id
    and rs.round_number = p_round_number
  for update;

  if found and v_existing_status in (
    'success'::public.submission_status,
    'skipped'::public.submission_status
  ) then
    return json_build_object(
      'success', true,
      'points', v_existing_points,
      'status', v_existing_status
    );
  end if;

  if found then
    update public.round_submissions
    set status = v_new_status,
        image_url = p_image_url,
        points_earned = v_new_points,
        verified_at = now()
    where room_id = p_room_id
      and user_id = v_caller_id
      and round_number = p_round_number;

    update public.game_participants
    set current_score = current_score + (v_new_points - v_existing_points)
    where room_id = p_room_id and user_id = v_caller_id;
  else
    insert into public.round_submissions (
      room_id,
      user_id,
      round_number,
      status,
      image_url,
      points_earned,
      verified_at
    )
    values (
      p_room_id,
      v_caller_id,
      p_round_number,
      v_new_status,
      p_image_url,
      v_new_points,
      now()
    );

    update public.game_participants
    set current_score = current_score + v_new_points
    where room_id = p_room_id and user_id = v_caller_id;
  end if;

  return json_build_object(
    'success', true,
    'points', v_new_points,
    'status', v_new_status
  );
end;
$$;

create or replace function public.advance_game_phase(p_room_id uuid)
returns json
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_caller_id uuid := (select auth.uid());
  v_current_phase text;
  v_current_round integer;
  v_total_rounds integer;
  v_cooldown_seconds integer;
  v_round_duration_seconds integer;
  v_new_phase text;
  v_new_phase_ends_at timestamptz;
  v_phase_ends_at timestamptz;
  v_status public.game_status;
begin
  select
    gr.current_phase,
    gr.current_round,
    gr.total_rounds,
    gr.cooldown_seconds,
    gr.round_duration_seconds,
    gr.phase_ends_at,
    gr.status
  into
    v_current_phase,
    v_current_round,
    v_total_rounds,
    v_cooldown_seconds,
    v_round_duration_seconds,
    v_phase_ends_at,
    v_status
  from public.game_rooms gr
  where gr.id = p_room_id
  for update;

  if not found then
    return json_build_object('success', false, 'message', 'Room not found');
  end if;

  -- A JWT caller must belong to this room. A null auth.uid() is reserved for
  -- trusted database jobs, which are the only non-JWT callers with EXECUTE.
  if v_caller_id is not null
     and not exists (
       select 1
       from public.game_participants gp
       where gp.room_id = p_room_id and gp.user_id = v_caller_id
     ) then
    raise exception 'Not authorized to advance this room' using errcode = '42501';
  end if;

  if v_status <> 'in_progress'::public.game_status then
    return json_build_object('success', false, 'message', 'Game is not in progress');
  end if;

  if v_phase_ends_at is null or v_phase_ends_at > now() then
    return json_build_object('success', false, 'message', 'Phase has not ended yet');
  end if;

  if v_current_phase = 'cooldown' then
    v_new_phase := 'round_active';
    v_new_phase_ends_at := now() + make_interval(secs => v_round_duration_seconds);
  elsif v_current_phase = 'round_active' then
    with inserted_failures as (
      insert into public.round_submissions (
        room_id,
        user_id,
        round_number,
        status,
        points_earned
      )
      select
        p_room_id,
        gp.user_id,
        v_current_round,
        'failed'::public.submission_status,
        -1
      from public.game_participants gp
      where gp.room_id = p_room_id
        and gp.is_playing is true
        and not exists (
          select 1
          from public.round_submissions rs
          where rs.room_id = p_room_id
            and rs.user_id = gp.user_id
            and rs.round_number = v_current_round
        )
      on conflict (room_id, user_id, round_number) do nothing
      returning user_id, points_earned
    )
    update public.game_participants gp
    set current_score = gp.current_score + f.points_earned
    from inserted_failures f
    where gp.room_id = p_room_id and gp.user_id = f.user_id;

    if v_current_round >= v_total_rounds then
      update public.game_rooms
      set status = 'finished'::public.game_status,
          current_phase = 'finished',
          phase_ends_at = null,
          updated_at = now()
      where id = p_room_id;

      return json_build_object('success', true, 'phase', 'finished');
    end if;

    v_current_round := v_current_round + 1;
    if v_cooldown_seconds > 0 then
      v_new_phase := 'cooldown';
      v_new_phase_ends_at := now() + make_interval(secs => v_cooldown_seconds);
    else
      v_new_phase := 'round_active';
      v_new_phase_ends_at := now() + make_interval(secs => v_round_duration_seconds);
    end if;
  else
    return json_build_object(
      'success', false,
      'message', 'Unknown phase: ' || v_current_phase
    );
  end if;

  update public.game_rooms
  set current_round = v_current_round,
      current_phase = v_new_phase,
      phase_ends_at = v_new_phase_ends_at,
      updated_at = now()
  where id = p_room_id;

  return json_build_object(
    'success', true,
    'phase', v_new_phase,
    'round', v_current_round
  );
end;
$$;

create or replace function public.auto_advance_game_phases()
returns table(room_id uuid, result json)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_game record;
begin
  for v_game in
    select gr.id
    from public.game_rooms gr
    where gr.status = 'in_progress'::public.game_status
      and gr.phase_ends_at is not null
      and gr.phase_ends_at <= now()
    order by gr.phase_ends_at
  loop
    room_id := v_game.id;
    begin
      result := public.advance_game_phase(v_game.id);
    exception when others then
      result := json_build_object('success', false, 'error', sqlerrm);
    end;
    return next;
  end loop;
end;
$$;

-- Trigger functions are not API endpoints.
revoke execute on all functions in schema public
from public, anon, authenticated;

grant execute on function public.generate_room_code() to authenticated;
grant execute on function public.get_server_time() to authenticated;
grant execute on function public.join_game_room(text, uuid) to authenticated;
grant execute on function public.start_game(uuid, uuid) to authenticated;
grant execute on function public.skip_round(uuid, uuid, integer) to authenticated;
grant execute on function public.submit_round(uuid, uuid, integer, text, boolean)
to authenticated;
grant execute on function public.advance_game_phase(uuid) to authenticated;

grant execute on function public.generate_room_code() to service_role;
grant execute on function public.get_server_time() to service_role;
grant execute on function public.join_game_room(text, uuid) to service_role;
grant execute on function public.start_game(uuid, uuid) to service_role;
grant execute on function public.skip_round(uuid, uuid, integer) to service_role;
grant execute on function public.submit_round(uuid, uuid, integer, text, boolean)
to service_role;
grant execute on function public.advance_game_phase(uuid) to service_role;
grant execute on function public.auto_advance_game_phases() to service_role;

alter default privileges for role postgres in schema public
revoke execute on functions from public, anon, authenticated;
