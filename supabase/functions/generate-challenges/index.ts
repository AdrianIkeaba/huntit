import { serve } from "https://deno.land/std@0.224.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.111.0"

const GEMINI_API_KEY = Deno.env.get("GEMINI_API_KEY")
const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? ""
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""

const admin = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
  auth: { persistSession: false, autoRefreshToken: false },
})

serve(async (req) => {
  try {
    if (req.method !== "POST") {
      return json({ success: false, error: "Method not allowed" }, 405)
    }
    if (!GEMINI_API_KEY || !SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
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

    const body = await req.json()
    const roomId = typeof body.roomId === "string" ? body.roomId : ""
    if (!isUuid(roomId)) {
      return json({ success: false, error: "Invalid room" }, 400)
    }

    const { data: room, error: roomError } = await admin
      .from("game_rooms")
      .select("host_id,status,theme,total_rounds")
      .eq("id", roomId)
      .single()

    if (roomError || !room) {
      return json({ success: false, error: "Room not found" }, 404)
    }
    if (room.host_id !== authData.user.id) {
      return json({ success: false, error: "Only the host can generate challenges" }, 403)
    }
    if (room.status !== "lobby") {
      return json({ success: false, error: "Challenges can only be generated in the lobby" }, 409)
    }

    const { count, error: countError } = await admin
      .from("game_challenges")
      .select("id", { count: "exact", head: true })
      .eq("room_id", roomId)

    if (countError) {
      return json({ success: false, error: "Unable to inspect challenges" }, 500)
    }
    if ((count ?? 0) >= room.total_rounds) {
      return json({ success: true, existing: true })
    }

    const themeDescriptions: Record<string, string> = {
      outdoors_nature:
        "outdoor scenes, nature, plants, animals, insects, and natural landscapes",
      indoors_house:
        "indoor household items, furniture, appliances, and decorations",
      fashion_style:
        "fashion and style items such as hats, shoes, belts, watches, sunglasses, jewelry, and clothing accessories",
      school_study:
        "school supplies, study materials, educational items, and stationery",
      pop_culture:
        "entertainment items, media, games, toys, and popular culture objects",
    }
    const themeDescription = themeDescriptions[room.theme] ?? room.theme

    const prompt = `Generate exactly ${room.total_rounds} unique scavenger hunt challenges for the theme: ${themeDescription}.

Each challenge must be a specific, findable object, be reasonably available in an everyday environment, and use 3-6 words beginning with "Find a/an" or "Find something".

Return only a valid JSON array of strings.`

    const geminiResponse = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-goog-api-key": GEMINI_API_KEY,
        },
        body: JSON.stringify({
          contents: [{ parts: [{ text: prompt }] }],
          generationConfig: {
            temperature: 0.8,
            maxOutputTokens: 5000,
            responseMimeType: "application/json",
          },
        }),
      },
    )

    if (!geminiResponse.ok) {
      return json({ success: false, error: "Challenge generation failed" }, 502)
    }

    const geminiData = await geminiResponse.json()
    const responseText = geminiData.candidates?.[0]?.content?.parts
      ?.filter((part: { text?: string }) => typeof part.text === "string")
      .map((part: { text: string }) => part.text)
      .join("")

    if (!responseText) {
      return json({ success: false, error: "Challenge generation returned no content" }, 502)
    }

    let challenges: unknown
    try {
      challenges = JSON.parse(extractJson(responseText))
    } catch {
      return json({ success: false, error: "Challenge generation returned invalid content" }, 502)
    }

    if (
      !Array.isArray(challenges) ||
      challenges.length < room.total_rounds ||
      challenges.some((challenge) =>
        typeof challenge !== "string" ||
        challenge.trim().length < 3 ||
        challenge.length > 120
      )
    ) {
      return json({ success: false, error: "Challenge generation returned invalid challenges" }, 502)
    }

    const records = challenges.slice(0, room.total_rounds).map(
      (challenge: string, index: number) => ({
        room_id: roomId,
        round_number: index + 1,
        challenge_text: challenge.trim(),
      }),
    )

    const { error: upsertError } = await admin
      .from("game_challenges")
      .upsert(records, { onConflict: "room_id,round_number" })

    if (upsertError) {
      return json({ success: false, error: "Unable to save challenges" }, 500)
    }

    return json({ success: true })
  } catch {
    return json({ success: false, error: "Unexpected server error" }, 500)
  }
})

function bearerToken(req: Request): string | null {
  const authorization = req.headers.get("Authorization") ?? ""
  return authorization.startsWith("Bearer ") ? authorization.slice(7).trim() : null
}

function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
    .test(value)
}

function extractJson(text: string): string {
  const cleaned = text.replace(/```json|```/gi, "").trim()
  const start = cleaned.indexOf("[")
  const end = cleaned.lastIndexOf("]")
  return start >= 0 && end > start ? cleaned.slice(start, end + 1) : cleaned
}

function json(body: unknown, status = 200): Response {
  return Response.json(body, {
    status,
    headers: { "Cache-Control": "no-store" },
  })
}
