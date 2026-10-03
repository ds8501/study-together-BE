import "dotenv/config";
import express from "express";
import cors from "cors";
import cookieParser from "cookie-parser";
import bcrypt from "bcryptjs";
import { randomBytes, timingSafeEqual } from "node:crypto";
import { OAuth2Client } from "google-auth-library";
import { Prisma, PrismaClient, TopicDifficulty, TopicPriority, TopicStatus } from "@prisma/client";
import { z } from "zod";

const prisma = new PrismaClient();
const app = express();
const port = Number(process.env.PORT ?? 4000);
const host = process.env.HOST ?? "0.0.0.0";
const origins = (process.env.FRONTEND_ORIGIN ?? "http://localhost:3000").split(",").map(value => value.trim()).filter(Boolean);
if (process.env.NODE_ENV !== "production") origins.push("http://127.0.0.1:3000", "http://localhost:3000");
const secret = process.env.AUTH_SECRET;
if (!secret || secret.length < 32) throw new Error("AUTH_SECRET must be set to at least 32 characters");

app.use(cors({ origin: origins, credentials: true }));
app.use(express.json({ limit: "1mb" }));
app.use(cookieParser());
const cookieOptions = { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "lax" as const, path: "/", maxAge: 1000 * 60 * 60 * 24 * 14 };
type AuthedRequest = express.Request & { userId?: string };

async function requireUser(req: AuthedRequest, res: express.Response, next: express.NextFunction) {
  try {
    const token = req.cookies.study_session;
    if (!token) return res.status(401).json({ error: "Sign in to continue" });
    const [id, signature] = String(token).split(".");
    if (!id || !signature) return res.status(401).json({ error: "Invalid session" });
    const { createHmac, timingSafeEqual } = await import("node:crypto");
    const expected = createHmac("sha256", secret!).update(id).digest("hex");
    const actualBuffer = Buffer.from(signature);
    const expectedBuffer = Buffer.from(expected);
    if (actualBuffer.length !== expectedBuffer.length || !timingSafeEqual(actualBuffer, expectedBuffer)) return res.status(401).json({ error: "Invalid session" });
    const decoded = Buffer.from(id, "base64url").toString("utf8");
    const [userId, expiry] = decoded.split(":");
    if (!userId || Number(expiry) < Date.now()) return res.status(401).json({ error: "Session expired" });
    req.userId = userId;
    return next();
  } catch { return res.status(401).json({ error: "Invalid session" }); }
}

function setSession(res: express.Response, userId: string) {
  const id = Buffer.from(`${userId}:${Date.now() + cookieOptions.maxAge}`).toString("base64url");
  const { createHmac } = require("node:crypto") as typeof import("node:crypto");
  const signature = createHmac("sha256", secret!).update(id).digest("hex");
  res.cookie("study_session", `${id}.${signature}`, cookieOptions);
}
function safeUser(user: { id: string; name: string; email: string; avatar: string | null }) { return { id: user.id, name: user.name, email: user.email, avatar: user.avatar }; }
const registration = z.object({ name: z.string().trim().min(2).max(80), email: z.string().trim().email().max(254), password: z.string().min(10).max(128), workspaceName: z.string().trim().min(2).max(80).optional(), inviteCode: z.string().trim().min(4).max(100).optional() });
const login = z.object({ email: z.string().trim().email(), password: z.string().min(1).max(128) });

app.get("/health", (_req, res) => res.json({ status: "ok" }));

const googleOAuthCookieOptions = { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "lax" as const, path: "/", maxAge: 10 * 60 * 1000 };
const clearGoogleOAuthCookieOptions = { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "lax" as const, path: "/" };
const frontendUrl = (process.env.FRONTEND_URL ?? origins[0] ?? "http://localhost:3000").replace(/\/$/, "");
function googleAuthFailure(res: express.Response, reason = "google") {
  res.clearCookie("google_oauth_state", clearGoogleOAuthCookieOptions);
  res.clearCookie("google_oauth_nonce", clearGoogleOAuthCookieOptions);
  return res.redirect(303, `${frontendUrl}/?authError=${encodeURIComponent(reason)}`);
}
app.get("/auth/google", (req, res) => {
  const clientId = process.env.GOOGLE_CLIENT_ID;
  const redirectUri = process.env.GOOGLE_REDIRECT_URI;
  if (!clientId || !process.env.GOOGLE_CLIENT_SECRET || !redirectUri) return googleAuthFailure(res, "google_not_configured");
  const state = randomBytes(32).toString("base64url");
  const nonce = randomBytes(32).toString("base64url");
  res.cookie("google_oauth_state", state, googleOAuthCookieOptions);
  res.cookie("google_oauth_nonce", nonce, googleOAuthCookieOptions);
  const googleClient = new OAuth2Client(clientId, process.env.GOOGLE_CLIENT_SECRET, redirectUri);
  return res.redirect(302, googleClient.generateAuthUrl({ access_type: "online", scope: ["openid", "email", "profile"], state, nonce, prompt: "select_account" }));
});
app.get("/auth/google/callback", async (req, res) => {
  const stateCookie = req.cookies.google_oauth_state;
  const nonceCookie = req.cookies.google_oauth_nonce;
  const state = typeof req.query.state === "string" ? req.query.state : "";
  const code = typeof req.query.code === "string" ? req.query.code : "";
  const clientId = process.env.GOOGLE_CLIENT_ID;
  const clientSecret = process.env.GOOGLE_CLIENT_SECRET;
  const redirectUri = process.env.GOOGLE_REDIRECT_URI;
  if (!stateCookie || !nonceCookie || !state || !code || !clientId || !clientSecret || !redirectUri) return googleAuthFailure(res, "google");
  const providedState = Buffer.from(state);
  const expectedState = Buffer.from(String(stateCookie));
  if (providedState.length !== expectedState.length || !timingSafeEqual(providedState, expectedState)) return googleAuthFailure(res, "google");
  try {
    const googleClient = new OAuth2Client(clientId, clientSecret, redirectUri);
    const { tokens } = await googleClient.getToken(code);
    if (!tokens.id_token) return googleAuthFailure(res);
    const ticket = await googleClient.verifyIdToken({ idToken: tokens.id_token, audience: clientId });
    const identity = ticket.getPayload();
    if (!identity || identity.nonce !== nonceCookie || identity.email_verified !== true || !identity.email || !identity.sub) return googleAuthFailure(res);
    const email = identity.email.trim().toLowerCase();
    if (!email.endsWith("@gmail.com") && !identity.hd) return googleAuthFailure(res, "google_email");
    const existing = await prisma.user.findUnique({ where: { email } });
    let user = existing;
    if (!user) {
      const passwordHash = await bcrypt.hash(randomBytes(48).toString("base64url"), 12);
      user = await prisma.$transaction(async tx => {
        const created = await tx.user.create({ data: { name: identity.name?.trim().slice(0, 80) || email.split("@")[0], email, avatar: identity.picture, passwordHash } });
        const workspace = await tx.workspace.create({ data: { name: "Study Room", members: { create: { userId: created.id, role: "OWNER" } } } });
        await tx.studyPlan.create({ data: { workspaceId: workspace.id, ownerId: created.id, name: `${created.name}’s roadmap` } });
        return created;
      });
    }
    setSession(res, user.id);
    res.clearCookie("google_oauth_state", clearGoogleOAuthCookieOptions);
    res.clearCookie("google_oauth_nonce", clearGoogleOAuthCookieOptions);
    return res.redirect(303, frontendUrl);
  } catch {
    return googleAuthFailure(res);
  }
});
app.post("/auth/register", async (req, res, next) => {
  try {
    const input = registration.parse(req.body);
    const email = input.email.toLowerCase();
    const passwordHash = await bcrypt.hash(input.password, 12);
    const user = await prisma.$transaction(async (tx) => {
      if (input.inviteCode) {
        const workspace = await tx.workspace.findUnique({ where: { inviteCode: input.inviteCode } });
        if (!workspace) throw new Error("INVITE_NOT_FOUND");
        if (await tx.workspaceMember.count({ where: { workspaceId: workspace.id } }) >= 2) throw new Error("WORKSPACE_FULL");
        const created = await tx.user.create({ data: { name: input.name, email, passwordHash } });
        await tx.workspaceMember.create({ data: { userId: created.id, workspaceId: workspace.id } });
        await tx.studyPlan.create({ data: { workspaceId: workspace.id, ownerId: created.id, name: `${created.name}’s roadmap` } });
        return created;
      }
      const created = await tx.user.create({ data: { name: input.name, email, passwordHash } });
      const workspace = await tx.workspace.create({ data: { name: input.workspaceName ?? "Study Room", members: { create: { userId: created.id, role: "OWNER" } } } });
      await tx.studyPlan.create({ data: { workspaceId: workspace.id, ownerId: created.id, name: `${created.name}’s roadmap` } });
      return created;
    });
    setSession(res, user.id);
    return res.status(201).json({ user: safeUser(user) });
  } catch (error) { if (error instanceof z.ZodError) return res.status(400).json({ error: error.issues[0]?.message ?? "Invalid input" }); if (error instanceof Error && error.message === "INVITE_NOT_FOUND") return res.status(400).json({ error: "That invite code is not valid" }); if (error instanceof Error && error.message === "WORKSPACE_FULL") return res.status(400).json({ error: "This workspace already has two members" }); if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === "P2002") return res.status(409).json({ error: "An account with that email already exists" }); return next(error); }
});

app.post("/auth/login", async (req, res, next) => {
  try {
    const input = login.parse(req.body);
    const user = await prisma.user.findUnique({ where: { email: input.email.toLowerCase() } });
    if (!user || !(await bcrypt.compare(input.password, user.passwordHash))) return res.status(401).json({ error: "Email or password is incorrect" });
    setSession(res, user.id);
    return res.json({ user: safeUser(user) });
  } catch (error) { if (error instanceof z.ZodError) return res.status(400).json({ error: error.issues[0]?.message ?? "Invalid input" }); return next(error); }
});
app.post("/auth/logout", (_req, res) => res.clearCookie("study_session", { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "lax", path: "/" }).json({ ok: true }));
app.get("/auth/me", requireUser, async (req: AuthedRequest, res, next) => { try { const user = await prisma.user.findUnique({ where: { id: req.userId! }, select: { id: true, name: true, email: true, avatar: true } }); if (!user) return res.status(401).json({ error: "Account not found" }); return res.json({ user }); } catch (e) { return next(e); } });

app.get("/workspace", requireUser, async (req: AuthedRequest, res, next) => {
  try {
    const membership = await prisma.workspaceMember.findFirst({ where: { userId: req.userId }, include: { workspace: { include: { members: { include: { user: { select: { id: true, name: true, email: true, avatar: true } } } } } } } });
    if (!membership) return res.status(404).json({ error: "You have not joined a workspace" });
    return res.json({ id: membership.workspace.id, name: membership.workspace.name, inviteCode: membership.role === "OWNER" ? membership.workspace.inviteCode : undefined, members: membership.workspace.members.map(m => ({ user: safeUser(m.user), role: m.role, joinedAt: m.joinedAt })) });
  } catch (e) { return next(e); }
});

app.get("/study-plans", requireUser, async (req: AuthedRequest, res, next) => {
  try {
    const membership = await prisma.workspaceMember.findFirst({ where: { userId: req.userId } });
    if (!membership) return res.status(404).json({ error: "You have not joined a workspace" });
    const plans = await prisma.studyPlan.findMany({ where: { workspaceId: membership.workspaceId }, include: { owner: { select: { id: true, name: true, avatar: true } }, categories: { orderBy: { order: "asc" } }, topics: { orderBy: [{ dayNumber: "asc" }, { order: "asc" }], include: { category: true } } }, orderBy: { createdAt: "asc" } });
    return res.json({ plans: plans.map(p => ({ ...p, owner: safeUser({ ...p.owner, email: "" }) })) });
  } catch (e) { return next(e); }
});

function localDayKey(date: Date, timeZone: string) {
  const parts = new Intl.DateTimeFormat("en-US", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(date);
  const part = (type: string) => parts.find(value => value.type === type)?.value ?? "00";
  return `${part("year")}-${part("month")}-${part("day")}`;
}
function dayKey(date: Date) { return date.toISOString().slice(0, 10); }
function dayDate(key: string) { return new Date(`${key}T00:00:00.000Z`); }
function utcDay(date: Date) { return new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate())); }
async function readStudyStats(userId: string) {
  const user = await prisma.user.findUnique({ where: { id: userId }, select: { timeZone: true } });
  const timeZone = user?.timeZone ?? "Asia/Kolkata";
  const today = dayDate(localDayKey(new Date(), timeZone));
  const yesterday = new Date(today); yesterday.setUTCDate(yesterday.getUTCDate() - 1);
  const weekStart = new Date(today); weekStart.setUTCDate(weekStart.getUTCDate() - ((weekStart.getUTCDay() + 6) % 7));
  const weekEnd = new Date(weekStart); weekEnd.setUTCDate(weekEnd.getUTCDate() + 7);
  const [stored, sessions] = await Promise.all([
    prisma.studyStreak.findUnique({ where: { userId } }),
    prisma.studySession.findMany({ where: { userId, date: { gte: weekStart, lt: weekEnd } }, select: { date: true } }),
  ]);
  let currentStreak = stored?.currentStreak ?? 0;
  if (!stored?.lastStudyDate || stored.lastStudyDate < yesterday) currentStreak = 0;
  if (stored && currentStreak !== stored.currentStreak) await prisma.studyStreak.update({ where: { userId }, data: { currentStreak: 0 } });
  const activeDays = new Set(sessions.map(session => dayKey(session.date)));
  const labels = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
  const week = Array.from({ length: 7 }, (_, index) => {
    const date = new Date(weekStart); date.setUTCDate(date.getUTCDate() + index);
    return { key: dayKey(date), label: labels[date.getUTCDay()], active: activeDays.has(dayKey(date)), today: dayKey(date) === dayKey(today) };
  });
  return { currentStreak, bestStreak: stored?.bestStreak ?? 0, week };
}
app.get("/study-stats", requireUser, async (req: AuthedRequest, res, next) => {
  try { return res.json(await readStudyStats(req.userId!)); } catch (e) { return next(e); }
});
const studySessionInput = z.object({ topicId: z.string().min(1), durationMinutes: z.number().int().min(1).max(720), notes: z.string().max(2000).optional(), timeZone: z.string().trim().min(1).max(64).optional() });
app.post("/study-sessions", requireUser, async (req: AuthedRequest, res, next) => {
  try {
    const input = studySessionInput.parse(req.body);
    const userId = req.userId!;
    const topic = await prisma.topic.findFirst({ where: { id: input.topicId, ownerId: userId, studyPlan: { workspace: { members: { some: { userId } } } } }, select: { id: true } });
    if (!topic) return res.status(404).json({ error: "Topic not found on your personal roadmap" });
    const user = await prisma.user.findUnique({ where: { id: userId }, select: { timeZone: true } });
    const timeZone = input.timeZone ?? user?.timeZone ?? "Asia/Kolkata";
    let todayKey: string;
    try { todayKey = localDayKey(new Date(), timeZone); } catch { return res.status(400).json({ error: "Use a valid time zone" }); }
    const today = dayDate(todayKey);
    const yesterday = new Date(today); yesterday.setUTCDate(yesterday.getUTCDate() - 1);
    const stats = await prisma.$transaction(async tx => {
      const existing = await tx.studyStreak.findUnique({ where: { userId } });
      const lastDay = existing?.lastStudyDate ? utcDay(existing.lastStudyDate) : null;
      const currentStreak = lastDay?.getTime() === today.getTime() ? (existing?.currentStreak ?? 1) : lastDay?.getTime() === yesterday.getTime() ? (existing?.currentStreak ?? 0) + 1 : 1;
      const bestStreak = Math.max(existing?.bestStreak ?? 0, currentStreak);
      await tx.user.update({ where: { id: userId }, data: { timeZone } });
      await tx.studySession.create({ data: { userId, topicId: input.topicId, durationMinutes: input.durationMinutes, notes: input.notes, date: today } });
      await tx.studyStreak.upsert({ where: { userId }, create: { userId, currentStreak, bestStreak, lastStudyDate: today }, update: { currentStreak, bestStreak, lastStudyDate: today } });
      return { currentStreak, bestStreak };
    });
    return res.status(201).json({ ok: true, ...stats, stats: await readStudyStats(userId) });
  } catch (e) { if (e instanceof z.ZodError) return res.status(400).json({ error: e.issues[0]?.message ?? "Invalid session" }); return next(e); }
});

const topicInput = z.object({ title: z.string().trim().min(1).max(160), description: z.string().max(3000).optional(), categoryId: z.string().optional().nullable(), categoryName: z.string().trim().min(1).max(80).optional(), dayNumber: z.number().int().positive().optional().nullable(), difficulty: z.nativeEnum(TopicDifficulty).optional(), priority: z.nativeEnum(TopicPriority).optional(), estimatedHours: z.number().positive().max(1000).optional().nullable(), status: z.nativeEnum(TopicStatus).optional() });
app.post("/study-plans/:planId/topics", requireUser, async (req: AuthedRequest, res, next) => {
  try {
    const input = topicInput.parse(req.body);
    const plan = await prisma.studyPlan.findFirst({ where: { id: req.params.planId, ownerId: req.userId, workspace: { members: { some: { userId: req.userId } } } } });
    if (!plan) return res.status(404).json({ error: "Roadmap not found for this account" });
    if (input.categoryId && !await prisma.category.findFirst({ where: { id: input.categoryId, studyPlanId: plan.id } })) return res.status(400).json({ error: "Category does not belong to this roadmap" });
    const category = input.categoryName ? await prisma.category.upsert({ where: { studyPlanId_name: { studyPlanId: plan.id, name: input.categoryName } }, create: { studyPlanId: plan.id, name: input.categoryName, order: await prisma.category.count({ where: { studyPlanId: plan.id } }) }, update: {} }) : null;
    const { categoryName: _categoryName, ...topicData } = input;
    const topic = await prisma.topic.create({ data: { ...topicData, categoryId: input.categoryId ?? category?.id, studyPlanId: plan.id, ownerId: req.userId!, order: await prisma.topic.count({ where: { studyPlanId: plan.id } }) } });
    return res.status(201).json({ topic });
  } catch (e) { if (e instanceof z.ZodError) return res.status(400).json({ error: e.issues[0]?.message ?? "Invalid input" }); return next(e); }
});
app.patch("/topics/:topicId", requireUser, async (req: AuthedRequest, res, next) => {
  try {
    const input = topicInput.partial().parse(req.body);
    const topic = await prisma.topic.findFirst({ where: { id: req.params.topicId, ownerId: req.userId, studyPlan: { workspace: { members: { some: { userId: req.userId } } } } } });
    if (!topic) return res.status(404).json({ error: "Topic not found for this account" });
    if (input.categoryId && !await prisma.category.findFirst({ where: { id: input.categoryId, studyPlanId: topic.studyPlanId } })) return res.status(400).json({ error: "Category does not belong to this roadmap" });
    if (input.categoryName) return res.status(400).json({ error: "Changing a topic category requires categoryId" });
    const { categoryName: _categoryName, ...topicData } = input;
    return res.json({ topic: await prisma.topic.update({ where: { id: topic.id }, data: topicData }) });
  } catch (e) { if (e instanceof z.ZodError) return res.status(400).json({ error: e.issues[0]?.message ?? "Invalid input" }); return next(e); }
});
app.delete("/topics/:topicId", requireUser, async (req: AuthedRequest, res, next) => {
  try { const result = await prisma.topic.deleteMany({ where: { id: req.params.topicId, ownerId: req.userId, studyPlan: { workspace: { members: { some: { userId: req.userId } } } } } }); if (!result.count) return res.status(404).json({ error: "Topic not found for this account" }); return res.status(204).end(); } catch (e) { return next(e); }
});
app.post("/workspace/invite/rotate", requireUser, async (req: AuthedRequest, res, next) => {
  try { const membership = await prisma.workspaceMember.findFirst({ where: { userId: req.userId, role: "OWNER" } }); if (!membership) return res.status(403).json({ error: "Only the workspace owner can rotate the invite" }); const workspace = await prisma.workspace.update({ where: { id: membership.workspaceId }, data: { inviteCode: randomBytes(18).toString("base64url") } }); return res.json({ inviteCode: workspace.inviteCode }); } catch (e) { return next(e); }
});

app.use((error: unknown, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  console.error(error);
  if (error instanceof z.ZodError) return res.status(400).json({ error: error.issues[0]?.message ?? "Invalid input" });
  return res.status(500).json({ error: "Something went wrong" });
});
const server = app.listen(port, host, () => console.log(`Study Together API listening on http://${host}:${port}`));
async function shutdown() { server.close(); await prisma.$disconnect(); }
process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
