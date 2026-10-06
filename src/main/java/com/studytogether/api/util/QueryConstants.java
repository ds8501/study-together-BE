package com.studytogether.api.util;

/** Central home for SQL used by JDBC repositories and services. */
public final class QueryConstants {
  private QueryConstants() {}

  // User queries
  public static final String USER_BY_EMAIL = "SELECT \"id\",\"name\",\"email\",\"avatar\",\"passwordHash\" FROM \"User\" WHERE \"email\"=:email";
  public static final String SAFE_USER_BY_ID = "SELECT \"id\",\"name\",\"email\",\"avatar\" FROM \"User\" WHERE \"id\"=:id";
  public static final String USER_ID_BY_EMAIL = "SELECT \"id\" FROM \"User\" WHERE \"email\"=:email";
  public static final String USER_TIME_ZONE = "SELECT \"timeZone\" FROM \"User\" WHERE \"id\"=:id";
  public static final String UPDATE_USER_TIME_ZONE = "UPDATE \"User\" SET \"timeZone\"=:zone WHERE \"id\"=:id";
  public static final String INSERT_USER = "INSERT INTO \"User\" (\"name\",\"email\",\"passwordHash\",\"createdAt\") VALUES (:name,:email,:hash,CURRENT_TIMESTAMP) RETURNING \"id\"";
  public static final String INSERT_USER_WITH_AVATAR = "INSERT INTO \"User\" (\"name\",\"email\",\"passwordHash\",\"avatar\",\"createdAt\") VALUES (:name,:email,:hash,:avatar,CURRENT_TIMESTAMP) RETURNING \"id\"";

  // Workspace queries
  public static final String WORKSPACE_BY_INVITE_FOR_UPDATE = "SELECT * FROM \"Workspace\" WHERE \"inviteCode\"=:code FOR UPDATE";
  public static final String WORKSPACE_MEMBER_COUNT = "SELECT COUNT(*) FROM \"WorkspaceMember\" WHERE \"workspaceId\"=?";
  public static final String WORKSPACE_FOR_USER = "SELECT wm.\"role\", w.\"id\",w.\"name\",w.\"inviteCode\" FROM \"WorkspaceMember\" wm JOIN \"Workspace\" w ON w.\"id\"=wm.\"workspaceId\" WHERE wm.\"userId\"=:user ORDER BY wm.\"joinedAt\" LIMIT 1";
  public static final String MEMBERS_FOR_WORKSPACE = "SELECT u.\"id\",u.\"name\",u.\"email\",u.\"avatar\",wm.\"role\",wm.\"joinedAt\" FROM \"WorkspaceMember\" wm JOIN \"User\" u ON u.\"id\"=wm.\"userId\" WHERE wm.\"workspaceId\"=:workspace ORDER BY wm.\"joinedAt\"";
  public static final String WORKSPACE_ID_FOR_USER = "SELECT \"workspaceId\" FROM \"WorkspaceMember\" WHERE \"userId\"=:user ORDER BY \"joinedAt\" LIMIT 1";
  public static final String OWNER_WORKSPACE_ID_FOR_USER = "SELECT \"workspaceId\" FROM \"WorkspaceMember\" WHERE \"userId\"=:user AND \"role\"='OWNER' LIMIT 1";
  public static final String INSERT_WORKSPACE = "INSERT INTO \"Workspace\" (\"name\",\"inviteCode\",\"createdAt\") VALUES (:name,:code,CURRENT_TIMESTAMP) RETURNING \"id\"";
  public static final String UPDATE_WORKSPACE_INVITE = "UPDATE \"Workspace\" SET \"inviteCode\"=:code WHERE \"id\"=:id";

  // WorkspaceMember queries
  public static final String INSERT_WORKSPACE_MEMBER = "INSERT INTO \"WorkspaceMember\" (\"workspaceId\",\"userId\",\"role\",\"joinedAt\") VALUES (:workspace,:user,CAST(:role AS \"MemberRole\"),CURRENT_TIMESTAMP) RETURNING \"id\"";

  // StudyPlan queries
  public static final String PLANS_FOR_WORKSPACE = "SELECT p.*, u.\"id\" AS owner_id,u.\"name\" AS owner_name,u.\"avatar\" AS owner_avatar FROM \"StudyPlan\" p JOIN \"User\" u ON u.\"id\"=p.\"ownerId\" WHERE p.\"workspaceId\"=:workspace ORDER BY p.\"createdAt\"";
  public static final String STUDY_PLAN_OWNER_CHECK = "SELECT p.\"id\" FROM \"StudyPlan\" p JOIN \"WorkspaceMember\" wm ON wm.\"workspaceId\"=p.\"workspaceId\" AND wm.\"userId\"=:user WHERE p.\"id\"=:plan AND p.\"ownerId\"=:user";
  public static final String INSERT_STUDY_PLAN = "INSERT INTO \"StudyPlan\" (\"workspaceId\",\"ownerId\",\"name\",\"createdAt\") VALUES (:workspace,:owner,:name,CURRENT_TIMESTAMP) RETURNING \"id\"";

  // Category queries
  public static final String CATEGORIES_FOR_PLAN = "SELECT * FROM \"Category\" WHERE \"studyPlanId\"=:plan ORDER BY \"order\"";
  public static final String CHECK_CATEGORY_IN_PLAN = "SELECT COUNT(*) FROM \"Category\" WHERE \"id\"=:id AND \"studyPlanId\"=:plan";
  public static final String UPSERT_CATEGORY = "INSERT INTO \"Category\" (\"studyPlanId\",\"name\",\"order\") VALUES (:plan,:name,(SELECT COUNT(*) FROM \"Category\" WHERE \"studyPlanId\"=:plan)) ON CONFLICT (\"studyPlanId\",\"name\") DO UPDATE SET \"name\"=EXCLUDED.\"name\" RETURNING \"id\"";

  // Topic queries
  public static final String TOPICS_FOR_PLAN = "SELECT t.*, c.\"id\" AS c_id,c.\"studyPlanId\" AS c_plan,c.\"name\" AS c_name,c.\"description\" AS c_description,c.\"icon\" AS c_icon,c.\"color\" AS c_color,c.\"order\" AS c_order FROM \"Topic\" t LEFT JOIN \"Category\" c ON c.\"id\"=t.\"categoryId\" AND c.\"studyPlanId\"=t.\"studyPlanId\" WHERE t.\"studyPlanId\"=:plan ORDER BY t.\"dayNumber\" NULLS LAST,t.\"order\"";
  public static final String TOPIC_BY_ID = "SELECT * FROM \"Topic\" WHERE \"id\"=:id";
  public static final String TOPIC_CHECK_FOR_SESSION = "SELECT t.\"id\" FROM \"Topic\" t JOIN \"StudyPlan\" p ON p.\"id\"=t.\"studyPlanId\" AND p.\"ownerId\"=t.\"ownerId\" JOIN \"WorkspaceMember\" wm ON wm.\"workspaceId\"=p.\"workspaceId\" AND wm.\"userId\"=:user WHERE t.\"id\"=:topic AND t.\"ownerId\"=:user";
  public static final String TOPIC_FOR_UPDATE = "SELECT t.* FROM \"Topic\" t JOIN \"StudyPlan\" p ON p.\"id\"=t.\"studyPlanId\" AND p.\"ownerId\"=t.\"ownerId\" JOIN \"WorkspaceMember\" wm ON wm.\"workspaceId\"=p.\"workspaceId\" AND wm.\"userId\"=:user WHERE t.\"id\"=:topic AND t.\"ownerId\"=:user";
  public static final String INSERT_TOPIC = "INSERT INTO \"Topic\" (\"categoryId\",\"studyPlanId\",\"ownerId\",\"title\",\"description\",\"dayNumber\",\"order\",\"difficulty\",\"priority\",\"estimatedHours\",\"status\",\"createdAt\",\"updatedAt\") VALUES (:category,:plan,:owner,:title,:description,:day,(SELECT COUNT(*) FROM \"Topic\" WHERE \"studyPlanId\"=:plan),CAST(:difficulty AS \"TopicDifficulty\"),CAST(:priority AS \"TopicPriority\"),:hours,CAST(:status AS \"TopicStatus\"),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) RETURNING \"id\"";
  public static final String DELETE_TOPIC = "DELETE FROM \"Topic\" t USING \"StudyPlan\" p, \"WorkspaceMember\" wm WHERE t.\"id\"=:topic AND t.\"ownerId\"=:user AND p.\"id\"=t.\"studyPlanId\" AND p.\"ownerId\"=t.\"ownerId\" AND wm.\"workspaceId\"=p.\"workspaceId\" AND wm.\"userId\"=:user";

  // StudySession queries
  public static final String WEEKLY_SESSIONS = "SELECT \"date\" FROM \"StudySession\" WHERE \"userId\"=:user AND \"date\">=:start AND \"date\"<:end";
  public static final String MONTHLY_SESSIONS = "SELECT \"date\" FROM \"StudySession\" WHERE \"userId\"=:user AND \"date\">=:start AND \"date\"<:end";
  public static final String INSERT_STUDY_SESSION = "INSERT INTO \"StudySession\" (\"userId\",\"topicId\",\"date\",\"durationMinutes\",\"notes\") VALUES (:user,:topic,:date,:minutes,:notes) RETURNING \"id\"";

  // StudyStreak queries
  public static final String STREAK_FOR_USER = "SELECT \"currentStreak\",\"bestStreak\",\"lastStudyDate\" FROM \"StudyStreak\" WHERE \"userId\"=:id";
  public static final String RESET_CURRENT_STREAK = "UPDATE \"StudyStreak\" SET \"currentStreak\"=0,\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"userId\"=:id";
  public static final String UPSERT_STUDY_STREAK = "INSERT INTO \"StudyStreak\" (\"userId\",\"currentStreak\",\"bestStreak\",\"lastStudyDate\",\"updatedAt\") VALUES (:user,:current,:best,:day,CURRENT_TIMESTAMP) ON CONFLICT (\"userId\") DO UPDATE SET \"currentStreak\"=EXCLUDED.\"currentStreak\",\"bestStreak\"=EXCLUDED.\"bestStreak\",\"lastStudyDate\"=EXCLUDED.\"lastStudyDate\",\"updatedAt\"=CURRENT_TIMESTAMP";
}
