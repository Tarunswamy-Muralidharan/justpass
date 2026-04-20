package com.justpass.app.data.model

data class StudentBiodata(
    val firstName: String? = null,
    val lastName: String? = null,
    val gender: String? = null,
    val dateOfBirth: String? = null,
    val motherTongue: String? = null,
    val nationality: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val course: String? = null,
    val rollNumber: String? = null,
    val quota: String? = null,
    val enrolledOn: String? = null,
    val bloodGroup: String? = null,
    val religion: String? = null,
    val community: String? = null,
    val fatherName: String? = null,
    val motherName: String? = null,
    val appFormNo: String? = null,
    val currentSem: Int? = null,
    val section: String? = null,
    val department: String? = null,
    val batchYear: Int? = null,
    val programDuration: Int? = null,
    val degreeName: String? = null,
    val programmeName: String? = null
) {
    companion object {
        fun fromJson(json: org.json.JSONObject): StudentBiodata {
            // Personal fields can be at top level or nested under "personal"/"basic"
            val personal = json.optJSONObject("personal") ?: json
            val basic = personal.optJSONObject("basic") ?: personal
            val contact = json.optJSONObject("contact") ?: personal.optJSONObject("contact") ?: json
            val family = json.optJSONObject("family") ?: personal.optJSONObject("family")
            val admissions = json.optJSONObject("admissions")

            // Course name from various possible locations
            val course = json.optString("courseName", "").ifEmpty {
                json.optJSONObject("course")?.optString("name", "")
                    ?: admissions?.optString("courseName", "") ?: ""
            }

            // Date formatting
            val rawDob = basic.optString("dateOfBirth", "").ifEmpty {
                basic.optString("dob", "")
            }
            val formattedDob = formatDateString(rawDob)

            val rawEnrolled = admissions?.optString("enrolledOn", "") ?: ""
            val formattedEnrolled = formatDateString(rawEnrolled)

            return StudentBiodata(
                firstName = basic.optString("firstName", "").ifEmpty { json.optString("firstName", "").ifEmpty { null } },
                lastName = basic.optString("lastName", "").ifEmpty { json.optString("lastName", "").ifEmpty { null } },
                gender = basic.optString("gender", "").ifEmpty { json.optString("gender", "").ifEmpty { null } },
                dateOfBirth = formattedDob.ifEmpty { null },
                motherTongue = basic.optString("motherTongue", "").ifEmpty { null },
                nationality = basic.optString("nationality", "").ifEmpty { json.optString("nationality", "").ifEmpty { null } },
                email = contact.optString("email", "").ifEmpty { json.optString("email", "").ifEmpty { null } },
                phone = contact.optString("mobile", "").ifEmpty { contact.optString("phone", "").ifEmpty { json.optString("mobile", "").ifEmpty { null } } },
                course = course.ifEmpty { null },
                rollNumber = json.optString("rollNumber", "").ifEmpty { null },
                quota = admissions?.optString("quota", "")?.ifEmpty { null },
                enrolledOn = formattedEnrolled.ifEmpty { null },
                bloodGroup = basic.optString("bloodGroup", "").ifEmpty { json.optString("bloodGroup", "").ifEmpty { null } },
                religion = basic.optString("religion", "").ifEmpty { null },
                community = basic.optString("community", "").ifEmpty { null },
                fatherName = family?.optString("fatherName", "")?.ifEmpty {
                    // Also check family.members array
                    val members = family.optJSONArray("members")
                    var father: String? = null
                    if (members != null) {
                        for (i in 0 until members.length()) {
                            val m = members.getJSONObject(i)
                            if (m.optString("type") == "FATHER") { father = m.optString("name", "").ifEmpty { null }; break }
                        }
                    }
                    father
                },
                motherName = family?.optString("motherName", "")?.ifEmpty {
                    val members = family.optJSONArray("members")
                    var mother: String? = null
                    if (members != null) {
                        for (i in 0 until members.length()) {
                            val m = members.getJSONObject(i)
                            if (m.optString("type") == "MOTHER") { mother = m.optString("name", "").ifEmpty { null }; break }
                        }
                    }
                    mother
                },
                appFormNo = admissions?.optString("appFormNo", "")?.ifEmpty { null },
                currentSem = json.optInt("currentSem", -1).takeIf { it > 0 },
                section = json.optString("section", "").ifEmpty { null },
                department = json.optString("department", "").ifEmpty { null },
                batchYear = json.optInt("batchYear", -1).takeIf { it > 0 },
                programDuration = json.optInt("programDuration", -1).takeIf { it > 0 },
                degreeName = json.optString("degreeName", "").ifEmpty { null },
                programmeName = json.optString("programmeName", "").ifEmpty { null }
            )
        }

        private fun formatDateString(raw: String): String {
            if (raw.isEmpty()) return ""
            return try {
                // Handle ISO date like "2023-07-05T00:00:00.000Z" or "2004-08-15"
                val dateStr = raw.substringBefore("T")
                val parts = dateStr.split("-")
                if (parts.size == 3) {
                    val months = arrayOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                    val month = parts[1].toIntOrNull() ?: return dateStr
                    "${parts[2]} ${months.getOrElse(month) { "" }} ${parts[0]}"
                } else dateStr
            } catch (_: Exception) { raw }
        }
    }
}
