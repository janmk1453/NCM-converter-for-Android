package com.mcn.fix.ui.theme

import androidx.compose.ui.graphics.Color

object StatusColors {
    val runState = RunStateColors()
    val delay = DelayColors()
    val actionButton = ActionButtonColors()
    val danger = Color(0xFFE53935)
    val healthy = Color(0xFF43A047)
    val warning = Color(0xFFFFA000)
    val neutral = Color(0xFF9E9E9E)
    val selectedNodeContainer = Color(0xFFE8F5E9)
}

class RunStateColors {
    val running = Color(0xFF43A047)
    val waiting = Color(0xFFFFA000)
    val failed = Color(0xFFE53935)
}

class DelayColors {
    val good = Color(0xFF43A047)
    val fair = Color(0xFFFFA000)
    val poor = Color(0xFFE53935)
    val untested = Color(0xFF9E9E9E)
}

class ActionButtonColors {
    val restart = Color(0xFF1E88E5)
    val stop = Color(0xFFE53935)
    val reload = Color(0xFF43A047)
}
