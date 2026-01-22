import admin from "firebase-admin";
import fs from "node:fs/promises";

const HABITS_COLLECTION = "habits";
const CREATED_BY = "public_catalog";

function requiredEnv(name) {
  const v = process.env[name];
  if (!v) throw new Error(`Missing env: ${name}`);
  return v;
}

function formatLocalDateYYYYMMDD(date = new Date()) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function buildTemplates(todayStr) {
  return [
    {
      title: "Читать книгу",
      description: "Читаем каждый день — маленькими шагами к большой цели.",
      icon: "📚",
      color: "#1D4ED8",
      goalType: "MEASURABLE",
      targetValue: 50,
      unit: "стр.",
      timeOfDay: "ANY_TIME"
    },
    {
      title: "Выпить воду",
      description: "Поддерживать водный баланс в течение дня.",
      icon: "💧",
      color: "#0891B2",
      goalType: "MEASURABLE",
      targetValue: 8,
      unit: "стак.",
      timeOfDay: "ANY_TIME"
    },
    {
      title: "Составлять планы на день",
      description: "Потратить 5 минут вечером — и завтра будет проще.",
      icon: "📝",
      color: "#F97316",
      goalType: "SIMPLE",
      targetValue: 1,
      unit: "раз",
      timeOfDay: "EVENING"
    },
    {
      title: "Вставать до 8 утра",
      description: "Ранний подъём — больше успеешь за день.",
      icon: "⏰",
      color: "#EAB308",
      goalType: "SIMPLE",
      targetValue: 1,
      unit: "раз",
      timeOfDay: "MORNING"
    },
    {
      title: "Прогулка",
      description: "Пройтись на свежем воздухе, чтобы перезагрузиться.",
      icon: "🚶",
      color: "#16A34A",
      goalType: "MEASURABLE",
      targetValue: 30,
      unit: "мин",
      timeOfDay: "AFTERNOON"
    },
    {
      title: "Медитация",
      description: "15 минут тишины для ясной головы.",
      icon: "🧘",
      color: "#7C3AED",
      goalType: "MEASURABLE",
      targetValue: 15,
      unit: "мин",
      timeOfDay: "MORNING"
    },
    {
      title: "Отжимания",
      description: "Становитесь сильнее каждый день.",
      icon: "💪",
      color: "#DC2626",
      goalType: "MEASURABLE",
      targetValue: 30,
      unit: "раз",
      timeOfDay: "MORNING"
    },
    {
      title: "Растяжка",
      description: "Снять напряжение и улучшите гибкость.",
      icon: "🤸",
      color: "#C026D3",
      goalType: "MEASURABLE",
      targetValue: 10,
      unit: "мин",
      timeOfDay: "EVENING"
    },
    {
      title: "Без сахара",
      description: "День без сахара — вклад в здоровье.",
      icon: "🚫",
      color: "#0F172A",
      goalType: "SIMPLE",
      targetValue: 1,
      unit: "раз",
      timeOfDay: "ANY_TIME"
    },
    {
      title: "Убрать рабочий стол/комнату",
      description: "Порядок вокруг помогает навести порядок в мыслях.",
      icon: "🧹",
      color: "#0EA5E9",
      goalType: "SIMPLE",
      targetValue: 1,
      unit: "раз",
      timeOfDay: "EVENING"
    }
  ].map((t) => ({
    title: t.title,
    description: t.description,
    icon: t.icon,
    color: t.color,
    type: "PERSONAL",
    frequency: "DAILY",
    reminderEnabled: false,
    reminderDays: [],
    targetValue: t.targetValue,
    unit: t.unit,
    createdBy: CREATED_BY,
    participants: [],
    maxParticipants: 10,
    inviteCode: null,
    public: true,
    isActive: true,
    createdAt: todayStr,
    updatedAt: todayStr,
    updatedAtMillis: Date.now(),
    deletedAtMillis: null,
    startDate: null,
    endDate: null,
    followUpReminderEnabled: false,
    followUpReminderDuration: { hour: 2, minute: 0 },
    timeOfDay: t.timeOfDay,
    goalType: t.goalType,
    timeGroupId: null
  }));
}

async function initFirebaseAdmin() {
  const serviceAccountPath = requiredEnv("SERVICE_ACCOUNT_JSON");
  const raw = await fs.readFile(serviceAccountPath, "utf8");
  const serviceAccount = JSON.parse(raw);

  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    projectId: serviceAccount.project_id
  });

  return admin.firestore();
}

async function main() {
  const dryRun = process.env.DRY_RUN === "1";
  const firestore = await initFirebaseAdmin();

  const todayStr = formatLocalDateYYYYMMDD();
  const templates = buildTemplates(todayStr);

  const existing = await firestore
    .collection(HABITS_COLLECTION)
    .where("createdBy", "==", CREATED_BY)
    .where("public", "==", true)
    .get();

  const existingTitles = new Set(
    existing.docs
      .map((d) => d.data()?.title)
      .filter((t) => typeof t === "string")
  );

  const toCreate = templates.filter((t) => !existingTitles.has(t.title));

  console.log(`[seed] existing public_catalog docs: ${existing.size}`);
  console.log(`[seed] to create: ${toCreate.length}`);

  if (toCreate.length === 0) {
    console.log("[seed] Nothing to do.");
    return;
  }

  if (dryRun) {
    console.log("[seed] DRY_RUN=1, not writing. Payload preview:");
    console.log(JSON.stringify(toCreate, null, 2));
    return;
  }

  const batch = firestore.batch();
  for (const habit of toCreate) {
    const docRef = firestore.collection(HABITS_COLLECTION).doc();
    batch.set(docRef, habit, { merge: false });
  }
  await batch.commit();

  console.log(`[seed] Created ${toCreate.length} public habits in '${HABITS_COLLECTION}'.`);
}

main().catch((e) => {
  console.error("[seed] Failed:", e);
  process.exit(1);
});
