package dev.claudeadmin.data.project

import java.io.File

object ProjectIconResolver {

    private val ANDROID_MODULE_DIRS = listOf(
        "app",
        "composeApp",
        "androidApp",
        "android",
        "mobile",
        "shared",
    )

    private val ANDROID_SOURCE_SETS = listOf("main", "androidMain")

    private val DESKTOP_SOURCE_SETS = listOf("main", "jvmMain", "desktopMain", "commonMain")

    private val DENSITIES = listOf("xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi")

    private val RASTER_EXTENSIONS = listOf("webp", "png")

    private val EXCLUDED_DIRS = setOf(
        "build",
        ".gradle",
        ".git",
        ".idea",
        ".kotlin",
        ".cxx",
        "node_modules",
        "out",
        "dist",
        "target",
        ".next",
        ".nuxt",
        "vendor",
        "DerivedData",
        "Pods",
        ".swiftpm",
        ".fleet",
        ".vscode",
    )

    private const val MAX_WALK_DEPTH = 10

    fun resolve(projectRoot: File): File? {
        if (!projectRoot.isDirectory) return null
        return androidDirectLookup(projectRoot)
            ?: desktopDirectLookup(projectRoot)
            ?: walkLookup(projectRoot)
    }

    private fun androidDirectLookup(root: File): File? {
        for (module in ANDROID_MODULE_DIRS) {
            val moduleDir = File(root, module)
            if (!moduleDir.isDirectory) continue
            for (sourceSet in ANDROID_SOURCE_SETS) {
                val sourceDir = File(moduleDir, "src/$sourceSet")
                if (!sourceDir.isDirectory) continue

                File(sourceDir, "ic_launcher-playstore.png")
                    .takeIf(File::isFile)
                    ?.let { return it }

                val resDir = File(sourceDir, "res")
                if (resDir.isDirectory) {
                    bestMipmapIcon(resDir)?.let { return it }
                }
            }
        }
        return null
    }

    private fun bestMipmapIcon(resDir: File): File? {
        for (density in DENSITIES) {
            val mipmap = File(resDir, "mipmap-$density")
            if (!mipmap.isDirectory) continue
            for (ext in RASTER_EXTENSIONS) {
                File(mipmap, "ic_launcher.$ext").takeIf(File::isFile)?.let { return it }
            }
            for (ext in RASTER_EXTENSIONS) {
                File(mipmap, "ic_launcher_round.$ext").takeIf(File::isFile)?.let { return it }
            }
        }
        return null
    }

    private fun desktopDirectLookup(root: File): File? {
        val moduleRoots = mutableListOf(root)
        root.listFiles { f -> f.isDirectory && f.name !in EXCLUDED_DIRS }?.let { moduleRoots += it }

        for (moduleRoot in moduleRoots) {
            for (sourceSet in DESKTOP_SOURCE_SETS) {
                val resourcesDir = File(moduleRoot, "src/$sourceSet/resources")
                if (resourcesDir.isDirectory) {
                    desktopIconIn(resourcesDir)?.let { return it }
                    val iconsSubdir = File(resourcesDir, "icons")
                    if (iconsSubdir.isDirectory) desktopIconIn(iconsSubdir)?.let { return it }
                }
            }
            val moduleIcons = File(moduleRoot, "icons")
            if (moduleIcons.isDirectory) desktopIconIn(moduleIcons)?.let { return it }
        }
        return null
    }

    private fun desktopIconIn(dir: File): File? {
        for (ext in RASTER_EXTENSIONS) {
            File(dir, "icon.$ext").takeIf(File::isFile)?.let { return it }
        }
        return null
    }

    private fun walkLookup(root: File): File? {
        var androidPlaystore: File? = null
        val mipmapsByDensity = HashMap<String, MutableList<File>>()
        val appIconSets = HashMap<File, MutableList<File>>()
        val desktopCandidates = mutableListOf<File>()

        val iterator = root.walkTopDown()
            .maxDepth(MAX_WALK_DEPTH)
            .onEnter { dir -> dir == root || dir.name !in EXCLUDED_DIRS }
            .iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!entry.isFile) continue
            val ext = entry.extension.lowercase()
            if (ext !in RASTER_EXTENSIONS) continue
            val parent = entry.parentFile ?: continue
            val parentName = parent.name

            when {
                entry.name == "ic_launcher-playstore.png" -> {
                    androidPlaystore = entry
                    return entry
                }
                parentName.startsWith("mipmap-") &&
                    entry.nameWithoutExtension in MIPMAP_NAMES -> {
                    val density = parentName.removePrefix("mipmap-")
                    mipmapsByDensity.getOrPut(density) { mutableListOf() } += entry
                }
                parentName.endsWith(".appiconset") && ext == "png" -> {
                    appIconSets.getOrPut(parent) { mutableListOf() } += entry
                }
                entry.nameWithoutExtension == "icon" &&
                    (parentName.equals("resources", ignoreCase = true) ||
                        parentName.equals("icons", ignoreCase = true)) -> {
                    desktopCandidates += entry
                }
            }
        }

        androidPlaystore?.let { return it }

        for (density in DENSITIES) {
            val files = mipmapsByDensity[density] ?: continue
            files.firstOrNull { it.nameWithoutExtension == "ic_launcher" }?.let { return it }
            files.firstOrNull { it.nameWithoutExtension == "ic_launcher_round" }?.let { return it }
        }

        bestAppIconSetIcon(appIconSets)?.let { return it }

        desktopCandidates.minByOrNull { it.absolutePath }?.let { return it }
        return null
    }

    private fun bestAppIconSetIcon(appIconSets: Map<File, List<File>>): File? {
        if (appIconSets.isEmpty()) return null
        val sortedSets = appIconSets.keys.sortedWith(compareBy(
            { if (it.nameWithoutExtension == "AppIcon") 0 else 1 },
            { it.nameWithoutExtension },
        ))
        for (set in sortedSets) {
            val pngs = appIconSets[set] ?: continue
            val largest = pngs.maxByOrNull { it.length() } ?: continue
            return largest
        }
        return null
    }

    private val MIPMAP_NAMES = setOf("ic_launcher", "ic_launcher_round")
}
