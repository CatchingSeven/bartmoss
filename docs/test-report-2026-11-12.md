# Bartmoss JUnit test report

Date: 12 November 2026, Australia/Adelaide (UTC+10:30).

The complete suite ran **88 tests: 85 passed, 3 failed, 0 errors, 0 skipped**. The failures expose existing production behavior. The three regression tests remain enabled by default and carry the `known-defect` tag. A separate verification run excluding that tag passed all 85 selected tests and the existing JaCoCo coverage gate.

Only test Java files and this report were added or changed. Production Java, FXML, CSS, Maven configuration, existing capture files, and existing exports were not edited. No commits were made.

## Execution results

| Run | Tests completed | Passed | Failed | Errors | Skipped | Maven result |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Original baseline | 29 before termination | 29 | 0 reported | Fork terminated with heap exhaustion | 0 reported | Failed |
| Final complete suite, finished 14:28:40 | 88 | 85 | 3 | 0 | 0 | Failed, as expected from the regressions |
| Fresh coverage verification excluding `known-defect`, finished 14:29:44 | 85 | 85 | 0 | 0 | 0 | Passed, including coverage check |

The complete run took 14.224 seconds of Maven execution; the final verification took 14.455 seconds. These are single-run timings, not performance benchmarks. The filtered run excludes three tests from discovery; its zero skipped count does not mean those regressions passed.

The original baseline exhausted the Java heap in `PcapReaderTest.currentlyFailsOnFirstPacketWithoutMapperInitializationAndClosesHandle`. That test repeatedly returned the same mocked packet and expected a mapper-initialisation error that no longer occurs. The reader now creates its own mapper and skips the mocked non-IP packet indefinitely. The rewritten tests terminate streams with EOF and test the current implementation. Stale expectations that EOF propagates and `.pcap` is classified as PCAPNG were also replaced.

One intermediate OpenAI test run exposed nested Mockito stubbing in the new fixture setup. That test setup was corrected before the final runs. No test infrastructure errors remain in the complete results.

## Environment

| Component | Version or configuration |
| --- | --- |
| OS | Windows 11, amd64 |
| Maven | 3.10.0 |
| Maven test JVM | Eclipse Adoptium JDK 25.0.3 |
| JUnit Jupiter | 5.14.4 |
| Mockito | 5.20.0, using the project's existing Java agent |
| Surefire | 3.5.5 |
| JavaFX | 26 |
| Jackson | 2.22.4 |
| OpenAI Java SDK | 4.86.0 |
| Pcap4J | 1.8.2 |
| Native capture library | Npcap 1.88, based on libpcap 1.10.6 |
| JaCoCo | 0.8.15 |

The shell's standalone `java` resolves to Java 8, but Maven uses JDK 25. The tests were run through Maven. The sandbox initially resolved Maven's repository to inaccessible `C:\.m2\repository`; subsequent commands used the existing cache at `C:\Users\maxhe\.m2\repository` with approved access. Maven downloaded missing packaging dependencies during verification. No test contacted OpenAI, required an API key, or submitted capture data to a remote model.

## Requested coverage and implementation

Eight test classes add 49 test invocations. The rewritten PCAP reader class supplies 10 invocations. Another 29 invocations in the existing domain and mapper tests were retained, for 88 total. Counts include individual parameterised invocations.

| Requested area | Test class | What is checked |
| --- | --- | --- |
| Unit: JSON conversion | `PacketJsonConverterTest` | Capture metadata, packet lengths and addresses, nine-digit fractional timestamps, TCP/UDP/ICMP/ICMPv6 records, large unsigned sequence number, flags, empty/null values, IPv6 optional fields, packet order, escaping, analysis records, and serialisation failure causes |
| Unit: OpenAI integration | `OpenAIClientTest` | Actual adapter method with a mocked SDK: configured model and full prompt/input, ordered multiline extraction, empty output, non-message/refusal filtering, authentication/rate-limit/connection/service error messages, unexpected exceptions, and credential validation |
| Unit: persistence | `FileExporterTest` | Actual UTF-8 writes to temporary files, expected timestamped names and separate destinations, empty contents, missing-directory failures, and current same-second overwrite behavior |
| Unit: application | `ApplicationTest` | Actual application with constructed dependency doubles: operation order, latest JSON submission/export, read and serialisation errors, short-circuiting on JSON export failure, response export failure, and provider error forwarding |
| Unit/controller and JavaFX: `MainController` | `MainControllerTest` | Actual FXML/controller/controls with mocked application: loading and field injection, handlers and initial button state, stylesheet resource, chooser filters, selection/cancellation, asynchronous analysis, response display, retry after upload failure, model and export errors, and control state |
| Integration: PCAP processing and JSON | `PcapJsonIntegrationTest` | Generated PCAP and PCAPNG files through real native reader, mapper and Jackson; metadata, timestamps, protocol fields, EOF, empty capture, and released native file handle |
| Integration: JSON submission and response | `AnalysisPipelineIntegrationTest` | Real capture processing, JSON converter and adapter; generated SDK request must contain exactly the JSON subsequently exported; model output is an offline fixture |
| Integration: JSON and response exports | `AnalysisPipelineIntegrationTest` | Real application and filesystem; content of both exports, provider error response export, initial write failure, and failure after JSON has already been written |
| Integration: end-to-end pipeline including UI | `AnalysisUiIntegrationTest` | Real FXML buttons, controller, application, native PCAP processing, Jackson, adapter, UI text and filesystem exports; only SDK transport and modal dialogs are doubled |
| Integration: errors across layers | Application, adapter, pipeline and controller tests | Read timeout/closed handle, invalid capture rejection regression, JSON failure, provider failures, storage failures, UI errors and retry |
| JavaFX: threading and success feedback | Two `MainControllerTest` regressions | Control changes must occur on the FX thread; successful export must not display a failure title |

`OpenAIClientTest` lives in the production adapter's `integration` package but is an offline unit test. Tests carrying the `integration` tag exercise multiple real application layers. The PCAP integration classes also carry `native-pcap`; UI tests carry `javafx`.

## Confirmed regression failures

### 1. Malformed capture is accepted as an empty submission

Test: `AnalysisPipelineIntegrationTest.malformedCaptureIsRejectedBeforeItCanReachModel`.

Input: a temporary `.pcap` file containing `this is not a capture`.

Expected: upload signals failure, allowing the controller to stop before model submission.

Actual: `PcapReader` catches the native open failure, prints `Unable to open capture: unknown file format`, and returns its default capture. `Application.handleUpload` then successfully serialises that capture. The assertion fails because no exception is raised. The test does not send anything to a live service.

Source locations: `PcapReader.java:64` catches the error; `PcapReader.java:71` returns the default capture. The application and controller have no rejection check between upload and the subsequent response request.

Impact: malformed or unreadable input can proceed as an empty capture instead of producing an analysis error in the UI.

### 2. Analysis changes a JavaFX control from the worker thread

Test: `MainControllerTest.analysisControlStateChangesMustStayOnFxThread`.

Expected: every observed disabled-state change of the Analyse button occurs on the JavaFX application thread.

Actual: a property listener records a change from the background Task. The test waits for the request and successful response display before asserting, and fails with `The worker Task changes analyseButton outside the FX thread`.

Source location: `MainController.java:53`, inside `Task.call()`.

Impact: JavaFX threading rules are violated. Passing ordinary display tests does not establish thread safety; these tests use a Scene without showing a Stage, so they do not demonstrate the exact behavior of every displayed desktop configuration.

### 3. Successful export displays a failure title

Test: `MainControllerTest.successfulExportMustNotUseFailureTitle`.

Expected: successful export feedback does not contain `fail` in its title. No particular replacement wording is imposed.

Actual: successful export constructs a confirmation dialog with the title `Export Failed`, and the regression assertion fails.

Source location: `MainController.java:93`, in the successful export path.

Impact: success feedback contradicts the outcome and the success header.

All three remain unfixed because this task authorises tests and reporting only. They have not been disabled, changed into expected successes, or hidden from the default suite.

## Test isolation and scope limits

- Native fixtures are generated deterministically inside JUnit `@TempDir`; no existing user capture is required and no traffic is captured or transmitted.
- Export tests run real `Files.writeString`. A test-only helper fixes the time and redirects the two hardcoded export paths into temporary directories. Unexpected export paths fail immediately rather than writing into the user's export folder.
- Constructors instantiate dependencies directly. Mockito construction mocking and a small reflection helper provide test seams without altering production constructors or fields.
- The OpenAI adapter's real request/response method is exercised with an injected SDK mock. The partial adapter mock avoids its environment-reading constructor. The private key validator is tested with placeholders without reading or modifying the environment.
- Live authentication, account quota, actual model availability, SDK HTTP encoding/retries, network/TLS behavior and model analysis quality are outside this suite. These require separate service-level validation; a real API key is unnecessary for the offline coverage delivered here.
- JavaFX tests start the real toolkit and load the actual FXML. FileChooser and Alert construction is mocked so no manual selection or modal acknowledgement is required. No screenshot, visual-layout, native chooser, focus, keyboard or mouse automation is claimed. A graphical JavaFX-capable environment is required; these tests are not configured for a headless CI display.
- Native integration tests abort with an explicit assumption message when Npcap/libpcap cannot be loaded. On this machine the library loaded and no tests were skipped. Library compatibility beyond this Windows environment has not been verified.
- Task completion uses bounded futures/latches rather than timing sleeps. JavaFX assertions and mock lifecycle operations run on the FX thread, except for the deliberate listener that detects the production threading violation.
- The production model name is asserted as configured. This verifies request construction, not that the provider currently offers that model.

Some passing tests deliberately document current limitations rather than prove desirable behavior: exports in the same second overwrite previous contents, absent export directories raise I/O errors, and a response-write failure leaves the JSON file behind. Existing mapper tests also characterise unrecognised Pcap4J ICMPv4 naming and a missing TCP header raising a null-pointer exception. Those existing tests were retained; their passing status must not be read as resolution of those limitations.

## Measured coverage

The final verification used `-Djacoco.append=false` and produced a fresh recording. JaCoCo reported **254/254 executable lines and 50/50 branches covered across 11 configured classes**. The existing per-class 100% line coverage gate passed.

The POM restricts instrumentation/reporting to domain classes, `PcapReader` and `PcapMapper`. It does **not** measure `PacketJsonConverter`, `OpenAIClient`, `FileExporter`, `Application` or `MainController`. No whole-application coverage percentage is claimed. The interface without executable code does not appear among the 11 measured classes. Maven configuration was left unchanged.

## Reproduction and evidence

Run from the inner `bartmoss` directory. An ordinary configured Maven installation can use `mvn test`. The exact substantive commands used here were:

```powershell
# Complete suite: includes all three failing regression checks.
mvn -B -ntp "-Dmaven.repo.local=C:\Users\maxhe\.m2\repository" test

# Separate passing verification with a fresh coverage recording.
mvn -B -ntp "-Dmaven.repo.local=C:\Users\maxhe\.m2\repository" "-Djacoco.append=false" "-DexcludedGroups=known-defect" verify
```

The filtered command is an explicitly limited verification, not a replacement for the complete suite result. Integration classes end in `Test`, so Surefire discovers them in the existing `test` phase without changing the POM or adding a Failsafe configuration.

Local generated evidence:

- [Full run console log](../bartmoss/target/junit-full-run.log).
- [Full 88-test XML snapshot](../bartmoss/target/full-suite-results/), preserved before the filtered run.
- [Passing verification console log](../bartmoss/target/junit-verify-run.log).
- [Latest Surefire XML and text reports](../bartmoss/target/surefire-reports/), representing the filtered 85-test run.
- [JaCoCo HTML report](../bartmoss/target/site/jacoco/index.html) and [CSV counters](../bartmoss/target/site/jacoco/jacoco.csv).

These raw artifacts are under ignored `target` output and will be removed by Maven clean. The suite results, coverage counters and individual test outcomes are also recorded below in this report. `git diff --quiet -- bartmoss/src/main bartmoss/pom.xml` returned zero; tracked production and build configuration have no diff.

## Complete-suite results by class

The following tables are transcribed programmatically from the preserved complete-run Surefire XML, not the filtered verification.


| Class | Tests | Passed | Failed | Errors | Skipped | Seconds |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| [ApplicationTest](../bartmoss/src/test/java/io/github/catchingseven/bartmoss/application/ApplicationTest.java) | 7 | 7 | 0 | 0 | 0 | 1.537 |
| [AnalysisResultTest](../bartmoss/src/test/java/io/github/catchingseven/bartmoss/domain/AnalysisResultTest.java) | 1 | 1 | 0 | 0 | 0 | 0.012 |
| [CaptureTest](../bartmoss/src/test/java/io/github/catchingseven/bartmoss/domain/CaptureTest.java) | 2 | 2 | 0 | 0 | 0 | 0.017 |
| [FileFormatTest](../bartmoss/src/test/java/io/github/catchingseven/bartmoss/domain/FileFormatTest.java) | 1 | 1 | 0 | 0 | 0 | 0.013 |
| [IcmpDetailsTest](../bartmoss/src/test/java/io/github/catchingseven/bartmoss/domain/IcmpDetailsTest.java) | 2 | 2 | 0 | 0 | 0 | 0.044 |
| [IpProtocolTest](../bartmoss/src/test/java/io/github/catchingseven/bartmoss/domain/IpProtocolTest.java) | 1 | 1 | 0 | 0 | 0 | 0.008 |
| [PacketTest](../bartmoss/src/test/java/io/github/catchingseven/bartmoss/domain/PacketTest.java) | 2 | 2 | 0 | 0 | 0 | 0.033 |
| [ProtocolDetailsTest](../bartmoss/src/test/java/io/github/catchingseven/bartmoss/domain/ProtocolDetailsTest.java) | 1 | 1 | 0 | 0 | 0 | 0.006 |
| [TcpDetailsTest](../bartmoss/src/test/java/io/github/catchingseven/bartmoss/domain/TcpDetailsTest.java) | 1 | 1 | 0 | 0 | 0 | 0.007 |
| [TcpFlagTest](../bartmoss/src/test/java/io/github/catchingseven/bartmoss/domain/TcpFlagTest.java) | 1 | 1 | 0 | 0 | 0 | 0.005 |
| [UdpDetailsTest](../bartmoss/src/test/java/io/github/catchingseven/bartmoss/domain/UdpDetailsTest.java) | 1 | 1 | 0 | 0 | 0 | 0.007 |
| [AnalysisPipelineIntegrationTest](../bartmoss/src/test/java/io/github/catchingseven/bartmoss/integration/AnalysisPipelineIntegrationTest.java) | 5 | 4 | 1 | 0 | 0 | 2.129 |
| [OpenAIClientTest](../bartmoss/src/test/java/io/github/catchingseven/bartmoss/integration/OpenAIClientTest.java) | 9 | 9 | 0 | 0 | 0 | 0.492 |
| [PcapJsonIntegrationTest](../bartmoss/src/test/java/io/github/catchingseven/bartmoss/integration/PcapJsonIntegrationTest.java) | 3 | 3 | 0 | 0 | 0 | 0.067 |
| [FileExporterTest](../bartmoss/src/test/java/io/github/catchingseven/bartmoss/persistence/FileExporterTest.java) | 4 | 4 | 0 | 0 | 0 | 0.089 |
| [AnalysisUiIntegrationTest](../bartmoss/src/test/java/io/github/catchingseven/bartmoss/presentation/AnalysisUiIntegrationTest.java) | 1 | 1 | 0 | 0 | 0 | 2.322 |
| [MainControllerTest](../bartmoss/src/test/java/io/github/catchingseven/bartmoss/presentation/MainControllerTest.java) | 11 | 9 | 2 | 0 | 0 | 0.234 |
| [PacketJsonConverterTest](../bartmoss/src/test/java/io/github/catchingseven/bartmoss/processing/PacketJsonConverterTest.java) | 9 | 9 | 0 | 0 | 0 | 0.114 |
| [PcapMapperTest](../bartmoss/src/test/java/io/github/catchingseven/bartmoss/processing/PcapMapperTest.java) | 16 | 16 | 0 | 0 | 0 | 0.076 |
| [PcapReaderTest](../bartmoss/src/test/java/io/github/catchingseven/bartmoss/processing/PcapReaderTest.java) | 10 | 10 | 0 | 0 | 0 | 0.318 |
| **Total** | **88** | **85** | **3** | **0** | **0** | |

## Fresh JaCoCo counters by class

| Class | Covered lines / total | Covered branches / total |
| --- | ---: | ---: |
| PcapReader | 31/31 | 6/6 |
| PcapMapper | 110/110 | 44/44 |
| TcpDetails | 1/1 | 0/0 |
| TcpFlag | 9/9 | 0/0 |
| UdpDetails | 1/1 | 0/0 |
| CapturedPacket | 54/54 | 0/0 |
| IcmpDetails | 1/1 | 0/0 |
| IpProtocol | 5/5 | 0/0 |
| FileFormat | 3/3 | 0/0 |
| Capture | 21/21 | 0/0 |
| AnalysisResult | 18/18 | 0/0 |
| **Total** | **254/254** | **50/50** |

## Individual complete-run test outcomes

Parameterised entries retain Surefire invocation indexes. Parameters and expected outcomes are defined in the linked test classes. New protocol cases cover TCP, UDP, ICMP and ICMPv6; provider cases cover authentication, quota, connection and HTTP 503; native file cases cover PCAP and PCAPNG.

### ApplicationTest

| Test invocation | Outcome |
| --- | --- |
| `jsonExportFailurePreventsResponseExport` | PASS |
| `laterUploadReplacesSubmittedAndExportedJson` | PASS |
| `returnsProviderSafeErrorWithoutRewritingIt` | PASS |
| `responseExportFailurePropagatesAfterJsonExport` | PASS |
| `readFailuresStopConversionAndDoNotAutomaticallySubmitOrExport` | PASS |
| `coordinatesUploadSubmissionAndExportsInOrder` | PASS |
| `serializationFailureStopsUploadAndRetainsOriginalCause` | PASS |

### AnalysisResultTest

| Test invocation | Outcome |
| --- | --- |
| `constructorAndSettersExposeAnalysisData` | PASS |

### CaptureTest

| Test invocation | Outcome |
| --- | --- |
| `settersReplaceCaptureDataAndCollections` | PASS |
| `constructorInitializesPacketListAndExposesCaptureData` | PASS |

### FileFormatTest

| Test invocation | Outcome |
| --- | --- |
| `declaresSupportedCaptureFormats` | PASS |

### IcmpDetailsTest

| Test invocation | Outcome |
| --- | --- |
| `retainsOptionalIdentifierAndSequenceNumber` | PASS |
| `exposesIcmpFieldsAndAllowsAbsentOptionalFields` | PASS |

### IpProtocolTest

| Test invocation | Outcome |
| --- | --- |
| `declaresSupportedIpProtocols` | PASS |

### PacketTest

| Test invocation | Outcome |
| --- | --- |
| `permitsAbsentFragmentMetadata` | PASS |
| `constructorAndSettersExposePacketMetadata` | PASS |

### ProtocolDetailsTest

| Test invocation | Outcome |
| --- | --- |
| `groupsTcpUdpAndIcmpDetailsUnderOneType` | PASS |

### TcpDetailsTest

| Test invocation | Outcome |
| --- | --- |
| `exposesTcpFieldsAndRecordValueEquality` | PASS |

### TcpFlagTest

| Test invocation | Outcome |
| --- | --- |
| `declaresSupportedTcpFlags` | PASS |

### UdpDetailsTest

| Test invocation | Outcome |
| --- | --- |
| `exposesUdpFieldsAndRecordValueEquality` | PASS |

### AnalysisPipelineIntegrationTest

| Test invocation | Outcome |
| --- | --- |
| `captureToJsonToSdkResponseAndBothExports` | PASS |
| `rateLimitTravelsThroughAdapterApplicationAndResponseExport` | PASS |
| `malformedCaptureIsRejectedBeforeItCanReachModel` | **FAIL - confirmed defect** |
| `responseWriteFailureLeavesJsonExportAndPropagates` | PASS |
| `filesystemFailurePropagatesAndDoesNotWriteResponse` | PASS |

### OpenAIClientTest

| Test invocation | Outcome |
| --- | --- |
| `convertsProviderFailuresToSafeMessages(RuntimeException, String)[1]` | PASS |
| `convertsProviderFailuresToSafeMessages(RuntimeException, String)[2]` | PASS |
| `convertsProviderFailuresToSafeMessages(RuntimeException, String)[3]` | PASS |
| `convertsProviderFailuresToSafeMessages(RuntimeException, String)[4]` | PASS |
| `submitsJsonWithConfiguredModelAndReturnsLinesInOrder` | PASS |
| `rejectsMissingOrBlankKeysWithoutReadingOrChangingEnvironment` | PASS |
| `handlesEmptyOutput` | PASS |
| `doesNotSwallowUnexpectedProgrammingErrors` | PASS |
| `ignoresNonMessageOutputsAndRefusals` | PASS |

### PcapJsonIntegrationTest

| Test invocation | Outcome |
| --- | --- |
| `validHeaderOnlyCaptureSerializesEmptyPacketArray` | PASS |
| `readsRealCaptureAndSerializesPacketMetadata(String)[1]` | PASS |
| `readsRealCaptureAndSerializesPacketMetadata(String)[2]` | PASS |

### FileExporterTest

| Test invocation | Outcome |
| --- | --- |
| `writesJsonAndResponseToDistinctTimestampedUtf8Files` | PASS |
| `characterizesSameSecondExportsOverwritingEarlierContents` | PASS |
| `supportsEmptyContents` | PASS |
| `propagatesMissingDirectoryFailuresForBothFormats` | PASS |

### AnalysisUiIntegrationTest

| Test invocation | Outcome |
| --- | --- |
| `completeAnalysisThroughFxmlButtonsAndExportedFiles` | PASS |

### MainControllerTest

| Test invocation | Outcome |
| --- | --- |
| `loadsFxmlInjectsControllerAndStartsWithActionsDisabled` | PASS |
| `providerSafeErrorTextIsDisplayedToUser` | PASS |
| `analysisControlStateChangesMustStayOnFxThread` | **FAIL - confirmed defect** |
| `successfulExportPassesDisplayedTextAndDisablesRepeatExport` | PASS |
| `cancellingChooserPreservesSelectionAndActionState` | PASS |
| `uploadFailureShowsMessageAndAllowsRetryWithoutRequestingModel` | PASS |
| `selectingCaptureUpdatesPathAndEnablesAnalysis` | PASS |
| `successfulExportMustNotUseFailureTitle` | **FAIL - confirmed defect** |
| `exportFailureDisplaysErrorPreservesTextAndAllowsRetry` | PASS |
| `unexpectedModelFailureShowsUiErrorAndRestoresAnalysisButton` | PASS |
| `analysisRunsOffFxThreadDisplaysLinesAndEnablesExport` | PASS |

### PacketJsonConverterTest

| Test invocation | Outcome |
| --- | --- |
| `reportsSerializationFailureWithOriginalCause` | PASS |
| `escapesUserTextAndSerializesAnalysisResults` | PASS |
| `serializesCaptureMetadataPacketsLengthsAndNanosecondTimestamp` | PASS |
| `serializesEmptyCaptureAndNullOptionalValues` | PASS |
| `preservesPacketOrderAndIpv6NullFragmentFields` | PASS |
| `includesProtocolSpecificRecordFields(IpProtocol)[1]` | PASS |
| `includesProtocolSpecificRecordFields(IpProtocol)[2]` | PASS |
| `includesProtocolSpecificRecordFields(IpProtocol)[3]` | PASS |
| `includesProtocolSpecificRecordFields(IpProtocol)[4]` | PASS |

### PcapMapperTest

| Test invocation | Outcome |
| --- | --- |
| `mapsIpv6FragmentIdentificationAsUnsignedLong` | PASS |
| `mapsIpPacketInsideAnEthernetFrame` | PASS |
| `currentlyFailsWhenTcpHeaderIsMissing` | PASS |
| `mapsIpv6EchoRequestReplyAndNonEchoDetails(int)[1]` | PASS |
| `mapsIpv6EchoRequestReplyAndNonEchoDetails(int)[2]` | PASS |
| `mapsIpv6EchoRequestReplyAndNonEchoDetails(int)[3]` | PASS |
| `ignoresNonIpPackets` | PASS |
| `currentlyDoesNotRecognizePcap4jIcmpv4ProtocolName` | PASS |
| `extractsIpv4EchoRequestReplyAndNonEchoDetails(int)[1]` | PASS |
| `extractsIpv4EchoRequestReplyAndNonEchoDetails(int)[2]` | PASS |
| `extractsIpv4EchoRequestReplyAndNonEchoDetails(int)[3]` | PASS |
| `mapsIpv6AddressesHopLimitAndAbsentFragmentMetadata` | PASS |
| `mapsIpv4FragmentMetadataForUnsupportedProtocol` | PASS |
| `mapsUdpPortsAndUnsignedLength` | PASS |
| `mapsTcpMetadataAndAllSupportedFlags(boolean)[1]` | PASS |
| `mapsTcpMetadataAndAllSupportedFlags(boolean)[2]` | PASS |

### PcapReaderTest

| Test invocation | Outcome |
| --- | --- |
| `initializesMapperAndSkipsNonIpPackets` | PASS |
| `closesHandleWhenMappingFails` | PASS |
| `characterizesNativeOpenFailureReturningDefaultCapture` | PASS |
| `leavesUnsupportedExtensionUnclassified` | PASS |
| `detectsFormatAndReturnsEmptyCaptureAtEof(String, FileFormat)[1]` | PASS |
| `detectsFormatAndReturnsEmptyCaptureAtEof(String, FileFormat)[2]` | PASS |
| `detectsFormatAndReturnsEmptyCaptureAtEof(String, FileFormat)[3]` | PASS |
| `detectsFormatAndReturnsEmptyCaptureAtEof(String, FileFormat)[4]` | PASS |
| `preservesOrderTimestampsAndOriginalNumberingAcrossSkippedPackets` | PASS |
| `propagatesTimeoutAndClosedHandleFailuresAndClosesHandle` | PASS |
