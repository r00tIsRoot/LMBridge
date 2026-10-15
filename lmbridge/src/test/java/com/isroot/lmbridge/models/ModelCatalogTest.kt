package com.isroot.lmbridge.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    @Test
    fun everyEntry_isPinnedToFullRevisionWithIntegrity() {
        ModelCatalog.ALL.forEach { info ->
            assertTrue("${info.modelId}: commitHash must be a full 40-hex sha", info.commitHash.matches(Regex("[0-9a-f]{40}")))
            assertTrue("${info.modelId}: sizeInBytes must be positive", info.sizeInBytes > 0)
            assertTrue(
                "${info.modelId}: sha256 must be 64-hex",
                info.sha256?.matches(Regex("[0-9a-f]{64}")) == true,
            )
            assertTrue("${info.modelId}: modelFile must be .litertlm", info.modelFile.endsWith(".litertlm"))
        }
    }

    @Test
    fun modelIds_areUnique() {
        val ids = ModelCatalog.ALL.map { it.modelId }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun dirNames_areUnique() {
        val dirs = ModelCatalog.ALL.map { it.toDirName() }
        assertEquals(dirs.size, dirs.toSet().size)
    }
}
