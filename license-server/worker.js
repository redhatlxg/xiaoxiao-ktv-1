const json = (data, status = 200) => new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", "x-content-type-options": "nosniff" } });
const now = () => Math.floor(Date.now() / 1000);
const enc = new TextEncoder();
const hex = b => [...new Uint8Array(b)].map(x => x.toString(16).padStart(2, "0")).join("");
const id = () => crypto.randomUUID();
const sha256 = async s => hex(await crypto.subtle.digest("SHA-256", enc.encode(s)));
const b64url = b => btoa(String.fromCharCode(...new Uint8Array(b))).replace(/=/g,"").replace(/\\+/g,"-").replace(/\\//g,"_");
const codePattern = /^(?:[A-HJ-NP-Z2-9]{5}-){3}[A-HJ-NP-Z2-9]{5}$/;
const devicePattern = /^[a-f0-9-]{16,64}$/i;
function code() { const a="ABCDEFGHJKLMNPQRSTUVWXYZ23456789", b=crypto.getRandomValues(new Uint8Array(20)); return [...b].map(x=>a[x%a.length]).join("").match(/.{5}/g).join("-"); }
async function admin(request, env) {
  const a=enc.encode(request.headers.get("x-admin-secret")||""), b=enc.encode(env.ADMIN_SECRET||"");
  let diff=a.length^b.length; for(let i=0;i<Math.max(a.length,b.length);i++) diff |= (a[i]||0)^(b[i]||0);
  return !!env.ADMIN_SECRET && diff===0;
}
async function signedToken(payload, env) {
  if (!env.LICENSE_SIGNING_SECRET) throw new Error("LICENSE_SIGNING_SECRET is not configured");
  const key=await crypto.subtle.importKey("raw",enc.encode(env.LICENSE_SIGNING_SECRET),{name:"HMAC",hash:"SHA-256"},false,["sign"]);
  const head=b64url(enc.encode(JSON.stringify({alg:"HS256",typ:"JWT"})));
  const body=b64url(enc.encode(JSON.stringify(payload)));
  const input=head+"."+body;
  return input+"."+b64url(await crypto.subtle.sign("HMAC",key,enc.encode(input)));
}
async function rateLimit(request, env, bucket, limit=12, window=60) {
  const ip=request.headers.get("CF-Connecting-IP")||"unknown", key=await sha256(bucket+":"+ip), t=now(), start=t-(t%window);
  await env.LICENSE_DB.prepare("INSERT INTO rate_limits(bucket, window_start, hits) VALUES(?,?,1) ON CONFLICT(bucket,window_start) DO UPDATE SET hits=hits+1").bind(key,start).run();
  const r=await env.LICENSE_DB.prepare("SELECT hits FROM rate_limits WHERE bucket=? AND window_start=?").bind(key,start).first();
  return (r?.hits||0)<=limit;
}
async function event(env, codeId, eventName, deviceId, detail="") {
  await env.LICENSE_DB.prepare("INSERT INTO license_events(id,code_id,event,device_id,created_at,detail) VALUES(?,?,?,?,?,?)").bind(id(),codeId,eventName,deviceId||null,now(),detail).run();
}
async function activate(request, env) {
  if(!await rateLimit(request,env,"activate",10)) return json({ok:false,error:"RATE_LIMITED"},429);
  const body=await request.json().catch(()=>null), c=String(body?.code||"").trim().toUpperCase(), d=String(body?.deviceId||"").trim();
  if(!codePattern.test(c)||!devicePattern.test(d)) return json({ok:false,error:"INVALID_INPUT"},400);
  const row=await env.LICENSE_DB.prepare("SELECT * FROM license_codes WHERE code_hash=?").bind(await sha256(c)).first();
  if(!row||row.status==="revoked") return json({ok:false,error:"INVALID_OR_REVOKED_CODE"},403);
  if(row.device_id&&row.device_id!==d) return json({ok:false,error:"CODE_BOUND_TO_ANOTHER_DEVICE"},409);
  const t=now();
  if(row.expires_at&&row.expires_at<=t) return json({ok:false,error:"LICENSE_EXPIRED"},403);
  if(row.status==="active" && row.device_id===d) return json({ok:false,error:"CODE_ALREADY_ACTIVATED"},409);
  const existing=await env.LICENSE_DB.prepare("SELECT id,plan,expires_at FROM license_codes WHERE device_id=? AND status='active' AND (expires_at IS NULL OR expires_at>?) ORDER BY CASE WHEN plan='lifetime' THEN 0 ELSE 1 END, expires_at DESC LIMIT 1").bind(d,t).first();
  if(existing?.plan==="lifetime" && row.plan==="annual") return json({ok:false,error:"DEVICE_ALREADY_HAS_LIFETIME"},409);
  if(existing?.id!==row.id && existing?.plan==="annual" && row.plan==="lifetime") { /* lifetime supersedes annual */ }
  let expiry=null;
  if(row.plan==="annual") expiry=Math.max(existing?.plan==="annual"?(existing.expires_at||t):t,t)+365*86400;
  const upd=await env.LICENSE_DB.prepare("UPDATE license_codes SET status='active',device_id=?,activated_at=COALESCE(activated_at,?),expires_at=? WHERE id=? AND status!='revoked' AND (device_id IS NULL OR device_id=?)").bind(d,t,expiry,row.id,d).run();
  if(!upd.success||!upd.meta?.changes) return json({ok:false,error:"ACTIVATION_CONFLICT"},409);
  await event(env,row.id,row.activated_at?"renew":"activate",d);
  const token=await signedToken({iss:"xiaoxiao-ktv-license",sub:d,plan:row.plan,iat:t,exp:expiry||null,licenseId:row.id},env);
  return json({ok:true,plan:row.plan,deviceBound:true,activatedAt:row.activated_at||t,expiresAt:expiry,token});
}
async function validate(request,env) {
  if(!await rateLimit(request,env,"validate",60)) return json({ok:false,error:"RATE_LIMITED"},429);
  const body=await request.json().catch(()=>null), d=String(body?.deviceId||"").trim();
  if(!devicePattern.test(d)) return json({ok:false,error:"INVALID_DEVICE"},400);
  const t=now();
  const row=await env.LICENSE_DB.prepare("SELECT id,plan,activated_at,expires_at FROM license_codes WHERE device_id=? AND status='active' AND (expires_at IS NULL OR expires_at>?) ORDER BY CASE WHEN plan='lifetime' THEN 0 ELSE 1 END, expires_at DESC LIMIT 1").bind(d,t).first();
  if(!row) return json({ok:false,error:"NOT_LICENSED"},403);
  const token=await signedToken({iss:"xiaoxiao-ktv-license",sub:d,plan:row.plan,iat:t,exp:row.expires_at||null,licenseId:row.id},env);
  return json({ok:true,plan:row.plan,expiresAt:row.expires_at,token});
}
async function adminRoute(request,env,url) {
  if(!await admin(request,env)) return json({ok:false,error:"UNAUTHORIZED"},401);
  if(request.method==="POST"&&url.pathname==="/v1/admin/codes") {
    const body=await request.json().catch(()=>null), plan=body?.plan, count=Number(body?.count||1);
    if(!["annual","lifetime"].includes(plan)||!Number.isInteger(count)||count<1||count>100) return json({ok:false,error:"INVALID_PLAN_OR_COUNT"},400);
    const codes=[];
    for(let i=0;i<count;i++){const c=code();await env.LICENSE_DB.prepare("INSERT INTO license_codes(id,code_hash,plan,created_at,note) VALUES(?,?,?,?,?)").bind(id(),await sha256(c),plan,now(),String(body?.note||"").slice(0,200)).run();codes.push(c);}
    return json({ok:true,plan,count,codes},201);
  }
  if(request.method==="GET"&&url.pathname==="/v1/admin/licenses") {
    const limit=Math.max(1,Math.min(Number(url.searchParams.get("limit")||100),500));
    const rows=await env.LICENSE_DB.prepare("SELECT id,plan,status,created_at,activated_at,expires_at,device_id,note FROM license_codes ORDER BY created_at DESC LIMIT ?").bind(limit).all();
    return json({ok:true,licenses:rows.results||[]});
  }
  if(request.method==="POST"&&url.pathname==="/v1/admin/revoke") {
    const b=await request.json().catch(()=>null); if(!b?.licenseId) return json({ok:false,error:"LICENSE_ID_REQUIRED"},400);
    const r=await env.LICENSE_DB.prepare("UPDATE license_codes SET status='revoked' WHERE id=?").bind(String(b.licenseId)).run();
    if(!r.meta?.changes) return json({ok:false,error:"NOT_FOUND"},404);
    await event(env,String(b.licenseId),"revoke",null,String(b.reason||"").slice(0,200)); return json({ok:true});
  }
  if(request.method==="POST"&&url.pathname==="/v1/admin/unbind") {
    const b=await request.json().catch(()=>null); if(!b?.licenseId||String(b.approved)!=="true") return json({ok:false,error:"EXPLICIT_APPROVAL_REQUIRED"},400);
    const row=await env.LICENSE_DB.prepare("SELECT id,device_id FROM license_codes WHERE id=? AND status='active'").bind(String(b.licenseId)).first();
    if(!row) return json({ok:false,error:"NOT_FOUND"},404);
    await env.LICENSE_DB.prepare("UPDATE license_codes SET status='unused',device_id=NULL,activated_at=NULL WHERE id=?").bind(row.id).run();
    await event(env,row.id,"admin_unbind",row.device_id,String(b.reason||"approved device replacement").slice(0,200)); return json({ok:true});
  }
  return json({ok:false,error:"NOT_FOUND"},404);
}
export default { async fetch(request,env) {
  const url=new URL(request.url);
  if(request.method==="OPTIONS") return new Response(null,{status:204,headers:{"access-control-allow-origin":"*","access-control-allow-methods":"GET,POST,OPTIONS","access-control-allow-headers":"content-type,x-admin-secret","access-control-max-age":"86400"}});
  try {
    if(request.method==="POST"&&url.pathname==="/v1/activate") return activate(request,env);
    if(request.method==="POST"&&url.pathname==="/v1/validate") return validate(request,env);
    if(url.pathname.startsWith("/v1/admin/")) return adminRoute(request,env,url);
    if(request.method==="GET"&&url.pathname==="/health") return json({ok:true,service:"xiaoxiao-ktv-license"});
    return json({ok:false,error:"NOT_FOUND"},404);
  } catch(e) { return json({ok:false,error:"INTERNAL_ERROR"},500); }
} };
