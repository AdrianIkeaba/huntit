import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const GEMINI_API_KEY = Deno.env.get("GEMINI_API_KEY")
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")

serve(async (req) => {
  try {
    const body = await req.json()
    const { imageBase64, challenge, theme, roomId, roundNumber } = body

    if (!imageBase64) {
      return json({ success: false, error: "Missing imageBase64" }, 400)
    }
    if (!challenge) {
      return json({ success: false, error: "Missing challenge" }, 400)
    }
    if (!roomId) {
      return json({ success: false, error: "Missing roomId" }, 400)
    }
    if (roundNumber === undefined) {
      return json({ success: false, error: "Missing roundNumber" }, 400)
    }

    // CRITICAL ADDITION: Verify if the round has already ended
    const supabase = createClient(
      SUPABASE_URL || '',
      SUPABASE_SERVICE_ROLE_KEY || ''
    )

    // 1. Get current game room info to check phase and phase_ends_at
    const { data: gameRoom, error: roomError } = await supabase
      .from('game_rooms')
      .select('current_phase, phase_ends_at, current_round')
      .eq('id', roomId)
      .single()
    
    if (roomError) {
      console.log("Error fetching game room:", roomError.message)
      return json({ success: false, error: "Failed to verify round status" }, 500)
    }
    
    // 2. Verify the room exists and is in the correct round
    if (!gameRoom) {
      return json({ success: false, error: "Game room not found" }, 404)
    }
    
    // 3. Check if current_round matches the submission roundNumber
    if (gameRoom.current_round !== roundNumber) {
      return json({ 
        success: false, 
        error: "Round mismatch - submission is for a different round than the current game round"
      }, 400)
    }
    
    // 4. Check if the round has already ended by comparing current time with phase_ends_at
    const now = new Date()
    const phaseEndsAt = new Date(gameRoom.phase_ends_at)
    
    if (now > phaseEndsAt && gameRoom.current_phase === 'in_progress') {
      console.log(`Round has ended. Current time: ${now.toISOString()}, Phase end time: ${phaseEndsAt.toISOString()}`)
      return json({ 
        success: false, 
        error: "Round has already ended. Submissions are no longer being accepted for this round."
      }, 400)
    }
    
    // If we get here, the round is still active - continue with Gemini verification
    const prompt = `
Verify this scavenger hunt submission.

Challenge: "${challenge}"
Theme: ${theme}

Return ONLY JSON:
DO NOT use code fences. DO NOT format the answer. Return ONLY raw JSON.
If the image matches the theme and challenge return valid as true, if not false.
{
  "valid": true/false,
  "reason": "short reason"
}
`

    const geminiResponse = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-goog-api-key": GEMINI_API_KEY
        },
        body: JSON.stringify({
          contents: [
            {
              parts: [
                { text: prompt },
                {
                  inlineData: {
                    mimeType: "image/jpeg",
                    data: imageBase64
                  }
                }
              ]
            }
          ],
          generationConfig: {
            temperature: 0.2,
            maxOutputTokens: 1000
          }
        })
      }
    )

    // First try to parse the response as JSON
    let data
    try {
      data = await geminiResponse.json()
      console.log("Raw Gemini response:", JSON.stringify(data).slice(0, 500))
    } catch (e) {
      const rawText = await geminiResponse.text()
      console.log("Raw text from Gemini:", rawText.slice(0, 500))
      return json({
        success: false,
        error: "Gemini returned non-JSON response",
        raw: rawText.slice(0, 300)
      })
    }

    if (data.error) {
      return json({ success: false, error: data.error.message })
    }

    if (!data.candidates || data.candidates.length === 0) {
      return json({ success: false, error: "No candidates returned by Gemini" })
    }

    // Extract the text from the response
    let responseText = ""
    try {
      const candidate = data.candidates[0]
      
      // Handle different response structures
      if (candidate.content && candidate.content.parts) {
        // New structure
        responseText = candidate.content.parts
          .filter(part => part.text)
          .map(part => part.text)
          .join('')
      } else if (candidate.contents && candidate.contents[0]?.parts) {
        // Alternative structure
        responseText = candidate.contents[0].parts
          .filter(part => part.text)
          .map(part => part.text)
          .join('')
      } else if (candidate.text) {
        // Simple structure
        responseText = candidate.text
      } else {
        console.log("Unexpected Gemini response structure:", JSON.stringify(candidate).slice(0, 500))
        return json({ success: false, error: "Could not extract text from Gemini response" })
      }
    } catch (e) {
      console.log("Error extracting text:", e.message)
      return json({ success: false, error: "Error extracting text from response" })
    }

    // Clean and parse the JSON from the text
    console.log("Raw text from Gemini:", responseText)
    let parsed
    try {
      parsed = JSON.parse(extractJson(responseText))
    } catch (e) {
      console.log("JSON parse error:", e.message)
      return json({
        success: false,
        error: "Gemini output was not valid JSON",
        raw: responseText.slice(0, 200)
      })
    }

    return json({
      success: true,
      valid: parsed.valid,
      reason: parsed.reason
    })
  } catch (e) {
    console.log("Unexpected error:", e.message)
    return json({ success: false, error: e.message }, 500)
  }
})

function extractJson(text) {
  console.log("Extracting JSON from:", text.slice(0, 300))
  
  // Remove code fences like ```json or ```anything
  text = text.replace(/```json\n|```\n|```/g, "")
  
  // Fallback: remove any stray backticks
  text = text.replace(/`/g, "")

  // Try to find valid JSON
  const start = text.indexOf('{')
  const end = text.lastIndexOf('}')
  
  if (start >= 0 && end > start) {
    return text.slice(start, end + 1)
  }
  
  return text // Return the cleaned text if JSON pattern not found
}

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "Content-Type": "application/json" }
  })
}