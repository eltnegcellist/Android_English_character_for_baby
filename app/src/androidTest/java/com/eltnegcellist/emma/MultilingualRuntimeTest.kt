package com.eltnegcellist.emma

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

/** Opt-in device check: downloads ~387 MB of pinned public models on first run. */
@RunWith(AndroidJUnit4::class)
class MultilingualRuntimeTest {
    @Test fun realWebViewWorkerLoadsAndClassifiesWithoutCloudInference() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("multilingualModels")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val runtime=MultilingualRuntime.get(context)
        runtime.prepare().getOrThrow()
        assertTrue(runtime.ready)
        val prediction=runtime.request("predict",JSONObject().put("text","The baby is drinking milk."))
        assertEquals("e5",prediction.getString("source"))
        assertEquals("milk",prediction.getJSONObject("topic").getString("id"))
        runtime.release()
        runtime.prepare().getOrThrow()
        assertTrue(runtime.ready)
    }
}
