import { serve } from "https://deno.land/std@0.224.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.111.0"

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? ""
const SUPABASE_SERVICE_ROLE_KEY =
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
const STORAGE_DELETE_BATCH_SIZE = 1000

const admin = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
  auth: { persistSession: false, autoRefreshToken: false },
})

serve(async (req) => {
  if (req.method !== "POST") {
    return json({ success: false, error: "Method not allowed" }, 405)
  }
  if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
    return json({ success: false, error: "Service is not configured" }, 503)
  }

  const token = bearerToken(req)
  if (!token) {
    return json({ success: false, error: "Authentication required" }, 401)
  }

  const { data: authData, error: authError } = await admin.auth.getUser(token)
  if (authError || !authData.user) {
    return json({ success: false, error: "Invalid session" }, 401)
  }

  const userId = authData.user.id
  const { data: pathRows, error: pathError } = await admin.rpc(
    "list_submission_paths_for_account_deletion",
    { p_user_id: userId },
  )

  if (pathError) {
    return json({
      success: false,
      error: "Could not prepare account data for deletion",
    }, 500)
  }

  const paths = Array.isArray(pathRows)
    ? pathRows
      .map((row: { object_name?: unknown }) => row.object_name)
      .filter((path: unknown): path is string => typeof path === "string")
    : []

  for (let offset = 0; offset < paths.length; offset += STORAGE_DELETE_BATCH_SIZE) {
    const batch = paths.slice(offset, offset + STORAGE_DELETE_BATCH_SIZE)
    const { error: storageError } = await admin.storage
      .from("submissions")
      .remove(batch)

    if (storageError) {
      return json({
        success: false,
        error: "Could not remove uploaded photos",
      }, 500)
    }
  }

  const { error: deleteError } = await admin.auth.admin.deleteUser(userId)
  if (deleteError) {
    return json({
      success: false,
      error: "Could not delete the account",
    }, 500)
  }

  return json({ success: true })
})

function bearerToken(req: Request): string | null {
  const authorization = req.headers.get("Authorization") ?? ""
  return authorization.startsWith("Bearer ")
    ? authorization.slice(7).trim()
    : null
}

function json(body: unknown, status = 200): Response {
  return Response.json(body, {
    status,
    headers: { "Cache-Control": "no-store" },
  })
}
