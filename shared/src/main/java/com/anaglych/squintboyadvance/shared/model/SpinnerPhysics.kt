package com.anaglych.squintboyadvance.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class SpinnerPhysics(
    val inertia: Float = 0.88f,       // velocity decay per 16 ms frame (0=instant stop)
    val snapStrength: Float = 0.22f,  // spring stiffness toward snapped item (0..1)
    val virtualRadius: Float = 4.0f,  // items at this distance = 90° arc (controls scale falloff)
    val fadeOpacity: Float = 0.30f,   // minimum alpha at outermost visible items
    val visibleEntries: Int = 3,      // items rendered on each side of centre
)
