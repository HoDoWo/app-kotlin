package com.hodowo.data

/**
 * Cart actions used by the Product Detail screen (issue #140).
 *
 * The real cart page and its state (#142) do not exist yet: this is the
 * integration seam that screen will replace with real cart state/API calls.
 */
interface CartRepository {
    suspend fun addToCart(productId: String, quantity: Int = 1): Boolean
    suspend fun buyNow(productId: String): Boolean
}

/** In-memory [CartRepository], used until #142 adds the real cart. */
class InMemoryCartRepository : CartRepository {
    private val quantities = mutableMapOf<String, Int>()

    override suspend fun addToCart(productId: String, quantity: Int): Boolean {
        quantities[productId] = (quantities[productId] ?: 0) + quantity
        return true
    }

    override suspend fun buyNow(productId: String): Boolean = true
}
