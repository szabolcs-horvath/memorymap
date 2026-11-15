package com.szabolcshorvath.memorymap.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlaceNameTest {

    @Test
    fun `a name made from the street and number is left out next to the address that says it`() {
        assertNull(PlaceName.besideAddress("Dér u. 42", "Budapest, Dér u. 42, 1104"))
    }

    @Test
    fun `the street and number in another order still count as said`() {
        assertNull(PlaceName.besideAddress("Amphitheatre Pkwy 1600", "1600 Amphitheatre Pkwy, Mountain View, CA 94043, USA"))
    }

    @Test
    fun `case and punctuation don't matter`() {
        assertNull(PlaceName.besideAddress("dér u 42", "Budapest, Dér u. 42, 1104"))
    }

    @Test
    fun `a place's own name is kept`() {
        val name = "Fővárosi Állat- és Növénykert"
        assertEquals(name, PlaceName.besideAddress(name, "Budapest, Állatkerti krt. 6-12, 1146"))
    }

    @Test
    fun `a name is kept when its words are spread over several parts of the address`() {
        assertEquals("Budapest 1104", PlaceName.besideAddress("Budapest 1104", "Budapest, Dér u. 42, 1104"))
    }

    @Test
    fun `part of a word is not the word`() {
        assertEquals("Bar", PlaceName.besideAddress("Bar", "Budapest, Barcsay u. 5, 1073"))
    }

    @Test
    fun `a name without an address is kept`() {
        assertEquals("Dér u. 42", PlaceName.besideAddress("Dér u. 42", null))
    }

    @Test
    fun `no name gives none`() {
        assertNull(PlaceName.besideAddress(null, "Budapest, Dér u. 42, 1104"))
        assertNull(PlaceName.besideAddress(" ", "Budapest, Dér u. 42, 1104"))
    }
}
