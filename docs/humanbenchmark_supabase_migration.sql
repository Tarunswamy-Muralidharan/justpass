-- HumanBenchmark integration into JustPass — schema migration v2.
-- Adds class scoping (batchYear-dept-section) so per-class leaderboards work.
-- Apply via Supabase SQL editor on project vjmmegregguphbbbevzd.
-- Run top-to-bottom; each statement is idempotent.

-- 1. Add class_id column (nullable to keep legacy rows valid).
alter table scores add column if not exists class_id text;

-- 2. Index on (game_id, class_id, best_score) for fast per-class leaderboard.
create index if not exists idx_game_class_top
  on scores(game_id, class_id, best_score);

-- 3. Index on class_id alone for cross-game class queries.
create index if not exists idx_class
  on scores(class_id);

-- 4. New RPC: submit_score_v2 — accepts class id, clamps + UPSERTs only-if-better.
create or replace function submit_score_v2(
  p_player_id text,
  p_game_id   text,
  p_score     real,
  p_name      text,
  p_class     text
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_lower_is_better boolean;
  v_clamped real := p_score;
  v_class text := nullif(trim(p_class), '');
  v_name  text := nullif(trim(p_name),  '');
begin
  -- Plausibility clamps per game (reject obvious garbage).
  v_lower_is_better := p_game_id in (
    'reaction_time', 'aim_trainer', 'chimp_test'
  );
  if v_lower_is_better then
    if v_clamped < 50 then v_clamped := 50; end if;
    if v_clamped > 600000 then v_clamped := 600000; end if;
  else
    if v_clamped < 0 then v_clamped := 0; end if;
    if v_clamped > 100000 then v_clamped := 100000; end if;
  end if;

  insert into scores(player_id, game_id, best_score, attempts, display_name, class_id, updated_at)
  values (p_player_id, p_game_id, v_clamped, 1, v_name, v_class, (extract(epoch from now()) * 1000)::bigint)
  on conflict (player_id, game_id) do update
  set best_score = case
        when v_lower_is_better and excluded.best_score < scores.best_score
          then excluded.best_score
        when not v_lower_is_better and excluded.best_score > scores.best_score
          then excluded.best_score
        else scores.best_score
      end,
      attempts = scores.attempts + 1,
      display_name = coalesce(v_name, scores.display_name),
      class_id     = coalesce(v_class, scores.class_id),
      updated_at   = (extract(epoch from now()) * 1000)::bigint;
end
$$;

-- 5. Grant exec to anon role (RLS path via SECURITY DEFINER).
grant execute on function submit_score_v2(text, text, real, text, text) to anon;
