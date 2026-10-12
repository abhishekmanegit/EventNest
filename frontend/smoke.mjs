import puppeteer from "puppeteer-core";
import fs from "node:fs";

const CHROME = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const BASE = "http://localhost:5173";
const API = "http://localhost:8080/api/v1";

const STUDENT = { email: "abhishek@student.edu.in", password: "Student@123" };
const ORGANIZER = { email: "rahul@xyz.edu.in", password: "Organizer@123" };
const ADMIN = { email: "admin@eventnest.dev", password: "Admin@12345" };

const studentRoutes = ["/", "/events", "/colleges", "/dashboard", "/me/registrations", "/me/saved", "/profile", "/notifications"];
const guestRoutes = ["/", "/events", "/colleges", "/login", "/register"];
const organizerRoutes = ["/organizer/dashboard", "/organizer/events", "/organizer/events/create", "/organizer/profile", "/organizer/events/{{eventId}}/edit", "/organizer/events/{{eventId}}/registrations"];
const adminRoutes = ["/admin/dashboard", "/admin/users", "/admin/organizers", "/admin/events", "/admin/colleges", "/admin/categories", "/admin/reports"];

async function login(creds) {
  const res = await fetch(`${API}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(creds),
  });
  if (!res.ok) throw new Error(`login failed ${res.status} for ${creds.email}`);
  return res.json();
}

const results = [];
function fail(route, msg) { results.push(`FAIL ${route}: ${msg}`); console.log("  ✗", route, "→", msg); }
function pass(route) { results.push(`PASS ${route}`); console.log("  ✓", route); }

async function checkRoute(page, route, minText) {
  try {
    const nav = page.goto(`${BASE}${route}`, { waitUntil: "domcontentloaded", timeout: 20000 });
    const resp = await nav;
    if (!resp || resp.status() >= 500) { fail(route, `HTTP ${resp ? resp.status() : "none"}`); return; }
    await page.waitForFunction(() => document.querySelector("h1"), { timeout: 15000 }).catch(() => {});
    if (minText) {
      const found = await page.evaluate((t) => document.body.innerText.includes(t), minText);
      if (!found) { fail(route, `expected text "${minText}" not rendered`); return; }
    }
    await new Promise((r) => setTimeout(r, 800));
    pass(route);
  } catch (e) {
    fail(route, e.message.slice(0, 200));
  }
}

const browser = await puppeteer.launch({
  executablePath: CHROME,
  headless: "new",
  args: ["--no-sandbox", "--disable-gpu", "--window-size=1440,900"],
});

async function runRole(label, creds, routes, mins = {}, dynamic = {}) {
  console.log(`\n== ${label} ==`);
  const context = await browser.createBrowserContext();
  const page = await context.newPage();
  await page.setViewport({ width: 1440, height: 900 });
  const logs = [];
  page.on("console", (m) => { if (m.type() === "error") logs.push(`[console] ${m.text()}`); });
  page.on("pageerror", (e) => logs.push(`[pageerror] ${e.message}`));

  let vars = {};
  if (creds) {
    const s = await login(creds);
    vars = dynamic.build ? await dynamic.build(s) : {};
    await page.goto(`${BASE}/`, { waitUntil: "domcontentloaded" });
    await page.evaluate((pair) => {
      localStorage.setItem("en_access", pair.accessToken);
      localStorage.setItem("en_refresh", pair.refreshToken);
    }, { accessToken: s.accessToken, refreshToken: s.refreshToken });
  }

  for (const route of routes) {
    const resolved = route.replace(/\{\{(\w+)\}\}/g, (_, k) => vars[k] ?? "");
    await checkRoute(page, resolved, mins[route]);
  }
  const realErrors = logs.filter((l) => !l.includes("favicon") && !l.includes("404"));
  await context.close();
  return realErrors;
}

const orgId = async (s) => {
  const res = await fetch(`${API}/organizer/events`, {
    headers: { Authorization: `Bearer ${s.accessToken}` },
  });
  const list = await res.json();
  const id = Array.isArray(list) && list[0]?.id;
  return { eventId: id == null ? "1" : String(id) };
};

const allErrors = [];
allErrors.push(...await runRole("STUDENT", STUDENT, studentRoutes, { "/dashboard": "Hey,", "/me/registrations": "registrations" }));
allErrors.push(...await runRole("GUEST", null, guestRoutes, { "/login": "Welcome back", "/register": "Create your account" }));
allErrors.push(...await runRole("ORGANIZER", ORGANIZER, organizerRoutes, { "/organizer/dashboard": "Organizer dashboard" }, { build: orgId }));
allErrors.push(...await runRole("ADMIN", ADMIN, adminRoutes, { "/admin/dashboard": "Platform overview" }));

// Real UI login flow
{
  console.log("\n== UI LOGIN (student) ==");
  const context = await browser.createBrowserContext();
  const page = await context.newPage();
  const logs = [];
  page.on("pageerror", (e) => logs.push(`[pageerror] ${e.message}`));
  page.on("console", (m) => { if (m.type() === "error") logs.push(`[console] ${m.text()}`); });
  await page.goto(`${BASE}/login`, { waitUntil: "domcontentloaded" });
  await page.waitForSelector("input[type=email]", { timeout: 15000 });
  await page.type("input[type=email]", STUDENT.email);
  await page.type("input[type=password]", STUDENT.password);
  await Promise.all([
    page.waitForNavigation({ waitUntil: "domcontentloaded", timeout: 20000 }).catch(() => {}),
    page.click("button[type=submit]"),
  ]);
  await new Promise((r) => setTimeout(r, 1500));
  const url = page.url();
  const onDashboard = url.endsWith("/dashboard");
  if (onDashboard && logs.length === 0) { results.push("PASS ui-login"); console.log("  ✓ UI login →", url); }
  else { results.push(`FAIL ui-login: url=${url} errors=${JSON.stringify(logs)}`); console.log("  ✗ UI login →", url, logs); }
  await context.close();
}

// End-to-end "Other…" college registration (student picks a college not listed)
{
  console.log("\n== OTHER COLLEGE REGISTRATION ==");
  const context = await browser.createBrowserContext();
  const page = await context.newPage();
  const logs = [];
  page.on("pageerror", (e) => logs.push(`[pageerror] ${e.message}`));
  page.on("console", (m) => { if (m.type() === "error") logs.push(`[console] ${m.text()}`); });

  const rnd = Date.now().toString().slice(-6);
  const collegeName = `Flow College ${rnd}`;
  const email = `otherflow-${rnd}@example.edu.in`;

  await page.goto(`${BASE}/register`, { waitUntil: "domcontentloaded" });
  try {
    await page.waitForSelector("input[type=email]", { timeout: 15000 });
    await page.type("input[placeholder='e.g. Ananya Iyer']", "Other Flow User");
    await page.type("input[type=email]", email);
    await page.type("input[type=password]", "Passw0rd!xyz");
    await page.select("select", "OTHER");
    await page.waitForSelector("input[aria-label='Your college name']", { timeout: 5000 });
    await page.type("input[aria-label='Your college name']", collegeName);
    await Promise.all([
      page.waitForNavigation({ waitUntil: "domcontentloaded", timeout: 20000 }).catch(() => {}),
      page.click("button[type=submit]"),
    ]);
    await new Promise((r) => setTimeout(r, 1500));
    const onDashboard = page.url().endsWith("/dashboard");
    if (!onDashboard) { results.push(`FAIL other-college: url=${page.url()} errors=${JSON.stringify(logs)}`); console.log("  ✗ register →", page.url(), logs); }
    else { results.push("PASS other-college register"); console.log("  ✓ registered with new college → /dashboard"); }

    const q = collegeName.trim().replace(/\s+/g, "+");
    const res = await fetch(`${API}/colleges?q=${q}&includePending=true`);
    const body = await res.json();
    const listed = (body.content || []).find((c) => c.name === collegeName);
    if (res.status === 200 && listed) { results.push("PASS other-college persisted"); console.log("  ✓ new college visible in includePending list"); }
    else { results.push(`FAIL other-college persisted: status=${res.status} total=${body.totalElements}`); console.log("  ✗ new college missing from API", res.status); }

    const second = await fetch(`${API}/colleges`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name: collegeName }),
    });
    const created = await second.json();
    if (created.id && listed && created.id === listed.id) { results.push("PASS other-college idempotent"); console.log("  ✓ retype reuses the same row"); }
    else { results.push(`FAIL other-college idempotent: ${JSON.stringify(created)}`); console.log("  ✗ second POST did not reuse the row"); }
  } catch (e) {
    results.push(`FAIL other-college: ${e.message.slice(0, 200)}`);
    console.log("  ✗", e.message.slice(0, 200));
  }
  await context.close();
}

await browser.close();

fs.writeFileSync("smoke-results.txt", results.join("\n"));
const failed = results.filter((r) => r.startsWith("FAIL"));
const consoleErrors = allErrors.filter((e) => !e.startsWith("[requestfailed]"));
console.log("\n" + results.length + " route checks, " + failed.length + " FAILED");
if (consoleErrors.length) { console.log("PAGE/CONSOLE ERRORS:"); consoleErrors.forEach((e) => console.log(" -", e.slice(0, 300))); }
process.exit(failed.length || consoleErrors.length ? 1 : 0);