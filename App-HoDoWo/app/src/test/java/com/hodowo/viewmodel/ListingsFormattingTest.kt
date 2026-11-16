package com.hodowo.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class ListingsFormattingTest {
    @Test fun wholeNumberGetsTwoDecimals() = assertEquals("$70.00", formatPriceLabel("70"))
    @Test fun existingLabelIsKept() = assertEquals("$85.00", formatPriceLabel("$85.00"))
    @Test fun commaDecimalIsAccepted() = assertEquals("$12.50", formatPriceLabel("12,5"))
    @Test fun extraDecimalsAreRounded() = assertEquals("$9.99", formatPriceLabel("9.987"))
    @Test fun blankBecomesZero() = assertEquals("$0.00", formatPriceLabel("  "))
    @Test fun nonNumericIsKeptAsTyped() = assertEquals("\$free", formatPriceLabel("free"))

    @Test fun singularInquiry() = assertEquals("1 inquiry", inquiryLabel(1))
    @Test fun pluralInquiries() = assertEquals("2 inquiries", inquiryLabel(2))
    @Test fun zeroInquiries() = assertEquals("0 inquiries", inquiryLabel(0))
}
