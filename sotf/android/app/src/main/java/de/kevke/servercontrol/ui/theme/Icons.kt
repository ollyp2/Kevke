package de.kevke.servercontrol.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Line-art icon set. Every glyph is stroked, never filled — no emoji, no
 * solid pictograms. Strokes inherit tint via [SolidColor] on currentColor
 * at draw time, so a single definition works on any background.
 */
private fun lineIcon(
    name: String,
    build: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply(build).build()

private fun androidx.compose.ui.graphics.vector.ImageVector.Builder.stroke(
    block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
) = path(
    fill = null,
    stroke = SolidColor(Color.Black),
    strokeLineWidth = 1.6f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
    pathBuilder = block,
)

object LineIcons {

    /** Power symbol: broken ring with a vertical stem. */
    val Power: ImageVector by lazy {
        lineIcon("Power") {
            stroke {
                moveTo(12f, 3f); lineTo(12f, 11f)
            }
            stroke {
                moveTo(7.5f, 6.2f)
                arcToRelative(7.5f, 7.5f, 0f, true, false, 9f, 0f)
            }
        }
    }

    /** Floppy disk — save now. */
    val Save: ImageVector by lazy {
        lineIcon("Save") {
            stroke {
                moveTo(4f, 5.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, -1.5f)
                lineTo(16f, 4f); lineTo(20f, 8f); lineTo(20f, 18.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, 1.5f)
                lineTo(5.5f, 20f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, -1.5f)
                close()
            }
            stroke {
                moveTo(8f, 4f); lineTo(8f, 9f); lineTo(15f, 9f); lineTo(15f, 4f)
            }
            stroke {
                moveTo(7f, 20f); lineTo(7f, 13f); lineTo(17f, 13f); lineTo(17f, 20f)
            }
        }
    }

    /** Arrow curving inside a circle — restore. */
    val Restore: ImageVector by lazy {
        lineIcon("Restore") {
            stroke {
                moveTo(20f, 12f)
                arcToRelative(8f, 8f, 0f, true, true, -2.4f, -5.7f)
            }
            stroke {
                moveTo(20f, 3.5f); lineTo(20f, 8.5f); lineTo(15f, 8.5f)
            }
        }
    }

    /** X — delete. */
    val Delete: ImageVector by lazy {
        lineIcon("Delete") {
            stroke { moveTo(6f, 6f); lineTo(18f, 18f) }
            stroke { moveTo(18f, 6f); lineTo(6f, 18f) }
        }
    }

    /** Chevron down — collapsible section. */
    val ChevronDown: ImageVector by lazy {
        lineIcon("ChevronDown") {
            stroke { moveTo(6f, 9.5f); lineTo(12f, 15.5f); lineTo(18f, 9.5f) }
        }
    }

    val ChevronRight: ImageVector by lazy {
        lineIcon("ChevronRight") {
            stroke { moveTo(9.5f, 6f); lineTo(15.5f, 12f); lineTo(9.5f, 18f) }
        }
    }

    /** Three rules — hamburger. */
    val Menu: ImageVector by lazy {
        lineIcon("Menu") {
            stroke { moveTo(4f, 7f); lineTo(20f, 7f) }
            stroke { moveTo(4f, 12f); lineTo(20f, 12f) }
            stroke { moveTo(4f, 17f); lineTo(20f, 17f) }
        }
    }

    /** Gauge — resources. */
    val Gauge: ImageVector by lazy {
        lineIcon("Gauge") {
            stroke {
                moveTo(4f, 17f)
                arcToRelative(8f, 8f, 0f, true, true, 16f, 0f)
            }
            stroke { moveTo(12f, 13f); lineTo(15.5f, 9.5f) }
            stroke { moveTo(4f, 17f); lineTo(20f, 17f) }
        }
    }

    /** Coins stacked — billing. */
    val Billing: ImageVector by lazy {
        lineIcon("Billing") {
            stroke {
                moveTo(4f, 7f)
                arcToRelative(8f, 3f, 0f, true, false, 16f, 0f)
                arcToRelative(8f, 3f, 0f, true, false, -16f, 0f)
            }
            stroke { moveTo(4f, 7f); lineTo(4f, 12f) }
            stroke { moveTo(20f, 7f); lineTo(20f, 12f) }
            stroke {
                moveTo(4f, 12f)
                curveTo(4f, 13.7f, 7.6f, 15f, 12f, 15f)
                curveTo(16.4f, 15f, 20f, 13.7f, 20f, 12f)
            }
            stroke { moveTo(4f, 12f); lineTo(4f, 17f) }
            stroke { moveTo(20f, 12f); lineTo(20f, 17f) }
            stroke {
                moveTo(4f, 17f)
                curveTo(4f, 18.7f, 7.6f, 20f, 12f, 20f)
                curveTo(16.4f, 20f, 20f, 18.7f, 20f, 17f)
            }
        }
    }

    /** Globe with meridian — world / map. */
    val World: ImageVector by lazy {
        lineIcon("World") {
            stroke {
                moveTo(12f, 3f)
                arcToRelative(9f, 9f, 0f, true, false, 0.01f, 0f)
                close()
            }
            stroke { moveTo(3f, 12f); lineTo(21f, 12f) }
            stroke {
                moveTo(12f, 3f)
                curveTo(15f, 6.5f, 15f, 17.5f, 12f, 21f)
                curveTo(9f, 17.5f, 9f, 6.5f, 12f, 3f)
                close()
            }
        }
    }

    /** Open box — give item. */
    val Item: ImageVector by lazy {
        lineIcon("Item") {
            stroke {
                moveTo(3.5f, 7.5f); lineTo(12f, 3.5f); lineTo(20.5f, 7.5f)
                lineTo(20.5f, 16.5f); lineTo(12f, 20.5f); lineTo(3.5f, 16.5f)
                close()
            }
            stroke { moveTo(3.5f, 7.5f); lineTo(12f, 11.5f); lineTo(20.5f, 7.5f) }
            stroke { moveTo(12f, 11.5f); lineTo(12f, 20.5f) }
        }
    }

    /** Pin with motion arc — teleport. */
    val Teleport: ImageVector by lazy {
        lineIcon("Teleport") {
            stroke {
                moveTo(12f, 21f)
                curveTo(12f, 21f, 5.5f, 14.6f, 5.5f, 10.2f)
                arcToRelative(6.5f, 6.5f, 0f, true, true, 13f, 0f)
                curveTo(18.5f, 14.6f, 12f, 21f, 12f, 21f)
                close()
            }
            stroke {
                moveTo(12f, 7.6f)
                arcToRelative(2.6f, 2.6f, 0f, true, false, 0.01f, 0f)
                close()
            }
        }
    }

    /** Sliders — config. */
    val Sliders: ImageVector by lazy {
        lineIcon("Sliders") {
            stroke { moveTo(4f, 7f); lineTo(20f, 7f) }
            stroke { moveTo(4f, 12f); lineTo(20f, 12f) }
            stroke { moveTo(4f, 17f); lineTo(20f, 17f) }
            stroke {
                moveTo(9f, 7f)
                arcToRelative(2f, 2f, 0f, true, false, 0.01f, 0f)
                close()
            }
            stroke {
                moveTo(16f, 12f)
                arcToRelative(2f, 2f, 0f, true, false, 0.01f, 0f)
                close()
            }
            stroke {
                moveTo(7.5f, 17f)
                arcToRelative(2f, 2f, 0f, true, false, 0.01f, 0f)
                close()
            }
        }
    }

    /** Upward tray — upload. */
    val Upload: ImageVector by lazy {
        lineIcon("Upload") {
            stroke { moveTo(12f, 16f); lineTo(12f, 4f) }
            stroke { moveTo(8f, 8f); lineTo(12f, 4f); lineTo(16f, 8f) }
            stroke {
                moveTo(4f, 15f); lineTo(4f, 19f)
                arcToRelative(1f, 1f, 0f, false, false, 1f, 1f)
                lineTo(19f, 20f)
                arcToRelative(1f, 1f, 0f, false, false, 1f, -1f)
                lineTo(20f, 15f)
            }
        }
    }

    /** Person outline — players. */
    val Player: ImageVector by lazy {
        lineIcon("Player") {
            stroke {
                moveTo(12f, 4f)
                arcToRelative(3.6f, 3.6f, 0f, true, false, 0.01f, 0f)
                close()
            }
            stroke {
                moveTo(4.5f, 20f)
                curveTo(4.5f, 15.9f, 7.9f, 13.5f, 12f, 13.5f)
                curveTo(16.1f, 13.5f, 19.5f, 15.9f, 19.5f, 20f)
            }
        }
    }

    /** Gear — settings. */
    val Settings: ImageVector by lazy {
        lineIcon("Settings") {
            stroke {
                moveTo(12f, 9f)
                arcToRelative(3f, 3f, 0f, true, false, 0.01f, 0f)
                close()
            }
            stroke {
                moveTo(19.4f, 14.5f)
                lineTo(20.9f, 15.4f); lineTo(19.4f, 18f); lineTo(17.7f, 17.3f)
                arcToRelative(6f, 6f, 0f, false, true, -1.9f, 1.1f)
                lineTo(15.5f, 20.2f); lineTo(12.5f, 20.2f); lineTo(12.2f, 18.4f)
                arcToRelative(6f, 6f, 0f, false, true, -1.9f, -1.1f)
                lineTo(8.6f, 18f); lineTo(7.1f, 15.4f); lineTo(8.6f, 14.5f)
                arcToRelative(6f, 6f, 0f, false, true, 0f, -2.2f)
                lineTo(7.1f, 11.4f); lineTo(8.6f, 8.8f); lineTo(10.3f, 9.5f)
                arcToRelative(6f, 6f, 0f, false, true, 1.9f, -1.1f)
                lineTo(12.5f, 6.6f); lineTo(15.5f, 6.6f); lineTo(15.8f, 8.4f)
                arcToRelative(6f, 6f, 0f, false, true, 1.9f, 1.1f)
                lineTo(19.4f, 8.8f); lineTo(20.9f, 11.4f); lineTo(19.4f, 12.3f)
                arcToRelative(6f, 6f, 0f, false, true, 0f, 2.2f)
                close()
            }
        }
    }

    /** Back arrow. */
    val Back: ImageVector by lazy {
        lineIcon("Back") {
            stroke { moveTo(19f, 12f); lineTo(5f, 12f) }
            stroke { moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f) }
        }
    }

    /** Plus. */
    val Plus: ImageVector by lazy {
        lineIcon("Plus") {
            stroke { moveTo(12f, 5f); lineTo(12f, 19f) }
            stroke { moveTo(5f, 12f); lineTo(19f, 12f) }
        }
    }

    /** Copy — two offset rectangles. */
    val Copy: ImageVector by lazy {
        lineIcon("Copy") {
            stroke {
                moveTo(9f, 9f); lineTo(19f, 9f); lineTo(19f, 20f); lineTo(9f, 20f)
                close()
            }
            stroke {
                moveTo(15f, 6f); lineTo(5f, 6f); lineTo(5f, 16f)
            }
        }
    }

    /** Server rack — generic server logo. */
    val Server: ImageVector by lazy {
        lineIcon("Server") {
            stroke {
                moveTo(3.5f, 4.5f); lineTo(20.5f, 4.5f); lineTo(20.5f, 10f)
                lineTo(3.5f, 10f); close()
            }
            stroke {
                moveTo(3.5f, 14f); lineTo(20.5f, 14f); lineTo(20.5f, 19.5f)
                lineTo(3.5f, 19.5f); close()
            }
            stroke { moveTo(7f, 7.2f); lineTo(7.02f, 7.2f) }
            stroke { moveTo(7f, 16.7f); lineTo(7.02f, 16.7f) }
        }
    }

    /** Pine tree — Sons of the Forest logo mark. */
    val Forest: ImageVector by lazy {
        lineIcon("Forest") {
            stroke {
                moveTo(12f, 3f); lineTo(7.5f, 10f); lineTo(10f, 10f)
                lineTo(6f, 16f); lineTo(10.5f, 16f); lineTo(10.5f, 21f)
                lineTo(13.5f, 21f); lineTo(13.5f, 16f); lineTo(18f, 16f)
                lineTo(14f, 10f); lineTo(16.5f, 10f); close()
            }
        }
    }
}
