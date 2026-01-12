import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

const GEMINI_API_KEY = Deno.env.get('GEMINI_API_KEY')

serve(async (req) => {
  try {
    // 1. Extract request data
    const requestData = await req.json()
    console.log(`Received request with keys: ${Object.keys(requestData).join(', ')}`)
    
    const { imageBase64, challenge, theme } = requestData
    
    // Validate required parameters
    if (!imageBase64) {
      return new Response(
        JSON.stringify({ 
          success: false, 
          error: "Missing required parameter: imageBase64" 
        }),
        { status: 400, headers: { "Content-Type": "application/json" } }
      )
    }
    
    if (!challenge) {
      return new Response(
        JSON.stringify({ 
          success: false, 
          error: "Missing required parameter: challenge" 
        }),
        { status: 400, headers: { "Content-Type": "application/json" } }
      )
    }

    // Check image size
    if (imageBase64.length > 1500000) { // ~1.5MB limit
      return new Response(
        JSON.stringify({ 
          success: false, 
          error: `Image too large: ${Math.round(imageBase64.length/1024)}KB exceeds limit of 1500KB` 
        }),
        { status: 400, headers: { "Content-Type": "application/json" } }
      )
    }
    
    const themeContext = {
      'outdoors_nature': 'outdoor/nature context',
      'indoors_house': 'indoor/household context',
      'fashion_style': 'fashion/style context',
      'school_study': 'school/study context',
      'pop_culture': 'pop culture context'
    }

    const prompt = `You are verifying a scavenger hunt submission.

Challenge: "${challenge}"
Theme: ${themeContext[theme] || theme}

Rules:
1. The photo must show a REAL object matching the challenge
2. Must be taken in ${themeContext[theme] || theme}
3. Must NOT be a photo of a screen, print, or drawing (unless challenge specifically asks for these)
4. Must be a clear, recognizable match to the challenge

Analyze this image and respond with ONLY a JSON object:
{
  "valid": true/false,
  "reason": "brief explanation"
}

Be fair but strict. If it matches the challenge legitimately, approve it.`

    // 2. Call Gemini Vision API with retry logic
    console.log(`Calling Gemini API with challenge: "${challenge}", theme: ${theme}`)
    console.log(`Image base64 length: ${imageBase64.length} chars, first 20 chars: ${imageBase64.substring(0, 20)}...`)
    
    let retryCount = 0;
    let geminiResponse = null;
    let responseText = "";
    let geminiData = null;
    
    while (retryCount < 2) {
      try {
        geminiResponse = await fetch(
          `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent`,
          {
            method: 'POST',
            headers: { 
              'Content-Type': 'application/json',
              'x-goog-api-key' : GEMINI_API_KEY 
            },
            body: JSON.stringify({
              contents: [{
                parts: [
                  { text: prompt },
                  {
                    inline_data: {
                      mime_type: "image/jpeg",
                      data: imageBase64
                    }
                  }
                ]
              }],
              generationConfig: {
                temperature: 0.1, // Lower temperature for more predictable format
                maxOutputTokens: 200,
              }
            })
          }
        );
        
        responseText = await geminiResponse.text();
        console.log(`Gemini API response status: ${geminiResponse.status}`)
        
        // Try to parse as JSON
        try {
          geminiData = JSON.parse(responseText);
          // If we reach here, JSON parsing succeeded
          break;
        } catch (parseError) {
          console.error(`Failed to parse Gemini response as JSON (attempt ${retryCount + 1}):`, parseError);
          console.log(`Raw response: ${responseText.substring(0, 200)}...`)
          retryCount++;
          if (retryCount < 2) {
            console.log("Retrying Gemini API call...")
            await new Promise(resolve => setTimeout(resolve, 1000)); // Wait 1 second before retry
          }
        }
      } catch (fetchError) {
        console.error(`Fetch error (attempt ${retryCount + 1}):`, fetchError);
        retryCount++;
        if (retryCount < 2) {
          console.log("Retrying Gemini API call due to fetch error...")
          await new Promise(resolve => setTimeout(resolve, 1000)); // Wait 1 second before retry
        }
      }
    }
    
    // Check if we still don't have valid data after retries
    if (!geminiData) {
      console.error("Failed to get valid response after retries")
      return new Response(
        JSON.stringify({ 
          success: false, 
          error: `Could not get valid response from Gemini API after retries`,
          rawResponse: responseText.substring(0, 500) // Include part of the raw response for debugging
        }),
        { status: 500, headers: { "Content-Type": "application/json" } }
      )
    }
    
    // Check for errors in the Gemini response
    if (geminiResponse.status !== 200 || geminiData.error) {
      const errorMessage = geminiData.error?.message || `Status code: ${geminiResponse.status}`;
      console.error("Gemini API error:", errorMessage)
      return new Response(
        JSON.stringify({ 
          success: false, 
          error: `Gemini API error: ${errorMessage}` 
        }),
        { status: 500, headers: { "Content-Type": "application/json" } }
      )
    }
    
    // 3. Validate response structure with detailed logging
    if (!geminiData.candidates || geminiData.candidates.length === 0) {
      console.error("No candidates in Gemini response:", JSON.stringify(geminiData))
      return new Response(
        JSON.stringify({ 
          success: false, 
          error: "Gemini API returned no candidates",
          responseDetails: JSON.stringify(geminiData)
        }),
        { status: 500, headers: { "Content-Type": "application/json" } }
      )
    }
    
    const candidate = geminiData.candidates[0];
    console.log("Candidate structure:", JSON.stringify(candidate).substring(0, 200))
    
    if (!candidate.content) {
      console.error("No content in candidate:", JSON.stringify(candidate))
      return new Response(
        JSON.stringify({ 
          success: false, 
          error: "Gemini API returned candidate without content property",
          candidateStructure: JSON.stringify(candidate)
        }),
        { status: 500, headers: { "Content-Type": "application/json" } }
      )
    }
    
    if (!candidate.content.parts || candidate.content.parts.length === 0) {
      console.error("No parts in candidate content:", JSON.stringify(candidate.content))
      return new Response(
        JSON.stringify({ 
          success: false, 
          error: "Gemini API returned content without parts",
          contentStructure: JSON.stringify(candidate.content)
        }),
        { status: 500, headers: { "Content-Type": "application/json" } }
      )
    }
    
    // Find the text part - Gemini sometimes returns non-text parts
    let textPart = null;
    for (const part of candidate.content.parts) {
      if (typeof part.text === 'string') {
        textPart = part;
        break;
      }
    }
    
    if (!textPart) {
      console.error("No text part in content parts:", JSON.stringify(candidate.content.parts))
      return new Response(
        JSON.stringify({ 
          success: false, 
          error: "Gemini API returned no text in response",
          partsStructure: JSON.stringify(candidate.content.parts)
        }),
        { status: 500, headers: { "Content-Type": "application/json" } }
      )
    }
    
    const resultText = textPart.text.trim();
    console.log("Result text:", resultText)
    
    // 4. Try to parse the JSON result safely - if not valid JSON, try to extract JSON from text
    let result;
    try {
      // First attempt: Direct parsing
      result = JSON.parse(resultText);
      
      // Validate required fields
      if (!('valid' in result) || !('reason' in result)) {
        throw new Error("Response missing required fields");
      }
    } catch (parseError) {
      console.log("Direct JSON parsing failed, trying to extract JSON from text:", parseError);
      
      try {
        // Second attempt: Try to find JSON within response text
        const jsonMatch = resultText.match(/\{[\s\S]*\}/);
        if (jsonMatch) {
          const extractedJson = jsonMatch[0];
          console.log("Extracted JSON:", extractedJson);
          result = JSON.parse(extractedJson);
          
          // Validate extracted JSON
          if (!('valid' in result) || !('reason' in result)) {
            throw new Error("Extracted JSON missing required fields");
          }
        } else {
          throw new Error("Could not extract JSON from response");
        }
      } catch (extractError) {
        console.error("JSON extraction also failed:", extractError);
        
        // Last resort - create our own result based on text analysis
        console.log("Creating default result based on text analysis");
        
        // Check if text contains positive indicators
        const isPositive = /yes|correct|valid|match|good|acceptable|approve/i.test(resultText);
        
        result = {
          valid: isPositive,
          reason: isPositive ? 
            "The image appears to match the challenge (based on text analysis)" : 
            "The image does not appear to match the challenge (based on text analysis)"
        };
      }
    }

    // 5. Return the result
    console.log("Final result:", JSON.stringify(result))
    return new Response(
      JSON.stringify({
        success: true,
        isValid: result.valid,
        reason: result.reason
      }),
      { headers: { "Content-Type": "application/json" } }
    )

  } catch (error) {
    console.error("Unexpected error in verify-submission:", error)
    return new Response(
      JSON.stringify({ 
        success: false, 
        error: `Unexpected error: ${error.message}` 
      }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    )
  }
})