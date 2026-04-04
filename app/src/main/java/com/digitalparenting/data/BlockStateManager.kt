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

    fun isBlocked(pkg: String): Boolean {
        return blockedPackages.contains(pkg)
    }

    fun getBlockMode(pkg: String): BlockMode {
        return blockModes[pkg] ?: BlockMode.NONE
    }
}
