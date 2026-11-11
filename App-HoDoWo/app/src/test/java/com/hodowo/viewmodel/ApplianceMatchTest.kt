package com.hodowo.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The appliance of the chat is not shown twice in the profile when it is already registered. */
class ApplianceMatchTest {

    private fun chat(name: String, brand: String? = null, model: String? = null) =
        Appliance(name = name, brand = brand, model = model, confidence = 0.9f)

    @Test
    fun theSameWordsInAnyCaseAndOrderAreTheSameAppliance() {
        val saved = UserAppliance(name = "Candy FIDC X502IT", brand = "Candy")

        assertTrue(chat("Candy FIDC X502IT", brand = "candy").isSameAs(saved))
        assertTrue(chat("Espresso machine").isSameAs(UserAppliance(name = "Espresso machine")))
    }

    @Test
    fun theSameModelNumberIsEnough() {
        val saved = UserAppliance(name = "Dishwasher", brand = "Candy", model = "FIDC X502IT")

        assertTrue(chat("Candy dishwasher", brand = "Candy", model = "fidc-x502it").isSameAs(saved))
    }

    @Test
    fun differentAppliancesStaySeparate() {
        assertFalse(chat("Oven").isSameAs(UserAppliance(name = "Indesit Oven")))
        assertFalse(chat("Oven", brand = "Bosch", model = "HBA1").isSameAs(UserAppliance(name = "Oven", brand = "Bosch", model = "HBA2")))
        assertFalse(chat("Washing machine").isSameAs(UserAppliance(name = "Dishwasher")))
    }
}