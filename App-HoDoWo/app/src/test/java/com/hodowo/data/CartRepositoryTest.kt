package com.hodowo.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/** [InMemoryCartRepository], the stand-in used until the real cart (#142) exists. */
class CartRepositoryTest {

    private val repository = InMemoryCartRepository()

    @Test
    fun addingToCartSucceeds() = runTest {
        assertTrue(repository.addToCart("p1"))
    }

    @Test
    fun addingTheSameProductTwiceAccumulatesTheQuantity() = runTest {
        repository.addToCart("p1", quantity = 2)

        assertTrue(repository.addToCart("p1", quantity = 3))
    }

    @Test
    fun buyingNowSucceeds() = runTest {
        assertTrue(repository.buyNow("p1"))
    }
}
