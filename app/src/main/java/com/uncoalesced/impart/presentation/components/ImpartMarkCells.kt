// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.components

// The Impart mark (spec 09 section 5): 21 cells on a 9x9 grid, generated once by
// design/impart_mark.py --seed 4 and committed as design/impart-mark.cells.json. This table
// is a copy of that file; ImpartMarkTest fails if the two disagree. The app never generates
// a mark at runtime. The center cell (4, 4) is always empty.
enum class MarkShape { CIRCLE, SQUARE }

enum class MarkTone { SHADE, BASE, TINT }

data class ImpartMarkCell(val row: Int, val col: Int, val shape: MarkShape, val tone: MarkTone)

object ImpartMarkCells {
    const val GRID = 9
    const val CENTER = 4

    // Fractions of one cell (spec 09 section 5).
    const val CIRCLE_DIAMETER = 0.62f
    const val SQUARE_SIDE = 0.66f
    const val SQUARE_CORNER = 0.12f

    val cells: List<ImpartMarkCell> = listOf(
        ImpartMarkCell(0, 4, MarkShape.CIRCLE, MarkTone.SHADE),
        ImpartMarkCell(1, 4, MarkShape.CIRCLE, MarkTone.SHADE),
        ImpartMarkCell(2, 3, MarkShape.CIRCLE, MarkTone.BASE),
        ImpartMarkCell(2, 4, MarkShape.SQUARE, MarkTone.TINT),
        ImpartMarkCell(2, 5, MarkShape.SQUARE, MarkTone.SHADE),
        ImpartMarkCell(3, 3, MarkShape.CIRCLE, MarkTone.BASE),
        ImpartMarkCell(3, 4, MarkShape.CIRCLE, MarkTone.SHADE),
        ImpartMarkCell(3, 5, MarkShape.CIRCLE, MarkTone.TINT),
        ImpartMarkCell(4, 3, MarkShape.SQUARE, MarkTone.TINT),
        ImpartMarkCell(4, 5, MarkShape.SQUARE, MarkTone.SHADE),
        ImpartMarkCell(5, 2, MarkShape.CIRCLE, MarkTone.BASE),
        ImpartMarkCell(5, 3, MarkShape.SQUARE, MarkTone.TINT),
        ImpartMarkCell(5, 4, MarkShape.SQUARE, MarkTone.SHADE),
        ImpartMarkCell(5, 5, MarkShape.CIRCLE, MarkTone.BASE),
        ImpartMarkCell(5, 6, MarkShape.CIRCLE, MarkTone.SHADE),
        ImpartMarkCell(6, 2, MarkShape.CIRCLE, MarkTone.SHADE),
        ImpartMarkCell(6, 3, MarkShape.SQUARE, MarkTone.TINT),
        ImpartMarkCell(6, 5, MarkShape.CIRCLE, MarkTone.BASE),
        ImpartMarkCell(6, 6, MarkShape.SQUARE, MarkTone.BASE),
        ImpartMarkCell(7, 1, MarkShape.SQUARE, MarkTone.TINT),
        ImpartMarkCell(7, 7, MarkShape.CIRCLE, MarkTone.BASE)
    )

    // What actually gets drawn: the table, with the center cell removed even if a future edit
    // put one there. ImpartMark draws only this list.
    fun drawable(): List<ImpartMarkCell> = cells.filterNot { it.row == CENTER && it.col == CENTER }
}
