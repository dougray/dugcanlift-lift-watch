package com.dugcanlift.liftwear
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/** LiftCore.Theme's values, verbatim. */
object DclColors {
    val Bg = Color(0xFF1C1B19); val Surface = Color(0xFF242220); val Text = Color(0xFFEDE7DD)
    val Muted = Color(0xFFA39C8E); val Accent = Color(0xFFC1442C); val Accent2 = Color(0xFF7C8B7A); val Rule = Color(0xFF3A3733)
}
@Composable fun LiftWearTheme(content: @Composable () -> Unit) = MaterialTheme(
    colors = Colors(primary = DclColors.Accent, secondary = DclColors.Accent2, background = DclColors.Bg,
        surface = DclColors.Surface, onPrimary = Color(0xFFF7F1E8), onBackground = DclColors.Text, onSurface = DclColors.Text),
    content = content)
