package dev.dsprobe.app.debug

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.pad.PRODUCT_DUALSENSE
import dev.dsprobe.app.pad.Transport
import dev.dsprobe.app.route.Route
import dev.dsprobe.app.route.usbPixelAnswers
import dev.dsprobe.pad.FirmwareInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val FOOTER = LogFooter(100, 2, 35, 0, 4, 1)
private val HEADER = listOf(
    "app_version" to "0.2.0",
    "commit" to "abc1234",
    "phone_model" to "Pixel",
)

private fun input(
    results: List<TestResult> = emptyList(),
    trace: List<TraceRow> = emptyList(),
    header: List<Pair<String, String>> = HEADER,
    crash: String? = null,
) = LogInput(header, usbPixelAnswers(), results, trace, crash, FOOTER)

private fun result(
    id: String,
    outcome: Outcome,
    route: Route = Route.ANDROID,
    detail: String = "d",
) = TestResult(id, route, outcome, detail)

class LogWriterTest {
    @Test fun theFirstLineIsTheFormatVersionAndHeaderLinesAreKeyValue() {
        val lines = renderLog(input()).lines()
        assertEquals("dsprobe-log 1", lines[0])
        assertEquals(
            listOf("app_version=0.2.0", "commit=abc1234", "phone_model=Pixel"),
            lines.subList(1, 4),
        )
    }

    @Test fun aTestLineHasIdRouteOutcomeAndDetailInThatOrder() {
        val log =
            renderLog(
                input(
                    results = listOf(
                        result(
                            "in.button.cross",
                            Outcome.PASSED,
                            Route.ANDROID,
                            "matching event seen",
                        ),
                    ),
                ),
            )
        assertTrue(
            log,
            log.lines().contains(
                "test id=in.button.cross route=android outcome=PASSED detail=matching event seen",
            ),
        )
    }

    @Test fun anOutputTestAlsoRecordsTheExactBytesSentAndAnInputTestItsRawReports() {
        val r =
            TestResult(
                "out.lightbar.red",
                Route.DIRECT_USB,
                Outcome.PASSED,
                "ok",
                rawHex = listOf("01 02"),
                sentHex = listOf("02 01 00"),
            )
        val lines = renderLog(input(results = listOf(r))).lines()
        assertTrue(lines.contains("sent test=out.lightbar.red route=usb hex=02 01 00"))
        assertTrue(lines.contains("raw test=out.lightbar.red route=usb hex=01 02"))
    }

    @Test fun theBodyHoldsAndroidsViewTheTraceAndTheCrashRecord() {
        val row =
            TraceRow(
                5L,
                Route.DIRECT_USB,
                "in.button.cross",
                traceLine("in.button.cross", Route.DIRECT_USB, "byte 8"),
            )
        val log = renderLog(input(trace = listOf(row), crash = "boom\nat Writer.run"))
        assertTrue(log.contains("android.device name="))
        assertTrue(log.contains("android.key code=96"))
        assertTrue(
            log.lines().any {
                it.startsWith("trace control=in.button.cross route=usb line=in.button.cross")
            },
        )
        assertTrue(log.contains("crash boom") && log.contains("crash at Writer.run"))
    }

    @Test fun theFooterHasTotalsPerOutcomePerRouteTheCountersAndTheLogcatCommand() {
        val log = renderLog(
            input(
                results = listOf(
                    result("a", Outcome.PASSED),
                    result("b", Outcome.PASSED),
                    result("c", Outcome.NOT_SEEN),
                    result("d", Outcome.PASSED, Route.DIRECT_USB),
                ),
            ),
        )
        val lines = log.lines()
        assertTrue(lines.contains("outcomes route=android PASSED=2 NOT_SEEN=1"))
        assertTrue(lines.contains("outcomes route=usb PASSED=1"))
        assertTrue(lines.contains("reports_received=100"))
        assertTrue(lines.contains("reports_dropped=2 (assumes byte 7 is a counter)"))
        assertTrue(lines.contains("longest_gap_ms=35"))
        assertTrue(
            lines.any {
                it.startsWith(
                    "feed_events_overflowed=0 axis_rows_skipped=4 late_android_events_dropped=1",
                )
            },
        )
        assertTrue(lines.contains("logcat_command=$LOGCAT_COMMAND"))
        assertTrue(LOGCAT_COMMAND.startsWith("adb logcat"))
    }

    @Test fun pastTheCapTheBodyStopsOneTruncatedLineIsWrittenAndTheFooterStillFits() {
        val caps = LogCaps(totalBytesMax = 6_000, footerBytesReserved = 3_000)
        val many = (0 until 500).map {
            result("in.test.$it", Outcome.PASSED, detail = "x".repeat(40))
        }
        val log = renderLog(input(results = many), caps)
        val lines = log.lines().filter { it.isNotEmpty() }
        assertTrue(
            "under the cap: ${log.toByteArray().size}",
            log.toByteArray().size <= caps.totalBytesMax,
        )
        assertEquals(1, lines.count { it == "truncated=true" })
        val truncatedAt = lines.indexOf("truncated=true")
        assertTrue(
            "footer comes after the truncated line",
            lines.indexOf("reports_received=100") > truncatedAt,
        )
        assertTrue(lines.none { it.startsWith("test id=in.test.499") })
        assertTrue(lines.any { it.startsWith("test id=in.test.0 ") })
    }

    @Test fun aLogUnderTheCapHasNoTruncatedLine() {
        assertFalse(
            renderLog(
                input(results = listOf(result("a", Outcome.PASSED))),
            ).contains("truncated=true"),
        )
    }

    @Test fun theDefaultCapIsTheDesignsTwoMegabytesWithRoomForTheFooter() {
        assertEquals(2_000_000, LogCaps.DEFAULT.totalBytesMax)
        assertEquals(2_000_000 - AppLimits.LOG_FOOTER_BYTES_RESERVED, LogCaps.DEFAULT.bodyBytesMax)
        val worstFooter =
            renderLog(input(results = Outcome.entries.map { result("x", it) }), LogCaps.DEFAULT)
        assertTrue(worstFooter.toByteArray().size < AppLimits.LOG_FOOTER_BYTES_RESERVED + 100_000)
    }

    @Test fun aFooterThatCannotFitFailsLoudlyInsteadOfOverrunningTheCap() {
        val caps = LogCaps(totalBytesMax = 300, footerBytesReserved = 100)
        val failed = runCatching { renderLog(input(), caps) }.exceptionOrNull()
        assertTrue(failed is IllegalStateException)
    }

    @Test fun aLineIsOneLineAndCutAtTheLineLimit() {
        val r = result(
            "t",
            Outcome.PASSED,
            detail =
            "first\nsecond\r" + "y".repeat(AppLimits.LOG_LINE_CHARS_MAX * 2),
        )
        val line = renderLog(input(results = listOf(r))).lines().single {
            it.startsWith("test id=t ")
        }
        assertTrue(line.length <= AppLimits.LOG_LINE_CHARS_MAX)
        assertTrue(line.contains("first second"))
    }

    @Test fun aBluetoothAddressNeverReachesAnyExportedLine() {
        val dirty = "9c:ab:12:34:56:78"
        val results =
            listOf(result("t", Outcome.PASSED, detail = "battery dualsense-$dirty-battery"))
        val row =
            TraceRow(1L, Route.ANDROID, "in.button.cross", "line mentions AA-BB-CC-DD-EE-FF here")
        val header = HEADER + ("kernel" to "builder at $dirty")
        val log =
            renderLog(
                input(
                    results = results,
                    trace = listOf(row),
                    header = header,
                    crash = "crash $dirty",
                ),
            )
        assertFalse(log, log.contains(dirty) || log.contains("AA-BB-CC-DD-EE-FF"))
        assertTrue(log.contains(BT_ADDRESS_MASK))
    }

    @Test fun padHeaderFieldsSayUnknownWithoutDirectUsbFirmware() {
        val fields = padHeaderFields(
            PRODUCT_DUALSENSE,
            Transport.BLUETOOTH,
            alsoPairedBluetooth = false,
            firmware = null,
        ).toMap()
        assertEquals("bluetooth", fields["transport"])
        assertEquals("unknown", fields["pad_firmware_version"])
        assertEquals("unknown", fields["pad_hardware_version"])
        assertEquals("0x0ce6 (DualSense)", fields["pad_product"])
        assertEquals("0x054c", fields["pad_vendor"])
        assertTrue(fields.keys.none { it.contains("serial") || it.contains("address") })
    }

    @Test fun padHeaderFieldsCarryTheFirmwareReadOverDirectUsb() {
        val info =
            FirmwareInfo(
                hardware = 0x413u,
                firmware = 0x0110002au,
                updateVersion = 0x0630u,
                buildDate = "Sep  9 2023",
            )
        val fields = padHeaderFields(
            PRODUCT_DUALSENSE,
            Transport.USB,
            alsoPairedBluetooth = true,
            firmware = info,
        ).toMap()
        assertEquals("usb", fields["transport"])
        assertEquals("true", fields["also_paired_over_bluetooth"])
        assertEquals("0x0630", fields["pad_firmware_update_version"])
        assertEquals("0x0110002a", fields["pad_firmware_version"])
        assertEquals("0x00000413", fields["pad_hardware_version"])
    }
}
