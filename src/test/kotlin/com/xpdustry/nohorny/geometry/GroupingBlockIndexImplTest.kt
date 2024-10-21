/*
 * This file is part of NoHorny. The plugin securing your server against nsfw builds.
 *
 * MIT License
 *
 * Copyright (c) 2024 Xpdustry
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.xpdustry.nohorny.geometry

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class GroupingBlockIndexImplTest {
    @Test
    fun `test blocks that share a side`() {
        val index = createIndex()
        index.upsert(0, 0, 1, Unit)
        index.upsert(1, 0, 1, Unit)
        Assertions.assertEquals(1, index.groups().size)

        val cluster = index.groups().toList()[0]
        Assertions.assertEquals(2, cluster.blocks.size)
        Assertions.assertEquals(0, cluster.x)
        Assertions.assertEquals(0, cluster.y)
        Assertions.assertEquals(2, cluster.w)
        Assertions.assertEquals(1, cluster.h)
    }

    @Test
    fun `test blocks that do not share a side`() {
        val index = createIndex()
        index.upsert(2, 2, 2, Unit)
        index.upsert(-2, 0, 1, Unit)
        index.upsert(10, 10, 10, Unit)
        Assertions.assertEquals(3, index.groups().size)
    }

    @Test
    fun `test blocks that partially share a side`() {
        val index = createIndex()
        index.upsert(1, 1, 2, Unit)
        index.upsert(3, 2, 2, Unit)
        Assertions.assertEquals(1, index.groups().size)
    }

    @Test
    fun `test blocks that only share a corner`() {
        val index = createIndex()
        index.upsert(0, 0, 1, Unit)
        index.upsert(1, 1, 1, Unit)
        Assertions.assertEquals(2, index.groups().size)
    }

    @Test
    fun `test block remove`() {
        val index = createIndex()
        for (x in 0..2) {
            for (y in 0..5) {
                index.upsert(x, y, 1, Unit)
            }
        }

        Assertions.assertEquals(1, index.groups().size)
        Assertions.assertEquals(18, index.groups().toList()[0].blocks.size)

        index.remove(0, 1)
        index.remove(1, 1)

        Assertions.assertEquals(1, index.groups().size)
        Assertions.assertEquals(16, index.groups().toList()[0].blocks.size)
    }

    @Test
    fun `test block remove from within`() {
        val index = createIndex()
        for (x in 0..4) {
            for (y in 0..4) {
                index.upsert(x, y, 1, Unit)
            }
        }

        Assertions.assertEquals(1, index.groups().size)
        Assertions.assertEquals(25, index.groups().toList()[0].blocks.size)

        // Removes a U shape inside the 5 by 5 square
        for (x in 1..3) {
            for (y in 1..3) {
                if (x == 1 && (y == 1 || y == 2)) continue
                index.remove(x, y)
            }
        }

        Assertions.assertEquals(1, index.groups().size)
        Assertions.assertEquals(18, index.groups().toList()[0].blocks.size)
    }

    @Test
    fun `test cluster split`() {
        val index = createIndex()
        for (x in 0..2) {
            index.upsert(x, 0, 1, Unit)
        }
        index.upsert(1, 1, 1, Unit)
        Assertions.assertEquals(1, index.groups().size)
        index.remove(1, 0)
        Assertions.assertEquals(3, index.groups().size)
    }

    @Test
    fun `test cluster merge`() {
        val index = createIndex()
        for (y in 0..2) {
            for (x in 0..2) {
                index.upsert(x, y * 2, 1, Unit)
            }
        }
        Assertions.assertEquals(3, index.groups().size)
        index.upsert(1, 1, 1, Unit)
        Assertions.assertEquals(2, index.groups().size)
        index.upsert(1, 3, 1, Unit)
        Assertions.assertEquals(1, index.groups().size)
    }

    /*
    @Test
    fun `test error on add to occupied`() {
        val index = createIndex()
        index.upsert(0, 0, 1, Unit)
        assertThrows<IllegalStateException> { index.upsert(createBlock(0, 0, 1)) }
    }

     */

    @Test
    fun `test cluster on same axis spaced by 1`() {
        val index = createIndex()
        index.upsert(0, 0, 6, Unit)
        index.upsert(7, 0, 6, Unit)
        index.upsert(0, 7, 6, Unit)
        index.upsert(7, 7, 6, Unit)
        Assertions.assertEquals(4, index.groups().size)
    }

    private fun createIndex() = GroupingBlockIndex.create<Unit>()
}
