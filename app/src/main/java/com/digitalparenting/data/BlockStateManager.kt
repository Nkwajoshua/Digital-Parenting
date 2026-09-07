package com.digitalparenting.data

object BlockStateManager {
    @JvmStatic
    val blockedPackages: MutableSet<String> = mutableSetOf()

    @JvmStatic
    val blockModes: MutableMap<String, BlockMode> = mutableMapOf()

    @Synchronized
    fun setBlocked(packages: Set<String>) {
        blockedPackages.clear()
        blockedPackages.addAll(packages)
    }

    @Synchronized
    fun replaceState(packages: Set<String>, modes: Map<String, BlockMode>) {
        blockedPackages.clear()
        blockedPackages.addAll(packages)
        blockModes.clear()
        blockModes.putAll(modes)
    }

    @Synchronized
    fun snapshotState(): Pair<Set<String>, Map<String, BlockMode>> {
        return blockedPackages.toSet() to blockModes.toMap()
    }

    @Synchronized
    fun removeBlocked(packageName: String) {
        blockedPackages.remove(packageName)
        blockModes.remove(packageName)
    }

    @Synchronized
    fun setBlockMode(packageName: String, mode: BlockMode) {
        if (mode == BlockMode.NONE) {
            blockModes.remove(packageName)
        } else {
            blockModes[packageName] = mode
        }
    }

    @Synchronized
    fun clearBlocked() {
        blockedPackages.clear()
        blockModes.clear()
    }

    @Synchronized
    fun isBlocked(pkg: String): Boolean {
        return blockedPackages.contains(pkg)
    }

    @Synchronized
    fun getBlockMode(pkg: String): BlockMode {
        return blockModes[pkg] ?: BlockMode.NONE
    }
}
