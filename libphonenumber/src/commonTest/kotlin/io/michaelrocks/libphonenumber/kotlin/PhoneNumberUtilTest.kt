/*
 * Copyright (C) 2009 The Libphonenumber Authors
 * Copyright (C) 2022 Michael Rozumyanskiy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.michaelrocks.libphonenumber.kotlin

import io.michaelrocks.libphonenumber.kotlin.CountryCodeToRegionCodeMapForTesting.countryCodeToRegionCodeMap
import io.michaelrocks.libphonenumber.kotlin.PhoneNumberUtil.*
import io.michaelrocks.libphonenumber.kotlin.PhoneNumberUtil.Companion.extractPossibleNumber
import io.michaelrocks.libphonenumber.kotlin.PhoneNumberUtil.Companion.getCountryMobileToken
import io.michaelrocks.libphonenumber.kotlin.PhoneNumberUtil.Companion.normalizeDiallableCharsOnly
import io.michaelrocks.libphonenumber.kotlin.PhoneNumberUtil.Companion.normalizeDigitsOnly
import io.michaelrocks.libphonenumber.kotlin.Phonemetadata.PhoneMetadata
import io.michaelrocks.libphonenumber.kotlin.Phonenumber.PhoneNumber
import io.michaelrocks.libphonenumber.kotlin.Phonenumber.PhoneNumber.CountryCodeSource
import io.michaelrocks.libphonenumber.kotlin.metadata.DefaultMetadataDependenciesProvider
import io.michaelrocks.libphonenumber.kotlin.metadata.defaultMetadataLoader
import io.michaelrocks.libphonenumber.kotlin.metadata.source.MetadataSource
import io.michaelrocks.libphonenumber.kotlin.util.InplaceStringBuilder
import io.michaelrocks.libphonenumber.kotlin.utils.RegionCode
import io.michaelrocks.libphonenumber.kotlin.utils.assertThrows
import org.kodein.mock.Mock
import org.kodein.mock.Mocker
import kotlin.test.*

/**
 * Unit tests for PhoneNumberUtil.java
 *
 * Note that these tests use the test metadata, not the normal metadata file, so should not be used
 * for regression test purposes - these tests are illustrative only and test functionality.
 *
 * @author Shaopeng Jia
 */
class PhoneNumberUtilTest : TestMetadataTestCase() {

    override val metadataLoader: MetadataLoader
        get() = defaultMetadataLoader()

    @Mock
    lateinit var mockedMetadataSource: MetadataSource

    lateinit var phoneNumberUtilWithMissingMetadata: PhoneNumberUtil

    val mocker = Mocker()

    @BeforeTest
    fun setUp() {
        mocker.reset() //(1)
        this.injectMocks(mocker) //(2)
        phoneNumberUtilWithMissingMetadata = PhoneNumberUtil(
            mockedMetadataSource, DefaultMetadataDependenciesProvider(metadataLoader), countryCodeToRegionCodeMap
        )
    }

    @Test
    fun testGetSupportedRegions() {
        assertTrue(phoneUtil.getSupportedRegions().size > 0)
    }

    @Test
    fun testGetSupportedGlobalNetworkCallingCodes() {
        val globalNetworkCallingCodes = phoneUtil.supportedGlobalNetworkCallingCodes
        assertTrue(globalNetworkCallingCodes.size > 0)
        for (callingCode in globalNetworkCallingCodes) {
            assertTrue(callingCode > 0)
            assertEquals(RegionCode.UN001, phoneUtil.getRegionCodeForCountryCode(callingCode))
        }
    }

    @Test
    fun testGetSupportedCallingCodes() {
        val callingCodes = phoneUtil.supportedCallingCodes
        assertTrue(callingCodes.size > 0)
        for (callingCode in callingCodes) {
            assertTrue(callingCode > 0)
            assertTrue(phoneUtil.getRegionCodeForCountryCode(callingCode) !== RegionCode.ZZ)
        }
        // There should be more than just the global network calling codes in this set.
        assertTrue(callingCodes.size > phoneUtil.supportedGlobalNetworkCallingCodes.size)
        // But they should be included. Testing one of them.
        assertTrue(callingCodes.contains(979))
    }

    @Test
    fun testGetInstanceLoadBadMetadata() {
        assertNull(phoneUtil.getMetadataForRegion("No Such Region"))
        assertNull(phoneUtil.getMetadataForNonGeographicalRegion(-1))
    }

    @Test
    fun testGetSupportedTypesForRegion() {
        assertTrue(
            phoneUtil.getSupportedTypesForRegion(RegionCode.BR).contains(PhoneNumberType.FIXED_LINE)
        )
        // Our test data has no mobile numbers for Brazil.
        assertFalse(
            phoneUtil.getSupportedTypesForRegion(RegionCode.BR).contains(PhoneNumberType.MOBILE)
        )
        // UNKNOWN should never be returned.
        assertFalse(
            phoneUtil.getSupportedTypesForRegion(RegionCode.BR).contains(PhoneNumberType.UNKNOWN)
        )
        // In the US, many numbers are classified as FIXED_LINE_OR_MOBILE; but we don't want to expose
        // this as a supported type, instead we say FIXED_LINE and MOBILE are both present.
        assertTrue(
            phoneUtil.getSupportedTypesForRegion(RegionCode.US).contains(PhoneNumberType.FIXED_LINE)
        )
        assertTrue(
            phoneUtil.getSupportedTypesForRegion(RegionCode.US).contains(PhoneNumberType.MOBILE)
        )
        assertFalse(
            phoneUtil.getSupportedTypesForRegion(RegionCode.US).contains(PhoneNumberType.FIXED_LINE_OR_MOBILE)
        )

        // Test the invalid region code.
        assertEquals(0, phoneUtil.getSupportedTypesForRegion(RegionCode.ZZ).size)
    }

    @Test
    fun testGetSupportedTypesForNonGeoEntity() {
        // No data exists for 999 at all, no types should be returned.
        assertEquals(0, phoneUtil.getSupportedTypesForNonGeoEntity(999).size)
        val typesFor979 = phoneUtil.getSupportedTypesForNonGeoEntity(979)
        assertTrue(typesFor979.contains(PhoneNumberType.PREMIUM_RATE))
        assertFalse(typesFor979.contains(PhoneNumberType.MOBILE))
        assertFalse(typesFor979.contains(PhoneNumberType.UNKNOWN))
    }

    @Test
    fun testGetInstanceLoadUSMetadata() {
        val metadata = phoneUtil.getMetadataForRegion(RegionCode.US)
        assertEquals("US", metadata!!.id)
        assertEquals(1, metadata.countryCode)
        assertEquals("011", metadata.internationalPrefix)
        assertTrue(metadata.hasNationalPrefix())
        assertEquals(2, metadata.numberFormatCount)
        assertEquals(
            "(\\d{3})(\\d{3})(\\d{4})", metadata.getNumberFormat(1).pattern
        )
        assertEquals("$1 $2 $3", metadata.getNumberFormat(1).format)
        assertEquals(
            "[13-689]\\d{9}|2[0-35-9]\\d{8}", metadata.generalDesc!!.nationalNumberPattern
        )
        assertEquals(
            "[13-689]\\d{9}|2[0-35-9]\\d{8}", metadata.fixedLine!!.nationalNumberPattern
        )
        assertEquals(1, metadata.generalDesc!!.possibleLengthCount)
        assertEquals(10, metadata.generalDesc!!.getPossibleLength(0))
        // Possible lengths are the same as the general description, so aren't stored separately in the
        // toll free element as well.
        assertEquals(0, metadata.tollFree!!.possibleLengthCount)
        assertEquals("900\\d{7}", metadata.premiumRate!!.nationalNumberPattern)
        // No shared-cost data is available, so its national number data should not be set.
        assertFalse(metadata.sharedCost!!.hasNationalNumberPattern())
    }

    @Test
    fun testGetInstanceLoadDEMetadata() {
        val metadata = phoneUtil.getMetadataForRegion(RegionCode.DE)
        assertEquals("DE", metadata!!.id)
        assertEquals(49, metadata.countryCode)
        assertEquals("00", metadata.internationalPrefix)
        assertEquals("0", metadata.nationalPrefix)
        assertEquals(6, metadata.numberFormatCount)
        assertEquals(1, metadata.getNumberFormat(5).leadingDigitsPatternCount)
        assertEquals("900", metadata.getNumberFormat(5).getLeadingDigitsPattern(0))
        assertEquals(
            "(\\d{3})(\\d{3,4})(\\d{4})", metadata.getNumberFormat(5).pattern
        )
        assertEquals("$1 $2 $3", metadata.getNumberFormat(5).format)
        assertEquals(2, metadata.generalDesc!!.possibleLengthLocalOnlyCount)
        assertEquals(8, metadata.generalDesc!!.possibleLengthCount)
        // Nothing is present for fixed-line, since it is the same as the general desc, so for
        // efficiency reasons we don't store an extra value.
        assertEquals(0, metadata.fixedLine!!.possibleLengthCount)
        assertEquals(2, metadata.mobile!!.possibleLengthCount)
        assertEquals(
            "(?:[24-6]\\d{2}|3[03-9]\\d|[789](?:0[2-9]|[1-9]\\d))\\d{1,8}", metadata.fixedLine!!.nationalNumberPattern
        )
        assertEquals("30123456", metadata.fixedLine!!.exampleNumber)
        assertEquals(10, metadata.tollFree!!.getPossibleLength(0))
        assertEquals("900([135]\\d{6}|9\\d{7})", metadata.premiumRate!!.nationalNumberPattern)
    }

    @Test
    fun testGetInstanceLoadARMetadata() {
        val metadata = phoneUtil.getMetadataForRegion(RegionCode.AR)
        assertEquals("AR", metadata!!.id)
        assertEquals(54, metadata.countryCode)
        assertEquals("00", metadata.internationalPrefix)
        assertEquals("0", metadata.nationalPrefix)
        assertEquals("0(?:(11|343|3715)15)?", metadata.nationalPrefixForParsing)
        assertEquals("9$1", metadata.nationalPrefixTransformRule)
        assertEquals("$2 15 $3-$4", metadata.getNumberFormat(2).format)
        assertEquals(
            "(\\d)(\\d{4})(\\d{2})(\\d{4})", metadata.getNumberFormat(3).pattern
        )
        assertEquals(
            "(\\d)(\\d{4})(\\d{2})(\\d{4})", metadata.getIntlNumberFormat(3).pattern
        )
        assertEquals("$1 $2 $3 $4", metadata.getIntlNumberFormat(3).format)
    }

    @Test
    fun testGetInstanceLoadInternationalTollFreeMetadata() {
        val metadata = phoneUtil.getMetadataForNonGeographicalRegion(800)
        assertEquals("001", metadata!!.id)
        assertEquals(800, metadata.countryCode)
        assertEquals("$1 $2", metadata.getNumberFormat(0).format)
        assertEquals("(\\d{4})(\\d{4})", metadata.getNumberFormat(0).pattern)
        assertEquals(0, metadata.generalDesc!!.possibleLengthLocalOnlyCount)
        assertEquals(1, metadata.generalDesc!!.possibleLengthCount)
        assertEquals("12345678", metadata.tollFree!!.exampleNumber)
    }

    @Test
    fun testIsNumberGeographical() {
        assertFalse(phoneUtil.isNumberGeographical(BS_MOBILE)) // Bahamas, mobile phone number.
        assertTrue(phoneUtil.isNumberGeographical(AU_NUMBER)) // Australian fixed line number.
        assertFalse(phoneUtil.isNumberGeographical(INTERNATIONAL_TOLL_FREE)) // International toll
        // free number.
        // We test that mobile phone numbers in relevant regions are indeed considered geographical.
        assertTrue(phoneUtil.isNumberGeographical(AR_MOBILE)) // Argentina, mobile phone number.
        assertTrue(phoneUtil.isNumberGeographical(MX_MOBILE1)) // Mexico, mobile phone number.
        assertTrue(phoneUtil.isNumberGeographical(MX_MOBILE2)) // Mexico, another mobile phone number.
    }

    @Test
    fun testGetLengthOfGeographicalAreaCode() {
        // Google MTV, which has area code "650".
        assertEquals(3, phoneUtil.getLengthOfGeographicalAreaCode(US_NUMBER))

        // A North America toll-free number, which has no area code.
        assertEquals(0, phoneUtil.getLengthOfGeographicalAreaCode(US_TOLLFREE))

        // Google London, which has area code "20".
        assertEquals(2, phoneUtil.getLengthOfGeographicalAreaCode(GB_NUMBER))

        // A mobile number in the UK does not have an area code (by default, mobile numbers do not,
        // unless they have been added to our list of exceptions).
        assertEquals(0, phoneUtil.getLengthOfGeographicalAreaCode(GB_MOBILE))

        // Google Buenos Aires, which has area code "11".
        assertEquals(2, phoneUtil.getLengthOfGeographicalAreaCode(AR_NUMBER))

        // A mobile number in Argentina also has an area code.
        assertEquals(3, phoneUtil.getLengthOfGeographicalAreaCode(AR_MOBILE))

        // Google Sydney, which has area code "2".
        assertEquals(1, phoneUtil.getLengthOfGeographicalAreaCode(AU_NUMBER))

        // Italian numbers - there is no national prefix, but it still has an area code.
        assertEquals(2, phoneUtil.getLengthOfGeographicalAreaCode(IT_NUMBER))

        // Google Singapore. Singapore has no area code and no national prefix.
        assertEquals(0, phoneUtil.getLengthOfGeographicalAreaCode(SG_NUMBER))

        // An invalid US number (1 digit shorter), which has no area code.
        assertEquals(0, phoneUtil.getLengthOfGeographicalAreaCode(US_SHORT_BY_ONE_NUMBER))

        // An international toll free number, which has no area code.
        assertEquals(0, phoneUtil.getLengthOfGeographicalAreaCode(INTERNATIONAL_TOLL_FREE))

        // A mobile number from China is geographical, but does not have an area code.
        val cnMobile = PhoneNumber().setCountryCode(86).setNationalNumber(18912341234L)
        assertEquals(0, phoneUtil.getLengthOfGeographicalAreaCode(cnMobile))
    }

    @Test
    fun testGetLengthOfNationalDestinationCode() {
        // Google MTV, which has national destination code (NDC) "650".
        assertEquals(3, phoneUtil.getLengthOfNationalDestinationCode(US_NUMBER))

        // A North America toll-free number, which has NDC "800".
        assertEquals(3, phoneUtil.getLengthOfNationalDestinationCode(US_TOLLFREE))

        // Google London, which has NDC "20".
        assertEquals(2, phoneUtil.getLengthOfNationalDestinationCode(GB_NUMBER))

        // A UK mobile phone, which has NDC "7912".
        assertEquals(4, phoneUtil.getLengthOfNationalDestinationCode(GB_MOBILE))

        // Google Buenos Aires, which has NDC "11".
        assertEquals(2, phoneUtil.getLengthOfNationalDestinationCode(AR_NUMBER))

        // An Argentinian mobile which has NDC "911".
        assertEquals(3, phoneUtil.getLengthOfNationalDestinationCode(AR_MOBILE))

        // Google Sydney, which has NDC "2".
        assertEquals(1, phoneUtil.getLengthOfNationalDestinationCode(AU_NUMBER))

        // Google Singapore, which has NDC "6521".
        assertEquals(4, phoneUtil.getLengthOfNationalDestinationCode(SG_NUMBER))

        // An invalid US number (1 digit shorter), which has no NDC.
        assertEquals(0, phoneUtil.getLengthOfNationalDestinationCode(US_SHORT_BY_ONE_NUMBER))

        // A number containing an invalid country calling code, which shouldn't have any NDC.
        val number = PhoneNumber().setCountryCode(123).setNationalNumber(6502530000L)
        assertEquals(0, phoneUtil.getLengthOfNationalDestinationCode(number))

        // An international toll free number, which has NDC "1234".
        assertEquals(4, phoneUtil.getLengthOfNationalDestinationCode(INTERNATIONAL_TOLL_FREE))

        // A mobile number from China is geographical, but does not have an area code: however it still
        // can be considered to have a national destination code.
        val cnMobile = PhoneNumber().setCountryCode(86).setNationalNumber(18912341234L)
        assertEquals(3, phoneUtil.getLengthOfNationalDestinationCode(cnMobile))
    }

    @Test
    fun testGetCountryMobileToken() {
        assertEquals(
            "9", getCountryMobileToken(
                phoneUtil.getCountryCodeForRegion(
                    RegionCode.AR
                )
            )
        )

        // Country calling code for Sweden, which has no mobile token.
        assertEquals(
            "", getCountryMobileToken(
                phoneUtil.getCountryCodeForRegion(
                    RegionCode.SE
                )
            )
        )
    }

    @Test
    fun testGetNationalSignificantNumber() {
        assertEquals("6502530000", phoneUtil.getNationalSignificantNumber(US_NUMBER))

        // An Italian mobile number.
        assertEquals("345678901", phoneUtil.getNationalSignificantNumber(IT_MOBILE))

        // An Italian fixed line number.
        assertEquals("0236618300", phoneUtil.getNationalSignificantNumber(IT_NUMBER))
        assertEquals("12345678", phoneUtil.getNationalSignificantNumber(INTERNATIONAL_TOLL_FREE))
    }

    @Test
    fun testGetNationalSignificantNumber_ManyLeadingZeros() {
        val number = PhoneNumber()
        number.setCountryCode(1)
        number.setNationalNumber(650)
        number.setItalianLeadingZero(true)
        number.setNumberOfLeadingZeros(2)
        assertEquals("00650", phoneUtil.getNationalSignificantNumber(number))

        // Set a bad value; we shouldn't crash, we shouldn't output any leading zeros at all.
        number.setNumberOfLeadingZeros(-3)
        assertEquals("650", phoneUtil.getNationalSignificantNumber(number))
    }

    @Test
    fun testGetExampleNumber() {
        assertEquals(DE_NUMBER, phoneUtil.getExampleNumber(RegionCode.DE))
        assertEquals(
            DE_NUMBER, phoneUtil.getExampleNumberForType(RegionCode.DE, PhoneNumberType.FIXED_LINE)
        )
        // Should return the same response if asked for FIXED_LINE_OR_MOBILE too.
        assertEquals(
            DE_NUMBER, phoneUtil.getExampleNumberForType(RegionCode.DE, PhoneNumberType.FIXED_LINE_OR_MOBILE)
        )
        assertNotNull(phoneUtil.getExampleNumberForType(RegionCode.US, PhoneNumberType.FIXED_LINE))
        assertNotNull(phoneUtil.getExampleNumberForType(RegionCode.US, PhoneNumberType.MOBILE))

        // We have data for the US, but no data for VOICEMAIL, so return null.
        assertNull(phoneUtil.getExampleNumberForType(RegionCode.US, PhoneNumberType.VOICEMAIL))
        // CS is an invalid region, so we have no data for it.
        assertNull(phoneUtil.getExampleNumberForType(RegionCode.CS, PhoneNumberType.MOBILE))
        // RegionCode 001 is reserved for supporting non-geographical country calling code. We don't
        // support getting an example number for it with this method.
        assertNull(phoneUtil.getExampleNumber(RegionCode.UN001))
    }

    @Test
    fun testGetInvalidExampleNumber() {
        // RegionCode 001 is reserved for supporting non-geographical country calling codes. We don't
        // support getting an invalid example number for it with getInvalidExampleNumber.
        assertNull(phoneUtil.getInvalidExampleNumber(RegionCode.UN001))
        assertNull(phoneUtil.getInvalidExampleNumber(RegionCode.CS))
        val usInvalidNumber = phoneUtil.getInvalidExampleNumber(RegionCode.US)
        assertEquals(1, usInvalidNumber!!.countryCode)
        assertFalse(usInvalidNumber.nationalNumber == 0L)
    }

    @Test
    fun testGetExampleNumberForNonGeoEntity() {
        assertEquals(INTERNATIONAL_TOLL_FREE, phoneUtil.getExampleNumberForNonGeoEntity(800))
        assertEquals(UNIVERSAL_PREMIUM_RATE, phoneUtil.getExampleNumberForNonGeoEntity(979))
    }

    @Test
    fun testGetExampleNumberWithoutRegion() {
        // In our test metadata we don't cover all types: in our real metadata, we do.
        assertNotNull(phoneUtil.getExampleNumberForType(PhoneNumberType.FIXED_LINE))
        assertNotNull(phoneUtil.getExampleNumberForType(PhoneNumberType.MOBILE))
        assertNotNull(phoneUtil.getExampleNumberForType(PhoneNumberType.PREMIUM_RATE))
    }

    @Test
    fun testConvertAlphaCharactersInNumber() {
        val input = "1800-ABC-DEF"
        // Alpha chars are converted to digits; everything else is left untouched.
        val expectedOutput = "1800-222-333"
        assertEquals(expectedOutput, PhoneNumberUtil.convertAlphaCharactersInNumber(input))
    }

    @Test
    fun testNormaliseRemovePunctuation() {
        val inputNumber = InplaceStringBuilder("034-56&+#2\u00AD34")
        val expectedOutput = "03456234"
        assertEquals(
            expectedOutput,
            PhoneNumberUtil.normalize(inputNumber).toString(),
            "Conversion did not correctly remove punctuation",
        )
    }

    @Test
    fun testNormaliseReplaceAlphaCharacters() {
        val inputNumber = InplaceStringBuilder("034-I-am-HUNGRY")
        val expectedOutput = "034426486479"
        assertEquals(
            expectedOutput,
            PhoneNumberUtil.normalize(inputNumber).toString(),
            "Conversion did not correctly replace alpha characters",
        )
    }

    @Test
    fun testNormaliseOtherDigits() {
        var inputNumber = InplaceStringBuilder("\uFF125\u0665")
        var expectedOutput = "255"
        assertEquals(
            expectedOutput,
            PhoneNumberUtil.normalize(inputNumber).toString(),
            "Conversion did not correctly replace non-latin digits",
        )
        // Eastern-Arabic digits.
        inputNumber = InplaceStringBuilder("\u06F52\u06F0")
        expectedOutput = "520"
        assertEquals(
            expectedOutput,
            PhoneNumberUtil.normalize(inputNumber).toString(),
            "Conversion did not correctly replace non-latin digits",
        )
    }

    @Test
    fun testNormaliseStripAlphaCharacters() {
        val inputNumber = "034-56&+a#234"
        val expectedOutput = "03456234"
        assertEquals(
            expectedOutput, normalizeDigitsOnly(inputNumber), "Conversion did not correctly remove alpha character",
        )
    }

    @Test
    fun testNormaliseStripNonDiallableCharacters() {
        val inputNumber = "03*4-56&+1a#234"
        val expectedOutput = "03*456+1#234"
        assertEquals(
            expectedOutput,
            normalizeDiallableCharsOnly(inputNumber),
            "Conversion did not correctly remove non-diallable characters",
        )
    }

    @Test
    fun testFormatUSNumber() {
        assertEquals("650 253 0000", phoneUtil.format(US_NUMBER, PhoneNumberFormat.NATIONAL))
        assertEquals("+1 650 253 0000", phoneUtil.format(US_NUMBER, PhoneNumberFormat.INTERNATIONAL))
        assertEquals("800 253 0000", phoneUtil.format(US_TOLLFREE, PhoneNumberFormat.NATIONAL))
        assertEquals("+1 800 253 0000", phoneUtil.format(US_TOLLFREE, PhoneNumberFormat.INTERNATIONAL))
        assertEquals("900 253 0000", phoneUtil.format(US_PREMIUM, PhoneNumberFormat.NATIONAL))
        assertEquals("+1 900 253 0000", phoneUtil.format(US_PREMIUM, PhoneNumberFormat.INTERNATIONAL))
        assertEquals("tel:+1-900-253-0000", phoneUtil.format(US_PREMIUM, PhoneNumberFormat.RFC3966))
        // Numbers with all zeros in the national number part will be formatted by using the raw_input
        // if that is available no matter which format is specified.
        assertEquals(
            "000-000-0000", phoneUtil.format(US_SPOOF_WITH_RAW_INPUT, PhoneNumberFormat.NATIONAL)
        )
        assertEquals("0", phoneUtil.format(US_SPOOF, PhoneNumberFormat.NATIONAL))
    }

    @Test
    fun testFormatBSNumber() {
        assertEquals("242 365 1234", phoneUtil.format(BS_NUMBER, PhoneNumberFormat.NATIONAL))
        assertEquals("+1 242 365 1234", phoneUtil.format(BS_NUMBER, PhoneNumberFormat.INTERNATIONAL))
    }

    @Test
    fun testFormatGBNumber() {
        assertEquals("(020) 7031 3000", phoneUtil.format(GB_NUMBER, PhoneNumberFormat.NATIONAL))
        assertEquals("+44 20 7031 3000", phoneUtil.format(GB_NUMBER, PhoneNumberFormat.INTERNATIONAL))
        assertEquals("(07912) 345 678", phoneUtil.format(GB_MOBILE, PhoneNumberFormat.NATIONAL))
        assertEquals("+44 7912 345 678", phoneUtil.format(GB_MOBILE, PhoneNumberFormat.INTERNATIONAL))
    }

    @Test
    fun testFormatDENumber() {
        val deNumber = PhoneNumber()
        deNumber.setCountryCode(49).setNationalNumber(301234L)
        assertEquals("030/1234", phoneUtil.format(deNumber, PhoneNumberFormat.NATIONAL))
        assertEquals("+49 30/1234", phoneUtil.format(deNumber, PhoneNumberFormat.INTERNATIONAL))
        assertEquals("tel:+49-30-1234", phoneUtil.format(deNumber, PhoneNumberFormat.RFC3966))
        deNumber.clear()
        deNumber.setCountryCode(49).setNationalNumber(291123L)
        assertEquals("0291 123", phoneUtil.format(deNumber, PhoneNumberFormat.NATIONAL))
        assertEquals("+49 291 123", phoneUtil.format(deNumber, PhoneNumberFormat.INTERNATIONAL))
        deNumber.clear()
        deNumber.setCountryCode(49).setNationalNumber(29112345678L)
        assertEquals("0291 12345678", phoneUtil.format(deNumber, PhoneNumberFormat.NATIONAL))
        assertEquals("+49 291 12345678", phoneUtil.format(deNumber, PhoneNumberFormat.INTERNATIONAL))
        deNumber.clear()
        deNumber.setCountryCode(49).setNationalNumber(912312345L)
        assertEquals("09123 12345", phoneUtil.format(deNumber, PhoneNumberFormat.NATIONAL))
        assertEquals("+49 9123 12345", phoneUtil.format(deNumber, PhoneNumberFormat.INTERNATIONAL))
        deNumber.clear()
        deNumber.setCountryCode(49).setNationalNumber(80212345L)
        assertEquals("08021 2345", phoneUtil.format(deNumber, PhoneNumberFormat.NATIONAL))
        assertEquals("+49 8021 2345", phoneUtil.format(deNumber, PhoneNumberFormat.INTERNATIONAL))
        // Note this number is correctly formatted without national prefix. Most of the numbers that
        // are treated as invalid numbers by the library are short numbers, and they are usually not
        // dialed with national prefix.
        assertEquals("1234", phoneUtil.format(DE_SHORT_NUMBER, PhoneNumberFormat.NATIONAL))
        assertEquals("+49 1234", phoneUtil.format(DE_SHORT_NUMBER, PhoneNumberFormat.INTERNATIONAL))
        deNumber.clear()
        deNumber.setCountryCode(49).setNationalNumber(41341234)
        assertEquals("04134 1234", phoneUtil.format(deNumber, PhoneNumberFormat.NATIONAL))
    }

    @Test
    fun testFormatITNumber() {
        assertEquals("02 3661 8300", phoneUtil.format(IT_NUMBER, PhoneNumberFormat.NATIONAL))
        assertEquals("+39 02 3661 8300", phoneUtil.format(IT_NUMBER, PhoneNumberFormat.INTERNATIONAL))
        assertEquals("+390236618300", phoneUtil.format(IT_NUMBER, PhoneNumberFormat.E164))
        assertEquals("345 678 901", phoneUtil.format(IT_MOBILE, PhoneNumberFormat.NATIONAL))
        assertEquals("+39 345 678 901", phoneUtil.format(IT_MOBILE, PhoneNumberFormat.INTERNATIONAL))
        assertEquals("+39345678901", phoneUtil.format(IT_MOBILE, PhoneNumberFormat.E164))
    }

    @Test
    fun testFormatAUNumber() {
        assertEquals("02 3661 8300", phoneUtil.format(AU_NUMBER, PhoneNumberFormat.NATIONAL))
        assertEquals("+61 2 3661 8300", phoneUtil.format(AU_NUMBER, PhoneNumberFormat.INTERNATIONAL))
        assertEquals("+61236618300", phoneUtil.format(AU_NUMBER, PhoneNumberFormat.E164))
        val auNumber = PhoneNumber().setCountryCode(61).setNationalNumber(1800123456L)
        assertEquals("1800 123 456", phoneUtil.format(auNumber, PhoneNumberFormat.NATIONAL))
        assertEquals("+61 1800 123 456", phoneUtil.format(auNumber, PhoneNumberFormat.INTERNATIONAL))
        assertEquals("+611800123456", phoneUtil.format(auNumber, PhoneNumberFormat.E164))
    }

    @Test
    fun testFormatARNumber() {
        assertEquals("011 8765-4321", phoneUtil.format(AR_NUMBER, PhoneNumberFormat.NATIONAL))
        assertEquals("+54 11 8765-4321", phoneUtil.format(AR_NUMBER, PhoneNumberFormat.INTERNATIONAL))
        assertEquals("+541187654321", phoneUtil.format(AR_NUMBER, PhoneNumberFormat.E164))
        assertEquals("011 15 8765-4321", phoneUtil.format(AR_MOBILE, PhoneNumberFormat.NATIONAL))
        assertEquals(
            "+54 9 11 8765 4321", phoneUtil.format(
                AR_MOBILE, PhoneNumberFormat.INTERNATIONAL
            )
        )
        assertEquals("+5491187654321", phoneUtil.format(AR_MOBILE, PhoneNumberFormat.E164))
    }

    @Test
    fun testFormatMXNumber() {
        assertEquals("045 234 567 8900", phoneUtil.format(MX_MOBILE1, PhoneNumberFormat.NATIONAL))
        assertEquals(
            "+52 1 234 567 8900", phoneUtil.format(
                MX_MOBILE1, PhoneNumberFormat.INTERNATIONAL
            )
        )
        assertEquals("+5212345678900", phoneUtil.format(MX_MOBILE1, PhoneNumberFormat.E164))
        assertEquals("045 55 1234 5678", phoneUtil.format(MX_MOBILE2, PhoneNumberFormat.NATIONAL))
        assertEquals(
            "+52 1 55 1234 5678", phoneUtil.format(
                MX_MOBILE2, PhoneNumberFormat.INTERNATIONAL
            )
        )
        assertEquals("+5215512345678", phoneUtil.format(MX_MOBILE2, PhoneNumberFormat.E164))
        assertEquals("01 33 1234 5678", phoneUtil.format(MX_NUMBER1, PhoneNumberFormat.NATIONAL))
        assertEquals("+52 33 1234 5678", phoneUtil.format(MX_NUMBER1, PhoneNumberFormat.INTERNATIONAL))
        assertEquals("+523312345678", phoneUtil.format(MX_NUMBER1, PhoneNumberFormat.E164))
        assertEquals("01 821 123 4567", phoneUtil.format(MX_NUMBER2, PhoneNumberFormat.NATIONAL))
        assertEquals("+52 821 123 4567", phoneUtil.format(MX_NUMBER2, PhoneNumberFormat.INTERNATIONAL))
        assertEquals("+528211234567", phoneUtil.format(MX_NUMBER2, PhoneNumberFormat.E164))
    }

    @Test
    fun testFormatOutOfCountryCallingNumber() {
        assertEquals(
            "00 1 900 253 0000", phoneUtil.formatOutOfCountryCallingNumber(US_PREMIUM, RegionCode.DE)
        )
        assertEquals(
            "1 650 253 0000", phoneUtil.formatOutOfCountryCallingNumber(US_NUMBER, RegionCode.BS)
        )
        assertEquals(
            "00 1 650 253 0000", phoneUtil.formatOutOfCountryCallingNumber(US_NUMBER, RegionCode.PL)
        )
        assertEquals(
            "011 44 7912 345 678", phoneUtil.formatOutOfCountryCallingNumber(GB_MOBILE, RegionCode.US)
        )
        assertEquals(
            "00 49 1234", phoneUtil.formatOutOfCountryCallingNumber(DE_SHORT_NUMBER, RegionCode.GB)
        )
        // Note this number is correctly formatted without national prefix. Most of the numbers that
        // are treated as invalid numbers by the library are short numbers, and they are usually not
        // dialed with national prefix.
        assertEquals("1234", phoneUtil.formatOutOfCountryCallingNumber(DE_SHORT_NUMBER, RegionCode.DE))
        assertEquals(
            "011 39 02 3661 8300", phoneUtil.formatOutOfCountryCallingNumber(IT_NUMBER, RegionCode.US)
        )
        assertEquals(
            "02 3661 8300", phoneUtil.formatOutOfCountryCallingNumber(IT_NUMBER, RegionCode.IT)
        )
        assertEquals(
            "+39 02 3661 8300", phoneUtil.formatOutOfCountryCallingNumber(IT_NUMBER, RegionCode.SG)
        )
        assertEquals(
            "6521 8000", phoneUtil.formatOutOfCountryCallingNumber(SG_NUMBER, RegionCode.SG)
        )
        assertEquals(
            "011 54 9 11 8765 4321", phoneUtil.formatOutOfCountryCallingNumber(AR_MOBILE, RegionCode.US)
        )
        assertEquals(
            "011 800 1234 5678", phoneUtil.formatOutOfCountryCallingNumber(INTERNATIONAL_TOLL_FREE, RegionCode.US)
        )
        val arNumberWithExtn = PhoneNumber().mergeFrom(AR_MOBILE).setExtension("1234")
        assertEquals(
            "011 54 9 11 8765 4321 ext. 1234",
            phoneUtil.formatOutOfCountryCallingNumber(arNumberWithExtn, RegionCode.US)
        )
        assertEquals(
            "0011 54 9 11 8765 4321 ext. 1234",
            phoneUtil.formatOutOfCountryCallingNumber(arNumberWithExtn, RegionCode.AU)
        )
        assertEquals(
            "011 15 8765-4321 ext. 1234", phoneUtil.formatOutOfCountryCallingNumber(arNumberWithExtn, RegionCode.AR)
        )
    }

    @Test
    fun testFormatOutOfCountryWithInvalidRegion() {
        // AQ/Antarctica isn't a valid region code for phone number formatting,
        // so this falls back to intl formatting.
        assertEquals(
            "+1 650 253 0000", phoneUtil.formatOutOfCountryCallingNumber(US_NUMBER, RegionCode.AQ)
        )
        // For region code 001, the out-of-country format always turns into the international format.
        assertEquals(
            "+1 650 253 0000", phoneUtil.formatOutOfCountryCallingNumber(US_NUMBER, RegionCode.UN001)
        )
    }

    @Test
    fun testFormatOutOfCountryWithPreferredIntlPrefix() {
        // This should use 0011, since that is the preferred international prefix (both 0011 and 0012
        // are accepted as possible international prefixes in our test metadta.)
        assertEquals(
            "0011 39 02 3661 8300", phoneUtil.formatOutOfCountryCallingNumber(IT_NUMBER, RegionCode.AU)
        )

        // Testing preferred international prefixes with ~ are supported (designates waiting).
        assertEquals(
            "8~10 39 02 3661 8300", phoneUtil.formatOutOfCountryCallingNumber(IT_NUMBER, RegionCode.UZ)
        )
    }

    @Test
    fun testFormatOutOfCountryKeepingAlphaChars() {
        val alphaNumericNumber = PhoneNumber()
        alphaNumericNumber.setCountryCode(1).setNationalNumber(8007493524L).setRawInput("1800 six-flag")
        assertEquals(
            "0011 1 800 SIX-FLAG", phoneUtil.formatOutOfCountryKeepingAlphaChars(alphaNumericNumber, RegionCode.AU)
        )
        alphaNumericNumber.setRawInput("1-800-SIX-flag")
        assertEquals(
            "0011 1 800-SIX-FLAG", phoneUtil.formatOutOfCountryKeepingAlphaChars(alphaNumericNumber, RegionCode.AU)
        )
        alphaNumericNumber.setRawInput("Call us from UK: 00 1 800 SIX-flag")
        assertEquals(
            "0011 1 800 SIX-FLAG", phoneUtil.formatOutOfCountryKeepingAlphaChars(alphaNumericNumber, RegionCode.AU)
        )
        alphaNumericNumber.setRawInput("800 SIX-flag")
        assertEquals(
            "0011 1 800 SIX-FLAG", phoneUtil.formatOutOfCountryKeepingAlphaChars(alphaNumericNumber, RegionCode.AU)
        )

        // Formatting from within the NANPA region.
        assertEquals(
            "1 800 SIX-FLAG", phoneUtil.formatOutOfCountryKeepingAlphaChars(alphaNumericNumber, RegionCode.US)
        )
        assertEquals(
            "1 800 SIX-FLAG", phoneUtil.formatOutOfCountryKeepingAlphaChars(alphaNumericNumber, RegionCode.BS)
        )

        // Testing that if the raw input doesn't exist, it is formatted using
        // formatOutOfCountryCallingNumber.
        alphaNumericNumber.clearRawInput()
        assertEquals(
            "00 1 800 749 3524", phoneUtil.formatOutOfCountryKeepingAlphaChars(alphaNumericNumber, RegionCode.DE)
        )

        // Testing AU alpha number formatted from Australia.
        alphaNumericNumber.setCountryCode(61).setNationalNumber(827493524L).setRawInput("+61 82749-FLAG")
        // This number should have the national prefix fixed.
        assertEquals(
            "082749-FLAG", phoneUtil.formatOutOfCountryKeepingAlphaChars(alphaNumericNumber, RegionCode.AU)
        )
        alphaNumericNumber.setRawInput("082749-FLAG")
        assertEquals(
            "082749-FLAG", phoneUtil.formatOutOfCountryKeepingAlphaChars(alphaNumericNumber, RegionCode.AU)
        )
        alphaNumericNumber.setNationalNumber(18007493524L).setRawInput("1-800-SIX-flag")
        // This number should not have the national prefix prefixed, in accordance with the override for
        // this specific formatting rule.
        assertEquals(
            "1-800-SIX-FLAG", phoneUtil.formatOutOfCountryKeepingAlphaChars(alphaNumericNumber, RegionCode.AU)
        )

        // The metadata should not be permanently changed, since we copied it before modifying patterns.
        // Here we check this.
        alphaNumericNumber.setNationalNumber(1800749352L)
        assertEquals(
            "1800 749 352", phoneUtil.formatOutOfCountryCallingNumber(alphaNumericNumber, RegionCode.AU)
        )

        // Testing a region with multiple international prefixes.
        assertEquals(
            "+61 1-800-SIX-FLAG", phoneUtil.formatOutOfCountryKeepingAlphaChars(alphaNumericNumber, RegionCode.SG)
        )
        // Testing the case of calling from a non-supported region.
        assertEquals(
            "+61 1-800-SIX-FLAG", phoneUtil.formatOutOfCountryKeepingAlphaChars(alphaNumericNumber, RegionCode.AQ)
        )

        // Testing the case with an invalid country calling code.
        alphaNumericNumber.setCountryCode(0).setNationalNumber(18007493524L).setRawInput("1-800-SIX-flag")
        // Uses the raw input only.
        assertEquals(
            "1-800-SIX-flag", phoneUtil.formatOutOfCountryKeepingAlphaChars(alphaNumericNumber, RegionCode.DE)
        )

        // Testing the case of an invalid alpha number.
        alphaNumericNumber.setCountryCode(1).setNationalNumber(80749L).setRawInput("180-SIX")
        // No country-code stripping can be done.
        assertEquals(
            "00 1 180-SIX", phoneUtil.formatOutOfCountryKeepingAlphaChars(alphaNumericNumber, RegionCode.DE)
        )

        // Testing the case of calling from a non-supported region.
        alphaNumericNumber.setCountryCode(1).setNationalNumber(80749L).setRawInput("180-SIX")
        // No country-code stripping can be done since the number is invalid.
        assertEquals(
            "+1 180-SIX", phoneUtil.formatOutOfCountryKeepingAlphaChars(alphaNumericNumber, RegionCode.AQ)
        )
    }

    @Test
    fun testFormatWithCarrierCode() {
        // We only support this for AR in our test metadata, and only for mobile numbers starting with
        // certain values.
        val arMobile = PhoneNumber().setCountryCode(54).setNationalNumber(92234654321L)
        assertEquals("02234 65-4321", phoneUtil.format(arMobile, PhoneNumberFormat.NATIONAL))
        // Here we force 14 as the carrier code.
        assertEquals(
            "02234 14 65-4321", phoneUtil.formatNationalNumberWithCarrierCode(arMobile, "14")
        )
        // Here we force the number to be shown with no carrier code.
        assertEquals(
            "02234 65-4321", phoneUtil.formatNationalNumberWithCarrierCode(arMobile, "")
        )
        // Here the international rule is used, so no carrier code should be present.
        assertEquals("+5492234654321", phoneUtil.format(arMobile, PhoneNumberFormat.E164))
        // We don't support this for the US so there should be no change.
        assertEquals("650 253 0000", phoneUtil.formatNationalNumberWithCarrierCode(US_NUMBER, "15"))

        // Invalid country code should just get the NSN.
        assertEquals(
            "12345", phoneUtil.formatNationalNumberWithCarrierCode(UNKNOWN_COUNTRY_CODE_NO_RAW_INPUT, "89")
        )
    }

    @Test
    fun testFormatWithPreferredCarrierCode() {
        // We only support this for AR in our test metadata.
        val arNumber = PhoneNumber()
        arNumber.setCountryCode(54).setNationalNumber(91234125678L)
        // Test formatting with no preferred carrier code stored in the number itself.
        assertEquals(
            "01234 15 12-5678", phoneUtil.formatNationalNumberWithPreferredCarrierCode(arNumber, "15")
        )
        assertEquals(
            "01234 12-5678", phoneUtil.formatNationalNumberWithPreferredCarrierCode(arNumber, "")
        )
        // Test formatting with preferred carrier code present.
        arNumber.setPreferredDomesticCarrierCode("19")
        assertEquals("01234 12-5678", phoneUtil.format(arNumber, PhoneNumberFormat.NATIONAL))
        assertEquals(
            "01234 19 12-5678", phoneUtil.formatNationalNumberWithPreferredCarrierCode(arNumber, "15")
        )
        assertEquals(
            "01234 19 12-5678", phoneUtil.formatNationalNumberWithPreferredCarrierCode(arNumber, "")
        )
        // When the preferred_domestic_carrier_code is present (even when it is just a space), use it
        // instead of the default carrier code passed in.
        arNumber.setPreferredDomesticCarrierCode(" ")
        assertEquals(
            "01234   12-5678", phoneUtil.formatNationalNumberWithPreferredCarrierCode(arNumber, "15")
        )
        // When the preferred_domestic_carrier_code is present but empty, treat it as unset and use
        // instead the default carrier code passed in.
        arNumber.setPreferredDomesticCarrierCode("")
        assertEquals(
            "01234 15 12-5678", phoneUtil.formatNationalNumberWithPreferredCarrierCode(arNumber, "15")
        )
        // We don't support this for the US so there should be no change.
        val usNumber = PhoneNumber()
        usNumber.setCountryCode(1).setNationalNumber(4241231234L).setPreferredDomesticCarrierCode("99")
        assertEquals("424 123 1234", phoneUtil.format(usNumber, PhoneNumberFormat.NATIONAL))
        assertEquals(
            "424 123 1234", phoneUtil.formatNationalNumberWithPreferredCarrierCode(usNumber, "15")
        )
    }

    @Test
    fun testFormatNumberForMobileDialing() {
        // Numbers are normally dialed in national format in-country, and international format from
        // outside the country.
        assertEquals(
            "6012345678", phoneUtil.formatNumberForMobileDialing(CO_FIXED_LINE, RegionCode.CO, false)
        )
        assertEquals(
            "030123456", phoneUtil.formatNumberForMobileDialing(DE_NUMBER, RegionCode.DE, false)
        )
        assertEquals(
            "+4930123456", phoneUtil.formatNumberForMobileDialing(DE_NUMBER, RegionCode.CH, false)
        )
        val deNumberWithExtn = PhoneNumber().mergeFrom(DE_NUMBER).setExtension("1234")
        assertEquals(
            "030123456", phoneUtil.formatNumberForMobileDialing(deNumberWithExtn, RegionCode.DE, false)
        )
        assertEquals(
            "+4930123456", phoneUtil.formatNumberForMobileDialing(deNumberWithExtn, RegionCode.CH, false)
        )

        // US toll free numbers are marked as noInternationalDialling in the test metadata for testing
        // purposes. For such numbers, we expect nothing to be returned when the region code is not the
        // same one.
        assertEquals(
            "800 253 0000", phoneUtil.formatNumberForMobileDialing(
                US_TOLLFREE, RegionCode.US, true /*  keep formatting */
            )
        )
        assertEquals("", phoneUtil.formatNumberForMobileDialing(US_TOLLFREE, RegionCode.CN, true))
        assertEquals(
            "+1 650 253 0000", phoneUtil.formatNumberForMobileDialing(US_NUMBER, RegionCode.US, true)
        )
        val usNumberWithExtn = PhoneNumber().mergeFrom(US_NUMBER).setExtension("1234")
        assertEquals(
            "+1 650 253 0000", phoneUtil.formatNumberForMobileDialing(usNumberWithExtn, RegionCode.US, true)
        )
        assertEquals(
            "8002530000", phoneUtil.formatNumberForMobileDialing(
                US_TOLLFREE, RegionCode.US, false /* remove formatting */
            )
        )
        assertEquals("", phoneUtil.formatNumberForMobileDialing(US_TOLLFREE, RegionCode.CN, false))
        assertEquals(
            "+16502530000", phoneUtil.formatNumberForMobileDialing(US_NUMBER, RegionCode.US, false)
        )
        assertEquals(
            "+16502530000", phoneUtil.formatNumberForMobileDialing(usNumberWithExtn, RegionCode.US, false)
        )

        // An invalid US number, which is one digit too long.
        assertEquals(
            "+165025300001", phoneUtil.formatNumberForMobileDialing(US_LONG_NUMBER, RegionCode.US, false)
        )
        assertEquals(
            "+1 65025300001", phoneUtil.formatNumberForMobileDialing(US_LONG_NUMBER, RegionCode.US, true)
        )

        // Star numbers. In real life they appear in Israel, but we have them in JP in our test
        // metadata.
        assertEquals(
            "*2345", phoneUtil.formatNumberForMobileDialing(JP_STAR_NUMBER, RegionCode.JP, false)
        )
        assertEquals(
            "*2345", phoneUtil.formatNumberForMobileDialing(JP_STAR_NUMBER, RegionCode.JP, true)
        )
        assertEquals(
            "+80012345678", phoneUtil.formatNumberForMobileDialing(INTERNATIONAL_TOLL_FREE, RegionCode.JP, false)
        )
        assertEquals(
            "+800 1234 5678", phoneUtil.formatNumberForMobileDialing(INTERNATIONAL_TOLL_FREE, RegionCode.JP, true)
        )

        // UAE numbers beginning with 600 (classified as UAN) need to be dialled without +971 locally.
        assertEquals(
            "+971600123456", phoneUtil.formatNumberForMobileDialing(AE_UAN, RegionCode.JP, false)
        )
        assertEquals(
            "600123456", phoneUtil.formatNumberForMobileDialing(AE_UAN, RegionCode.AE, false)
        )
        assertEquals(
            "+523312345678", phoneUtil.formatNumberForMobileDialing(MX_NUMBER1, RegionCode.MX, false)
        )
        assertEquals(
            "+523312345678", phoneUtil.formatNumberForMobileDialing(MX_NUMBER1, RegionCode.US, false)
        )

        // Test whether Uzbek phone numbers are returned in international format even when dialled from
        // same region or other regions.
        assertEquals(
            "+998612201234", phoneUtil.formatNumberForMobileDialing(UZ_FIXED_LINE, RegionCode.UZ, false)
        )
        assertEquals(
            "+998950123456", phoneUtil.formatNumberForMobileDialing(UZ_MOBILE, RegionCode.UZ, false)
        )
        assertEquals(
            "+998950123456", phoneUtil.formatNumberForMobileDialing(UZ_MOBILE, RegionCode.US, false)
        )

        // Non-geographical numbers should always be dialed in international format.
        assertEquals(
            "+80012345678", phoneUtil.formatNumberForMobileDialing(INTERNATIONAL_TOLL_FREE, RegionCode.US, false)
        )
        assertEquals(
            "+80012345678", phoneUtil.formatNumberForMobileDialing(INTERNATIONAL_TOLL_FREE, RegionCode.UN001, false)
        )

        // Test that a short number is formatted correctly for mobile dialing within the region,
        // and is not diallable from outside the region.
        val deShortNumber = PhoneNumber().setCountryCode(49).setNationalNumber(123L)
        assertEquals(
            "123", phoneUtil.formatNumberForMobileDialing(
                deShortNumber, RegionCode.DE, false
            )
        )
        assertEquals("", phoneUtil.formatNumberForMobileDialing(deShortNumber, RegionCode.IT, false))

        // Test the special logic for NANPA countries, for which regular length phone numbers are always
        // output in international format, but short numbers are in national format.
        assertEquals(
            "+16502530000", phoneUtil.formatNumberForMobileDialing(
                US_NUMBER, RegionCode.US, false
            )
        )
        assertEquals(
            "+16502530000", phoneUtil.formatNumberForMobileDialing(
                US_NUMBER, RegionCode.CA, false
            )
        )
        assertEquals(
            "+16502530000", phoneUtil.formatNumberForMobileDialing(
                US_NUMBER, RegionCode.BR, false
            )
        )
        val usShortNumber = PhoneNumber().setCountryCode(1).setNationalNumber(911L)
        assertEquals(
            "911", phoneUtil.formatNumberForMobileDialing(
                usShortNumber, RegionCode.US, false
            )
        )
        assertEquals("", phoneUtil.formatNumberForMobileDialing(usShortNumber, RegionCode.CA, false))
        assertEquals("", phoneUtil.formatNumberForMobileDialing(usShortNumber, RegionCode.BR, false))

        // Test that the Australian emergency number 000 is formatted correctly.
        val auNumber = PhoneNumber().setCountryCode(61).setNationalNumber(0L).setItalianLeadingZero(true)
            .setNumberOfLeadingZeros(2)
        assertEquals("000", phoneUtil.formatNumberForMobileDialing(auNumber, RegionCode.AU, false))
        assertEquals("", phoneUtil.formatNumberForMobileDialing(auNumber, RegionCode.NZ, false))
    }

    @Test
    fun testFormatByPattern() {
        val newNumFormat = Phonemetadata.NumberFormat.newBuilder()
        newNumFormat.setPattern("(\\d{3})(\\d{3})(\\d{4})")
        newNumFormat.setFormat("($1) $2-$3")
        val newNumberFormats: MutableList<Phonemetadata.NumberFormat> = ArrayList()
        newNumberFormats.add(newNumFormat.build())
        assertEquals(
            "(650) 253-0000", phoneUtil.formatByPattern(
                US_NUMBER, PhoneNumberFormat.NATIONAL, newNumberFormats
            )
        )
        assertEquals(
            "+1 (650) 253-0000", phoneUtil.formatByPattern(
                US_NUMBER, PhoneNumberFormat.INTERNATIONAL, newNumberFormats
            )
        )
        assertEquals(
            "tel:+1-650-253-0000", phoneUtil.formatByPattern(
                US_NUMBER, PhoneNumberFormat.RFC3966, newNumberFormats
            )
        )

        // $NP is set to '1' for the US. Here we check that for other NANPA countries the US rules are
        // followed.
        newNumFormat.setNationalPrefixFormattingRule("\$NP (\$FG)")
        newNumFormat.setFormat("$1 $2-$3")
        newNumberFormats[0] = newNumFormat.build()
        assertEquals(
            "1 (242) 365-1234", phoneUtil.formatByPattern(
                BS_NUMBER, PhoneNumberFormat.NATIONAL, newNumberFormats
            )
        )
        assertEquals(
            "+1 242 365-1234", phoneUtil.formatByPattern(
                BS_NUMBER, PhoneNumberFormat.INTERNATIONAL, newNumberFormats
            )
        )
        newNumFormat.setPattern("(\\d{2})(\\d{5})(\\d{3})")
        newNumFormat.setFormat("$1-$2 $3")
        newNumberFormats[0] = newNumFormat.build()
        assertEquals(
            "02-36618 300", phoneUtil.formatByPattern(
                IT_NUMBER, PhoneNumberFormat.NATIONAL, newNumberFormats
            )
        )
        assertEquals(
            "+39 02-36618 300", phoneUtil.formatByPattern(
                IT_NUMBER, PhoneNumberFormat.INTERNATIONAL, newNumberFormats
            )
        )
        newNumFormat.setNationalPrefixFormattingRule("\$NP\$FG")
        newNumFormat.setPattern("(\\d{2})(\\d{4})(\\d{4})")
        newNumFormat.setFormat("$1 $2 $3")
        newNumberFormats[0] = newNumFormat.build()
        assertEquals(
            "020 7031 3000", phoneUtil.formatByPattern(
                GB_NUMBER, PhoneNumberFormat.NATIONAL, newNumberFormats
            )
        )
        newNumFormat.setNationalPrefixFormattingRule("(\$NP\$FG)")
        newNumberFormats[0] = newNumFormat.build()
        assertEquals(
            "(020) 7031 3000", phoneUtil.formatByPattern(
                GB_NUMBER, PhoneNumberFormat.NATIONAL, newNumberFormats
            )
        )
        newNumFormat.setNationalPrefixFormattingRule("")
        newNumberFormats[0] = newNumFormat.build()
        assertEquals(
            "20 7031 3000", phoneUtil.formatByPattern(
                GB_NUMBER, PhoneNumberFormat.NATIONAL, newNumberFormats
            )
        )
        assertEquals(
            "+44 20 7031 3000", phoneUtil.formatByPattern(
                GB_NUMBER, PhoneNumberFormat.INTERNATIONAL, newNumberFormats
            )
        )
    }

    @Test
    fun testFormatE164Number() {
        assertEquals("+16502530000", phoneUtil.format(US_NUMBER, PhoneNumberFormat.E164))
        assertEquals("+4930123456", phoneUtil.format(DE_NUMBER, PhoneNumberFormat.E164))
        assertEquals("+80012345678", phoneUtil.format(INTERNATIONAL_TOLL_FREE, PhoneNumberFormat.E164))
    }

    @Test
    fun testFormatNumberWithExtension() {
        val nzNumber = PhoneNumber().mergeFrom(NZ_NUMBER).setExtension("1234")
        // Uses default extension prefix:
        assertEquals("03-331 6005 ext. 1234", phoneUtil.format(nzNumber, PhoneNumberFormat.NATIONAL))
        // Uses RFC 3966 syntax.
        assertEquals(
            "tel:+64-3-331-6005;ext=1234", phoneUtil.format(nzNumber, PhoneNumberFormat.RFC3966)
        )
        // Extension prefix overridden in the territory information for the US:
        val usNumberWithExtension = PhoneNumber().mergeFrom(US_NUMBER).setExtension("4567")
        assertEquals(
            "650 253 0000 extn. 4567", phoneUtil.format(
                usNumberWithExtension, PhoneNumberFormat.NATIONAL
            )
        )
    }

    @Throws(Exception::class)
    @Test
    fun testFormatInOriginalFormat() {
        val number1 = phoneUtil.parseAndKeepRawInput("+442087654321", RegionCode.GB)
        assertEquals("+44 20 8765 4321", phoneUtil.formatInOriginalFormat(number1, RegionCode.GB))
        val number2 = phoneUtil.parseAndKeepRawInput("02087654321", RegionCode.GB)
        assertEquals("(020) 8765 4321", phoneUtil.formatInOriginalFormat(number2, RegionCode.GB))
        val number3 = phoneUtil.parseAndKeepRawInput("011442087654321", RegionCode.US)
        assertEquals("011 44 20 8765 4321", phoneUtil.formatInOriginalFormat(number3, RegionCode.US))
        val number4 = phoneUtil.parseAndKeepRawInput("442087654321", RegionCode.GB)
        assertEquals("44 20 8765 4321", phoneUtil.formatInOriginalFormat(number4, RegionCode.GB))
        val number5 = phoneUtil.parse("+442087654321", RegionCode.GB)
        assertEquals("(020) 8765 4321", phoneUtil.formatInOriginalFormat(number5, RegionCode.GB))

        // Invalid numbers that we have a formatting pattern for should be formatted properly. Note area
        // codes starting with 7 are intentionally excluded in the test metadata for testing purposes.
        val number6 = phoneUtil.parseAndKeepRawInput("7345678901", RegionCode.US)
        assertEquals("734 567 8901", phoneUtil.formatInOriginalFormat(number6, RegionCode.US))

        // US is not a leading zero country, and the presence of the leading zero leads us to format the
        // number using raw_input.
        val number7 = phoneUtil.parseAndKeepRawInput("0734567 8901", RegionCode.US)
        assertEquals("0734567 8901", phoneUtil.formatInOriginalFormat(number7, RegionCode.US))

        // This number is valid, but we don't have a formatting pattern for it. Fall back to the raw
        // input.
        val number8 = phoneUtil.parseAndKeepRawInput("02-4567-8900", RegionCode.KR)
        assertEquals("02-4567-8900", phoneUtil.formatInOriginalFormat(number8, RegionCode.KR))
        val number9 = phoneUtil.parseAndKeepRawInput("01180012345678", RegionCode.US)
        assertEquals("011 800 1234 5678", phoneUtil.formatInOriginalFormat(number9, RegionCode.US))
        val number10 = phoneUtil.parseAndKeepRawInput("+80012345678", RegionCode.KR)
        assertEquals("+800 1234 5678", phoneUtil.formatInOriginalFormat(number10, RegionCode.KR))

        // US local numbers are formatted correctly, as we have formatting patterns for them.
        val localNumberUS = phoneUtil.parseAndKeepRawInput("2530000", RegionCode.US)
        assertEquals("253 0000", phoneUtil.formatInOriginalFormat(localNumberUS, RegionCode.US))
        val numberWithNationalPrefixUS = phoneUtil.parseAndKeepRawInput("18003456789", RegionCode.US)
        assertEquals(
            "1 800 345 6789", phoneUtil.formatInOriginalFormat(numberWithNationalPrefixUS, RegionCode.US)
        )
        val numberWithoutNationalPrefixGB = phoneUtil.parseAndKeepRawInput("2087654321", RegionCode.GB)
        assertEquals(
            "20 8765 4321", phoneUtil.formatInOriginalFormat(numberWithoutNationalPrefixGB, RegionCode.GB)
        )
        // Make sure no metadata is modified as a result of the previous function call.
        assertEquals("(020) 8765 4321", phoneUtil.formatInOriginalFormat(number5, RegionCode.GB))
        val numberWithNationalPrefixMX = phoneUtil.parseAndKeepRawInput("013312345678", RegionCode.MX)
        assertEquals(
            "01 33 1234 5678", phoneUtil.formatInOriginalFormat(numberWithNationalPrefixMX, RegionCode.MX)
        )
        val numberWithoutNationalPrefixMX = phoneUtil.parseAndKeepRawInput("3312345678", RegionCode.MX)
        assertEquals(
            "33 1234 5678", phoneUtil.formatInOriginalFormat(numberWithoutNationalPrefixMX, RegionCode.MX)
        )
        val italianFixedLineNumber = phoneUtil.parseAndKeepRawInput("0212345678", RegionCode.IT)
        assertEquals(
            "02 1234 5678", phoneUtil.formatInOriginalFormat(italianFixedLineNumber, RegionCode.IT)
        )
        val numberWithNationalPrefixJP = phoneUtil.parseAndKeepRawInput("00777012", RegionCode.JP)
        assertEquals(
            "0077-7012", phoneUtil.formatInOriginalFormat(numberWithNationalPrefixJP, RegionCode.JP)
        )
        val numberWithoutNationalPrefixJP = phoneUtil.parseAndKeepRawInput("0777012", RegionCode.JP)
        assertEquals(
            "0777012", phoneUtil.formatInOriginalFormat(numberWithoutNationalPrefixJP, RegionCode.JP)
        )
        val numberWithCarrierCodeBR = phoneUtil.parseAndKeepRawInput("012 3121286979", RegionCode.BR)
        assertEquals(
            "012 3121286979", phoneUtil.formatInOriginalFormat(numberWithCarrierCodeBR, RegionCode.BR)
        )

        // The default national prefix used in this case is 045. When a number with national prefix 044
        // is entered, we return the raw input as we don't want to change the number entered.
        val numberWithNationalPrefixMX1 = phoneUtil.parseAndKeepRawInput("044(33)1234-5678", RegionCode.MX)
        assertEquals(
            "044(33)1234-5678", phoneUtil.formatInOriginalFormat(numberWithNationalPrefixMX1, RegionCode.MX)
        )
        val numberWithNationalPrefixMX2 = phoneUtil.parseAndKeepRawInput("045(33)1234-5678", RegionCode.MX)
        assertEquals(
            "045 33 1234 5678", phoneUtil.formatInOriginalFormat(numberWithNationalPrefixMX2, RegionCode.MX)
        )

        // The default international prefix used in this case is 0011. When a number with international
        // prefix 0012 is entered, we return the raw input as we don't want to change the number
        // entered.
        val outOfCountryNumberFromAU1 = phoneUtil.parseAndKeepRawInput("0012 16502530000", RegionCode.AU)
        assertEquals(
            "0012 16502530000", phoneUtil.formatInOriginalFormat(outOfCountryNumberFromAU1, RegionCode.AU)
        )
        val outOfCountryNumberFromAU2 = phoneUtil.parseAndKeepRawInput("0011 16502530000", RegionCode.AU)
        assertEquals(
            "0011 1 650 253 0000", phoneUtil.formatInOriginalFormat(outOfCountryNumberFromAU2, RegionCode.AU)
        )

        // Test the star sign is not removed from or added to the original input by this method.
        val starNumber = phoneUtil.parseAndKeepRawInput("*1234", RegionCode.JP)
        assertEquals("*1234", phoneUtil.formatInOriginalFormat(starNumber, RegionCode.JP))
        val numberWithoutStar = phoneUtil.parseAndKeepRawInput("1234", RegionCode.JP)
        assertEquals("1234", phoneUtil.formatInOriginalFormat(numberWithoutStar, RegionCode.JP))

        // Test an invalid national number without raw input is just formatted as the national number.
        assertEquals(
            "650253000", phoneUtil.formatInOriginalFormat(US_SHORT_BY_ONE_NUMBER, RegionCode.US)
        )
    }

    @Test
    fun testIsPremiumRate() {
        assertEquals(PhoneNumberType.PREMIUM_RATE, phoneUtil.getNumberType(US_PREMIUM))
        val premiumRateNumber = PhoneNumber()
        premiumRateNumber.setCountryCode(39).setNationalNumber(892123L)
        assertEquals(PhoneNumberType.PREMIUM_RATE, phoneUtil.getNumberType(premiumRateNumber))
        premiumRateNumber.clear()
        premiumRateNumber.setCountryCode(44).setNationalNumber(9187654321L)
        assertEquals(PhoneNumberType.PREMIUM_RATE, phoneUtil.getNumberType(premiumRateNumber))
        premiumRateNumber.clear()
        premiumRateNumber.setCountryCode(49).setNationalNumber(9001654321L)
        assertEquals(PhoneNumberType.PREMIUM_RATE, phoneUtil.getNumberType(premiumRateNumber))
        premiumRateNumber.clear()
        premiumRateNumber.setCountryCode(49).setNationalNumber(90091234567L)
        assertEquals(PhoneNumberType.PREMIUM_RATE, phoneUtil.getNumberType(premiumRateNumber))
        assertEquals(PhoneNumberType.PREMIUM_RATE, phoneUtil.getNumberType(UNIVERSAL_PREMIUM_RATE))
    }

    @Test
    fun testIsTollFree() {
        val tollFreeNumber = PhoneNumber()
        tollFreeNumber.setCountryCode(1).setNationalNumber(8881234567L)
        assertEquals(PhoneNumberType.TOLL_FREE, phoneUtil.getNumberType(tollFreeNumber))
        tollFreeNumber.clear()
        tollFreeNumber.setCountryCode(39).setNationalNumber(803123L)
        assertEquals(PhoneNumberType.TOLL_FREE, phoneUtil.getNumberType(tollFreeNumber))
        tollFreeNumber.clear()
        tollFreeNumber.setCountryCode(44).setNationalNumber(8012345678L)
        assertEquals(PhoneNumberType.TOLL_FREE, phoneUtil.getNumberType(tollFreeNumber))
        tollFreeNumber.clear()
        tollFreeNumber.setCountryCode(49).setNationalNumber(8001234567L)
        assertEquals(PhoneNumberType.TOLL_FREE, phoneUtil.getNumberType(tollFreeNumber))
        assertEquals(PhoneNumberType.TOLL_FREE, phoneUtil.getNumberType(INTERNATIONAL_TOLL_FREE))
    }

    @Test
    fun testIsMobile() {
        assertEquals(PhoneNumberType.MOBILE, phoneUtil.getNumberType(BS_MOBILE))
        assertEquals(PhoneNumberType.MOBILE, phoneUtil.getNumberType(GB_MOBILE))
        assertEquals(PhoneNumberType.MOBILE, phoneUtil.getNumberType(IT_MOBILE))
        assertEquals(PhoneNumberType.MOBILE, phoneUtil.getNumberType(AR_MOBILE))
        val mobileNumber = PhoneNumber()
        mobileNumber.setCountryCode(49).setNationalNumber(15123456789L)
        assertEquals(PhoneNumberType.MOBILE, phoneUtil.getNumberType(mobileNumber))
    }

    @Test
    fun testIsFixedLine() {
        assertEquals(PhoneNumberType.FIXED_LINE, phoneUtil.getNumberType(BS_NUMBER))
        assertEquals(PhoneNumberType.FIXED_LINE, phoneUtil.getNumberType(IT_NUMBER))
        assertEquals(PhoneNumberType.FIXED_LINE, phoneUtil.getNumberType(GB_NUMBER))
        assertEquals(PhoneNumberType.FIXED_LINE, phoneUtil.getNumberType(DE_NUMBER))
    }

    @Test
    fun testIsFixedLineAndMobile() {
        assertEquals(PhoneNumberType.FIXED_LINE_OR_MOBILE, phoneUtil.getNumberType(US_NUMBER))
        val fixedLineAndMobileNumber = PhoneNumber().setCountryCode(54).setNationalNumber(1987654321L)
        assertEquals(
            PhoneNumberType.FIXED_LINE_OR_MOBILE, phoneUtil.getNumberType(fixedLineAndMobileNumber)
        )
    }

    @Test
    fun testIsSharedCost() {
        val gbNumber = PhoneNumber()
        gbNumber.setCountryCode(44).setNationalNumber(8431231234L)
        assertEquals(PhoneNumberType.SHARED_COST, phoneUtil.getNumberType(gbNumber))
    }

    @Test
    fun testIsVoip() {
        val gbNumber = PhoneNumber()
        gbNumber.setCountryCode(44).setNationalNumber(5631231234L)
        assertEquals(PhoneNumberType.VOIP, phoneUtil.getNumberType(gbNumber))
    }

    @Test
    fun testIsPersonalNumber() {
        val gbNumber = PhoneNumber()
        gbNumber.setCountryCode(44).setNationalNumber(7031231234L)
        assertEquals(PhoneNumberType.PERSONAL_NUMBER, phoneUtil.getNumberType(gbNumber))
    }

    @Test
    fun testIsUnknown() {
        // Invalid numbers should be of type UNKNOWN.
        assertEquals(PhoneNumberType.UNKNOWN, phoneUtil.getNumberType(US_LOCAL_NUMBER))
    }

    @Test
    fun testIsValidNumber() {
        assertTrue(phoneUtil.isValidNumber(US_NUMBER))
        assertTrue(phoneUtil.isValidNumber(IT_NUMBER))
        assertTrue(phoneUtil.isValidNumber(GB_MOBILE))
        assertTrue(phoneUtil.isValidNumber(INTERNATIONAL_TOLL_FREE))
        assertTrue(phoneUtil.isValidNumber(UNIVERSAL_PREMIUM_RATE))
        val nzNumber = PhoneNumber().setCountryCode(64).setNationalNumber(21387835L)
        assertTrue(phoneUtil.isValidNumber(nzNumber))
    }

    @Test
    fun testIsValidForRegion() {
        // This number is valid for the Bahamas, but is not a valid US number.
        assertTrue(phoneUtil.isValidNumber(BS_NUMBER))
        assertTrue(phoneUtil.isValidNumberForRegion(BS_NUMBER, RegionCode.BS))
        assertFalse(phoneUtil.isValidNumberForRegion(BS_NUMBER, RegionCode.US))
        val bsInvalidNumber = PhoneNumber().setCountryCode(1).setNationalNumber(2421232345L)
        // This number is no longer valid.
        assertFalse(phoneUtil.isValidNumber(bsInvalidNumber))

        // La Mayotte and Reunion use 'leadingDigits' to differentiate them.
        val reNumber = PhoneNumber()
        reNumber.setCountryCode(262).setNationalNumber(262123456L)
        assertTrue(phoneUtil.isValidNumber(reNumber))
        assertTrue(phoneUtil.isValidNumberForRegion(reNumber, RegionCode.RE))
        assertFalse(phoneUtil.isValidNumberForRegion(reNumber, RegionCode.YT))
        // Now change the number to be a number for La Mayotte.
        reNumber.setNationalNumber(269601234L)
        assertTrue(phoneUtil.isValidNumberForRegion(reNumber, RegionCode.YT))
        assertFalse(phoneUtil.isValidNumberForRegion(reNumber, RegionCode.RE))
        // This number is no longer valid for La Reunion.
        reNumber.setNationalNumber(269123456L)
        assertFalse(phoneUtil.isValidNumberForRegion(reNumber, RegionCode.YT))
        assertFalse(phoneUtil.isValidNumberForRegion(reNumber, RegionCode.RE))
        assertFalse(phoneUtil.isValidNumber(reNumber))
        // However, it should be recognised as from La Mayotte, since it is valid for this region.
        assertEquals(RegionCode.YT, phoneUtil.getRegionCodeForNumber(reNumber))
        // This number is valid in both places.
        reNumber.setNationalNumber(800123456L)
        assertTrue(phoneUtil.isValidNumberForRegion(reNumber, RegionCode.YT))
        assertTrue(phoneUtil.isValidNumberForRegion(reNumber, RegionCode.RE))
        assertTrue(phoneUtil.isValidNumberForRegion(INTERNATIONAL_TOLL_FREE, RegionCode.UN001))
        assertFalse(phoneUtil.isValidNumberForRegion(INTERNATIONAL_TOLL_FREE, RegionCode.US))
        assertFalse(phoneUtil.isValidNumberForRegion(INTERNATIONAL_TOLL_FREE, RegionCode.ZZ))
        val invalidNumber = PhoneNumber()
        // Invalid country calling codes.
        invalidNumber.setCountryCode(3923).setNationalNumber(2366L)
        assertFalse(phoneUtil.isValidNumberForRegion(invalidNumber, RegionCode.ZZ))
        assertFalse(phoneUtil.isValidNumberForRegion(invalidNumber, RegionCode.UN001))
        invalidNumber.setCountryCode(0)
        assertFalse(phoneUtil.isValidNumberForRegion(invalidNumber, RegionCode.UN001))
        assertFalse(phoneUtil.isValidNumberForRegion(invalidNumber, RegionCode.ZZ))
    }

    @Test
    fun testIsNotValidNumber() {
        assertFalse(phoneUtil.isValidNumber(US_LOCAL_NUMBER))
        val invalidNumber = PhoneNumber()
        invalidNumber.setCountryCode(39).setNationalNumber(23661830000L).setItalianLeadingZero(true)
        assertFalse(phoneUtil.isValidNumber(invalidNumber))
        invalidNumber.clear()
        invalidNumber.setCountryCode(44).setNationalNumber(791234567L)
        assertFalse(phoneUtil.isValidNumber(invalidNumber))
        invalidNumber.clear()
        invalidNumber.setCountryCode(49).setNationalNumber(1234L)
        assertFalse(phoneUtil.isValidNumber(invalidNumber))
        invalidNumber.clear()
        invalidNumber.setCountryCode(64).setNationalNumber(3316005L)
        assertFalse(phoneUtil.isValidNumber(invalidNumber))
        invalidNumber.clear()
        // Invalid country calling codes.
        invalidNumber.setCountryCode(3923).setNationalNumber(2366L)
        assertFalse(phoneUtil.isValidNumber(invalidNumber))
        invalidNumber.setCountryCode(0)
        assertFalse(phoneUtil.isValidNumber(invalidNumber))
        assertFalse(phoneUtil.isValidNumber(INTERNATIONAL_TOLL_FREE_TOO_LONG))
    }

    @Test
    fun testGetRegionCodeForCountryCode() {
        assertEquals(RegionCode.US, phoneUtil.getRegionCodeForCountryCode(1))
        assertEquals(RegionCode.GB, phoneUtil.getRegionCodeForCountryCode(44))
        assertEquals(RegionCode.DE, phoneUtil.getRegionCodeForCountryCode(49))
        assertEquals(RegionCode.UN001, phoneUtil.getRegionCodeForCountryCode(800))
        assertEquals(RegionCode.UN001, phoneUtil.getRegionCodeForCountryCode(979))
    }

    @Test
    fun testGetRegionCodeForNumber() {
        assertEquals(RegionCode.BS, phoneUtil.getRegionCodeForNumber(BS_NUMBER))
        assertEquals(RegionCode.US, phoneUtil.getRegionCodeForNumber(US_NUMBER))
        assertEquals(RegionCode.GB, phoneUtil.getRegionCodeForNumber(GB_MOBILE))
        assertEquals(RegionCode.UN001, phoneUtil.getRegionCodeForNumber(INTERNATIONAL_TOLL_FREE))
        assertEquals(RegionCode.UN001, phoneUtil.getRegionCodeForNumber(UNIVERSAL_PREMIUM_RATE))
    }

    @Test
    fun testGetRegionCodesForCountryCode() {
        val regionCodesForNANPA = phoneUtil.getRegionCodesForCountryCode(1)
        assertTrue(regionCodesForNANPA.contains(RegionCode.US))
        assertTrue(regionCodesForNANPA.contains(RegionCode.BS))
        assertTrue(phoneUtil.getRegionCodesForCountryCode(44).contains(RegionCode.GB))
        assertTrue(phoneUtil.getRegionCodesForCountryCode(49).contains(RegionCode.DE))
        assertTrue(phoneUtil.getRegionCodesForCountryCode(800).contains(RegionCode.UN001))
        // Test with invalid country calling code.
        assertTrue(phoneUtil.getRegionCodesForCountryCode(-1).isEmpty())
    }

    @Test
    fun testGetCountryCodeForRegion() {
        assertEquals(1, phoneUtil.getCountryCodeForRegion(RegionCode.US))
        assertEquals(64, phoneUtil.getCountryCodeForRegion(RegionCode.NZ))
        assertEquals(0, phoneUtil.getCountryCodeForRegion(null))
        assertEquals(0, phoneUtil.getCountryCodeForRegion(RegionCode.ZZ))
        assertEquals(0, phoneUtil.getCountryCodeForRegion(RegionCode.UN001))
        // CS is already deprecated so the library doesn't support it.
        assertEquals(0, phoneUtil.getCountryCodeForRegion(RegionCode.CS))
    }

    @Test
    fun testGetNationalDiallingPrefixForRegion() {
        assertEquals("1", phoneUtil.getNddPrefixForRegion(RegionCode.US, false))
        // Test non-main country to see it gets the national dialling prefix for the main country with
        // that country calling code.
        assertEquals("1", phoneUtil.getNddPrefixForRegion(RegionCode.BS, false))
        assertEquals("0", phoneUtil.getNddPrefixForRegion(RegionCode.NZ, false))
        // Test case with non digit in the national prefix.
        assertEquals("0~0", phoneUtil.getNddPrefixForRegion(RegionCode.AO, false))
        assertEquals("00", phoneUtil.getNddPrefixForRegion(RegionCode.AO, true))
        // Test cases with invalid regions.
        assertEquals(null, phoneUtil.getNddPrefixForRegion(null, false))
        assertEquals(null, phoneUtil.getNddPrefixForRegion(RegionCode.ZZ, false))
        assertEquals(null, phoneUtil.getNddPrefixForRegion(RegionCode.UN001, false))
        // CS is already deprecated so the library doesn't support it.
        assertEquals(null, phoneUtil.getNddPrefixForRegion(RegionCode.CS, false))
    }

    @Test
    fun testIsNANPACountry() {
        assertTrue(phoneUtil.isNANPACountry(RegionCode.US))
        assertTrue(phoneUtil.isNANPACountry(RegionCode.BS))
        assertFalse(phoneUtil.isNANPACountry(RegionCode.DE))
        assertFalse(phoneUtil.isNANPACountry(RegionCode.ZZ))
        assertFalse(phoneUtil.isNANPACountry(RegionCode.UN001))
        assertFalse(phoneUtil.isNANPACountry(null))
    }

    @Test
    fun testIsPossibleNumber() {
        assertTrue(phoneUtil.isPossibleNumber(US_NUMBER))
        assertTrue(phoneUtil.isPossibleNumber(US_LOCAL_NUMBER))
        assertTrue(phoneUtil.isPossibleNumber(GB_NUMBER))
        assertTrue(phoneUtil.isPossibleNumber(INTERNATIONAL_TOLL_FREE))
        assertTrue(phoneUtil.isPossibleNumber("+1 650 253 0000", RegionCode.US))
        assertTrue(phoneUtil.isPossibleNumber("+1 650 GOO OGLE", RegionCode.US))
        assertTrue(phoneUtil.isPossibleNumber("(650) 253-0000", RegionCode.US))
        assertTrue(phoneUtil.isPossibleNumber("253-0000", RegionCode.US))
        assertTrue(phoneUtil.isPossibleNumber("+1 650 253 0000", RegionCode.GB))
        assertTrue(phoneUtil.isPossibleNumber("+44 20 7031 3000", RegionCode.GB))
        assertTrue(phoneUtil.isPossibleNumber("(020) 7031 300", RegionCode.GB))
        assertTrue(phoneUtil.isPossibleNumber("7031 3000", RegionCode.GB))
        assertTrue(phoneUtil.isPossibleNumber("3331 6005", RegionCode.NZ))
        assertTrue(phoneUtil.isPossibleNumber("+800 1234 5678", RegionCode.UN001))
    }

    @Test
    fun testIsPossibleNumberForType_DifferentTypeLengths() {
        // We use Argentinian numbers since they have different possible lengths for different types.
        val number = PhoneNumber()
        number.setCountryCode(54).setNationalNumber(12345L)
        // Too short for any Argentinian number, including fixed-line.
        assertFalse(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.FIXED_LINE))
        assertFalse(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.UNKNOWN))

        // 6-digit numbers are okay for fixed-line.
        number.setNationalNumber(123456L)
        assertTrue(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.UNKNOWN))
        assertTrue(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.FIXED_LINE))
        // But too short for mobile.
        assertFalse(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.MOBILE))
        // And too short for toll-free.
        assertFalse(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.TOLL_FREE))

        // The same applies to 9-digit numbers.
        number.setNationalNumber(123456789L)
        assertTrue(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.UNKNOWN))
        assertTrue(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.FIXED_LINE))
        assertFalse(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.MOBILE))
        assertFalse(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.TOLL_FREE))

        // 10-digit numbers are universally possible.
        number.setNationalNumber(1234567890L)
        assertTrue(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.UNKNOWN))
        assertTrue(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.FIXED_LINE))
        assertTrue(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.MOBILE))
        assertTrue(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.TOLL_FREE))

        // 11-digit numbers are only possible for mobile numbers. Note we don't require the leading 9,
        // which all mobile numbers start with, and would be required for a valid mobile number.
        number.setNationalNumber(12345678901L)
        assertTrue(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.UNKNOWN))
        assertFalse(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.FIXED_LINE))
        assertTrue(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.MOBILE))
        assertFalse(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.TOLL_FREE))
    }

    @Test
    fun testIsPossibleNumberForType_LocalOnly() {
        val number = PhoneNumber()
        // Here we test a number length which matches a local-only length.
        number.setCountryCode(49).setNationalNumber(12L)
        assertTrue(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.UNKNOWN))
        assertTrue(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.FIXED_LINE))
        // Mobile numbers must be 10 or 11 digits, and there are no local-only lengths.
        assertFalse(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.MOBILE))
    }

    @Test
    fun testIsPossibleNumberForType_DataMissingForSizeReasons() {
        val number = PhoneNumber()
        // Here we test something where the possible lengths match the possible lengths of the country
        // as a whole, and hence aren't present in the binary for size reasons - this should still work.
        // Local-only number.
        number.setCountryCode(55).setNationalNumber(12345678L)
        assertTrue(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.UNKNOWN))
        assertTrue(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.FIXED_LINE))
        number.setNationalNumber(1234567890L)
        assertTrue(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.UNKNOWN))
        assertTrue(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.FIXED_LINE))
    }

    @Test
    fun testIsPossibleNumberForType_NumberTypeNotSupportedForRegion() {
        val number = PhoneNumber()
        // There are *no* mobile numbers for this region at all, so we return false.
        number.setCountryCode(55).setNationalNumber(12345678L)
        assertFalse(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.MOBILE))
        // This matches a fixed-line length though.
        assertTrue(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.FIXED_LINE))
        assertTrue(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.FIXED_LINE_OR_MOBILE))

        // There are *no* fixed-line OR mobile numbers for this country calling code at all, so we
        // return false for these.
        number.setCountryCode(979).setNationalNumber(123456789L)
        assertFalse(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.MOBILE))
        assertFalse(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.FIXED_LINE))
        assertFalse(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.FIXED_LINE_OR_MOBILE))
        assertTrue(phoneUtil.isPossibleNumberForType(number, PhoneNumberType.PREMIUM_RATE))
    }

    @Test
    fun testIsPossibleNumberWithReason() {
        // National numbers for country calling code +1 that are within 7 to 10 digits are possible.
        assertEquals(ValidationResult.IS_POSSIBLE, phoneUtil.isPossibleNumberWithReason(US_NUMBER))
        assertEquals(
            ValidationResult.IS_POSSIBLE_LOCAL_ONLY, phoneUtil.isPossibleNumberWithReason(US_LOCAL_NUMBER)
        )
        assertEquals(ValidationResult.TOO_LONG, phoneUtil.isPossibleNumberWithReason(US_LONG_NUMBER))
        val number = PhoneNumber()
        number.setCountryCode(0).setNationalNumber(2530000L)
        assertEquals(
            ValidationResult.INVALID_COUNTRY_CODE, phoneUtil.isPossibleNumberWithReason(number)
        )
        number.clear()
        number.setCountryCode(1).setNationalNumber(253000L)
        assertEquals(ValidationResult.TOO_SHORT, phoneUtil.isPossibleNumberWithReason(number))
        number.clear()
        number.setCountryCode(65).setNationalNumber(1234567890L)
        assertEquals(ValidationResult.IS_POSSIBLE, phoneUtil.isPossibleNumberWithReason(number))
        assertEquals(
            ValidationResult.TOO_LONG, phoneUtil.isPossibleNumberWithReason(INTERNATIONAL_TOLL_FREE_TOO_LONG)
        )
    }

    @Test
    fun testIsPossibleNumberForTypeWithReason_DifferentTypeLengths() {
        // We use Argentinian numbers since they have different possible lengths for different types.
        val number = PhoneNumber()
        number.setCountryCode(54).setNationalNumber(12345L)
        // Too short for any Argentinian number.
        assertEquals(
            ValidationResult.TOO_SHORT, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.UNKNOWN)
        )
        assertEquals(
            ValidationResult.TOO_SHORT, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE)
        )

        // 6-digit numbers are okay for fixed-line.
        number.setNationalNumber(123456L)
        assertEquals(
            ValidationResult.IS_POSSIBLE, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.UNKNOWN)
        )
        assertEquals(
            ValidationResult.IS_POSSIBLE,
            phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE)
        )
        // But too short for mobile.
        assertEquals(
            ValidationResult.TOO_SHORT, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.MOBILE)
        )
        // And too short for toll-free.
        assertEquals(
            ValidationResult.TOO_SHORT, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.TOLL_FREE)
        )

        // The same applies to 9-digit numbers.
        number.setNationalNumber(123456789L)
        assertEquals(
            ValidationResult.IS_POSSIBLE, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.UNKNOWN)
        )
        assertEquals(
            ValidationResult.IS_POSSIBLE,
            phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE)
        )
        assertEquals(
            ValidationResult.TOO_SHORT, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.MOBILE)
        )
        assertEquals(
            ValidationResult.TOO_SHORT, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.TOLL_FREE)
        )

        // 10-digit numbers are universally possible.
        number.setNationalNumber(1234567890L)
        assertEquals(
            ValidationResult.IS_POSSIBLE, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.UNKNOWN)
        )
        assertEquals(
            ValidationResult.IS_POSSIBLE,
            phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE)
        )
        assertEquals(
            ValidationResult.IS_POSSIBLE, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.MOBILE)
        )
        assertEquals(
            ValidationResult.IS_POSSIBLE, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.TOLL_FREE)
        )

        // 11-digit numbers are only possible for mobile numbers. Note we don't require the leading 9,
        // which all mobile numbers start with, and would be required for a valid mobile number.
        number.setNationalNumber(12345678901L)
        assertEquals(
            ValidationResult.IS_POSSIBLE, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.UNKNOWN)
        )
        assertEquals(
            ValidationResult.TOO_LONG, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE)
        )
        assertEquals(
            ValidationResult.IS_POSSIBLE, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.MOBILE)
        )
        assertEquals(
            ValidationResult.TOO_LONG, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.TOLL_FREE)
        )
    }

    @Test
    fun testIsPossibleNumberForTypeWithReason_LocalOnly() {
        val number = PhoneNumber()
        // Here we test a number length which matches a local-only length.
        number.setCountryCode(49).setNationalNumber(12L)
        assertEquals(
            ValidationResult.IS_POSSIBLE_LOCAL_ONLY,
            phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.UNKNOWN)
        )
        assertEquals(
            ValidationResult.IS_POSSIBLE_LOCAL_ONLY,
            phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE)
        )
        // Mobile numbers must be 10 or 11 digits, and there are no local-only lengths.
        assertEquals(
            ValidationResult.TOO_SHORT, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.MOBILE)
        )
    }

    @Test
    fun testIsPossibleNumberForTypeWithReason_DataMissingForSizeReasons() {
        val number = PhoneNumber()
        // Here we test something where the possible lengths match the possible lengths of the country
        // as a whole, and hence aren't present in the binary for size reasons - this should still work.
        // Local-only number.
        number.setCountryCode(55).setNationalNumber(12345678L)
        assertEquals(
            ValidationResult.IS_POSSIBLE_LOCAL_ONLY,
            phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.UNKNOWN)
        )
        assertEquals(
            ValidationResult.IS_POSSIBLE_LOCAL_ONLY,
            phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE)
        )

        // Normal-length number.
        number.setNationalNumber(1234567890L)
        assertEquals(
            ValidationResult.IS_POSSIBLE, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.UNKNOWN)
        )
        assertEquals(
            ValidationResult.IS_POSSIBLE,
            phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE)
        )
    }

    @Test
    fun testIsPossibleNumberForTypeWithReason_NumberTypeNotSupportedForRegion() {
        val number = PhoneNumber()
        // There are *no* mobile numbers for this region at all, so we return INVALID_LENGTH.
        number.setCountryCode(55).setNationalNumber(12345678L)
        assertEquals(
            ValidationResult.INVALID_LENGTH, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.MOBILE)
        )
        // This matches a fixed-line length though.
        assertEquals(
            ValidationResult.IS_POSSIBLE_LOCAL_ONLY,
            phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE_OR_MOBILE)
        )
        // This is too short for fixed-line, and no mobile numbers exist.
        number.setCountryCode(55).setNationalNumber(1234567L)
        assertEquals(
            ValidationResult.INVALID_LENGTH, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.MOBILE)
        )
        assertEquals(
            ValidationResult.TOO_SHORT,
            phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE_OR_MOBILE)
        )
        assertEquals(
            ValidationResult.TOO_SHORT, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE)
        )

        // This is too short for mobile, and no fixed-line numbers exist.
        number.setCountryCode(882).setNationalNumber(1234567L)
        assertEquals(
            ValidationResult.TOO_SHORT, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.MOBILE)
        )
        assertEquals(
            ValidationResult.TOO_SHORT,
            phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE_OR_MOBILE)
        )
        assertEquals(
            ValidationResult.INVALID_LENGTH,
            phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE)
        )

        // There are *no* fixed-line OR mobile numbers for this country calling code at all, so we
        // return INVALID_LENGTH.
        number.setCountryCode(979).setNationalNumber(123456789L)
        assertEquals(
            ValidationResult.INVALID_LENGTH, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.MOBILE)
        )
        assertEquals(
            ValidationResult.INVALID_LENGTH,
            phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE)
        )
        assertEquals(
            ValidationResult.INVALID_LENGTH,
            phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE_OR_MOBILE)
        )
        assertEquals(
            ValidationResult.IS_POSSIBLE,
            phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.PREMIUM_RATE)
        )
    }

    @Test
    fun testIsPossibleNumberForTypeWithReason_FixedLineOrMobile() {
        val number = PhoneNumber()
        // For FIXED_LINE_OR_MOBILE, a number should be considered valid if it matches the possible
        // lengths for mobile *or* fixed-line numbers.
        number.setCountryCode(290).setNationalNumber(1234L)
        assertEquals(
            ValidationResult.TOO_SHORT, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE)
        )
        assertEquals(
            ValidationResult.IS_POSSIBLE, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.MOBILE)
        )
        assertEquals(
            ValidationResult.IS_POSSIBLE,
            phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE_OR_MOBILE)
        )
        number.setNationalNumber(12345L)
        assertEquals(
            ValidationResult.TOO_SHORT, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE)
        )
        assertEquals(
            ValidationResult.TOO_LONG, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.MOBILE)
        )
        assertEquals(
            ValidationResult.INVALID_LENGTH,
            phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE_OR_MOBILE)
        )
        number.setNationalNumber(123456L)
        assertEquals(
            ValidationResult.IS_POSSIBLE,
            phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE)
        )
        assertEquals(
            ValidationResult.TOO_LONG, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.MOBILE)
        )
        assertEquals(
            ValidationResult.IS_POSSIBLE,
            phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE_OR_MOBILE)
        )
        number.setNationalNumber(1234567L)
        assertEquals(
            ValidationResult.TOO_LONG, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE)
        )
        assertEquals(
            ValidationResult.TOO_LONG, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.MOBILE)
        )
        assertEquals(
            ValidationResult.TOO_LONG,
            phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE_OR_MOBILE)
        )

        // 8-digit numbers are possible for toll-free and premium-rate numbers only.
        number.setNationalNumber(12345678L)
        assertEquals(
            ValidationResult.IS_POSSIBLE, phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.TOLL_FREE)
        )
        assertEquals(
            ValidationResult.TOO_LONG,
            phoneUtil.isPossibleNumberForTypeWithReason(number, PhoneNumberType.FIXED_LINE_OR_MOBILE)
        )
    }

    @Test
    fun testIsNotPossibleNumber() {
        assertFalse(phoneUtil.isPossibleNumber(US_LONG_NUMBER))
        assertFalse(phoneUtil.isPossibleNumber(INTERNATIONAL_TOLL_FREE_TOO_LONG))
        val number = PhoneNumber()
        number.setCountryCode(1).setNationalNumber(253000L)
        assertFalse(phoneUtil.isPossibleNumber(number))
        number.clear()
        number.setCountryCode(44).setNationalNumber(300L)
        assertFalse(phoneUtil.isPossibleNumber(number))
        assertFalse(phoneUtil.isPossibleNumber("+1 650 253 00000", RegionCode.US))
        assertFalse(phoneUtil.isPossibleNumber("(650) 253-00000", RegionCode.US))
        assertFalse(phoneUtil.isPossibleNumber("I want a Pizza", RegionCode.US))
        assertFalse(phoneUtil.isPossibleNumber("253-000", RegionCode.US))
        assertFalse(phoneUtil.isPossibleNumber("1 3000", RegionCode.GB))
        assertFalse(phoneUtil.isPossibleNumber("+44 300", RegionCode.GB))
        assertFalse(phoneUtil.isPossibleNumber("+800 1234 5678 9", RegionCode.UN001))
    }

    @Test
    fun testTruncateTooLongNumber() {
        // GB number 080 1234 5678, but entered with 4 extra digits at the end.
        val tooLongNumber = PhoneNumber()
        tooLongNumber.setCountryCode(44).setNationalNumber(80123456780123L)
        val validNumber = PhoneNumber()
        validNumber.setCountryCode(44).setNationalNumber(8012345678L)
        assertTrue(phoneUtil.truncateTooLongNumber(tooLongNumber))
        assertEquals(validNumber, tooLongNumber)

        // IT number 022 3456 7890, but entered with 3 extra digits at the end.
        tooLongNumber.clear()
        tooLongNumber.setCountryCode(39).setNationalNumber(2234567890123L).setItalianLeadingZero(true)
        validNumber.clear()
        validNumber.setCountryCode(39).setNationalNumber(2234567890L).setItalianLeadingZero(true)
        assertTrue(phoneUtil.truncateTooLongNumber(tooLongNumber))
        assertEquals(validNumber, tooLongNumber)

        // US number 650-253-0000, but entered with one additional digit at the end.
        tooLongNumber.clear()
        tooLongNumber.mergeFrom(US_LONG_NUMBER)
        assertTrue(phoneUtil.truncateTooLongNumber(tooLongNumber))
        assertEquals(US_NUMBER, tooLongNumber)
        tooLongNumber.clear()
        tooLongNumber.mergeFrom(INTERNATIONAL_TOLL_FREE_TOO_LONG)
        assertTrue(phoneUtil.truncateTooLongNumber(tooLongNumber))
        assertEquals(INTERNATIONAL_TOLL_FREE, tooLongNumber)

        // Tests what happens when a valid number is passed in.
        val validNumberCopy = PhoneNumber().mergeFrom(validNumber)
        assertTrue(phoneUtil.truncateTooLongNumber(validNumber))
        // Tests the number is not modified.
        assertEquals(validNumberCopy, validNumber)

        // Tests what happens when a number with invalid prefix is passed in.
        val numberWithInvalidPrefix = PhoneNumber()
        // The test metadata says US numbers cannot have prefix 240.
        numberWithInvalidPrefix.setCountryCode(1).setNationalNumber(2401234567L)
        val invalidNumberCopy = PhoneNumber().mergeFrom(numberWithInvalidPrefix)
        assertFalse(phoneUtil.truncateTooLongNumber(numberWithInvalidPrefix))
        // Tests the number is not modified.
        assertEquals(invalidNumberCopy, numberWithInvalidPrefix)

        // Tests what happens when a too short number is passed in.
        val tooShortNumber = PhoneNumber().setCountryCode(1).setNationalNumber(1234L)
        val tooShortNumberCopy = PhoneNumber().mergeFrom(tooShortNumber)
        assertFalse(phoneUtil.truncateTooLongNumber(tooShortNumber))
        // Tests the number is not modified.
        assertEquals(tooShortNumberCopy, tooShortNumber)
    }

    @Test
    fun testIsViablePhoneNumber() {
//        assertFalse(PhoneNumberUtil.isViablePhoneNumber("1"))
//        // Only one or two digits before strange non-possible punctuation.
//        assertFalse(PhoneNumberUtil.isViablePhoneNumber("1+1+1"))
        assertFalse(PhoneNumberUtil.isViablePhoneNumber("80+0"))
        // Two digits is viable.
        assertTrue(PhoneNumberUtil.isViablePhoneNumber("00"))
        assertTrue(PhoneNumberUtil.isViablePhoneNumber("111"))
        // Alpha numbers.
        assertTrue(PhoneNumberUtil.isViablePhoneNumber("0800-4-pizza"))
        assertTrue(PhoneNumberUtil.isViablePhoneNumber("0800-4-PIZZA"))
        // We need at least three digits before any alpha characters.
        assertFalse(PhoneNumberUtil.isViablePhoneNumber("08-PIZZA"))
        assertFalse(PhoneNumberUtil.isViablePhoneNumber("8-PIZZA"))
        assertFalse(PhoneNumberUtil.isViablePhoneNumber("12. March"))
    }

    @Test
    fun testIsViablePhoneNumberNonAscii() {
        // Only one or two digits before possible punctuation followed by more digits.
        assertTrue(PhoneNumberUtil.isViablePhoneNumber("1\u300034"))
        assertFalse(PhoneNumberUtil.isViablePhoneNumber("1\u30003+4"))
        // Unicode variants of possible starting character and other allowed punctuation/digits.
        assertTrue(PhoneNumberUtil.isViablePhoneNumber("\uFF081\uFF09\u30003456789"))
        // Testing a leading + is okay.
        assertTrue(PhoneNumberUtil.isViablePhoneNumber("+1\uFF09\u30003456789"))
    }

    @Test
    fun testExtractPossibleNumber() {
        // Removes preceding funky punctuation and letters but leaves the rest untouched.
        assertEquals("0800-345-600", extractPossibleNumber("Tel:0800-345-600").toString())
        assertEquals("0800 FOR PIZZA", extractPossibleNumber("Tel:0800 FOR PIZZA").toString())
        // Should not remove plus sign
        assertEquals("+800-345-600", extractPossibleNumber("Tel:+800-345-600").toString())
        // Should recognise wide digits as possible start values.
        assertEquals(
            "\uFF10\uFF12\uFF13", extractPossibleNumber("\uFF10\uFF12\uFF13").toString()
        )
        // Dashes are not possible start values and should be removed.
        assertEquals(
            "\uFF11\uFF12\uFF13", extractPossibleNumber("Num-\uFF11\uFF12\uFF13").toString()
        )
        // If not possible number present, return empty string.
        assertEquals("", extractPossibleNumber("Num-....").toString())
        // Leading brackets are stripped - these are not used when parsing.
        assertEquals("650) 253-0000", extractPossibleNumber("(650) 253-0000").toString())

        // Trailing non-alpha-numeric characters should be removed.
        assertEquals("650) 253-0000", extractPossibleNumber("(650) 253-0000..- ..").toString())
        assertEquals("650) 253-0000", extractPossibleNumber("(650) 253-0000.").toString())
        // This case has a trailing RTL char.
        assertEquals("650) 253-0000", extractPossibleNumber("(650) 253-0000\u200F").toString())
    }

    @Test
    fun testMaybeStripNationalPrefix() {
        val metadata = PhoneMetadata.newBuilder()
        metadata.setId("ignored")
        metadata.setNationalPrefixForParsing("34")
        metadata.generalDescBuilder.setNationalNumberPattern("\\d{4,8}")
        var numberToStrip = InplaceStringBuilder("34356778")
        var strippedNumber = "356778"
        assertTrue(phoneUtil.maybeStripNationalPrefixAndCarrierCode(numberToStrip, metadata.build(), null))
        assertEquals(
            strippedNumber, numberToStrip.toString(), "Should have had national prefix stripped.",
        )
        // Retry stripping - now the number should not start with the national prefix, so no more
        // stripping should occur.
        assertFalse(phoneUtil.maybeStripNationalPrefixAndCarrierCode(numberToStrip, metadata.build(), null))
        assertEquals(
            strippedNumber, numberToStrip.toString(), "Should have had no change - no national prefix present.",
        )
        // Some countries have no national prefix. Repeat test with none specified.
        metadata.setNationalPrefixForParsing("")
        assertFalse(phoneUtil.maybeStripNationalPrefixAndCarrierCode(numberToStrip, metadata.build(), null))
        assertEquals(
            strippedNumber, numberToStrip.toString(), "Should not strip anything with empty national prefix.",
        )
        // If the resultant number doesn't match the national rule, it shouldn't be stripped.
        metadata.setNationalPrefixForParsing("3")
        numberToStrip = InplaceStringBuilder("3123")
        strippedNumber = "3123"
        assertFalse(phoneUtil.maybeStripNationalPrefixAndCarrierCode(numberToStrip, metadata.build(), null))
        assertEquals(
            strippedNumber,
            numberToStrip.toString(),
            "Should have had no change - after stripping, it wouldn't have matched " + "the national rule.",
        )
        // Test extracting carrier selection code.
        metadata.setNationalPrefixForParsing("0(81)?")
        numberToStrip = InplaceStringBuilder("08122123456")
        strippedNumber = "22123456"
        val carrierCode = InplaceStringBuilder()
        assertTrue(
            phoneUtil.maybeStripNationalPrefixAndCarrierCode(
                numberToStrip, metadata.build(), carrierCode
            )
        )
        assertEquals("81", carrierCode.toString())
        assertEquals(
            strippedNumber, numberToStrip.toString(), "Should have had national prefix and carrier code stripped.",
        )
        // If there was a transform rule, check it was applied.
        metadata.setNationalPrefixTransformRule("5$15")
        // Note that a capturing group is present here.
        metadata.setNationalPrefixForParsing("0(\\d{2})")
        numberToStrip = InplaceStringBuilder("031123")
        val transformedNumber = "5315123"
        assertTrue(
            phoneUtil.maybeStripNationalPrefixAndCarrierCode(numberToStrip, metadata.build(), null)
        )
        assertEquals(
            transformedNumber, numberToStrip.toString(), "Should transform the 031 to a 5315.",
        )
    }

    @Test
    fun testMaybeStripInternationalPrefix() {
        val internationalPrefix = "00[39]"
        var numberToStrip = InplaceStringBuilder("0034567700-3898003")
        // Note the dash is removed as part of the normalization.
        var strippedNumber = InplaceStringBuilder("45677003898003")
        assertEquals(
            CountryCodeSource.FROM_NUMBER_WITH_IDD, phoneUtil.maybeStripInternationalPrefixAndNormalize(
                numberToStrip, internationalPrefix
            )
        )
        assertEquals(
            strippedNumber.toString(),
            numberToStrip.toString(),
            "The number supplied was not stripped of its international prefix.",
        )
        // Now the number no longer starts with an IDD prefix, so it should now report
        // FROM_DEFAULT_COUNTRY.
        assertEquals(
            CountryCodeSource.FROM_DEFAULT_COUNTRY, phoneUtil.maybeStripInternationalPrefixAndNormalize(
                numberToStrip, internationalPrefix
            )
        )
        numberToStrip = InplaceStringBuilder("00945677003898003")
        assertEquals(
            CountryCodeSource.FROM_NUMBER_WITH_IDD, phoneUtil.maybeStripInternationalPrefixAndNormalize(
                numberToStrip, internationalPrefix
            )
        )
        assertEquals(
            strippedNumber.toString(),
            numberToStrip.toString(),
            "The number supplied was not stripped of its international prefix.",
        )
        // Test it works when the international prefix is broken up by spaces.
        numberToStrip = InplaceStringBuilder("00 9 45677003898003")
        assertEquals(
            CountryCodeSource.FROM_NUMBER_WITH_IDD, phoneUtil.maybeStripInternationalPrefixAndNormalize(
                numberToStrip, internationalPrefix
            )
        )
        assertEquals(
            strippedNumber.toString(),
            numberToStrip.toString(),
            "The number supplied was not stripped of its international prefix.",
        )
        // Now the number no longer starts with an IDD prefix, so it should now report
        // FROM_DEFAULT_COUNTRY.
        assertEquals(
            CountryCodeSource.FROM_DEFAULT_COUNTRY, phoneUtil.maybeStripInternationalPrefixAndNormalize(
                numberToStrip, internationalPrefix
            )
        )

        // Test the + symbol is also recognised and stripped.
        numberToStrip = InplaceStringBuilder("+45677003898003")
        strippedNumber = InplaceStringBuilder("45677003898003")
        assertEquals(
            CountryCodeSource.FROM_NUMBER_WITH_PLUS_SIGN, phoneUtil.maybeStripInternationalPrefixAndNormalize(
                numberToStrip, internationalPrefix
            )
        )
        assertEquals(
            strippedNumber.toString(),
            numberToStrip.toString(),
            "The number supplied was not stripped of the plus symbol.",
        )

        // If the number afterwards is a zero, we should not strip this - no country calling code begins
        // with 0.
        numberToStrip = InplaceStringBuilder("0090112-3123")
        strippedNumber = InplaceStringBuilder("00901123123")
        assertEquals(
            CountryCodeSource.FROM_DEFAULT_COUNTRY, phoneUtil.maybeStripInternationalPrefixAndNormalize(
                numberToStrip, internationalPrefix
            )
        )
        assertEquals(
            strippedNumber.toString(),
            numberToStrip.toString(),
            "The number supplied had a 0 after the match so shouldn't be stripped.",
        )
        // Here the 0 is separated by a space from the IDD.
        numberToStrip = InplaceStringBuilder("009 0-112-3123")
        assertEquals(
            CountryCodeSource.FROM_DEFAULT_COUNTRY, phoneUtil.maybeStripInternationalPrefixAndNormalize(
                numberToStrip, internationalPrefix
            )
        )
    }

    @Test
    fun testMaybeExtractCountryCode() {
        val number = PhoneNumber()
        val metadata = phoneUtil.getMetadataForRegion(RegionCode.US)
        // Note that for the US, the IDD is 011.
        try {
            val phoneNumber = "011112-3456789"
            val strippedNumber = "123456789"
            val countryCallingCode = 1
            val numberToFill = InplaceStringBuilder()
            assertEquals(
                countryCallingCode,
                phoneUtil.maybeExtractCountryCode(
                    phoneNumber, metadata, numberToFill, true, number
                ),
                "Did not extract country calling code $countryCallingCode correctly.",
            )
            assertEquals(
                CountryCodeSource.FROM_NUMBER_WITH_IDD,
                number.countryCodeSource,
                "Did not figure out CountryCodeSource correctly",
            )
            // Should strip and normalize national significant number.
            assertEquals(
                strippedNumber, numberToFill.toString(), "Did not strip off the country calling code correctly.",
            )
        } catch (e: NumberParseException) {
            fail("Should not have thrown an exception: $e")
        }
        number.clear()
        try {
            val phoneNumber = "+6423456789"
            val countryCallingCode = 64
            val numberToFill = InplaceStringBuilder()
            assertEquals(
                countryCallingCode,
                phoneUtil.maybeExtractCountryCode(
                    phoneNumber, metadata, numberToFill, true, number
                ),
                "Did not extract country calling code $countryCallingCode correctly.",
            )
            assertEquals(
                CountryCodeSource.FROM_NUMBER_WITH_PLUS_SIGN,
                number.countryCodeSource,
                "Did not figure out CountryCodeSource correctly",
            )
        } catch (e: NumberParseException) {
            fail("Should not have thrown an exception: $e")
        }
        number.clear()
        try {
            val phoneNumber = "+80012345678"
            val countryCallingCode = 800
            val numberToFill = InplaceStringBuilder()
            assertEquals(
                countryCallingCode,
                phoneUtil.maybeExtractCountryCode(
                    phoneNumber, metadata, numberToFill, true, number
                ),
                "Did not extract country calling code $countryCallingCode correctly.",
            )
            assertEquals(
                CountryCodeSource.FROM_NUMBER_WITH_PLUS_SIGN,
                number.countryCodeSource,
                "Did not figure out CountryCodeSource correctly",
            )
        } catch (e: NumberParseException) {
            fail("Should not have thrown an exception: $e")
        }
        number.clear()
        try {
            val phoneNumber = "2345-6789"
            val numberToFill = InplaceStringBuilder()
            assertEquals(
                0,
                phoneUtil.maybeExtractCountryCode(phoneNumber, metadata, numberToFill, true, number),
                "Should not have extracted a country calling code - no international prefix present.",
            )
            assertEquals(
                CountryCodeSource.FROM_DEFAULT_COUNTRY,
                number.countryCodeSource,
                "Did not figure out CountryCodeSource correctly",
            )
        } catch (e: NumberParseException) {
            fail("Should not have thrown an exception: $e")
        }
        number.clear()
        try {
            val phoneNumber = "0119991123456789"
            val numberToFill = InplaceStringBuilder()
            phoneUtil.maybeExtractCountryCode(phoneNumber, metadata, numberToFill, true, number)
            fail("Should have thrown an exception, no valid country calling code present.")
        } catch (e: NumberParseException) {
            // Expected.
            assertEquals(
                NumberParseException.ErrorType.INVALID_COUNTRY_CODE,
                e.errorType,
                "Wrong error type stored in exception.",
            )
        }
        number.clear()
        try {
            val phoneNumber = "(1 610) 619 4466"
            val countryCallingCode = 1
            val numberToFill = InplaceStringBuilder()
            assertEquals(
                countryCallingCode,
                phoneUtil.maybeExtractCountryCode(
                    phoneNumber, metadata, numberToFill, true, number
                ),
                "Should have extracted the country calling code of the region passed in",
            )
            assertEquals(
                CountryCodeSource.FROM_NUMBER_WITHOUT_PLUS_SIGN,
                number.countryCodeSource,
                "Did not figure out CountryCodeSource correctly",
            )
        } catch (e: NumberParseException) {
            fail("Should not have thrown an exception: $e")
        }
        number.clear()
        try {
            val phoneNumber = "(1 610) 619 4466"
            val countryCallingCode = 1
            val numberToFill = InplaceStringBuilder()
            assertEquals(
                countryCallingCode,
                phoneUtil.maybeExtractCountryCode(
                    phoneNumber, metadata, numberToFill, false, number
                ),
                "Should have extracted the country calling code of the region passed in",
            )
            assertFalse(number.hasCountryCodeSource(), "Should not contain CountryCodeSource.")
        } catch (e: NumberParseException) {
            fail("Should not have thrown an exception: $e")
        }
        number.clear()
        try {
            val phoneNumber = "(1 610) 619 446"
            val numberToFill = InplaceStringBuilder()
            assertEquals(
                0,
                phoneUtil.maybeExtractCountryCode(phoneNumber, metadata, numberToFill, false, number),
                "Should not have extracted a country calling code - invalid number after " + "extraction of uncertain country calling code.",
            )
            assertFalse(number.hasCountryCodeSource(), "Should not contain CountryCodeSource.")
        } catch (e: NumberParseException) {
            fail("Should not have thrown an exception: $e")
        }
        number.clear()
        try {
            val phoneNumber = "(1 610) 619"
            val numberToFill = InplaceStringBuilder()
            assertEquals(
                0,
                phoneUtil.maybeExtractCountryCode(phoneNumber, metadata, numberToFill, true, number),
                "Should not have extracted a country calling code - too short number both " + "before and after extraction of uncertain country calling code.",
            )
            assertEquals(
                CountryCodeSource.FROM_DEFAULT_COUNTRY,
                number.countryCodeSource,
                "Did not figure out CountryCodeSource correctly",
            )
        } catch (e: NumberParseException) {
            fail("Should not have thrown an exception: $e")
        }
    }

    @Throws(Exception::class)
    @Test
    fun testParseNationalNumber() {
        // National prefix attached.
        assertEquals(NZ_NUMBER, phoneUtil.parse("033316005", RegionCode.NZ))
        // Some fields are not filled in by parse, but only by parseAndKeepRawInput.
        assertFalse(NZ_NUMBER.hasCountryCodeSource())
        assertEquals(CountryCodeSource.UNSPECIFIED, NZ_NUMBER.countryCodeSource)
        assertEquals(NZ_NUMBER, phoneUtil.parse("33316005", RegionCode.NZ))
        // National prefix attached and some formatting present.
        assertEquals(NZ_NUMBER, phoneUtil.parse("03-331 6005", RegionCode.NZ))
        assertEquals(NZ_NUMBER, phoneUtil.parse("03 331 6005", RegionCode.NZ))
        // Test parsing RFC3966 format with a phone context.
        assertEquals(NZ_NUMBER, phoneUtil.parse("tel:03-331-6005;phone-context=+64", RegionCode.NZ))
        assertEquals(NZ_NUMBER, phoneUtil.parse("tel:331-6005;phone-context=+64-3", RegionCode.NZ))
        assertEquals(NZ_NUMBER, phoneUtil.parse("tel:331-6005;phone-context=+64-3", RegionCode.US))
        assertEquals(
            NZ_NUMBER, phoneUtil.parse(
                "My number is tel:03-331-6005;phone-context=+64", RegionCode.NZ
            )
        )
        // Test parsing RFC3966 format with optional user-defined parameters. The parameters will appear
        // after the context if present.
        assertEquals(
            NZ_NUMBER, phoneUtil.parse(
                "tel:03-331-6005;phone-context=+64;a=%A1", RegionCode.NZ
            )
        )
        // Test parsing RFC3966 with an ISDN subaddress.
        assertEquals(
            NZ_NUMBER, phoneUtil.parse(
                "tel:03-331-6005;isub=12345;phone-context=+64", RegionCode.NZ
            )
        )
        assertEquals(NZ_NUMBER, phoneUtil.parse("tel:+64-3-331-6005;isub=12345", RegionCode.NZ))
        // Test parsing RFC3966 with "tel:" missing.
        assertEquals(NZ_NUMBER, phoneUtil.parse("03-331-6005;phone-context=+64", RegionCode.NZ))
        // Testing international prefixes.
        // Should strip country calling code.
        assertEquals(NZ_NUMBER, phoneUtil.parse("0064 3 331 6005", RegionCode.NZ))
        // Try again, but this time we have an international number with Region Code US. It should
        // recognise the country calling code and parse accordingly.
        assertEquals(NZ_NUMBER, phoneUtil.parse("01164 3 331 6005", RegionCode.US))
        assertEquals(NZ_NUMBER, phoneUtil.parse("+64 3 331 6005", RegionCode.US))
        // We should ignore the leading plus here, since it is not followed by a valid country code but
        // instead is followed by the IDD for the US.
        assertEquals(NZ_NUMBER, phoneUtil.parse("+01164 3 331 6005", RegionCode.US))
        assertEquals(NZ_NUMBER, phoneUtil.parse("+0064 3 331 6005", RegionCode.NZ))
        assertEquals(NZ_NUMBER, phoneUtil.parse("+ 00 64 3 331 6005", RegionCode.NZ))
        assertEquals(
            US_LOCAL_NUMBER, phoneUtil.parse("tel:253-0000;phone-context=www.google.com", RegionCode.US)
        )
        assertEquals(
            US_LOCAL_NUMBER, phoneUtil.parse("tel:253-0000;isub=12345;phone-context=www.google.com", RegionCode.US)
        )
        assertEquals(
            US_LOCAL_NUMBER, phoneUtil.parse("tel:2530000;isub=12345;phone-context=1234.com", RegionCode.US)
        )
        val nzNumber = PhoneNumber()
        nzNumber.setCountryCode(64).setNationalNumber(64123456L)
        assertEquals(nzNumber, phoneUtil.parse("64(0)64123456", RegionCode.NZ))
        // Check that using a "/" is fine in a phone number.
        assertEquals(DE_NUMBER, phoneUtil.parse("301/23456", RegionCode.DE))
        val usNumber = PhoneNumber()
        // Check it doesn't use the '1' as a country calling code when parsing if the phone number was
        // already possible.
        usNumber.setCountryCode(1).setNationalNumber(1234567890L)
        assertEquals(usNumber, phoneUtil.parse("123-456-7890", RegionCode.US))

        // Test star numbers. Although this is not strictly valid, we would like to make sure we can
        // parse the output we produce when formatting the number.
        assertEquals(JP_STAR_NUMBER, phoneUtil.parse("+81 *2345", RegionCode.JP))
        val shortNumber = PhoneNumber()
        shortNumber.setCountryCode(64).setNationalNumber(12L)
        assertEquals(shortNumber, phoneUtil.parse("12", RegionCode.NZ))

        // Test for short-code with leading zero for a country which has 0 as national prefix. Ensure
        // it's not interpreted as national prefix if the remaining number length is local-only in
        // terms of length. Example: In GB, length 6-7 are only possible local-only.
        shortNumber.setCountryCode(44).setNationalNumber(123456).setItalianLeadingZero(true)
        assertEquals(shortNumber, phoneUtil.parse("0123456", RegionCode.GB))
    }

    @Throws(Exception::class)
    @Test
    fun testParseNumberWithAlphaCharacters() {
        // Test case with alpha characters.
        val tollfreeNumber = PhoneNumber()
        tollfreeNumber.setCountryCode(64).setNationalNumber(800332005L)
        assertEquals(tollfreeNumber, phoneUtil.parse("0800 DDA 005", RegionCode.NZ))
        val premiumNumber = PhoneNumber()
        premiumNumber.setCountryCode(64).setNationalNumber(9003326005L)
        assertEquals(premiumNumber, phoneUtil.parse("0900 DDA 6005", RegionCode.NZ))
        // Not enough alpha characters for them to be considered intentional, so they are stripped.
        assertEquals(premiumNumber, phoneUtil.parse("0900 332 6005a", RegionCode.NZ))
        assertEquals(premiumNumber, phoneUtil.parse("0900 332 600a5", RegionCode.NZ))
        assertEquals(premiumNumber, phoneUtil.parse("0900 332 600A5", RegionCode.NZ))
        assertEquals(premiumNumber, phoneUtil.parse("0900 a332 600A5", RegionCode.NZ))
    }

    @Throws(Exception::class)
    @Test
    fun testParseMaliciousInput() {
        // Lots of leading + signs before the possible number.
        val maliciousNumber = InplaceStringBuilder(6000)
        for (i in 0..5999) {
            maliciousNumber.append('+')
        }
        maliciousNumber.append("12222-33-244 extensioB 343+")
        try {
            phoneUtil.parse(maliciousNumber.toString(), RegionCode.US)
            fail("This should not parse without throwing an exception $maliciousNumber")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.TOO_LONG, e.errorType, "Wrong error type stored in exception."
            )
        }
        val maliciousNumberWithAlmostExt = InplaceStringBuilder(6000)
        for (i in 0..349) {
            maliciousNumberWithAlmostExt.append("200")
        }
        maliciousNumberWithAlmostExt.append(" extensiOB 345")
        try {
            phoneUtil.parse(maliciousNumberWithAlmostExt.toString(), RegionCode.US)
            fail("This should not parse without throwing an exception $maliciousNumberWithAlmostExt")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.TOO_LONG, e.errorType, "Wrong error type stored in exception."
            )
        }
    }

    @Throws(Exception::class)
    @Test
    fun testParseWithInternationalPrefixes() {
        assertEquals(US_NUMBER, phoneUtil.parse("+1 (650) 253-0000", RegionCode.NZ))
        assertEquals(INTERNATIONAL_TOLL_FREE, phoneUtil.parse("011 800 1234 5678", RegionCode.US))
        assertEquals(US_NUMBER, phoneUtil.parse("1-650-253-0000", RegionCode.US))
        // Calling the US number from Singapore by using different service providers
        // 1st test: calling using SingTel IDD service (IDD is 001)
        assertEquals(US_NUMBER, phoneUtil.parse("0011-650-253-0000", RegionCode.SG))
        // 2nd test: calling using StarHub IDD service (IDD is 008)
        assertEquals(US_NUMBER, phoneUtil.parse("0081-650-253-0000", RegionCode.SG))
        // 3rd test: calling using SingTel V019 service (IDD is 019)
        assertEquals(US_NUMBER, phoneUtil.parse("0191-650-253-0000", RegionCode.SG))
        // Calling the US number from Poland
        assertEquals(US_NUMBER, phoneUtil.parse("0~01-650-253-0000", RegionCode.PL))
        // Using "++" at the start.
        assertEquals(US_NUMBER, phoneUtil.parse("++1 (650) 253-0000", RegionCode.PL))
    }

    @Throws(Exception::class)
    @Test
    fun testParseNonAscii() {
        // Using a full-width plus sign.
        assertEquals(US_NUMBER, phoneUtil.parse("\uFF0B1 (650) 253-0000", RegionCode.SG))
        // Using a soft hyphen U+00AD.
        assertEquals(US_NUMBER, phoneUtil.parse("1 (650) 253\u00AD-0000", RegionCode.US))
        // The whole number, including punctuation, is here represented in full-width form.
        assertEquals(
            US_NUMBER, phoneUtil.parse(
                "\uFF0B\uFF11\u3000\uFF08\uFF16\uFF15\uFF10\uFF09" + "\u3000\uFF12\uFF15\uFF13\uFF0D\uFF10\uFF10\uFF10\uFF10",
                RegionCode.SG
            )
        )
        // Using U+30FC dash instead.
        assertEquals(
            US_NUMBER, phoneUtil.parse(
                "\uFF0B\uFF11\u3000\uFF08\uFF16\uFF15\uFF10\uFF09" + "\u3000\uFF12\uFF15\uFF13\u30FC\uFF10\uFF10\uFF10\uFF10",
                RegionCode.SG
            )
        )

        // Using a very strange decimal digit range (Mongolian digits).
        assertEquals(
            US_NUMBER, phoneUtil.parse(
                "\u1811 \u1816\u1815\u1810 " + "\u1812\u1815\u1813 \u1810\u1810\u1810\u1810", RegionCode.US
            )
        )
    }

    @Throws(Exception::class)
    @Test
    fun testParseWithLeadingZero() {
        assertEquals(IT_NUMBER, phoneUtil.parse("+39 02-36618 300", RegionCode.NZ))
        assertEquals(IT_NUMBER, phoneUtil.parse("02-36618 300", RegionCode.IT))
        assertEquals(IT_MOBILE, phoneUtil.parse("345 678 901", RegionCode.IT))
    }

    @Throws(Exception::class)
    @Test
    fun testParseNationalNumberArgentina() {
        // Test parsing mobile numbers of Argentina.
        val arNumber = PhoneNumber()
        arNumber.setCountryCode(54).setNationalNumber(93435551212L)
        assertEquals(arNumber, phoneUtil.parse("+54 9 343 555 1212", RegionCode.AR))
        assertEquals(arNumber, phoneUtil.parse("0343 15 555 1212", RegionCode.AR))
        arNumber.clear()
        arNumber.setCountryCode(54).setNationalNumber(93715654320L)
        assertEquals(arNumber, phoneUtil.parse("+54 9 3715 65 4320", RegionCode.AR))
        assertEquals(arNumber, phoneUtil.parse("03715 15 65 4320", RegionCode.AR))
        assertEquals(AR_MOBILE, phoneUtil.parse("911 876 54321", RegionCode.AR))

        // Test parsing fixed-line numbers of Argentina.
        assertEquals(AR_NUMBER, phoneUtil.parse("+54 11 8765 4321", RegionCode.AR))
        assertEquals(AR_NUMBER, phoneUtil.parse("011 8765 4321", RegionCode.AR))
        arNumber.clear()
        arNumber.setCountryCode(54).setNationalNumber(3715654321L)
        assertEquals(arNumber, phoneUtil.parse("+54 3715 65 4321", RegionCode.AR))
        assertEquals(arNumber, phoneUtil.parse("03715 65 4321", RegionCode.AR))
        arNumber.clear()
        arNumber.setCountryCode(54).setNationalNumber(2312340000L)
        assertEquals(arNumber, phoneUtil.parse("+54 23 1234 0000", RegionCode.AR))
        assertEquals(arNumber, phoneUtil.parse("023 1234 0000", RegionCode.AR))
    }

    @Throws(Exception::class)
    @Test
    fun testParseWithXInNumber() {
        // Test that having an 'x' in the phone number at the start is ok and that it just gets removed.
        assertEquals(AR_NUMBER, phoneUtil.parse("01187654321", RegionCode.AR))
        assertEquals(AR_NUMBER, phoneUtil.parse("(0) 1187654321", RegionCode.AR))
        assertEquals(AR_NUMBER, phoneUtil.parse("0 1187654321", RegionCode.AR))
        assertEquals(AR_NUMBER, phoneUtil.parse("(0xx) 1187654321", RegionCode.AR))
        val arFromUs = PhoneNumber()
        arFromUs.setCountryCode(54).setNationalNumber(81429712L)
        // This test is intentionally constructed such that the number of digit after xx is larger than
        // 7, so that the number won't be mistakenly treated as an extension, as we allow extensions up
        // to 7 digits. This assumption is okay for now as all the countries where a carrier selection
        // code is written in the form of xx have a national significant number of length larger than 7.
        assertEquals(arFromUs, phoneUtil.parse("011xx5481429712", RegionCode.US))
    }

    @Throws(Exception::class)
    @Test
    fun testParseNumbersMexico() {
        // Test parsing fixed-line numbers of Mexico.
        val mxNumber = PhoneNumber()
        mxNumber.setCountryCode(52).setNationalNumber(4499780001L)
        assertEquals(mxNumber, phoneUtil.parse("+52 (449)978-0001", RegionCode.MX))
        assertEquals(mxNumber, phoneUtil.parse("01 (449)978-0001", RegionCode.MX))
        assertEquals(mxNumber, phoneUtil.parse("(449)978-0001", RegionCode.MX))

        // Test parsing mobile numbers of Mexico.
        mxNumber.clear()
        mxNumber.setCountryCode(52).setNationalNumber(13312345678L)
        assertEquals(mxNumber, phoneUtil.parse("+52 1 33 1234-5678", RegionCode.MX))
        assertEquals(mxNumber, phoneUtil.parse("044 (33) 1234-5678", RegionCode.MX))
        assertEquals(mxNumber, phoneUtil.parse("045 33 1234-5678", RegionCode.MX))
    }

    @Test
    fun testFailedParseOnInvalidNumbers() {
        try {
            val sentencePhoneNumber = "This is not a phone number"
            phoneUtil.parse(sentencePhoneNumber, RegionCode.NZ)
            fail("This should not parse without throwing an exception $sentencePhoneNumber")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.NOT_A_NUMBER, e.errorType, "Wrong error type stored in exception."
            )
        }
        try {
            val sentencePhoneNumber = "1 Still not a number"
            phoneUtil.parse(sentencePhoneNumber, RegionCode.NZ)
            fail("This should not parse without throwing an exception $sentencePhoneNumber")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.NOT_A_NUMBER, e.errorType, "Wrong error type stored in exception.",
            )
        }
        try {
            val sentencePhoneNumber = "1 MICROSOFT"
            phoneUtil.parse(sentencePhoneNumber, RegionCode.NZ)
            fail("This should not parse without throwing an exception $sentencePhoneNumber")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.NOT_A_NUMBER, e.errorType, "Wrong error type stored in exception.",
            )
        }
        try {
            val sentencePhoneNumber = "12 MICROSOFT"
            phoneUtil.parse(sentencePhoneNumber, RegionCode.NZ)
            fail("This should not parse without throwing an exception $sentencePhoneNumber")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.NOT_A_NUMBER, e.errorType, "Wrong error type stored in exception.",
            )
        }
        try {
            val tooLongPhoneNumber = "01495 72553301873 810104"
            phoneUtil.parse(tooLongPhoneNumber, RegionCode.GB)
            fail("This should not parse without throwing an exception $tooLongPhoneNumber")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.TOO_LONG, e.errorType, "Wrong error type stored in exception.",
            )
        }
        try {
            val plusMinusPhoneNumber = "+---"
            phoneUtil.parse(plusMinusPhoneNumber, RegionCode.DE)
            fail("This should not parse without throwing an exception $plusMinusPhoneNumber")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.NOT_A_NUMBER, e.errorType, "Wrong error type stored in exception.",
            )
        }
        try {
            val plusStar = "+***"
            phoneUtil.parse(plusStar, RegionCode.DE)
            fail("This should not parse without throwing an exception $plusStar")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.NOT_A_NUMBER, e.errorType, "Wrong error type stored in exception.",
            )
        }
        try {
            val plusStarPhoneNumber = "+*******91"
            phoneUtil.parse(plusStarPhoneNumber, RegionCode.DE)
            fail("This should not parse without throwing an exception $plusStarPhoneNumber")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.NOT_A_NUMBER, e.errorType, "Wrong error type stored in exception.",
            )
        }
        try {
            val tooShortPhoneNumber = "+49 0"
            phoneUtil.parse(tooShortPhoneNumber, RegionCode.DE)
            fail("This should not parse without throwing an exception $tooShortPhoneNumber")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.TOO_SHORT_NSN, e.errorType, "Wrong error type stored in exception.",
            )
        }
        try {
            val invalidCountryCode = "+210 3456 56789"
            phoneUtil.parse(invalidCountryCode, RegionCode.NZ)
            fail("This is not a recognised region code: should fail: $invalidCountryCode")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.INVALID_COUNTRY_CODE,
                e.errorType,
                "Wrong error type stored in exception.",
            )
        }
        try {
            val plusAndIddAndInvalidCountryCode = "+ 00 210 3 331 6005"
            phoneUtil.parse(plusAndIddAndInvalidCountryCode, RegionCode.NZ)
            fail("This should not parse without throwing an exception.")
        } catch (e: NumberParseException) {
            // Expected this exception. 00 is a correct IDD, but 210 is not a valid country code.
            assertEquals(
                NumberParseException.ErrorType.INVALID_COUNTRY_CODE,
                e.errorType,
                "Wrong error type stored in exception.",
            )
        }
        try {
            val someNumber = "123 456 7890"
            phoneUtil.parse(someNumber, RegionCode.ZZ)
            fail("'Unknown' region code not allowed: should fail.")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.INVALID_COUNTRY_CODE,
                e.errorType,
                "Wrong error type stored in exception.",
            )
        }
        try {
            val someNumber = "123 456 7890"
            phoneUtil.parse(someNumber, RegionCode.CS)
            fail("Deprecated region code not allowed: should fail.")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.INVALID_COUNTRY_CODE,
                e.errorType,
                "Wrong error type stored in exception.",
            )
        }
        try {
            val someNumber = "123 456 7890"
            phoneUtil.parse(someNumber, null)
            fail("Null region code not allowed: should fail.")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.INVALID_COUNTRY_CODE,
                e.errorType,
                "Wrong error type stored in exception.",
            )
        }
        try {
            val someNumber = "0044------"
            phoneUtil.parse(someNumber, RegionCode.GB)
            fail("No number provided, only region code: should fail")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.TOO_SHORT_AFTER_IDD,
                e.errorType,
                "Wrong error type stored in exception.",
            )
        }
        try {
            val someNumber = "0044"
            phoneUtil.parse(someNumber, RegionCode.GB)
            fail("No number provided, only region code: should fail")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.TOO_SHORT_AFTER_IDD,
                e.errorType,
                "Wrong error type stored in exception.",
            )
        }
        try {
            val someNumber = "011"
            phoneUtil.parse(someNumber, RegionCode.US)
            fail("Only IDD provided - should fail.")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.TOO_SHORT_AFTER_IDD,
                e.errorType,
                "Wrong error type stored in exception.",
            )
        }
        try {
            val someNumber = "0119"
            phoneUtil.parse(someNumber, RegionCode.US)
            fail("Only IDD provided and then 9 - should fail.")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.TOO_SHORT_AFTER_IDD,
                e.errorType,
                "Wrong error type stored in exception.",
            )
        }
        try {
            val emptyNumber = ""
            // Invalid region.
            phoneUtil.parse(emptyNumber, RegionCode.ZZ)
            fail("Empty string - should fail.")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.NOT_A_NUMBER, e.errorType, "Wrong error type stored in exception.",
            )
        }
        try {
            val nullNumber: String? = null
            // Invalid region.
            phoneUtil.parse(nullNumber, RegionCode.ZZ)
            fail("Null string - should fail.")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.NOT_A_NUMBER, e.errorType, "Wrong error type stored in exception.",
            )
        } catch (e: NullPointerException) {
            fail("Null string - but should not throw a null pointer exception.")
        }
        try {
            val nullNumber: String? = null
            phoneUtil.parse(nullNumber, RegionCode.US)
            fail("Null string - should fail.")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.NOT_A_NUMBER, e.errorType, "Wrong error type stored in exception.",
            )
        } catch (e: NullPointerException) {
            fail("Null string - but should not throw a null pointer exception.")
        }
        try {
            val domainRfcPhoneContext = "tel:555-1234;phone-context=www.google.com"
            phoneUtil.parse(domainRfcPhoneContext, RegionCode.ZZ)
            fail("'Unknown' region code not allowed: should fail.")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.INVALID_COUNTRY_CODE,
                e.errorType,
                "Wrong error type stored in exception.",
            )
        }
        try {
            // This is invalid because no "+" sign is present as part of phone-context. This should not
            // succeed in being parsed.
            val invalidRfcPhoneContext = "tel:555-1234;phone-context=1-331"
            phoneUtil.parse(invalidRfcPhoneContext, RegionCode.ZZ)
            fail("phone-context is missing '+' sign: should fail.")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.NOT_A_NUMBER, e.errorType, "Wrong error type stored in exception.",
            )
        }
        try {
            // Only the phone-context symbol is present, but no data.
            val invalidRfcPhoneContext = ";phone-context="
            phoneUtil.parse(invalidRfcPhoneContext, RegionCode.ZZ)
            fail("phone-context can't be empty: should fail.")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.NOT_A_NUMBER, e.errorType, "Wrong error type stored in exception.",
            )
        }
    }

    @Throws(Exception::class)
    @Test
    fun testParseNumbersWithPlusWithNoRegion() {
        // RegionCode.ZZ is allowed only if the number starts with a '+' - then the country calling code
        // can be calculated.
        assertEquals(NZ_NUMBER, phoneUtil.parse("+64 3 331 6005", RegionCode.ZZ))
        // Test with full-width plus.
        assertEquals(NZ_NUMBER, phoneUtil.parse("\uFF0B64 3 331 6005", RegionCode.ZZ))
        // Test with normal plus but leading characters that need to be stripped.
        assertEquals(NZ_NUMBER, phoneUtil.parse("Tel: +64 3 331 6005", RegionCode.ZZ))
        assertEquals(NZ_NUMBER, phoneUtil.parse("+64 3 331 6005", null))
        assertEquals(INTERNATIONAL_TOLL_FREE, phoneUtil.parse("+800 1234 5678", null))
        assertEquals(UNIVERSAL_PREMIUM_RATE, phoneUtil.parse("+979 123 456 789", null))

        // Test parsing RFC3966 format with a phone context.
        assertEquals(NZ_NUMBER, phoneUtil.parse("tel:03-331-6005;phone-context=+64", RegionCode.ZZ))
        assertEquals(NZ_NUMBER, phoneUtil.parse("  tel:03-331-6005;phone-context=+64", RegionCode.ZZ))
        assertEquals(
            NZ_NUMBER, phoneUtil.parse(
                "tel:03-331-6005;isub=12345;phone-context=+64", RegionCode.ZZ
            )
        )
        val nzNumberWithRawInput = PhoneNumber().mergeFrom(NZ_NUMBER).setRawInput("+64 3 331 6005")
            .setCountryCodeSource(CountryCodeSource.FROM_NUMBER_WITH_PLUS_SIGN)
        assertEquals(
            nzNumberWithRawInput, phoneUtil.parseAndKeepRawInput(
                "+64 3 331 6005", RegionCode.ZZ
            )
        )
        // Null is also allowed for the region code in these cases.
        assertEquals(nzNumberWithRawInput, phoneUtil.parseAndKeepRawInput("+64 3 331 6005", null))
    }

    @Throws(Exception::class)
    @Test
    fun testParseNumberTooShortIfNationalPrefixStripped() {
        // Test that a number whose first digits happen to coincide with the national prefix does not
        // get them stripped if doing so would result in a number too short to be a possible (regular
        // length) phone number for that region.
        val byNumber = PhoneNumber().setCountryCode(375).setNationalNumber(8123L)
        assertEquals(byNumber, phoneUtil.parse("8123", RegionCode.BY))
        byNumber.setNationalNumber(81234L)
        assertEquals(byNumber, phoneUtil.parse("81234", RegionCode.BY))

        // The prefix doesn't get stripped, since the input is a viable 6-digit number, whereas the
        // result of stripping is only 5 digits.
        byNumber.setNationalNumber(812345L)
        assertEquals(byNumber, phoneUtil.parse("812345", RegionCode.BY))

        // The prefix gets stripped, since only 6-digit numbers are possible.
        byNumber.setNationalNumber(123456L)
        assertEquals(byNumber, phoneUtil.parse("8123456", RegionCode.BY))
    }

    @Throws(Exception::class)
    @Test
    fun testParseExtensions() {
        val nzNumber = PhoneNumber()
        nzNumber.setCountryCode(64).setNationalNumber(33316005L).setExtension("3456")
        assertEquals(nzNumber, phoneUtil.parse("03 331 6005 ext 3456", RegionCode.NZ))
        assertEquals(nzNumber, phoneUtil.parse("03-3316005x3456", RegionCode.NZ))
        assertEquals(nzNumber, phoneUtil.parse("03-3316005 int.3456", RegionCode.NZ))
        assertEquals(nzNumber, phoneUtil.parse("03 3316005 #3456", RegionCode.NZ))
        // Test the following do not extract extensions:
        assertEquals(ALPHA_NUMERIC_NUMBER, phoneUtil.parse("1800 six-flags", RegionCode.US))
        assertEquals(ALPHA_NUMERIC_NUMBER, phoneUtil.parse("1800 SIX FLAGS", RegionCode.US))
        assertEquals(ALPHA_NUMERIC_NUMBER, phoneUtil.parse("0~0 1800 7493 5247", RegionCode.PL))
        assertEquals(ALPHA_NUMERIC_NUMBER, phoneUtil.parse("(1800) 7493.5247", RegionCode.US))
        // Check that the last instance of an extension token is matched.
        val extnNumber = PhoneNumber().mergeFrom(ALPHA_NUMERIC_NUMBER).setExtension("1234")
        assertEquals(extnNumber, phoneUtil.parse("0~0 1800 7493 5247 ~1234", RegionCode.PL))
        // Verifying bug-fix where the last digit of a number was previously omitted if it was a 0 when
        // extracting the extension. Also verifying a few different cases of extensions.
        val ukNumber = PhoneNumber()
        ukNumber.setCountryCode(44).setNationalNumber(2034567890L).setExtension("456")
        assertEquals(ukNumber, phoneUtil.parse("+44 2034567890x456", RegionCode.NZ))
        assertEquals(ukNumber, phoneUtil.parse("+44 2034567890x456", RegionCode.GB))
        assertEquals(ukNumber, phoneUtil.parse("+44 2034567890 x456", RegionCode.GB))
        assertEquals(ukNumber, phoneUtil.parse("+44 2034567890 X456", RegionCode.GB))
        assertEquals(ukNumber, phoneUtil.parse("+44 2034567890 X 456", RegionCode.GB))
        assertEquals(ukNumber, phoneUtil.parse("+44 2034567890 X  456", RegionCode.GB))
        assertEquals(ukNumber, phoneUtil.parse("+44 2034567890 x 456  ", RegionCode.GB))
        assertEquals(ukNumber, phoneUtil.parse("+44 2034567890  X 456", RegionCode.GB))
        assertEquals(ukNumber, phoneUtil.parse("+44-2034567890;ext=456", RegionCode.GB))
        assertEquals(
            ukNumber, phoneUtil.parse(
                "tel:2034567890;ext=456;phone-context=+44", RegionCode.ZZ
            )
        )
        // Full-width extension, "extn" only.
        assertEquals(
            ukNumber, phoneUtil.parse(
                "+442034567890\uFF45\uFF58\uFF54\uFF4E456", RegionCode.GB
            )
        )
        // "xtn" only.
        assertEquals(
            ukNumber, phoneUtil.parse(
                "+442034567890\uFF58\uFF54\uFF4E456", RegionCode.GB
            )
        )
        // "xt" only.
        assertEquals(
            ukNumber, phoneUtil.parse(
                "+442034567890\uFF58\uFF54456", RegionCode.GB
            )
        )
        val usWithExtension = PhoneNumber()
        usWithExtension.setCountryCode(1).setNationalNumber(8009013355L).setExtension("7246433")
        assertEquals(usWithExtension, phoneUtil.parse("(800) 901-3355 x 7246433", RegionCode.US))
        assertEquals(usWithExtension, phoneUtil.parse("(800) 901-3355 , ext 7246433", RegionCode.US))
        assertEquals(usWithExtension, phoneUtil.parse("(800) 901-3355 ; 7246433", RegionCode.US))
        // To test an extension character without surrounding spaces.
        assertEquals(usWithExtension, phoneUtil.parse("(800) 901-3355;7246433", RegionCode.US))
        assertEquals(
            usWithExtension, phoneUtil.parse("(800) 901-3355 ,extension 7246433", RegionCode.US)
        )
        assertEquals(
            usWithExtension, phoneUtil.parse("(800) 901-3355 ,extensi\u00F3n 7246433", RegionCode.US)
        )
        // Repeat with the small letter o with acute accent created by combining characters.
        assertEquals(
            usWithExtension, phoneUtil.parse("(800) 901-3355 ,extensio\u0301n 7246433", RegionCode.US)
        )
        assertEquals(usWithExtension, phoneUtil.parse("(800) 901-3355 , 7246433", RegionCode.US))
        assertEquals(usWithExtension, phoneUtil.parse("(800) 901-3355 ext: 7246433", RegionCode.US))
        // Testing Russian extension \u0434\u043E\u0431 with variants found online.
        val ruWithExtension = PhoneNumber()
        ruWithExtension.setCountryCode(7).setNationalNumber(4232022511L).setExtension("100")
        assertEquals(
            ruWithExtension, phoneUtil.parse("8 (423) 202-25-11, \u0434\u043E\u0431. 100", RegionCode.RU)
        )
        assertEquals(
            ruWithExtension, phoneUtil.parse("8 (423) 202-25-11 \u0434\u043E\u0431. 100", RegionCode.RU)
        )
        assertEquals(
            ruWithExtension, phoneUtil.parse("8 (423) 202-25-11, \u0434\u043E\u0431 100", RegionCode.RU)
        )
        assertEquals(
            ruWithExtension, phoneUtil.parse("8 (423) 202-25-11 \u0434\u043E\u0431 100", RegionCode.RU)
        )
        assertEquals(
            ruWithExtension, phoneUtil.parse("8 (423) 202-25-11\u0434\u043E\u0431100", RegionCode.RU)
        )
        // In upper case
        assertEquals(
            ruWithExtension, phoneUtil.parse("8 (423) 202-25-11, \u0414\u041E\u0411. 100", RegionCode.RU)
        )

        // Test that if a number has two extensions specified, we ignore the second.
        val usWithTwoExtensionsNumber = PhoneNumber()
        usWithTwoExtensionsNumber.setCountryCode(1).setNationalNumber(2121231234L).setExtension("508")
        assertEquals(
            usWithTwoExtensionsNumber, phoneUtil.parse(
                "(212)123-1234 x508/x1234", RegionCode.US
            )
        )
        assertEquals(
            usWithTwoExtensionsNumber, phoneUtil.parse(
                "(212)123-1234 x508/ x1234", RegionCode.US
            )
        )
        assertEquals(
            usWithTwoExtensionsNumber, phoneUtil.parse(
                "(212)123-1234 x508\\x1234", RegionCode.US
            )
        )

        // Test parsing numbers in the form (645) 123-1234-910# works, where the last 3 digits before
        // the # are an extension.
        usWithExtension.clear()
        usWithExtension.setCountryCode(1).setNationalNumber(6451231234L).setExtension("910")
        assertEquals(usWithExtension, phoneUtil.parse("+1 (645) 123 1234-910#", RegionCode.US))
        // Retry with the same number in a slightly different format.
        assertEquals(usWithExtension, phoneUtil.parse("+1 (645) 123 1234 ext. 910#", RegionCode.US))
    }

    @Throws(Exception::class)
    @Test
    fun testParseHandlesLongExtensionsWithExplicitLabels() {
        // Test lower and upper limits of extension lengths for each type of label.
        val nzNumber = PhoneNumber()
        nzNumber.setCountryCode(64).setNationalNumber(33316005L)

        // Firstly, when in RFC format: PhoneNumberUtil.extLimitAfterExplicitLabel
        nzNumber.setExtension("0")
        assertEquals(nzNumber, phoneUtil.parse("tel:+6433316005;ext=0", RegionCode.NZ))
        nzNumber.setExtension("01234567890123456789")
        assertEquals(
            nzNumber, phoneUtil.parse("tel:+6433316005;ext=01234567890123456789", RegionCode.NZ)
        )
        // Extension too long.
        try {
            phoneUtil.parse("tel:+6433316005;ext=012345678901234567890", RegionCode.NZ)
            fail(
                "This should not parse as length of extension is higher than allowed: " + "tel:+6433316005;ext=012345678901234567890"
            )
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.NOT_A_NUMBER, e.errorType, "Wrong error type stored in exception.",
            )
        }

        // Explicit extension label: PhoneNumberUtil.extLimitAfterExplicitLabel
        nzNumber.setExtension("1")
        assertEquals(nzNumber, phoneUtil.parse("03 3316005ext:1", RegionCode.NZ))
        nzNumber.setExtension("12345678901234567890")
        assertEquals(nzNumber, phoneUtil.parse("03 3316005 xtn:12345678901234567890", RegionCode.NZ))
        assertEquals(
            nzNumber, phoneUtil.parse("03 3316005 extension\t12345678901234567890", RegionCode.NZ)
        )
        assertEquals(
            nzNumber, phoneUtil.parse("03 3316005 xtensio:12345678901234567890", RegionCode.NZ)
        )
        assertEquals(
            nzNumber, phoneUtil.parse("03 3316005 xtensi\u00F3n, 12345678901234567890#", RegionCode.NZ)
        )
        assertEquals(
            nzNumber, phoneUtil.parse("03 3316005extension.12345678901234567890", RegionCode.NZ)
        )
        assertEquals(nzNumber, phoneUtil.parse("03 3316005 \u0434\u043E\u0431:12345678901234567890", RegionCode.NZ))
        // Extension too long.
        try {
            phoneUtil.parse("03 3316005 extension 123456789012345678901", RegionCode.NZ)
            fail(
                "This should not parse as length of extension is higher than allowed: " + "03 3316005 extension 123456789012345678901"
            )
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.TOO_LONG, e.errorType, "Wrong error type stored in exception.",
            )
        }
    }

    @Throws(Exception::class)
    @Test
    fun testParseHandlesLongExtensionsWithAutoDiallingLabels() {
        // Secondly, cases of auto-dialling and other standard extension labels,
        // PhoneNumberUtil.extLimitAfterLikelyLabel
        val usNumberUserInput = PhoneNumber()
        usNumberUserInput.setCountryCode(1).setNationalNumber(2679000000L)
        usNumberUserInput.setExtension("123456789012345")
        assertEquals(
            usNumberUserInput, phoneUtil.parse("+12679000000,,123456789012345#", RegionCode.US)
        )
        assertEquals(
            usNumberUserInput, phoneUtil.parse("+12679000000;123456789012345#", RegionCode.US)
        )
        val ukNumberUserInput = PhoneNumber()
        ukNumberUserInput.setCountryCode(44).setNationalNumber(2034000000L).setExtension("123456789")
        assertEquals(ukNumberUserInput, phoneUtil.parse("+442034000000,,123456789#", RegionCode.GB))
        // Extension too long.
        try {
            phoneUtil.parse("+12679000000,,1234567890123456#", RegionCode.US)
            fail(
                "This should not parse as length of extension is higher than allowed: " + "+12679000000,,1234567890123456#"
            )
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.NOT_A_NUMBER, e.errorType, "Wrong error type stored in exception.",
            )
        }
    }

    @Throws(Exception::class)
    @Test
    fun testParseHandlesShortExtensionsWithAmbiguousChar() {
        val nzNumber = PhoneNumber()
        nzNumber.setCountryCode(64).setNationalNumber(33316005L)

        // Thirdly, for single and non-standard cases:
        // PhoneNumberUtil.extLimitAfterAmbiguousChar
        nzNumber.setExtension("123456789")
        assertEquals(nzNumber, phoneUtil.parse("03 3316005 x 123456789", RegionCode.NZ))
        assertEquals(nzNumber, phoneUtil.parse("03 3316005 x. 123456789", RegionCode.NZ))
        assertEquals(nzNumber, phoneUtil.parse("03 3316005 #123456789#", RegionCode.NZ))
        assertEquals(nzNumber, phoneUtil.parse("03 3316005 ~ 123456789", RegionCode.NZ))
        // Extension too long.
        try {
            phoneUtil.parse("03 3316005 ~ 1234567890", RegionCode.NZ)
            fail(
                "This should not parse as length of extension is higher than allowed: " + "03 3316005 ~ 1234567890"
            )
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.TOO_LONG, e.errorType, "Wrong error type stored in exception.",
            )
        }
    }

    @Throws(Exception::class)
    @Test
    fun testParseHandlesShortExtensionsWhenNotSureOfLabel() {
        // Lastly, when no explicit extension label present, but denoted by tailing #:
        // PhoneNumberUtil.extLimitWhenNotSure
        val usNumber = PhoneNumber()
        usNumber.setCountryCode(1).setNationalNumber(1234567890L).setExtension("666666")
        assertEquals(usNumber, phoneUtil.parse("+1123-456-7890 666666#", RegionCode.US))
        usNumber.setExtension("6")
        assertEquals(usNumber, phoneUtil.parse("+11234567890-6#", RegionCode.US))
        // Extension too long.
        try {
            phoneUtil.parse("+1123-456-7890 7777777#", RegionCode.US)
            fail(
                "This should not parse as length of extension is higher than allowed: " + "+1123-456-7890 7777777#"
            )
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.NOT_A_NUMBER, e.errorType, "Wrong error type stored in exception.",
            )
        }
    }

    @Throws(Exception::class)
    @Test
    fun testParseAndKeepRaw() {
        val alphaNumericNumber = PhoneNumber().mergeFrom(ALPHA_NUMERIC_NUMBER).setRawInput("800 six-flags")
            .setCountryCodeSource(CountryCodeSource.FROM_DEFAULT_COUNTRY)
        assertEquals(
            alphaNumericNumber, phoneUtil.parseAndKeepRawInput("800 six-flags", RegionCode.US)
        )
        val shorterAlphaNumber =
            PhoneNumber().setCountryCode(1).setNationalNumber(8007493524L).setRawInput("1800 six-flag")
                .setCountryCodeSource(CountryCodeSource.FROM_NUMBER_WITHOUT_PLUS_SIGN)
        assertEquals(
            shorterAlphaNumber, phoneUtil.parseAndKeepRawInput("1800 six-flag", RegionCode.US)
        )
        shorterAlphaNumber.setRawInput("+1800 six-flag")
            .setCountryCodeSource(CountryCodeSource.FROM_NUMBER_WITH_PLUS_SIGN)
        assertEquals(
            shorterAlphaNumber, phoneUtil.parseAndKeepRawInput("+1800 six-flag", RegionCode.NZ)
        )
        shorterAlphaNumber.setRawInput("001800 six-flag").setCountryCodeSource(CountryCodeSource.FROM_NUMBER_WITH_IDD)
        assertEquals(
            shorterAlphaNumber, phoneUtil.parseAndKeepRawInput("001800 six-flag", RegionCode.NZ)
        )

        // Invalid region code supplied.
        try {
            phoneUtil.parseAndKeepRawInput("123 456 7890", RegionCode.CS)
            fail("Deprecated region code not allowed: should fail.")
        } catch (e: NumberParseException) {
            // Expected this exception.
            assertEquals(
                NumberParseException.ErrorType.INVALID_COUNTRY_CODE,
                e.errorType,
                "Wrong error type stored in exception.",
            )
        }
        val koreanNumber = PhoneNumber()
        koreanNumber.setCountryCode(82).setNationalNumber(22123456).setRawInput("08122123456")
            .setCountryCodeSource(CountryCodeSource.FROM_DEFAULT_COUNTRY).setPreferredDomesticCarrierCode("81")
        assertEquals(koreanNumber, phoneUtil.parseAndKeepRawInput("08122123456", RegionCode.KR))
    }

    @Throws(Exception::class)
    @Test
    fun testParseItalianLeadingZeros() {
        // Test the number "011".
        val oneZero = PhoneNumber()
        oneZero.setCountryCode(61).setNationalNumber(11L).setItalianLeadingZero(true)
        assertEquals(oneZero, phoneUtil.parse("011", RegionCode.AU))

        // Test the number "001".
        val twoZeros = PhoneNumber()
        twoZeros.setCountryCode(61).setNationalNumber(1).setItalianLeadingZero(true).setNumberOfLeadingZeros(2)
        assertEquals(twoZeros, phoneUtil.parse("001", RegionCode.AU))

        // Test the number "000". This number has 2 leading zeros.
        val stillTwoZeros = PhoneNumber()
        stillTwoZeros.setCountryCode(61).setNationalNumber(0L).setItalianLeadingZero(true).setNumberOfLeadingZeros(2)
        assertEquals(stillTwoZeros, phoneUtil.parse("000", RegionCode.AU))

        // Test the number "0000". This number has 3 leading zeros.
        val threeZeros = PhoneNumber()
        threeZeros.setCountryCode(61).setNationalNumber(0L).setItalianLeadingZero(true).setNumberOfLeadingZeros(3)
        assertEquals(threeZeros, phoneUtil.parse("0000", RegionCode.AU))
    }

    @Throws(Exception::class)
    @Test
    fun testParseWithPhoneContext() {
        // context    = ";phone-context=" descriptor
        // descriptor = domainname / global-number-digits

        // Valid global-phone-digits
        assertEquals(NZ_NUMBER, phoneUtil.parse("tel:033316005;phone-context=+64", RegionCode.ZZ))
        assertEquals(
            NZ_NUMBER, phoneUtil.parse(
                "tel:033316005;phone-context=+64;{this isn't part of phone-context anymore!}", RegionCode.ZZ
            )
        )
        val nzFromPhoneContext = PhoneNumber()
        nzFromPhoneContext.setCountryCode(64).setNationalNumber(3033316005L)
        assertEquals(
            nzFromPhoneContext, phoneUtil.parse("tel:033316005;phone-context=+64-3", RegionCode.ZZ)
        )
        val brFromPhoneContext = PhoneNumber()
        brFromPhoneContext.setCountryCode(55).setNationalNumber(5033316005L)
        assertEquals(
            brFromPhoneContext, phoneUtil.parse("tel:033316005;phone-context=+(555)", RegionCode.ZZ)
        )
        val usFromPhoneContext = PhoneNumber()
        usFromPhoneContext.setCountryCode(1).setNationalNumber(23033316005L)
        assertEquals(
            usFromPhoneContext, phoneUtil.parse("tel:033316005;phone-context=+-1-2.3()", RegionCode.ZZ)
        )

        // Valid domainname
        assertEquals(NZ_NUMBER, phoneUtil.parse("tel:033316005;phone-context=abc.nz", RegionCode.NZ))
        assertEquals(
            NZ_NUMBER, phoneUtil.parse("tel:033316005;phone-context=www.PHONE-numb3r.com", RegionCode.NZ)
        )
        assertEquals(NZ_NUMBER, phoneUtil.parse("tel:033316005;phone-context=a", RegionCode.NZ))
        assertEquals(
            NZ_NUMBER, phoneUtil.parse("tel:033316005;phone-context=3phone.J.", RegionCode.NZ)
        )
        assertEquals(NZ_NUMBER, phoneUtil.parse("tel:033316005;phone-context=a--z", RegionCode.NZ))

        // Invalid descriptor
        assertThrowsForInvalidPhoneContext("tel:033316005;phone-context=")
        assertThrowsForInvalidPhoneContext("tel:033316005;phone-context=+")
        assertThrowsForInvalidPhoneContext("tel:033316005;phone-context=64")
        assertThrowsForInvalidPhoneContext("tel:033316005;phone-context=++64")
        assertThrowsForInvalidPhoneContext("tel:033316005;phone-context=+abc")
        assertThrowsForInvalidPhoneContext("tel:033316005;phone-context=.")
        assertThrowsForInvalidPhoneContext("tel:033316005;phone-context=3phone")
        assertThrowsForInvalidPhoneContext("tel:033316005;phone-context=a-.nz")
        assertThrowsForInvalidPhoneContext("tel:033316005;phone-context=a{b}c")
    }

    private fun assertThrowsForInvalidPhoneContext(numberToParse: String) {
        assertEquals(NumberParseException.ErrorType.NOT_A_NUMBER, (assertThrows(NumberParseException::class) {
            phoneUtil.parse(numberToParse, RegionCode.ZZ)
        } as NumberParseException).errorType)
    }

    @Test
    fun testCountryWithNoNumberDesc() {
        // Andorra is a country where we don't have PhoneNumberDesc info in the metadata.
        val adNumber = PhoneNumber()
        adNumber.setCountryCode(376).setNationalNumber(12345L)
        assertEquals("+376 12345", phoneUtil.format(adNumber, PhoneNumberFormat.INTERNATIONAL))
        assertEquals("+37612345", phoneUtil.format(adNumber, PhoneNumberFormat.E164))
        assertEquals("12345", phoneUtil.format(adNumber, PhoneNumberFormat.NATIONAL))
        assertEquals(PhoneNumberType.UNKNOWN, phoneUtil.getNumberType(adNumber))
        assertFalse(phoneUtil.isValidNumber(adNumber))

        // Test dialing a US number from within Andorra.
        assertEquals(
            "00 1 650 253 0000", phoneUtil.formatOutOfCountryCallingNumber(US_NUMBER, RegionCode.AD)
        )
    }

    @Test
    fun testUnknownCountryCallingCode() {
        assertFalse(phoneUtil.isValidNumber(UNKNOWN_COUNTRY_CODE_NO_RAW_INPUT))
        // It's not very well defined as to what the E164 representation for a number with an invalid
        // country calling code is, but just prefixing the country code and national number is about
        // the best we can do.
        assertEquals(
            "+212345", phoneUtil.format(UNKNOWN_COUNTRY_CODE_NO_RAW_INPUT, PhoneNumberFormat.E164)
        )
    }

    @Throws(Exception::class)
    @Test
    fun testIsNumberMatchMatches() {
        // Test simple matches where formatting is different, or leading zeros, or country calling code
        // has been specified.
        assertEquals(
            PhoneNumberUtil.MatchType.EXACT_MATCH, phoneUtil.isNumberMatch("+64 3 331 6005", "+64 03 331 6005")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.EXACT_MATCH, phoneUtil.isNumberMatch("+800 1234 5678", "+80012345678")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.EXACT_MATCH, phoneUtil.isNumberMatch("+64 03 331-6005", "+64 03331 6005")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.EXACT_MATCH, phoneUtil.isNumberMatch("+643 331-6005", "+64033316005")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.EXACT_MATCH, phoneUtil.isNumberMatch("+643 331-6005", "+6433316005")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.EXACT_MATCH, phoneUtil.isNumberMatch("+64 3 331-6005", "+6433316005")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.EXACT_MATCH,
            phoneUtil.isNumberMatch("+64 3 331-6005", "tel:+64-3-331-6005;isub=123")
        )
        // Test alpha numbers.
        assertEquals(
            PhoneNumberUtil.MatchType.EXACT_MATCH, phoneUtil.isNumberMatch("+1800 siX-Flags", "+1 800 7493 5247")
        )
        // Test numbers with extensions.
        assertEquals(
            PhoneNumberUtil.MatchType.EXACT_MATCH,
            phoneUtil.isNumberMatch("+64 3 331-6005 extn 1234", "+6433316005#1234")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.EXACT_MATCH,
            phoneUtil.isNumberMatch("+64 3 331-6005 ext. 1234", "+6433316005;1234")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.EXACT_MATCH, phoneUtil.isNumberMatch(
                "+7 423 202-25-11 ext 100", "+7 4232022511 \u0434\u043E\u0431. 100"
            )
        )
        // Test proto buffers.
        assertEquals(
            PhoneNumberUtil.MatchType.EXACT_MATCH, phoneUtil.isNumberMatch(NZ_NUMBER, "+6403 331 6005")
        )
        val nzNumber = PhoneNumber().mergeFrom(NZ_NUMBER).setExtension("3456")
        assertEquals(
            PhoneNumberUtil.MatchType.EXACT_MATCH, phoneUtil.isNumberMatch(nzNumber, "+643 331 6005 ext 3456")
        )
        // Check empty extensions are ignored.
        nzNumber.setExtension("")
        assertEquals(
            PhoneNumberUtil.MatchType.EXACT_MATCH, phoneUtil.isNumberMatch(nzNumber, "+6403 331 6005")
        )
        // Check variant with two proto buffers.
        assertEquals(
            PhoneNumberUtil.MatchType.EXACT_MATCH,
            phoneUtil.isNumberMatch(nzNumber, NZ_NUMBER),
            "Number $nzNumber did not match $NZ_NUMBER",
        )
    }

    @Throws(Exception::class)
    @Test
    fun testIsNumberMatchShortMatchIfDiffNumLeadingZeros() {
        val nzNumberOne = PhoneNumber()
        val nzNumberTwo = PhoneNumber()
        nzNumberOne.setCountryCode(64).setNationalNumber(33316005L).setItalianLeadingZero(true)
        nzNumberTwo.setCountryCode(64).setNationalNumber(33316005L).setItalianLeadingZero(true)
            .setNumberOfLeadingZeros(2)
        assertEquals(
            PhoneNumberUtil.MatchType.SHORT_NSN_MATCH, phoneUtil.isNumberMatch(nzNumberOne, nzNumberTwo)
        )
        nzNumberOne.setItalianLeadingZero(false).setNumberOfLeadingZeros(1)
        nzNumberTwo.setItalianLeadingZero(true).setNumberOfLeadingZeros(1)
        // Since one doesn't have the "italian_leading_zero" set to true, we ignore the number of
        // leading zeros present (1 is in any case the default value).
        assertEquals(
            PhoneNumberUtil.MatchType.SHORT_NSN_MATCH, phoneUtil.isNumberMatch(nzNumberOne, nzNumberTwo)
        )
    }

    @Throws(Exception::class)
    @Test
    fun testIsNumberMatchAcceptsProtoDefaultsAsMatch() {
        val nzNumberOne = PhoneNumber()
        val nzNumberTwo = PhoneNumber()
        nzNumberOne.setCountryCode(64).setNationalNumber(33316005L).setItalianLeadingZero(true)
        // The default for number_of_leading_zeros is 1, so it shouldn't normally be set, however if it
        // is it should be considered equivalent.
        nzNumberTwo.setCountryCode(64).setNationalNumber(33316005L).setItalianLeadingZero(true)
            .setNumberOfLeadingZeros(1)
        assertEquals(
            PhoneNumberUtil.MatchType.EXACT_MATCH, phoneUtil.isNumberMatch(nzNumberOne, nzNumberTwo)
        )
    }

    @Throws(Exception::class)
    @Test
    fun testIsNumberMatchMatchesDiffLeadingZerosIfItalianLeadingZeroFalse() {
        val nzNumberOne = PhoneNumber()
        val nzNumberTwo = PhoneNumber()
        nzNumberOne.setCountryCode(64).setNationalNumber(33316005L)
        // The default for number_of_leading_zeros is 1, so it shouldn't normally be set, however if it
        // is it should be considered equivalent.
        nzNumberTwo.setCountryCode(64).setNationalNumber(33316005L).setNumberOfLeadingZeros(1)
        assertEquals(
            PhoneNumberUtil.MatchType.EXACT_MATCH, phoneUtil.isNumberMatch(nzNumberOne, nzNumberTwo)
        )

        // Even if it is set to ten, it is still equivalent because in both cases
        // italian_leading_zero is not true.
        nzNumberTwo.setNumberOfLeadingZeros(10)
        assertEquals(
            PhoneNumberUtil.MatchType.EXACT_MATCH, phoneUtil.isNumberMatch(nzNumberOne, nzNumberTwo)
        )
    }

    @Throws(Exception::class)
    @Test
    fun testIsNumberMatchIgnoresSomeFields() {
        // Check raw_input, country_code_source and preferred_domestic_carrier_code are ignored.
        val brNumberOne = PhoneNumber()
        val brNumberTwo = PhoneNumber()
        brNumberOne.setCountryCode(55).setNationalNumber(3121286979L)
            .setCountryCodeSource(CountryCodeSource.FROM_NUMBER_WITH_PLUS_SIGN).setPreferredDomesticCarrierCode("12")
            .setRawInput("012 3121286979")
        brNumberTwo.setCountryCode(55).setNationalNumber(3121286979L)
            .setCountryCodeSource(CountryCodeSource.FROM_DEFAULT_COUNTRY).setPreferredDomesticCarrierCode("14")
            .setRawInput("143121286979")
        assertEquals(
            PhoneNumberUtil.MatchType.EXACT_MATCH, phoneUtil.isNumberMatch(brNumberOne, brNumberTwo)
        )
    }

    @Throws(Exception::class)
    @Test
    fun testIsNumberMatchNonMatches() {
        // Non-matches.
        assertEquals(
            PhoneNumberUtil.MatchType.NO_MATCH, phoneUtil.isNumberMatch("03 331 6005", "03 331 6006")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.NO_MATCH, phoneUtil.isNumberMatch("+800 1234 5678", "+1 800 1234 5678")
        )
        // Different country calling code, partial number match.
        assertEquals(
            PhoneNumberUtil.MatchType.NO_MATCH, phoneUtil.isNumberMatch("+64 3 331-6005", "+16433316005")
        )
        // Different country calling code, same number.
        assertEquals(
            PhoneNumberUtil.MatchType.NO_MATCH, phoneUtil.isNumberMatch("+64 3 331-6005", "+6133316005")
        )
        // Extension different, all else the same.
        assertEquals(
            PhoneNumberUtil.MatchType.NO_MATCH,
            phoneUtil.isNumberMatch("+64 3 331-6005 extn 1234", "0116433316005#1235")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.NO_MATCH, phoneUtil.isNumberMatch(
                "+64 3 331-6005 extn 1234", "tel:+64-3-331-6005;ext=1235"
            )
        )
        // NSN matches, but extension is different - not the same number.
        assertEquals(
            PhoneNumberUtil.MatchType.NO_MATCH, phoneUtil.isNumberMatch("+64 3 331-6005 ext.1235", "3 331 6005#1234")
        )

        // Invalid numbers that can't be parsed.
        assertEquals(
            PhoneNumberUtil.MatchType.NOT_A_NUMBER, phoneUtil.isNumberMatch("4", "3 331 6043")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.NOT_A_NUMBER, phoneUtil.isNumberMatch("+43", "+64 3 331 6005")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.NOT_A_NUMBER, phoneUtil.isNumberMatch("+43", "64 3 331 6005")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.NOT_A_NUMBER, phoneUtil.isNumberMatch("Dog", "64 3 331 6005")
        )
    }

    @Throws(Exception::class)
    @Test
    fun testIsNumberMatchNsnMatches() {
        // NSN matches.
        assertEquals(
            PhoneNumberUtil.MatchType.NSN_MATCH, phoneUtil.isNumberMatch("+64 3 331-6005", "03 331 6005")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.NSN_MATCH, phoneUtil.isNumberMatch(
                "+64 3 331-6005", "tel:03-331-6005;isub=1234;phone-context=abc.nz"
            )
        )
        assertEquals(
            PhoneNumberUtil.MatchType.NSN_MATCH, phoneUtil.isNumberMatch(NZ_NUMBER, "03 331 6005")
        )
        // Here the second number possibly starts with the country calling code for New Zealand,
        // although we are unsure.
        val unchangedNzNumber = PhoneNumber().mergeFrom(NZ_NUMBER)
        assertEquals(
            PhoneNumberUtil.MatchType.NSN_MATCH, phoneUtil.isNumberMatch(unchangedNzNumber, "(64-3) 331 6005")
        )
        // Check the phone number proto was not edited during the method call.
        assertEquals(NZ_NUMBER, unchangedNzNumber)

        // Here, the 1 might be a national prefix, if we compare it to the US number, so the resultant
        // match is an NSN match.
        assertEquals(
            PhoneNumberUtil.MatchType.NSN_MATCH, phoneUtil.isNumberMatch(US_NUMBER, "1-650-253-0000")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.NSN_MATCH, phoneUtil.isNumberMatch(US_NUMBER, "6502530000")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.NSN_MATCH, phoneUtil.isNumberMatch("+1 650-253 0000", "1 650 253 0000")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.NSN_MATCH, phoneUtil.isNumberMatch("1 650-253 0000", "1 650 253 0000")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.NSN_MATCH, phoneUtil.isNumberMatch("1 650-253 0000", "+1 650 253 0000")
        )
        // For this case, the match will be a short NSN match, because we cannot assume that the 1 might
        // be a national prefix, so don't remove it when parsing.
        val randomNumber = PhoneNumber()
        randomNumber.setCountryCode(41).setNationalNumber(6502530000L)
        assertEquals(
            PhoneNumberUtil.MatchType.SHORT_NSN_MATCH, phoneUtil.isNumberMatch(randomNumber, "1-650-253-0000")
        )
    }

    @Throws(Exception::class)
    @Test
    fun testIsNumberMatchShortNsnMatches() {
        // Short NSN matches with the country not specified for either one or both numbers.
        assertEquals(
            PhoneNumberUtil.MatchType.SHORT_NSN_MATCH, phoneUtil.isNumberMatch("+64 3 331-6005", "331 6005")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.SHORT_NSN_MATCH,
            phoneUtil.isNumberMatch("+64 3 331-6005", "tel:331-6005;phone-context=abc.nz")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.SHORT_NSN_MATCH, phoneUtil.isNumberMatch(
                "+64 3 331-6005", "tel:331-6005;isub=1234;phone-context=abc.nz"
            )
        )
        assertEquals(
            PhoneNumberUtil.MatchType.SHORT_NSN_MATCH, phoneUtil.isNumberMatch(
                "+64 3 331-6005", "tel:331-6005;isub=1234;phone-context=abc.nz;a=%A1"
            )
        )
        // We did not know that the "0" was a national prefix since neither number has a country code,
        // so this is considered a SHORT_NSN_MATCH.
        assertEquals(
            PhoneNumberUtil.MatchType.SHORT_NSN_MATCH, phoneUtil.isNumberMatch("3 331-6005", "03 331 6005")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.SHORT_NSN_MATCH, phoneUtil.isNumberMatch("3 331-6005", "331 6005")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.SHORT_NSN_MATCH,
            phoneUtil.isNumberMatch("3 331-6005", "tel:331-6005;phone-context=abc.nz")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.SHORT_NSN_MATCH, phoneUtil.isNumberMatch("3 331-6005", "+64 331 6005")
        )
        // Short NSN match with the country specified.
        assertEquals(
            PhoneNumberUtil.MatchType.SHORT_NSN_MATCH, phoneUtil.isNumberMatch("03 331-6005", "331 6005")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.SHORT_NSN_MATCH, phoneUtil.isNumberMatch("1 234 345 6789", "345 6789")
        )
        assertEquals(
            PhoneNumberUtil.MatchType.SHORT_NSN_MATCH, phoneUtil.isNumberMatch("+1 (234) 345 6789", "345 6789")
        )
        // NSN matches, country calling code omitted for one number, extension missing for one.
        assertEquals(
            PhoneNumberUtil.MatchType.SHORT_NSN_MATCH, phoneUtil.isNumberMatch("+64 3 331-6005", "3 331 6005#1234")
        )
        // One has Italian leading zero, one does not.
        val italianNumberOne = PhoneNumber()
        italianNumberOne.setCountryCode(39).setNationalNumber(1234L).setItalianLeadingZero(true)
        val italianNumberTwo = PhoneNumber()
        italianNumberTwo.setCountryCode(39).setNationalNumber(1234L)
        assertEquals(
            PhoneNumberUtil.MatchType.SHORT_NSN_MATCH, phoneUtil.isNumberMatch(italianNumberOne, italianNumberTwo)
        )
        // One has an extension, the other has an extension of "".
        italianNumberOne.setExtension("1234").clearItalianLeadingZero()
        italianNumberTwo.setExtension("")
        assertEquals(
            PhoneNumberUtil.MatchType.SHORT_NSN_MATCH, phoneUtil.isNumberMatch(italianNumberOne, italianNumberTwo)
        )
    }

    @Throws(Exception::class)
    @Test
    fun testCanBeInternationallyDialled() {
        // We have no-international-dialling rules for the US in our test metadata that say that
        // toll-free numbers cannot be dialled internationally.
        assertFalse(phoneUtil.canBeInternationallyDialled(US_TOLLFREE))

        // Normal US numbers can be internationally dialled.
        assertTrue(phoneUtil.canBeInternationallyDialled(US_NUMBER))

        // Invalid number.
        assertTrue(phoneUtil.canBeInternationallyDialled(US_LOCAL_NUMBER))

        // We have no data for NZ - should return true.
        assertTrue(phoneUtil.canBeInternationallyDialled(NZ_NUMBER))
        assertTrue(phoneUtil.canBeInternationallyDialled(INTERNATIONAL_TOLL_FREE))
    }

    @Throws(Exception::class)
    @Test
    fun testIsAlphaNumber() {
        assertTrue(phoneUtil.isAlphaNumber("1800 six-flags"))
        assertTrue(phoneUtil.isAlphaNumber("1800 six-flags ext. 1234"))
        assertTrue(phoneUtil.isAlphaNumber("+800 six-flags"))
        assertTrue(phoneUtil.isAlphaNumber("180 six-flags"))
        assertFalse(phoneUtil.isAlphaNumber("1800 123-1234"))
        assertFalse(phoneUtil.isAlphaNumber("1 six-flags"))
        assertFalse(phoneUtil.isAlphaNumber("18 six-flags"))
        assertFalse(phoneUtil.isAlphaNumber("1800 123-1234 extension: 1234"))
        assertFalse(phoneUtil.isAlphaNumber("+800 1234-1234"))
    }

    @Test
    fun testIsMobileNumberPortableRegion() {
        assertTrue(phoneUtil.isMobileNumberPortableRegion(RegionCode.US))
        assertTrue(phoneUtil.isMobileNumberPortableRegion(RegionCode.GB))
        assertFalse(phoneUtil.isMobileNumberPortableRegion(RegionCode.AE))
        assertFalse(phoneUtil.isMobileNumberPortableRegion(RegionCode.BS))
    }

    @Test
    fun testGetMetadataForRegionForNonGeoEntity_shouldBeNull() {
        assertNull(phoneUtil.getMetadataForRegion(RegionCode.UN001))
    }

    @Test
    fun testGetMetadataForRegionForUnknownRegion_shouldBeNull() {
        assertNull(phoneUtil.getMetadataForRegion(RegionCode.ZZ))
    }

    @Test
    fun testGetMetadataForNonGeographicalRegionForGeoRegion_shouldBeNull() {
        assertNull(phoneUtil.getMetadataForNonGeographicalRegion( /* countryCallingCode = */1))
    }

    @Test
    fun testGetMetadataForRegionForMissingMetadata() {
        mocker.every { mockedMetadataSource.getMetadataForRegion(isAny()) } returns null
        assertThrows(
            MissingMetadataException::class
        ) { phoneNumberUtilWithMissingMetadata.getMetadataForRegion(RegionCode.US) }
    }

    @Test
    fun testGetMetadataForNonGeographicalRegionForMissingMetadata() {
        mocker.every { mockedMetadataSource.getMetadataForNonGeographicalRegion(isAny()) } returns null
        assertThrows(
            MissingMetadataException::class
        ) { phoneNumberUtilWithMissingMetadata.getMetadataForNonGeographicalRegion(800) }
    }

    companion object {
        // Set up some test numbers to re-use.
        // TODO: Rewrite this as static functions that return new numbers each time to avoid
        // any risk of accidental changes to mutable static state affecting many tests.
        private val ALPHA_NUMERIC_NUMBER = PhoneNumber().setCountryCode(1).setNationalNumber(80074935247L)
        private val AE_UAN = PhoneNumber().setCountryCode(971).setNationalNumber(600123456L)
        private val AR_MOBILE = PhoneNumber().setCountryCode(54).setNationalNumber(91187654321L)
        private val AR_NUMBER = PhoneNumber().setCountryCode(54).setNationalNumber(1187654321)
        private val AU_NUMBER = PhoneNumber().setCountryCode(61).setNationalNumber(236618300L)
        private val BS_MOBILE = PhoneNumber().setCountryCode(1).setNationalNumber(2423570000L)
        private val BS_NUMBER = PhoneNumber().setCountryCode(1).setNationalNumber(2423651234L)
        private val CO_FIXED_LINE = PhoneNumber().setCountryCode(57).setNationalNumber(6012345678L)

        // Note that this is the same as the example number for DE in the metadata.
        private val DE_NUMBER = PhoneNumber().setCountryCode(49).setNationalNumber(30123456L)
        private val DE_SHORT_NUMBER = PhoneNumber().setCountryCode(49).setNationalNumber(1234L)
        private val GB_MOBILE = PhoneNumber().setCountryCode(44).setNationalNumber(7912345678L)
        private val GB_NUMBER = PhoneNumber().setCountryCode(44).setNationalNumber(2070313000L)
        private val IT_MOBILE = PhoneNumber().setCountryCode(39).setNationalNumber(345678901L)
        private val IT_NUMBER =
            PhoneNumber().setCountryCode(39).setNationalNumber(236618300L).setItalianLeadingZero(true)
        private val JP_STAR_NUMBER = PhoneNumber().setCountryCode(81).setNationalNumber(2345)

        // Numbers to test the formatting rules from Mexico.
        private val MX_MOBILE1 = PhoneNumber().setCountryCode(52).setNationalNumber(12345678900L)
        private val MX_MOBILE2 = PhoneNumber().setCountryCode(52).setNationalNumber(15512345678L)
        private val MX_NUMBER1 = PhoneNumber().setCountryCode(52).setNationalNumber(3312345678L)
        private val MX_NUMBER2 = PhoneNumber().setCountryCode(52).setNationalNumber(8211234567L)
        private val NZ_NUMBER = PhoneNumber().setCountryCode(64).setNationalNumber(33316005L)
        private val SG_NUMBER = PhoneNumber().setCountryCode(65).setNationalNumber(65218000L)

        // A too-long and hence invalid US number.
        private val US_LONG_NUMBER = PhoneNumber().setCountryCode(1).setNationalNumber(65025300001L)
        private val US_NUMBER = PhoneNumber().setCountryCode(1).setNationalNumber(6502530000L)
        private val US_PREMIUM = PhoneNumber().setCountryCode(1).setNationalNumber(9002530000L)

        // Too short, but still possible US numbers.
        private val US_LOCAL_NUMBER = PhoneNumber().setCountryCode(1).setNationalNumber(2530000L)
        private val US_SHORT_BY_ONE_NUMBER = PhoneNumber().setCountryCode(1).setNationalNumber(650253000L)
        private val US_TOLLFREE = PhoneNumber().setCountryCode(1).setNationalNumber(8002530000L)
        private val US_SPOOF = PhoneNumber().setCountryCode(1).setNationalNumber(0L)
        private val US_SPOOF_WITH_RAW_INPUT =
            PhoneNumber().setCountryCode(1).setNationalNumber(0L).setRawInput("000-000-0000")
        private val UZ_FIXED_LINE = PhoneNumber().setCountryCode(998).setNationalNumber(612201234L)
        private val UZ_MOBILE = PhoneNumber().setCountryCode(998).setNationalNumber(950123456L)
        private val INTERNATIONAL_TOLL_FREE = PhoneNumber().setCountryCode(800).setNationalNumber(12345678L)

        // We set this to be the same length as numbers for the other non-geographical country prefix that
        // we have in our test metadata. However, this is not considered valid because they differ in
        // their country calling code.
        private val INTERNATIONAL_TOLL_FREE_TOO_LONG = PhoneNumber().setCountryCode(800).setNationalNumber(123456789L)
        private val UNIVERSAL_PREMIUM_RATE = PhoneNumber().setCountryCode(979).setNationalNumber(123456789L)
        private val UNKNOWN_COUNTRY_CODE_NO_RAW_INPUT = PhoneNumber().setCountryCode(2).setNationalNumber(12345L)
    }
}
