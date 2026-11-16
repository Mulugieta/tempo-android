package app.tempo.plan

import app.tempo.data.Kind
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Saves and restores a [Plan] as JSON so it survives app restarts. */
object PlanJson {
    fun toJson(plan: Plan): JSONObject = JSONObject().apply {
        put("created", plan.created.toString())
        put("profile", JSONObject().apply {
            put("goal", plan.profile.goal.name)
            put("level", plan.profile.level.name)
            put("days", plan.profile.days)
            put("minutes", plan.profile.minutes)
            put("equipment", plan.profile.equipment.name)
            put("lowImpact", plan.profile.lowImpact)
        })
        put("notes", JSONArray(plan.notes))
        put("sessions", JSONArray().apply {
            plan.sessions.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("week", s.week)
                    put("date", s.date.toString())
                    put("title", s.title)
                    put("minutes", s.minutes)
                    put("done", s.done)
                    put("items", JSONArray().apply {
                        s.items.forEach { i ->
                            put(JSONObject().apply {
                                put("name", i.name)
                                put("detail", i.detail)
                                put("minutes", i.minutes)
                                put("kind", i.kind?.name ?: "")
                                put("targetReps", i.targetReps)
                                put("note", i.note)
                            })
                        }
                    })
                })
            }
        })
    }

    fun fromJson(o: JSONObject): Plan {
        val p = o.getJSONObject("profile")
        val profile = Profile(
            goal = Goal.valueOf(p.getString("goal")),
            level = Level.valueOf(p.getString("level")),
            days = p.getInt("days"),
            minutes = p.getInt("minutes"),
            equipment = Equipment.valueOf(p.getString("equipment")),
            lowImpact = p.getBoolean("lowImpact"),
        )
        val notesArr = o.getJSONArray("notes")
        val sessionsArr = o.getJSONArray("sessions")
        val sessions = (0 until sessionsArr.length()).map { si ->
            val s = sessionsArr.getJSONObject(si)
            val itemsArr = s.getJSONArray("items")
            PlanSession(
                id = s.getString("id"),
                week = s.getInt("week"),
                date = LocalDate.parse(s.getString("date")),
                title = s.getString("title"),
                minutes = s.getInt("minutes"),
                done = s.getBoolean("done"),
                items = (0 until itemsArr.length()).map { ii ->
                    val i = itemsArr.getJSONObject(ii)
                    PlanItem(
                        name = i.getString("name"),
                        detail = i.getString("detail"),
                        minutes = i.getInt("minutes"),
                        kind = i.optString("kind", "").takeIf { it.isNotBlank() }?.let { Kind.valueOf(it) },
                        targetReps = i.optInt("targetReps", 0),
                        note = i.optString("note", ""),
                    )
                },
            )
        }
        return Plan(
            profile = profile,
            created = LocalDate.parse(o.getString("created")),
            sessions = sessions,
            notes = (0 until notesArr.length()).map { notesArr.getString(it) },
        )
    }
}
