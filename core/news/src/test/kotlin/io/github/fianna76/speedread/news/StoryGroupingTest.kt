package io.github.fianna76.speedread.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryGroupingTest {

    // Pairs taken from the live feeds of the built-in sources.
    @Test
    fun `headlines about the same event match`() {
        assertTrue(
            StoryGrouping.isSameStory(
                "Pope discharged from hospital days after lung cancer surgery",
                "Pope Leo leaves hospital after cancer surgery",
            ),
        )
        assertTrue(
            StoryGrouping.isSameStory(
                "Zelenskyy urges support for new US sanctions on Russia after Trump puts them on hold",
                "Zelenskyy urges support for new U.S. sanctions on Russia hours after Trump paused them",
            ),
        )
        assertTrue(StoryGrouping.isSameStory("Hurricane Trudy makes its way towards Mexico", "Mexico braces as Hurricane Trudy nears coast"))
    }

    @Test
    fun `stories that only share a name or a topic do not match`() {
        assertFalse(
            StoryGrouping.isSameStory(
                "Trump pauses sanctions package targeted at Russian economy",
                "Trump says he will meet Xi in Beijing next month",
            ),
        )
        assertFalse(StoryGrouping.isSameStory("Ukraine war: Russia strikes energy grid", "Ukraine wins Eurovision"))
        assertFalse(StoryGrouping.isSameStory("Budget 2027: what it means for you", "Budget 2027: what it means for renters"))
    }

    @Test
    fun `words are compared without case, accents, plurals or possessives`() {
        assertEquals(
            setOf("taoiseach", "cafe", "protester", "dublin"),
            StoryGrouping.significantWords("Taoiseach's café: protesters in Dublin"),
        )
    }

    @Test
    fun `groups keep the lead's position and never merge one outlet's stories`() {
        data class H(val outlet: String, val title: String)
        val items = listOf(
            H("rte", "Pope Leo leaves hospital after cancer surgery"),
            H("bbc", "Storm Amy brings flooding to Galway"),
            H("dw", "Pope Leo discharged from hospital after cancer surgery"),
            H("rte", "Pope leaves Rome hospital after cancer surgery, says Vatican"),
            H("npr", "Pope Leo XIV leaves hospital four days after cancer surgery"),
        )

        val groups = StoryGrouping.group(items, title = { it.title }, outlet = { it.outlet })

        assertEquals(listOf("rte", "bbc", "rte"), groups.map { it.lead.outlet })
        assertEquals(listOf("dw", "npr"), groups[0].related.map { it.outlet })
        assertTrue(groups[1].related.isEmpty())
        assertTrue(groups[2].related.isEmpty())
    }
}
