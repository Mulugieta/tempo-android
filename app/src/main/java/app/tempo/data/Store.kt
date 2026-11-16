package app.tempo.data

import android.content.Context
import app.tempo.plan.Plan
import app.tempo.plan.PlanJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Plain JSON file in app storage plus one preference. No account, nothing leaves the phone. */
class Store(context: Context) {
    private val dir = context.filesDir
    private val file = File(dir, "workouts.json")
    private val prefs = context.getSharedPreferences("tempo", Context.MODE_PRIVATE)

    var goal: Int
        get() = prefs.getInt("goal", 150)
        set(value) {
            prefs.edit().putInt("goal", value).apply()
        }

    fun load(): List<Workout> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                runCatching { fromJson(arr.getJSONObject(i)) }.getOrNull()
            }
        } catch (e: Exception) {
            runCatching { file.copyTo(File(dir, "workouts.unreadable.json"), overwrite = true) }
            emptyList()
        }
    }

    fun save(list: List<Workout>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        val tmp = File(dir, "workouts.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(arr.toString())
            tmp.delete()
        }
    }

    private val planFile = File(dir, "plan.json")

    fun loadPlan(): Plan? {
        if (!planFile.exists()) return null
        return runCatching { PlanJson.fromJson(JSONObject(planFile.readText())) }.getOrNull()
    }

    /** Pass null to remove the saved plan. */
    fun savePlan(plan: Plan?) {
        if (plan == null) {
            planFile.delete()
            return
        }
        val tmp = File(dir, "plan.json.tmp")
        tmp.writeText(PlanJson.toJson(plan).toString())
        if (!tmp.renameTo(planFile)) {
            planFile.writeText(PlanJson.toJson(plan).toString())
            tmp.delete()
        }
    }

    private fun toJson(w: Workout) = JSONObject().apply {
        put("id", w.id)
        put("date", w.date.toString())
        put("kind", w.kind.name)
        put("minutes", w.minutes)
        put("reps", w.reps)
        put("km", w.km)
        put("intensity", w.intensity)
        put("note", w.note)
        put("camera", w.camera)
    }

    private fun fromJson(o: JSONObject) = Workout(
        id = o.getString("id"),
        date = LocalDate.parse(o.getString("date")),
        kind = Kind.valueOf(o.getString("kind")),
        minutes = o.getInt("minutes"),
        reps = o.optInt("reps", 0),
        km = o.optDouble("km", 0.0),
        intensity = o.optInt("intensity", 2),
        note = o.optString("note", ""),
        camera = o.optBoolean("camera", false),
    )
}
