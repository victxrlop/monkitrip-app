import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

const FCM_URL = "https://fcm.googleapis.com/v1/projects/YOUR_PROJECT_ID/messages:send"

serve(async (req) => {
  if(req.method === 'OPTIONS'){
    return new Response('ok', { headers: {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': 'authorization, content-type',
    }})
  }

  try {
    const { token, gameId, gameName, senderName } = await req.json()

    // Get FCM access token using service account
    const serviceAccount = JSON.parse(Deno.env.get('FIREBASE_SERVICE_ACCOUNT') || '{}')
    const accessToken = await getAccessToken(serviceAccount)

    const projectId = serviceAccount.project_id
    const url = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`

    const res = await fetch(url, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        message: {
          token,
          notification: {
            title: `¿Juguemos ${gameName}? 🎮`,
            body: `${senderName} te invita a jugar`,
          },
          data: { gameId },
          android: {
            priority: 'high',
            notification: { sound: 'default' }
          }
        }
      })
    })

    const data = await res.json()
    return new Response(JSON.stringify(data), {
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' },
      status: res.ok ? 200 : 400
    })

  } catch(e) {
    return new Response(JSON.stringify({ error: e.message }), {
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' },
      status: 500
    })
  }
})

async function getAccessToken(serviceAccount: any): Promise<string> {
  const header = { alg: 'RS256', typ: 'JWT' }
  const now = Math.floor(Date.now() / 1000)
  const payload = {
    iss: serviceAccount.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  }

  const key = await crypto.subtle.importKey(
    'pkcs8',
    pemToArrayBuffer(serviceAccount.private_key),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false, ['sign']
  )

  const headerB64  = btoa(JSON.stringify(header))
  const payloadB64 = btoa(JSON.stringify(payload))
  const unsigned   = `${headerB64}.${payloadB64}`
  const sig        = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(unsigned))
  const jwt        = `${unsigned}.${btoa(String.fromCharCode(...new Uint8Array(sig)))}`

  const tokenRes  = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`
  })
  const tokenData = await tokenRes.json()
  return tokenData.access_token
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const b64 = pem.replace(/-----[^-]+-----/g, '').replace(/\s/g, '')
  const bin = atob(b64)
  const buf = new ArrayBuffer(bin.length)
  const arr = new Uint8Array(buf)
  for(let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i)
  return buf
}
