package com.prismde.core.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme

/**
 * Material 3 Expressive Motion Specifications.
 * Uses physics-based spring curves for snappy, organic feedback.
 */
object PrismMotion {
    // Bouncy spring for interactive buttons, tabs, and FABs
    val BouncySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    // Smooth spring for sheet expansions and dialog transitions
    val SmoothSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    // Spatial spring for layout movements (file tree collapse/expand)
    val SpatialSpring = spring<Int>(
        dampingRatio = 0.8f,
        stiffness = 380f
    )
}
