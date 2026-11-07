package io.github.lazy.dsh.feature.conversation

import io.github.lazy.dsh.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolPresentationTest {

    /** The generated picture is the result, so the row needs its own title, act and inline preview. */
    @Test fun imageGenerationHasItsOwnRow() {
        val info = ToolPresentation.info("image_generate")
        assertEquals(R.string.tool_image_generate, info.title)
        assertEquals(Activity.Image, info.activity)
        assertEquals(BodyKind.Image, info.body)
    }
}
