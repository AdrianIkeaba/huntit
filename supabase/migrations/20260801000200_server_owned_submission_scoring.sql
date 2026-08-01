-- Release order 2: keep AI verification and score authority on the server.

alter table public.round_submissions
  add column if not exists verification_reason text;

alter table public.round_submissions
  drop constraint if exists round_submissions_verification_reason_length;

alter table public.round_submissions
  add constraint round_submissions_verification_reason_length
  check (
    verification_reason is null
    or char_length(verification_reason) <= 240
  );

-- Skipping and verification both lock the room and participant in the same order.
-- This prevents a concurrent skip and verified submission from racing the unique
-- (room_id, user_id, round_number) constraint.
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
  v_room_status public.game_status;
  v_current_phase text;
  v_current_round integer;
  v_phase_ends_at timestamptz;
  v_is_playing boolean;
  v_existing_status public.submission_status;
begin
  if v_caller_id is null or v_caller_id <> p_user_id then
    raise exception 'Not authorized to skip as this user' using errcode = '42501';
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
    return json_build_object('success', false, 'error', 'Round is not accepting skips');
  end if;

  select gp.is_playing
  into v_is_playing
  from public.game_participants gp
  where gp.room_id = p_room_id
    and gp.user_id = v_caller_id
  for update;

  if not found or v_is_playing is not true then
    return json_build_object('success', false, 'error', 'You are not an active participant');
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
      'points', 0,
      'status', v_existing_status,
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

  return json_build_object(
    'success', true,
    'points', 0,
    'status', 'skipped'::public.submission_status
  );
end;
$$;

-- This is the only function allowed to turn an AI decision into a score.
-- It is invoked by the authenticated Edge Function with the service role.
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

  select gp.is_playing
  into v_is_playing
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
  v_new_points := case when p_is_success then 2 else -1 end;
  v_reason := left(
    coalesce(nullif(btrim(p_reason), ''), 'Verification completed'),
    240
  );

  select
    rs.status,
    coalesce(rs.points_earned, 0),
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

    update public.game_participants
    set current_score = current_score + (v_new_points - v_existing_points)
    where room_id = p_room_id
      and user_id = p_user_id;
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

    update public.game_participants
    set current_score = current_score + v_new_points
    where room_id = p_room_id
      and user_id = p_user_id;
  end if;

  return json_build_object(
    'success', true,
    'valid', p_is_success,
    'points', v_new_points,
    'status', v_new_status,
    'reason', v_reason
  );
end;
$$;

revoke all on function public.finalize_verified_submission(
  uuid,
  uuid,
  integer,
  text,
  boolean,
  text
) from public, anon, authenticated;
grant execute on function public.finalize_verified_submission(
  uuid,
  uuid,
  integer,
  text,
  boolean,
  text
) to service_role;

-- Remove the client-controlled scoring endpoint entirely.
revoke all on function public.submit_round(uuid, uuid, integer, text, boolean)
from public, anon, authenticated, service_role;

drop function public.submit_round(uuid, uuid, integer, text, boolean);

revoke all on function public.skip_round(uuid, uuid, integer)
from public, anon, authenticated, service_role;
grant execute on function public.skip_round(uuid, uuid, integer)
to authenticated, service_role;
