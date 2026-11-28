// Cep Atölyesi - e-Devlet IMEI sorgu köprüsü (Cloudflare Worker)
//
// Tarayıcı, başka bir siteden turkiye.gov.tr'ye doğrudan sorgu gönderemez (CORS).
// Bu Worker uygulama adına e-Devlet'teki "IMEI Sorgulama" (BTK) sayfasını doldurur,
// sonucu okur ve JSON olarak döner. Sayfa doğrulama kodu (CAPTCHA) isterse kodun
// resmini uygulamaya gönderir; kodu kullanıcı yazar, Worker formu onunla gönderir.
//
// POST /sorgu  {"imei":"15 hane"}                       -> sorgu başlatır
// POST /sorgu  {"imei":"...","state":"...","code":"..."} -> doğrulama koduyla devam eder
// Cevap: {ok:true, imei, durum, sinif, kaynak, tarih, model}
//     |  {captcha:{img:"data:...", state:"..."}}
//     |  {error:"..."}
//
// Ortam değişkeni ALLOWED_ORIGINS: izin verilen site adresleri, virgülle ayrılmış
// (varsayılan: https://ulgen00.github.io). Android uygulaması "X-Cep-Atolyesi: android" başlığını gönderir.

const BASE = "https://www.turkiye.gov.tr";
const PAGE = BASE + "/imei-sorgulama";
const UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36";

class Jar {
  constructor(c) { this.c = c || {}; }
  add(res) {
    const list = res.headers.getSetCookie ? res.headers.getSetCookie() : [];
    for (const h of list) {
      const kv = h.split(";")[0], i = kv.indexOf("=");
      if (i > 0) this.c[kv.slice(0, i).trim()] = kv.slice(i + 1).trim();
    }
  }
  get header() { return Object.entries(this.c).map(([k, v]) => k + "=" + v).join("; "); }
}

async function req(jar, url, opt = {}) {
  const headers = { "User-Agent": UA, "Accept-Language": "tr-TR,tr;q=0.9", Accept: "text/html,application/json,*/*", Referer: PAGE, ...(opt.headers || {}) };
  if (jar.header) headers.Cookie = jar.header; // boş Cookie başlığı e-Devlet'te 520 hatasına yol açıyor
  const res = await fetch(url, { ...opt, redirect: "manual", headers });
  jar.add(res);
  return res;
}

// Yönlendirmeleri (en fazla 5) takip ederek sayfanın HTML'ini getirir.
async function getHtml(jar, url, opt) {
  let res = await req(jar, url, opt);
  for (let i = 0; i < 5 && res.status >= 300 && res.status < 400; i++) {
    res = await req(jar, new URL(res.headers.get("location"), BASE).href);
  }
  return { html: await res.text(), url: res.url, status: res.status };
}

const text = s => s.replace(/<script[\s\S]*?<\/script>/gi, " ").replace(/<[^>]+>/g, " ")
  .replace(/&nbsp;/g, " ").replace(/&quot;/g, '"').replace(/&#0?39;/g, "'").replace(/&lt;/g, "<")
  .replace(/&gt;/g, ">").replace(/&amp;/g, "&").replace(/\s+/g, " ").trim();
const attr = (tag, name) => { const m = tag.match(new RegExp("\\b" + name + "\\s*=\\s*\"([^\"]*)\"", "i")); return m ? m[1].replace(/&amp;/g, "&") : null; };
const sleep = ms => new Promise(r => setTimeout(r, ms));

// mainForm'u okur: gönderilecek gizli alanlar, IMEI dışındaki yazı alanı (doğrulama kodu) ve resmi.
function parseForm(html) {
  const m = html.match(/<form[^>]*name="mainForm"[\s\S]*?<\/form>/i);
  if (!m) return null;
  const form = m[0], open = form.match(/<form[^>]*>/i)[0];
  const f = { action: new URL(attr(open, "action") || "/imei-sorgulama?submit", BASE).href, hidden: {}, field: null, img: null };
  for (const tag of form.match(/<input[^>]*>/gi) || []) {
    const name = attr(tag, "name"), type = (attr(tag, "type") || "text").toLowerCase();
    if (!name) continue;
    if (type === "hidden") f.hidden[name] = attr(tag, "value") || "";
    else if (name !== "txtImei" && (type === "text" || type === "tel" || type === "number")) f.field = name;
  }
  if (f.field) {
    const img = form.match(/<img[^>]*>/i);
    if (img) f.img = new URL(attr(img[0], "src"), BASE).href;
  }
  return f;
}

function pageError(html) {
  for (const cls of ["errorContainer", "fieldError", "warningContainer"]) {
    const m = html.match(new RegExp("<[^>]*class=\"[^\"]*" + cls + "[^\"]*\"[^>]*>([\\s\\S]*?)</(div|ul|span)>", "i"));
    if (m && text(m[1])) return text(m[1]);
  }
  return null;
}

function classify(durum) {
  const d = durum.toLocaleLowerCase("tr");
  if (/kayıp|çalıntı|kaçak|klon|kapatıl|kapalı|engellen/.test(d)) return "kapali";
  if (/kayıtsız|kayıt dışı|kayıtlı değil|bulunamad|kayıt yok/.test(d)) return "kayitsiz";
  if (/kayıtlı|kayıtlıdır/.test(d)) return "kayitli";
  return "bilinmiyor";
}

function parseResult(html) {
  const dl = html.match(/<dl[^>]*>([\s\S]*?)<\/dl>/i);
  if (!dl) return null;
  const out = {};
  const re = /<dt[^>]*>([\s\S]*?)<\/dt>\s*<dd[^>]*>([\s\S]*?)<\/dd>/gi;
  let m;
  while ((m = re.exec(dl[1]))) out[text(m[1])] = text(m[2]);
  if (!out["Durum"]) return null;
  return { ok: true, imei: out["IMEI"] || "", durum: out["Durum"], sinif: classify(out["Durum"]),
    kaynak: out["Kaynak"] && out["Kaynak"] !== "--" ? out["Kaynak"] : "", tarih: out["Sorgu Tarihi"] || "", model: out["Marka/Model"] || "" };
}

const enc = o => btoa(unescape(encodeURIComponent(JSON.stringify(o))));
const dec = s => JSON.parse(decodeURIComponent(escape(atob(s))));

async function captchaChallenge(jar, form) {
  const res = await req(jar, form.img, { headers: { Accept: "image/*" } });
  const buf = new Uint8Array(await res.arrayBuffer());
  let bin = "";
  for (let i = 0; i < buf.length; i += 0x8000) bin += String.fromCharCode(...buf.subarray(i, i + 0x8000));
  const img = "data:" + (res.headers.get("content-type") || "image/png") + ";base64," + btoa(bin);
  return { captcha: { img, state: enc({ c: jar.c, a: form.action, h: form.hidden, f: form.field }) } };
}

// Formu gönderir, e-Devlet'in sıra/bekleme aşamasını yoklar ve sonucu okur.
async function submit(jar, form, imei, code) {
  const body = new URLSearchParams({ ...form.hidden, txtImei: imei });
  if (form.field) body.set(form.field, code || "");
  let page = await getHtml(jar, form.action, {
    method: "POST", body, headers: { "Content-Type": "application/x-www-form-urlencoded" },
  });
  for (let i = 0; i < 20 && /asyncRequired/.test(page.html); i++) {
    const js = page.html.match(/var URL = '([^']+)'[\s\S]*?var redirectURL = '([^']*)'/);
    const token = (page.html.match(/<body[^>]*data-token="([^"]*)"/) || [])[1] || "";
    if (!js) break;
    const r = await req(jar, new URL(js[1], BASE).href, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded", "X-Requested-With": "XMLHttpRequest" },
      body: "ajax=1&token=" + encodeURIComponent(token) + "&asyncQueue=&redirectURL=" + js[2],
    });
    let st = {};
    try { st = await r.json(); } catch (e) {}
    if (st.requestStatus === "FINISHED") { page = await getHtml(jar, new URL(st.redirectURL || page.url, BASE).href); break; }
    if (st.requestStatus === "ERROR" || st.requestStatus === "FAILED") return { error: "e-Devlet sorguyu tamamlayamadı. Biraz sonra tekrar deneyin." };
    await sleep(1000);
  }
  const result = parseResult(page.html);
  if (result) return result;
  const again = parseForm(page.html);
  if (again && again.field && again.img) {
    const ch = await captchaChallenge(jar, again);
    ch.captcha.error = pageError(page.html) || (code ? "Doğrulama kodu hatalı, tekrar deneyin." : "");
    return ch;
  }
  return { error: pageError(page.html) || "e-Devlet sonucu okunamadı. Biraz sonra tekrar deneyin." };
}

async function query(input) {
  const imei = String(input.imei || "").replace(/\D/g, "");
  if (imei.length !== 15) return { error: "IMEI 15 haneli olmalı." };
  if (input.state) {
    let s;
    try { s = dec(input.state); } catch (e) { return { error: "Oturum bilgisi bozuk, sorguyu yeniden başlatın." }; }
    return submit(new Jar(s.c), { action: s.a, hidden: s.h, field: s.f }, imei, String(input.code || "").trim());
  }
  const jar = new Jar();
  const page = await getHtml(jar, PAGE);
  const form = parseForm(page.html);
  if (!form) return { error: "e-Devlet IMEI sorgu sayfası açılamadı." };
  if (form.field && form.img) return captchaChallenge(jar, form);
  return submit(jar, form, imei);
}

export default {
  async fetch(request, env) {
    const allowed = (env.ALLOWED_ORIGINS || "https://ulgen00.github.io").split(",").map(s => s.trim()).filter(Boolean);
    const origin = request.headers.get("Origin") || "";
    // Cep Atölyesi Android uygulaması tarayıcı olmadığı için Origin yerine kendi başlığını gönderir
    const fromApp = request.headers.get("X-Cep-Atolyesi") === "android";
    const ok = fromApp || allowed.includes("*") || allowed.includes(origin);
    const cors = {
      "Access-Control-Allow-Origin": ok ? (allowed.includes("*") ? "*" : origin) : "null",
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
      Vary: "Origin",
    };
    const json = (o, status = 200) => new Response(JSON.stringify(o), { status, headers: { ...cors, "Content-Type": "application/json; charset=utf-8" } });
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    const path = new URL(request.url).pathname;
    if (request.method === "GET" && path === "/") return json({ ok: true, hizmet: "Cep Atölyesi e-Devlet IMEI köprüsü" });
    if (request.method !== "POST" || path !== "/sorgu") return json({ error: "Bulunamadı" }, 404);
    if (!ok) return json({ error: "Bu siteden sorguya izin verilmiyor." }, 403);
    let input;
    try { input = await request.json(); } catch (e) { return json({ error: "Geçersiz istek." }, 400); }
    try { return json(await query(input)); }
    catch (e) { return json({ error: "e-Devlet'e ulaşılamadı. Biraz sonra tekrar deneyin." }, 502); }
  },
};
