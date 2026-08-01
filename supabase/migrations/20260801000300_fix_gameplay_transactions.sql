-- Release order 3: transactional room creation and consistent gameplay scoring.

update public.game_participants
set current_score = greatest(coalesce(current_score, 0), 0)
where current_score is null or current_score < 0;

alter table public.game_participants
  alter column current_score set default 0,
  alter column current_score set not null;

alter table public.game_participants
  drop constraint if exists game_participants_current_score_nonnegative;
alter table public.game_participants
  add constraint game_participants_current_score_nonnegative
  check (current_score >= 0);

alter table public.game_participants
  drop constraint if exists game_participants_join_order_positive;
alter table public.game_participants
  add constraint game_participants_join_order_positive
  check (join_order >= 1);

alter table public.game_participants
  drop constraint if exists game_participants_room_join_order_key;
alter table public.game_participants
  add constraint game_participants_room_join_order_key
  unique (room_id, join_order);

alter table public.game_rooms
  drop constraint if exists game_rooms_room_name_valid;
alter table public.game_rooms
  add constraint game_rooms_room_name_valid
  check (
    room_name = btrim(room_name)
    and char_length(room_name) between 1 and 30
  );

alter table public.game_rooms
  drop constraint if exists game_rooms_max_players_valid;
alter table public.game_rooms
  add constraint game_rooms_max_players_valid
  check (max_players is null or max_players between 2 and 99);

alter table public.game_rooms
  drop constraint if exists game_rooms_round_duration_consistent;
alter table public.game_rooms
  add constraint game_rooms_round_duration_consistent
  check (
    (round_duration = 'quick'::public.round_duration and round_duration_seconds = 30)
    or (
      round_duration = 'standard'::public.round_duration
      and round_duration_seconds = 60
    )
    or (
      round_duration = 'marathon'::public.round_duration
      and round_duration_seconds = 90
    )
  );

update public.round_submissions
set status = coalesce(status, 'pending'::public.submission_status),
    points_earned = coalesce(points_earned, 0)
where status is null or points_earned is null;

alter table public.round_submissions
  alter column status set not null,
  alter column points_earned set default 0,
  alter column points_earned set not null;

alter table public.round_submissions
  drop constraint if exists round_submissions_status_points_consistent;
alter table public.round_submissions
  add constraint round_submissions_status_points_consistent
  check (
    (status = 'success'::public.submission_status and points_earned = 2)
    or (
      status = 'failed'::public.submission_status
      and points_earned in (-1, 0)
    )
    or (
      status in (
        'pending'::public.submission_status,
        'skipped'::public.submission_status
      )
      and points_earned = 0
    )
  );

-- Room code generation, room insertion, and host membership now commit or roll
-- back together. Direct table inserts are removed so clients cannot bypass this.
create or replace function public.create_game_room(
  p_room_name text,
  p_round_duration text,
  p_theme text,
  p_max_players integer,
  p_total_rounds integer,
  p_cooldown_seconds integer,
  p_is_public boolean
)
returns json
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_caller_id uuid := (select auth.uid());
  v_room_id uuid;
  v_room_code text;
  v_room_name text := btrim(p_room_name);
  v_round_duration_seconds integer;
  v_attempt integer;
  v_constraint_name text;
begin
  if v_caller_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if v_room_name is null or char_length(v_room_name) not between 1 and 30 then
    return json_build_object(
      'success', false,
      'error', 'Room name must be between 1 and 30 characters'
    );
  end if;

  v_round_duration_seconds := case p_round_duration
    when 'quick' then 30
    when 'standard' then 60
    when 'marathon' then 90
    else null
  end;

  if v_round_duration_seconds is null then
    return json_build_object('success', false, 'error', 'Invalid round duration');
  end if;

  if p_theme is null or p_theme not in (
    'outdoors_nature',
    'indoors_house',
    'fashion_style',
    'school_study',
    'pop_culture'
  ) then
    return json_build_object('success', false, 'error', 'Invalid game theme');
  end if;

  if p_max_players is not null and p_max_players not between 2 and 99 then
    return json_build_object(
      'success', false,
      'error', 'Maximum players must be between 2 and 99'
    );
  end if;

  if p_total_rounds is null or p_total_rounds not between 3 and 15 then
    return json_build_object(
      'success', false,
      'error', 'Total rounds must be between 3 and 15'
    );
  end if;

  if p_cooldown_seconds is null or p_cooldown_seconds not between 10 and 90 then
    return json_build_object(
      'success', false,
      'error', 'Cooldown must be between 10 and 90 seconds'
    );
  end if;

  if p_is_public is null then
    return json_build_object('success', false, 'error', 'Visibility is required');
  end if;

  if not exists (
    select 1 from public.profiles p where p.id = v_caller_id
  ) then
    return json_build_object(
      'success', false,
      'error', 'Complete your profile before creating a room'
    );
  end if;

  for v_attempt in 1..25 loop
    v_room_code := lpad(floor(random() * 1000000)::text, 6, '0');
    v_room_id := null;

    begin
      insert into public.game_rooms (
        room_code,
        room_name,
        host_id,
        theme,
        round_duration,
        round_duration_seconds,
        total_rounds,
        cooldown_seconds,
        max_players,
        status,
        current_round,
        current_phase,
        is_public,
        phase_ends_at
      )
      values (
        v_room_code,
        v_room_name,
        v_caller_id,
        p_theme::public.game_theme,
        p_round_duration::public.round_duration,
        v_round_duration_seconds,
        p_total_rounds,
        p_cooldown_seconds,
        p_max_players,
        'lobby'::public.game_status,
        0,
        'lobby',
        p_is_public,
        null
      )
      returning id into v_room_id;
    exception
      when unique_violation then
        get stacked diagnostics v_constraint_name = constraint_name;
        if v_constraint_name <> 'game_rooms_room_code_key' then
          raise;
        end if;
    end;

    exit when v_room_id is not null;
  end loop;

  if v_room_id is null then
    raise exception 'Unable to allocate a unique room code';
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
    true,
    0,
    1,
    true
  );

  return json_build_object(
    'success', true,
    'room_id', v_room_id,
    'room_code', v_room_code
  );
end;
$$;

revoke all on function public.create_game_room(
  text,
  text,
  text,
  integer,
  integer,
  integer,
  boolean
) from public, anon, authenticated, service_role;
grant execute on function public.create_game_room(
  text,
  text,
  text,
  integer,
  integer,
  integer,
  boolean
) to authenticated;

revoke insert on public.game_rooms from authenticated;
revoke insert on public.game_participants from authenticated;
drop policy if exists "game_rooms_insert_host" on public.game_rooms;
drop policy if exists "game_participants_insert_host" on public.game_participants;

-- Room codes are no longer issued separately from room creation.
revoke all on function public.generate_room_code()
from public, anon, authenticated, service_role;
drop function public.generate_room_code();

-- The caller identity is derived exclusively from auth.uid(). The room row lock
-- serializes capacity checks and join-order assignment for concurrent requests.
revoke all on function public.join_game_room(text, uuid)
from public, anon, authenticated, service_role;
drop function public.join_game_room(text, uuid);

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
grant execute on function public.join_game_room(text)
to authenticated;

-- Verification failures apply at most one point and never make the total score
-- negative. Existing round points are replaced rather than accumulated.
create or replace function public.finalize_verified_submission(
  p_room_id uuid,
  p_user_id uuid,
  p_round_number integer,
  p_image_path text,
  p_is_success boolean,
  p_reason text
)
returns json
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_room_status public.game_status;
  v_current_phase text;
  v_current_round integer;
  v_phase_ends_at timestamptz;
  v_is_playing boolean;
  v_current_score integer;
  v_score_without_existing integer;
  v_new_status public.submission_status;
  v_new_points integer;
  v_reason text;
  v_existing_status public.submission_status;
  v_existing_points integer;
  v_existing_image_path text;
  v_existing_reason text;
begin
  if current_user <> 'service_role' then
    raise exception 'Only the verification service may finalize submissions'
      using errcode = '42501';
  end if;

  if p_room_id is null
     or p_user_id is null
     or p_round_number < 1
     or p_image_path is null
     or p_image_path !~ (
       '^'
       || p_room_id::text
       || '/'
       || p_user_id::text
       || '/round_'
       || p_round_number::text
       || '_[0-9]{10,16}[.]jpg$'
     ) then
    return json_build_object('success', false, 'error', 'Invalid submission image path');
  end if;

  select
    gr.status,
    gr.current_phase,
    gr.current_round,
    gr.phase_ends_at
  into
    v_room_status,
    v_current_phase,
    v_current_round,
    v_phase_ends_at
  from public.game_rooms gr
  where gr.id = p_room_id
  for update;

  if not found
     or v_room_status <> 'in_progress'::public.game_status
     or v_current_phase <> 'round_active'
     or v_current_round <> p_round_number
     or v_phase_ends_at is null
     or v_phase_ends_at <= now() then
    return json_build_object(
      'success', false,
      'error', 'Round has already ended or is no longer accepting submissions'
    );
  end if;

  select gp.is_playing, gp.current_score
  into v_is_playing, v_current_score
  from public.game_participants gp
  where gp.room_id = p_room_id
    and gp.user_id = p_user_id
  for update;

  if not found or v_is_playing is not true then
    return json_build_object('success', false, 'error', 'You are not an active participant');
  end if;

  v_new_status := case
    when p_is_success then 'success'::public.submission_status
    else 'failed'::public.submission_status
  end;
  v_reason := left(
    coalesce(nullif(btrim(p_reason), ''), 'Verification completed'),
    240
  );

  select
    rs.status,
    rs.points_earned,
    rs.image_url,
    rs.verification_reason
  into
    v_existing_status,
    v_existing_points,
    v_existing_image_path,
    v_existing_reason
  from public.round_submissions rs
  where rs.room_id = p_room_id
    and rs.user_id = p_user_id
    and rs.round_number = p_round_number
  for update;

  if found and v_existing_status in (
    'success'::public.submission_status,
    'skipped'::public.submission_status
  ) then
    return json_build_object(
      'success', true,
      'valid', v_existing_status = 'success'::public.submission_status,
      'points', v_existing_points,
      'status', v_existing_status,
      'reason', coalesce(v_existing_reason, 'Round already finalized')
    );
  end if;

  -- Retrying the same Edge Function request is idempotent.
  if found
     and v_existing_status = 'failed'::public.submission_status
     and v_existing_image_path = p_image_path then
    return json_build_object(
      'success', true,
      'valid', false,
      'points', v_existing_points,
      'status', v_existing_status,
      'reason', coalesce(v_existing_reason, v_reason)
    );
  end if;

  v_existing_points := coalesce(v_existing_points, 0);
  v_score_without_existing := greatest(v_current_score - v_existing_points, 0);
  v_new_points := case
    when p_is_success then 2
    when v_score_without_existing > 0 then -1
    else 0
  end;

  if found then
    update public.round_submissions
    set status = v_new_status,
        image_url = p_image_path,
        points_earned = v_new_points,
        verification_reason = v_reason,
        verified_at = now()
    where room_id = p_room_id
      and user_id = p_user_id
      and round_number = p_round_number;
  else
    insert into public.round_submissions (
      room_id,
      user_id,
      round_number,
      status,
      image_url,
      points_earned,
      verification_reason,
      verified_at
    )
    values (
      p_room_id,
      p_user_id,
      p_round_number,
      v_new_status,
      p_image_path,
      v_new_points,
      v_reason,
      now()
    );
  end if;

  update public.game_participants
  set current_score = v_score_without_existing + v_new_points
  where room_id = p_room_id
    and user_id = p_user_id;

  return json_build_object(
    'success', true,
    'valid', p_is_success,
    'points', v_new_points,
    'status', v_new_status,
    'reason', v_reason
  );
end;
$$;

-- Missing a round also obeys the score floor. Skipped rounds already have a row
-- and are therefore not converted into failures.
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
        case when gp.current_score > 0 then -1 else 0 end
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
    set current_score = greatest(gp.current_score + f.points_earned, 0)
    from inserted_failures f
    where gp.room_id = p_room_id
      and gp.user_id = f.user_id;

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
