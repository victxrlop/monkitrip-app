import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, content-type',
}

serve(async (req) => {
  if(req.method === 'OPTIONS') return new Response('ok', { headers: CORS })

  try {
    const { token, gameId, gameName, senderName, inviteId } = await req.json()
    if(!token) return new Response(JSON.stringify({ error: 'Missing token' }), { status: 400, headers: CORS })

    const raw = Deno.env.get('FIREBASE_SERVICE_ACCOUNT') || '{}'
    const sa  = JSON.parse(raw)

    // Fix common issue: private_key stored with literal \n instead of real newlines
    const privateKey = sa.private_key.replace(/\\n/g, '\n')

    const accessToken = await getGoogleAccessToken(sa.client_email, privateKey)

    const url = `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`
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
          data: { gameId, inviteId: inviteId || '' },
          android: { priority: 'high', notification: { sound: 'default' } }
        }
      })
    })

    const data = await res.json()
    return new Response(JSON.stringify(data), {
      headers: { ...CORS, 'Content-Type': 'application/json' },
      status: res.ok ? 200 : 400
    })

  } catch(e) {
    return new Response(JSON.stringify({ error: e.message }), {
      headers: { ...CORS, 'Content-Type': 'application/json' },
      status: 500
    })
  }
})

async function getGoogleAccessToken(clientEmail: string, privateKey: string): Promise<string> {
  const now = Math.floor(Date.now() / 1000)
  const header  = { alg: 'RS256', typ: 'JWT' }
  const payload = {
    iss: clientEmail,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  }

  const encode = (obj: object) => btoa(JSON.stringify(obj))
    .replace(/=/g,'').replace(/\+/g,'-').replace(/\//g,'_')

  const unsigned = `${encode(header)}.${encode(payload)}`

  const pemBody = privateKey
    .replace('-----BEGIN PRIVATE KEY-----', '')
    .replace('-----END PRIVATE KEY-----', '')
    .replace(/\s/g, '')

  const der = Uint8Array.from(atob(pemBody), c => c.charCodeAt(0))

  const key = await crypto.subtle.importKey(
    'pkcs8', der.buffer,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false, ['sign']
  )

  const sig = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5', key,
    new TextEncoder().encode(unsigned)
  )

  const sigB64 = btoa(String.fromCharCode(...new Uint8Array(sig)))
    .replace(/=/g,'').replace(/\+/g,'-').replace(/\//g,'_')

  const jwt = `${unsigned}.${sigB64}`

  const tokenRes = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`
  })

  const tokenData = await tokenRes.json()
  if(!tokenData.access_token) throw new Error('No access_token: ' + JSON.stringify(tokenData))
  return tokenData.access_token
}
