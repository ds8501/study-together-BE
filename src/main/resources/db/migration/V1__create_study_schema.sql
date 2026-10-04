DO $$ BEGIN CREATE TYPE "MemberRole" AS ENUM ('OWNER', 'MEMBER'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE "TopicStatus" AS ENUM ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE "TopicDifficulty" AS ENUM ('EASY', 'MEDIUM', 'HARD'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE "TopicPriority" AS ENUM ('LOW', 'MEDIUM', 'HIGH'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS "User" (
  "id" TEXT PRIMARY KEY, "name" TEXT NOT NULL, "email" TEXT NOT NULL UNIQUE,
  "passwordHash" TEXT NOT NULL, "avatar" TEXT, "timeZone" TEXT NOT NULL DEFAULT 'Asia/Kolkata',
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS "Workspace" (
  "id" TEXT PRIMARY KEY, "name" TEXT NOT NULL, "inviteCode" TEXT NOT NULL UNIQUE,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS "WorkspaceMember" (
  "id" TEXT PRIMARY KEY, "workspaceId" TEXT NOT NULL REFERENCES "Workspace"("id") ON DELETE CASCADE,
  "userId" TEXT NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "role" "MemberRole" NOT NULL DEFAULT 'MEMBER', "joinedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE ("workspaceId", "userId")
);
CREATE INDEX IF NOT EXISTS "WorkspaceMember_userId_idx" ON "WorkspaceMember"("userId");
CREATE TABLE IF NOT EXISTS "StudyPlan" (
  "id" TEXT PRIMARY KEY, "workspaceId" TEXT NOT NULL REFERENCES "Workspace"("id") ON DELETE CASCADE,
  "ownerId" TEXT NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "name" TEXT NOT NULL, "description" TEXT, "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE ("id", "ownerId")
);
CREATE INDEX IF NOT EXISTS "StudyPlan_workspaceId_ownerId_idx" ON "StudyPlan"("workspaceId", "ownerId");
CREATE TABLE IF NOT EXISTS "Category" (
  "id" TEXT PRIMARY KEY, "studyPlanId" TEXT NOT NULL REFERENCES "StudyPlan"("id") ON DELETE CASCADE,
  "name" TEXT NOT NULL, "description" TEXT, "icon" TEXT, "color" TEXT, "order" INTEGER NOT NULL DEFAULT 0,
  UNIQUE ("studyPlanId", "name"), UNIQUE ("id", "studyPlanId")
);
CREATE TABLE IF NOT EXISTS "Topic" (
  "id" TEXT PRIMARY KEY, "categoryId" TEXT, "studyPlanId" TEXT NOT NULL, "ownerId" TEXT NOT NULL,
  "title" TEXT NOT NULL, "description" TEXT, "dayNumber" INTEGER, "order" INTEGER NOT NULL DEFAULT 0,
  "difficulty" "TopicDifficulty" NOT NULL DEFAULT 'MEDIUM', "priority" "TopicPriority" NOT NULL DEFAULT 'MEDIUM',
  "estimatedHours" DOUBLE PRECISION, "status" "TopicStatus" NOT NULL DEFAULT 'NOT_STARTED',
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, "updatedAt" TIMESTAMP(3) NOT NULL,
  FOREIGN KEY ("studyPlanId", "ownerId") REFERENCES "StudyPlan"("id", "ownerId") ON DELETE CASCADE,
  FOREIGN KEY ("categoryId", "studyPlanId") REFERENCES "Category"("id", "studyPlanId") ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS "Topic_studyPlanId_ownerId_dayNumber_order_idx" ON "Topic"("studyPlanId", "ownerId", "dayNumber", "order");
CREATE TABLE IF NOT EXISTS "StudySession" (
  "id" TEXT PRIMARY KEY, "userId" TEXT NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "topicId" TEXT NOT NULL REFERENCES "Topic"("id") ON DELETE CASCADE,
  "date" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, "durationMinutes" INTEGER NOT NULL, "notes" TEXT
);
CREATE INDEX IF NOT EXISTS "StudySession_userId_date_idx" ON "StudySession"("userId", "date");
CREATE INDEX IF NOT EXISTS "StudySession_topicId_date_idx" ON "StudySession"("topicId", "date");
CREATE TABLE IF NOT EXISTS "StudyStreak" (
  "userId" TEXT PRIMARY KEY REFERENCES "User"("id") ON DELETE CASCADE,
  "currentStreak" INTEGER NOT NULL DEFAULT 0, "bestStreak" INTEGER NOT NULL DEFAULT 0,
  "lastStudyDate" TIMESTAMP(3), "updatedAt" TIMESTAMP(3) NOT NULL
);
