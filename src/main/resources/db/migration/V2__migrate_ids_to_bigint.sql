-- ============================================================================
-- Migration: V2__migrate_ids_to_bigint.sql
-- Description: Migrate all primary keys and foreign keys from TEXT to BIGINT
--              Preserves all existing data, generates sequential BIGINT IDs,
--              maintains all foreign key relationships, attaches auto-increment
--              sequences, and recreates primary/foreign key constraints & indexes.
-- ============================================================================

DO $$
BEGIN
    -- Only execute migration if "User"."id" is currently TEXT or VARCHAR
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'User'
          AND column_name = 'id'
          AND data_type IN ('text', 'character varying')
    ) THEN

        -- 1. Drop existing constraints by type in strict order:
        --    First foreign keys ('f'), then primary keys ('p'), then unique constraints ('u')
        --    We preserve 'User_email_key' and 'Workspace_inviteCode_key' because those columns are unchanged.
        DECLARE
            r RECORD;
        BEGIN
            -- Drop foreign keys first to remove dependency locks across tables
            FOR r IN (
                SELECT conrelid::regclass::text AS tbl, conname
                FROM pg_constraint
                WHERE contype = 'f'
                  AND conrelid::regclass::text IN (
                      '"User"', '"Workspace"', '"WorkspaceMember"', '"StudyPlan"',
                      '"Category"', '"Topic"', '"StudySession"', '"StudyStreak"',
                      'User', 'Workspace', 'WorkspaceMember', 'StudyPlan',
                      'Category', 'Topic', 'StudySession', 'StudyStreak'
                  )
            ) LOOP
                EXECUTE 'ALTER TABLE ' || r.tbl || ' DROP CONSTRAINT IF EXISTS ' || quote_ident(r.conname) || ' CASCADE';
            END LOOP;

            -- Drop primary keys second
            FOR r IN (
                SELECT conrelid::regclass::text AS tbl, conname
                FROM pg_constraint
                WHERE contype = 'p'
                  AND conrelid::regclass::text IN (
                      '"User"', '"Workspace"', '"WorkspaceMember"', '"StudyPlan"',
                      '"Category"', '"Topic"', '"StudySession"', '"StudyStreak"',
                      'User', 'Workspace', 'WorkspaceMember', 'StudyPlan',
                      'Category', 'Topic', 'StudySession', 'StudyStreak'
                  )
            ) LOOP
                EXECUTE 'ALTER TABLE ' || r.tbl || ' DROP CONSTRAINT IF EXISTS ' || quote_ident(r.conname) || ' CASCADE';
            END LOOP;

            -- Drop unique constraints third, excluding email and inviteCode
            FOR r IN (
                SELECT conrelid::regclass::text AS tbl, conname
                FROM pg_constraint
                WHERE contype = 'u'
                  AND conname NOT IN ('User_email_key', 'Workspace_inviteCode_key')
                  AND conrelid::regclass::text IN (
                      '"User"', '"Workspace"', '"WorkspaceMember"', '"StudyPlan"',
                      '"Category"', '"Topic"', '"StudySession"', '"StudyStreak"',
                      'User', 'Workspace', 'WorkspaceMember', 'StudyPlan',
                      'Category', 'Topic', 'StudySession', 'StudyStreak'
                  )
            ) LOOP
                EXECUTE 'ALTER TABLE ' || r.tbl || ' DROP CONSTRAINT IF EXISTS ' || quote_ident(r.conname) || ' CASCADE';
            END LOOP;
        END;

        -- Drop existing indexes to accelerate updates and avoid column drop conflicts
        DROP INDEX IF EXISTS "WorkspaceMember_userId_idx";
        DROP INDEX IF EXISTS "StudyPlan_workspaceId_ownerId_idx";
        DROP INDEX IF EXISTS "Topic_studyPlanId_ownerId_dayNumber_order_idx";
        DROP INDEX IF EXISTS "StudySession_userId_date_idx";
        DROP INDEX IF EXISTS "StudySession_topicId_date_idx";

        -- 2. Create sequences for all primary keys
        CREATE SEQUENCE IF NOT EXISTS "User_id_seq" AS BIGINT;
        CREATE SEQUENCE IF NOT EXISTS "Workspace_id_seq" AS BIGINT;
        CREATE SEQUENCE IF NOT EXISTS "WorkspaceMember_id_seq" AS BIGINT;
        CREATE SEQUENCE IF NOT EXISTS "StudyPlan_id_seq" AS BIGINT;
        CREATE SEQUENCE IF NOT EXISTS "Category_id_seq" AS BIGINT;
        CREATE SEQUENCE IF NOT EXISTS "Topic_id_seq" AS BIGINT;
        CREATE SEQUENCE IF NOT EXISTS "StudySession_id_seq" AS BIGINT;

        -- 3. Add temporary BIGINT columns
        ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "new_id" BIGINT;

        ALTER TABLE "Workspace" ADD COLUMN IF NOT EXISTS "new_id" BIGINT;

        ALTER TABLE "WorkspaceMember" ADD COLUMN IF NOT EXISTS "new_id" BIGINT;
        ALTER TABLE "WorkspaceMember" ADD COLUMN IF NOT EXISTS "new_workspaceId" BIGINT;
        ALTER TABLE "WorkspaceMember" ADD COLUMN IF NOT EXISTS "new_userId" BIGINT;

        ALTER TABLE "StudyPlan" ADD COLUMN IF NOT EXISTS "new_id" BIGINT;
        ALTER TABLE "StudyPlan" ADD COLUMN IF NOT EXISTS "new_workspaceId" BIGINT;
        ALTER TABLE "StudyPlan" ADD COLUMN IF NOT EXISTS "new_ownerId" BIGINT;

        ALTER TABLE "Category" ADD COLUMN IF NOT EXISTS "new_id" BIGINT;
        ALTER TABLE "Category" ADD COLUMN IF NOT EXISTS "new_studyPlanId" BIGINT;

        ALTER TABLE "Topic" ADD COLUMN IF NOT EXISTS "new_id" BIGINT;
        ALTER TABLE "Topic" ADD COLUMN IF NOT EXISTS "new_categoryId" BIGINT;
        ALTER TABLE "Topic" ADD COLUMN IF NOT EXISTS "new_studyPlanId" BIGINT;
        ALTER TABLE "Topic" ADD COLUMN IF NOT EXISTS "new_ownerId" BIGINT;

        ALTER TABLE "StudySession" ADD COLUMN IF NOT EXISTS "new_id" BIGINT;
        ALTER TABLE "StudySession" ADD COLUMN IF NOT EXISTS "new_userId" BIGINT;
        ALTER TABLE "StudySession" ADD COLUMN IF NOT EXISTS "new_topicId" BIGINT;

        ALTER TABLE "StudyStreak" ADD COLUMN IF NOT EXISTS "new_userId" BIGINT;

        -- 4. Assign sequential BIGINT IDs to all existing rows
        -- User
        UPDATE "User" u
        SET "new_id" = n.seq_num
        FROM (
            SELECT "id", ROW_NUMBER() OVER (ORDER BY "createdAt", "id") AS seq_num
            FROM "User"
        ) n
        WHERE u."id" = n."id";

        -- Workspace
        UPDATE "Workspace" w
        SET "new_id" = n.seq_num
        FROM (
            SELECT "id", ROW_NUMBER() OVER (ORDER BY "createdAt", "id") AS seq_num
            FROM "Workspace"
        ) n
        WHERE w."id" = n."id";

        -- WorkspaceMember
        UPDATE "WorkspaceMember" wm
        SET "new_id" = n.seq_num
        FROM (
            SELECT "id", ROW_NUMBER() OVER (ORDER BY "joinedAt", "id") AS seq_num
            FROM "WorkspaceMember"
        ) n
        WHERE wm."id" = n."id";

        -- StudyPlan
        UPDATE "StudyPlan" sp
        SET "new_id" = n.seq_num
        FROM (
            SELECT "id", ROW_NUMBER() OVER (ORDER BY "createdAt", "id") AS seq_num
            FROM "StudyPlan"
        ) n
        WHERE sp."id" = n."id";

        -- Category
        UPDATE "Category" c
        SET "new_id" = n.seq_num
        FROM (
            SELECT "id", ROW_NUMBER() OVER (ORDER BY "order", "id") AS seq_num
            FROM "Category"
        ) n
        WHERE c."id" = n."id";

        -- Topic
        UPDATE "Topic" t
        SET "new_id" = n.seq_num
        FROM (
            SELECT "id", ROW_NUMBER() OVER (ORDER BY "createdAt", "id") AS seq_num
            FROM "Topic"
        ) n
        WHERE t."id" = n."id";

        -- StudySession
        UPDATE "StudySession" ss
        SET "new_id" = n.seq_num
        FROM (
            SELECT "id", ROW_NUMBER() OVER (ORDER BY "date", "id") AS seq_num
            FROM "StudySession"
        ) n
        WHERE ss."id" = n."id";

        -- 5. Wire foreign keys from old TEXT IDs to new BIGINT IDs
        UPDATE "WorkspaceMember" wm
        SET "new_workspaceId" = w."new_id"
        FROM "Workspace" w
        WHERE wm."workspaceId" = w."id";

        UPDATE "WorkspaceMember" wm
        SET "new_userId" = u."new_id"
        FROM "User" u
        WHERE wm."userId" = u."id";

        UPDATE "StudyPlan" sp
        SET "new_workspaceId" = w."new_id"
        FROM "Workspace" w
        WHERE sp."workspaceId" = w."id";

        UPDATE "StudyPlan" sp
        SET "new_ownerId" = u."new_id"
        FROM "User" u
        WHERE sp."ownerId" = u."id";

        UPDATE "Category" c
        SET "new_studyPlanId" = sp."new_id"
        FROM "StudyPlan" sp
        WHERE c."studyPlanId" = sp."id";

        UPDATE "Topic" t
        SET "new_studyPlanId" = sp."new_id",
            "new_ownerId" = sp."new_ownerId"
        FROM "StudyPlan" sp
        WHERE t."studyPlanId" = sp."id";

        UPDATE "Topic" t
        SET "new_categoryId" = c."new_id"
        FROM "Category" c
        WHERE t."categoryId" = c."id";

        -- Reset categoryId if not belonging to same study plan
        UPDATE "Topic" t
        SET "new_categoryId" = NULL
        WHERE "new_categoryId" IS NOT NULL
          AND NOT EXISTS (
              SELECT 1 FROM "Category" c
              WHERE c."new_id" = t."new_categoryId"
                AND c."new_studyPlanId" = t."new_studyPlanId"
          );

        UPDATE "StudySession" ss
        SET "new_userId" = u."new_id"
        FROM "User" u
        WHERE ss."userId" = u."id";

        UPDATE "StudySession" ss
        SET "new_topicId" = t."new_id"
        FROM "Topic" t
        WHERE ss."topicId" = t."id";

        UPDATE "StudyStreak" st
        SET "new_userId" = u."new_id"
        FROM "User" u
        WHERE st."userId" = u."id";

        -- 6. Clean up any orphaned rows before enforcing NOT NULL / FKs
        DELETE FROM "WorkspaceMember" WHERE "new_workspaceId" IS NULL OR "new_userId" IS NULL;
        DELETE FROM "StudyPlan" WHERE "new_workspaceId" IS NULL OR "new_ownerId" IS NULL;
        DELETE FROM "Category" WHERE "new_studyPlanId" IS NULL;
        DELETE FROM "Topic" WHERE "new_studyPlanId" IS NULL OR "new_ownerId" IS NULL;
        DELETE FROM "StudySession" WHERE "new_userId" IS NULL OR "new_topicId" IS NULL;
        DELETE FROM "StudyStreak" WHERE "new_userId" IS NULL;

        -- 7. Swap columns for User
        ALTER TABLE "User" DROP COLUMN "id" CASCADE;
        ALTER TABLE "User" RENAME COLUMN "new_id" TO "id";
        ALTER TABLE "User" ALTER COLUMN "id" SET NOT NULL;
        ALTER TABLE "User" ALTER COLUMN "id" SET DEFAULT nextval('"User_id_seq"');
        ALTER SEQUENCE "User_id_seq" OWNED BY "User"."id";
        PERFORM setval('"User_id_seq"', COALESCE((SELECT MAX("id") FROM "User"), 0) + 1, false);

        -- 8. Swap columns for Workspace
        ALTER TABLE "Workspace" DROP COLUMN "id" CASCADE;
        ALTER TABLE "Workspace" RENAME COLUMN "new_id" TO "id";
        ALTER TABLE "Workspace" ALTER COLUMN "id" SET NOT NULL;
        ALTER TABLE "Workspace" ALTER COLUMN "id" SET DEFAULT nextval('"Workspace_id_seq"');
        ALTER SEQUENCE "Workspace_id_seq" OWNED BY "Workspace"."id";
        PERFORM setval('"Workspace_id_seq"', COALESCE((SELECT MAX("id") FROM "Workspace"), 0) + 1, false);

        -- 9. Swap columns for WorkspaceMember
        ALTER TABLE "WorkspaceMember" DROP COLUMN "id" CASCADE;
        ALTER TABLE "WorkspaceMember" RENAME COLUMN "new_id" TO "id";
        ALTER TABLE "WorkspaceMember" ALTER COLUMN "id" SET NOT NULL;
        ALTER TABLE "WorkspaceMember" ALTER COLUMN "id" SET DEFAULT nextval('"WorkspaceMember_id_seq"');
        ALTER SEQUENCE "WorkspaceMember_id_seq" OWNED BY "WorkspaceMember"."id";
        PERFORM setval('"WorkspaceMember_id_seq"', COALESCE((SELECT MAX("id") FROM "WorkspaceMember"), 0) + 1, false);

        ALTER TABLE "WorkspaceMember" DROP COLUMN "workspaceId" CASCADE;
        ALTER TABLE "WorkspaceMember" RENAME COLUMN "new_workspaceId" TO "workspaceId";
        ALTER TABLE "WorkspaceMember" ALTER COLUMN "workspaceId" SET NOT NULL;

        ALTER TABLE "WorkspaceMember" DROP COLUMN "userId" CASCADE;
        ALTER TABLE "WorkspaceMember" RENAME COLUMN "new_userId" TO "userId";
        ALTER TABLE "WorkspaceMember" ALTER COLUMN "userId" SET NOT NULL;

        -- 10. Swap columns for StudyPlan
        ALTER TABLE "StudyPlan" DROP COLUMN "id" CASCADE;
        ALTER TABLE "StudyPlan" RENAME COLUMN "new_id" TO "id";
        ALTER TABLE "StudyPlan" ALTER COLUMN "id" SET NOT NULL;
        ALTER TABLE "StudyPlan" ALTER COLUMN "id" SET DEFAULT nextval('"StudyPlan_id_seq"');
        ALTER SEQUENCE "StudyPlan_id_seq" OWNED BY "StudyPlan"."id";
        PERFORM setval('"StudyPlan_id_seq"', COALESCE((SELECT MAX("id") FROM "StudyPlan"), 0) + 1, false);

        ALTER TABLE "StudyPlan" DROP COLUMN "workspaceId" CASCADE;
        ALTER TABLE "StudyPlan" RENAME COLUMN "new_workspaceId" TO "workspaceId";
        ALTER TABLE "StudyPlan" ALTER COLUMN "workspaceId" SET NOT NULL;

        ALTER TABLE "StudyPlan" DROP COLUMN "ownerId" CASCADE;
        ALTER TABLE "StudyPlan" RENAME COLUMN "new_ownerId" TO "ownerId";
        ALTER TABLE "StudyPlan" ALTER COLUMN "ownerId" SET NOT NULL;

        -- 11. Swap columns for Category
        ALTER TABLE "Category" DROP COLUMN "id" CASCADE;
        ALTER TABLE "Category" RENAME COLUMN "new_id" TO "id";
        ALTER TABLE "Category" ALTER COLUMN "id" SET NOT NULL;
        ALTER TABLE "Category" ALTER COLUMN "id" SET DEFAULT nextval('"Category_id_seq"');
        ALTER SEQUENCE "Category_id_seq" OWNED BY "Category"."id";
        PERFORM setval('"Category_id_seq"', COALESCE((SELECT MAX("id") FROM "Category"), 0) + 1, false);

        ALTER TABLE "Category" DROP COLUMN "studyPlanId" CASCADE;
        ALTER TABLE "Category" RENAME COLUMN "new_studyPlanId" TO "studyPlanId";
        ALTER TABLE "Category" ALTER COLUMN "studyPlanId" SET NOT NULL;

        -- 12. Swap columns for Topic
        ALTER TABLE "Topic" DROP COLUMN "id" CASCADE;
        ALTER TABLE "Topic" RENAME COLUMN "new_id" TO "id";
        ALTER TABLE "Topic" ALTER COLUMN "id" SET NOT NULL;
        ALTER TABLE "Topic" ALTER COLUMN "id" SET DEFAULT nextval('"Topic_id_seq"');
        ALTER SEQUENCE "Topic_id_seq" OWNED BY "Topic"."id";
        PERFORM setval('"Topic_id_seq"', COALESCE((SELECT MAX("id") FROM "Topic"), 0) + 1, false);

        ALTER TABLE "Topic" DROP COLUMN "categoryId" CASCADE;
        ALTER TABLE "Topic" RENAME COLUMN "new_categoryId" TO "categoryId";

        ALTER TABLE "Topic" DROP COLUMN "studyPlanId" CASCADE;
        ALTER TABLE "Topic" RENAME COLUMN "new_studyPlanId" TO "studyPlanId";
        ALTER TABLE "Topic" ALTER COLUMN "studyPlanId" SET NOT NULL;

        ALTER TABLE "Topic" DROP COLUMN "ownerId" CASCADE;
        ALTER TABLE "Topic" RENAME COLUMN "new_ownerId" TO "ownerId";
        ALTER TABLE "Topic" ALTER COLUMN "ownerId" SET NOT NULL;

        -- 13. Swap columns for StudySession
        ALTER TABLE "StudySession" DROP COLUMN "id" CASCADE;
        ALTER TABLE "StudySession" RENAME COLUMN "new_id" TO "id";
        ALTER TABLE "StudySession" ALTER COLUMN "id" SET NOT NULL;
        ALTER TABLE "StudySession" ALTER COLUMN "id" SET DEFAULT nextval('"StudySession_id_seq"');
        ALTER SEQUENCE "StudySession_id_seq" OWNED BY "StudySession"."id";
        PERFORM setval('"StudySession_id_seq"', COALESCE((SELECT MAX("id") FROM "StudySession"), 0) + 1, false);

        ALTER TABLE "StudySession" DROP COLUMN "userId" CASCADE;
        ALTER TABLE "StudySession" RENAME COLUMN "new_userId" TO "userId";
        ALTER TABLE "StudySession" ALTER COLUMN "userId" SET NOT NULL;

        ALTER TABLE "StudySession" DROP COLUMN "topicId" CASCADE;
        ALTER TABLE "StudySession" RENAME COLUMN "new_topicId" TO "topicId";
        ALTER TABLE "StudySession" ALTER COLUMN "topicId" SET NOT NULL;

        -- 14. Swap columns for StudyStreak
        ALTER TABLE "StudyStreak" DROP COLUMN "userId" CASCADE;
        ALTER TABLE "StudyStreak" RENAME COLUMN "new_userId" TO "userId";
        ALTER TABLE "StudyStreak" ALTER COLUMN "userId" SET NOT NULL;

        -- 15. Recreate Primary Keys & Unique Constraints cleanly
        -- User
        ALTER TABLE "User" DROP CONSTRAINT IF EXISTS "User_pkey" CASCADE;
        DROP INDEX IF EXISTS "User_pkey" CASCADE;
        ALTER TABLE "User" ADD PRIMARY KEY ("id");

        IF NOT EXISTS (
            SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE c.relname = 'User_email_key' AND n.nspname = current_schema()
        ) THEN
            ALTER TABLE "User" ADD CONSTRAINT "User_email_key" UNIQUE ("email");
        END IF;

        -- Workspace
        ALTER TABLE "Workspace" DROP CONSTRAINT IF EXISTS "Workspace_pkey" CASCADE;
        DROP INDEX IF EXISTS "Workspace_pkey" CASCADE;
        ALTER TABLE "Workspace" ADD PRIMARY KEY ("id");

        IF NOT EXISTS (
            SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE c.relname = 'Workspace_inviteCode_key' AND n.nspname = current_schema()
        ) THEN
            ALTER TABLE "Workspace" ADD CONSTRAINT "Workspace_inviteCode_key" UNIQUE ("inviteCode");
        END IF;

        -- WorkspaceMember
        ALTER TABLE "WorkspaceMember" DROP CONSTRAINT IF EXISTS "WorkspaceMember_pkey" CASCADE;
        DROP INDEX IF EXISTS "WorkspaceMember_pkey" CASCADE;
        ALTER TABLE "WorkspaceMember" ADD PRIMARY KEY ("id");

        ALTER TABLE "WorkspaceMember" DROP CONSTRAINT IF EXISTS "WorkspaceMember_workspaceId_userId_key" CASCADE;
        DROP INDEX IF EXISTS "WorkspaceMember_workspaceId_userId_key" CASCADE;
        ALTER TABLE "WorkspaceMember" ADD CONSTRAINT "WorkspaceMember_workspaceId_userId_key" UNIQUE ("workspaceId", "userId");

        -- StudyPlan
        ALTER TABLE "StudyPlan" DROP CONSTRAINT IF EXISTS "StudyPlan_pkey" CASCADE;
        DROP INDEX IF EXISTS "StudyPlan_pkey" CASCADE;
        ALTER TABLE "StudyPlan" ADD PRIMARY KEY ("id");

        ALTER TABLE "StudyPlan" DROP CONSTRAINT IF EXISTS "StudyPlan_id_ownerId_key" CASCADE;
        DROP INDEX IF EXISTS "StudyPlan_id_ownerId_key" CASCADE;
        ALTER TABLE "StudyPlan" ADD CONSTRAINT "StudyPlan_id_ownerId_key" UNIQUE ("id", "ownerId");

        -- Category
        ALTER TABLE "Category" DROP CONSTRAINT IF EXISTS "Category_pkey" CASCADE;
        DROP INDEX IF EXISTS "Category_pkey" CASCADE;
        ALTER TABLE "Category" ADD PRIMARY KEY ("id");

        ALTER TABLE "Category" DROP CONSTRAINT IF EXISTS "Category_id_studyPlanId_key" CASCADE;
        DROP INDEX IF EXISTS "Category_id_studyPlanId_key" CASCADE;
        ALTER TABLE "Category" ADD CONSTRAINT "Category_id_studyPlanId_key" UNIQUE ("id", "studyPlanId");

        ALTER TABLE "Category" DROP CONSTRAINT IF EXISTS "Category_studyPlanId_name_key" CASCADE;
        DROP INDEX IF EXISTS "Category_studyPlanId_name_key" CASCADE;
        ALTER TABLE "Category" ADD CONSTRAINT "Category_studyPlanId_name_key" UNIQUE ("studyPlanId", "name");

        -- Topic
        ALTER TABLE "Topic" DROP CONSTRAINT IF EXISTS "Topic_pkey" CASCADE;
        DROP INDEX IF EXISTS "Topic_pkey" CASCADE;
        ALTER TABLE "Topic" ADD PRIMARY KEY ("id");

        -- StudySession
        ALTER TABLE "StudySession" DROP CONSTRAINT IF EXISTS "StudySession_pkey" CASCADE;
        DROP INDEX IF EXISTS "StudySession_pkey" CASCADE;
        ALTER TABLE "StudySession" ADD PRIMARY KEY ("id");

        -- StudyStreak
        ALTER TABLE "StudyStreak" DROP CONSTRAINT IF EXISTS "StudyStreak_pkey" CASCADE;
        DROP INDEX IF EXISTS "StudyStreak_pkey" CASCADE;
        ALTER TABLE "StudyStreak" ADD PRIMARY KEY ("userId");

        -- 16. Recreate Foreign Key Constraints
        ALTER TABLE "WorkspaceMember" DROP CONSTRAINT IF EXISTS "WorkspaceMember_workspaceId_fkey" CASCADE;
        ALTER TABLE "WorkspaceMember" ADD CONSTRAINT "WorkspaceMember_workspaceId_fkey" FOREIGN KEY ("workspaceId") REFERENCES "Workspace"("id") ON DELETE CASCADE;
        ALTER TABLE "WorkspaceMember" DROP CONSTRAINT IF EXISTS "WorkspaceMember_userId_fkey" CASCADE;
        ALTER TABLE "WorkspaceMember" ADD CONSTRAINT "WorkspaceMember_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE;

        ALTER TABLE "StudyPlan" DROP CONSTRAINT IF EXISTS "StudyPlan_workspaceId_fkey" CASCADE;
        ALTER TABLE "StudyPlan" ADD CONSTRAINT "StudyPlan_workspaceId_fkey" FOREIGN KEY ("workspaceId") REFERENCES "Workspace"("id") ON DELETE CASCADE;
        ALTER TABLE "StudyPlan" DROP CONSTRAINT IF EXISTS "StudyPlan_ownerId_fkey" CASCADE;
        ALTER TABLE "StudyPlan" ADD CONSTRAINT "StudyPlan_ownerId_fkey" FOREIGN KEY ("ownerId") REFERENCES "User"("id") ON DELETE CASCADE;

        ALTER TABLE "Category" DROP CONSTRAINT IF EXISTS "Category_studyPlanId_fkey" CASCADE;
        ALTER TABLE "Category" ADD CONSTRAINT "Category_studyPlanId_fkey" FOREIGN KEY ("studyPlanId") REFERENCES "StudyPlan"("id") ON DELETE CASCADE;

        ALTER TABLE "Topic" DROP CONSTRAINT IF EXISTS "Topic_studyPlanId_ownerId_fkey" CASCADE;
        ALTER TABLE "Topic" ADD CONSTRAINT "Topic_studyPlanId_ownerId_fkey" FOREIGN KEY ("studyPlanId", "ownerId") REFERENCES "StudyPlan"("id", "ownerId") ON DELETE CASCADE;
        ALTER TABLE "Topic" DROP CONSTRAINT IF EXISTS "Topic_categoryId_studyPlanId_fkey" CASCADE;
        ALTER TABLE "Topic" ADD CONSTRAINT "Topic_categoryId_studyPlanId_fkey" FOREIGN KEY ("categoryId", "studyPlanId") REFERENCES "Category"("id", "studyPlanId") ON DELETE CASCADE;

        -- StudySession
        ALTER TABLE "StudySession" DROP CONSTRAINT IF EXISTS "StudySession_userId_fkey" CASCADE;
        ALTER TABLE "StudySession" ADD CONSTRAINT "StudySession_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE;
        ALTER TABLE "StudySession" DROP CONSTRAINT IF EXISTS "StudySession_topicId_fkey" CASCADE;
        ALTER TABLE "StudySession" ADD CONSTRAINT "StudySession_topicId_fkey" FOREIGN KEY ("topicId") REFERENCES "Topic"("id") ON DELETE CASCADE;

        -- StudyStreak
        ALTER TABLE "StudyStreak" DROP CONSTRAINT IF EXISTS "StudyStreak_userId_fkey" CASCADE;
        ALTER TABLE "StudyStreak" ADD CONSTRAINT "StudyStreak_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE;

        -- 17. Recreate Indexes
        DROP INDEX IF EXISTS "WorkspaceMember_userId_idx";
        CREATE INDEX "WorkspaceMember_userId_idx" ON "WorkspaceMember"("userId");

        DROP INDEX IF EXISTS "StudyPlan_workspaceId_ownerId_idx";
        CREATE INDEX "StudyPlan_workspaceId_ownerId_idx" ON "StudyPlan"("workspaceId", "ownerId");

        DROP INDEX IF EXISTS "Topic_studyPlanId_ownerId_dayNumber_order_idx";
        CREATE INDEX "Topic_studyPlanId_ownerId_dayNumber_order_idx" ON "Topic"("studyPlanId", "ownerId", "dayNumber", "order");

        DROP INDEX IF EXISTS "StudySession_userId_date_idx";
        CREATE INDEX "StudySession_userId_date_idx" ON "StudySession"("userId", "date");

        DROP INDEX IF EXISTS "StudySession_topicId_date_idx";
        CREATE INDEX "StudySession_topicId_date_idx" ON "StudySession"("topicId", "date");

    END IF;
END $$;
