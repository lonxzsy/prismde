package com.prismde.feature_build.engine

import java.io.File

data class ProjectSkill(
    val name: String,
    val file: File,
    val content: String,
    val description: String = ""
)

object ProjectSkillManager {

    /**
     * Scans project directory for skills inside .prismde/skills or .prism/skills.
     */
    fun loadProjectSkills(projectDir: File): List<ProjectSkill> {
        val skillDirs = listOf(
            File(projectDir, ".prismde/skills"),
            File(projectDir, ".prism/skills")
        )

        val targetDir = skillDirs.firstOrNull { it.exists() && it.isDirectory } ?: return emptyList()
        val skills = mutableListOf<ProjectSkill>()

        targetDir.listFiles()?.forEach { file ->
            if (file.isFile && (file.extension.equals("md", ignoreCase = true) || file.extension.equals("txt", ignoreCase = true))) {
                val content = try { file.readText().trim() } catch (_: Exception) { "" }
                if (content.isNotBlank()) {
                    val name = file.nameWithoutExtension
                    val firstLine = content.lines().firstOrNull { it.isNotBlank() }?.removePrefix("#")?.trim() ?: name
                    skills.add(ProjectSkill(name = name, file = file, content = content, description = firstLine))
                }
            } else if (file.isDirectory) {
                // Check if directory has SKILL.md or similar
                val skillMd = File(file, "SKILL.md").takeIf { it.exists() }
                    ?: File(file, "skill.md").takeIf { it.exists() }
                    ?: file.listFiles { _, name -> name.endsWith(".md", ignoreCase = true) }?.firstOrNull()

                if (skillMd != null && skillMd.isFile) {
                    val content = try { skillMd.readText().trim() } catch (_: Exception) { "" }
                    if (content.isNotBlank()) {
                        val name = file.name
                        val firstLine = content.lines().firstOrNull { it.isNotBlank() }?.removePrefix("#")?.trim() ?: name
                        skills.add(ProjectSkill(name = name, file = skillMd, content = content, description = firstLine))
                    }
                }
            }
        }

        return skills
    }

    /**
     * Formats skills for injection into system prompt or LLM instructions.
     */
    fun formatSkillsPrompt(skills: List<ProjectSkill>, isRu: Boolean): String {
        if (skills.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append(
            if (isRu) "\n\n=== НАВЫКИ ПРОЕКТА (.prismde/skills) ===\n" +
                    "В проекте обнаружены специализированные инструкции и навыки (Skills). Строго следуй им при выполнении задач:\n"
            else "\n\n=== PROJECT SKILLS (.prismde/skills) ===\n" +
                    "Project-specific instructions and skills found in .prismde/skills. Follow them strictly when performing tasks:\n"
        )

        for (skill in skills) {
            sb.append("\n--- НАВЫК: ${skill.name} (${skill.file.name}) ---\n")
            // Include full content if reasonable size (< 8KB), else summarize and tell how to read
            if (skill.content.length <= 8000) {
                sb.append(skill.content)
            } else {
                sb.append(skill.content.take(2500)).append("\n...(содержимое сокращено, вызови read_file для полного прочтения)...")
            }
            sb.append("\n--- КОНЕЦ НАВЫКА: ${skill.name} ---\n")
        }

        return sb.toString()
    }
}
