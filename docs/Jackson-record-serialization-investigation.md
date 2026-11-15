# Why protocol records are missing from Bartmoss JSON

Investigation date: 20 October 2026. This report describes the saved working tree on branch `feature/OpenAI-API-Integration`, based on commit `d1846e74746e144e35757a9d232a7072ff9e7fc2`, including the existing local changes to `Main.java` and `OpenAIClient.java`.

## Finding

**Jackson can serialize the records in this project. The immediate cause of the missing TCP/UDP information is that `CapturedPacket.protocolDetails` is private and has no getter or Jackson annotation.** Jackson does not discover that parent property, so it never reaches the record stored inside it.

There is a separate mapping defect behind the four packets reported as `ipProtocol: null`: Pcap4J names IPv4 ICMP `ICMPv4`, but the mapper only recognizes `ICMP`. Those four packets lose their protocol classification and ICMP details before serialization.

Both causes were reproduced locally using the actual `traffic_capture.pcapng` and Jackson 2.22.4. No production source was changed and no OpenAI request was made for this investigation. The requested deliverable is this analysis; the repair snippets below are recommendations, not applied fixes.

## 1. What the capture and JSON actually contain

The offline probe read the same path currently selected by `Main`:

```text
bartmoss/src/main/resources/traffic_capture.pcapng
SHA-256: 706C0AAF7F6CC52EB6B7FB9839327BB10377B0844CCFEFB22D8B827EFBA50805
```

It reproduced the reported 235 IPv4 packets, from `2026-10-20T08:00:00.100Z` to `2026-10-20T08:00:09.500Z`, a span of 9.4 seconds. The generated baseline JSON also confirms matching original/captured lengths for every packet, TTL 64 throughout, zero fragment offsets, and no `moreFragments` flags.

| Measurement | Observed result |
|---|---:|
| Frames read directly from the capture | 235 |
| Packets retained by `PcapReader` | 235 |
| Mapped TCP packets | 211 |
| Mapped UDP packets | 20 |
| Packets with mapped protocol `null` | 4 |
| Non-null `TcpDetails` objects stored inside packets | 211 |
| Non-null `UdpDetails` objects stored inside packets | 20 |
| Packets containing a `protocolDetails` key in baseline JSON | **0** |
| Packets containing that key after isolated field exposure | **235** |
| Non-null detail objects after isolated field exposure | **231** |

The distinction is decisive: the 231 TCP/UDP records are present in the Java objects, but the original serializer omits the property that would expose them. The four ICMP records are not present in the Java objects at all.

This reproduces the missing metadata in the supplied model response. The original HTTP request body was not available, so this is a reproduction of current behavior, not a recovered transcript of the earlier API call.

## 2. Where information is lost in the pipeline

```text
traffic_capture.pcapng
  -> PcapReader.readFromFile(...)
  -> PcapMapper.mapPacket(...)
       -> extracts TCP/UDP headers into detail records
       -> stores the record in CapturedPacket.protocolDetails
  -> Capture.packets
  -> PacketJsonConverter.convertToJson(capture)
       -> discovers CapturedPacket's visible JSON properties
       -> protocolDetails is not a discovered property  <-- TCP/UDP loss
  -> Main passes the resulting JSON String to OpenAIClient
  -> OpenAIClient places that String in the request input
```

Relevant source, with line numbers from the inspected snapshot:

| File | Evidence |
|---|---|
| [`CapturedPacket.java`](../bartmoss/src/main/java/io/github/catchingseven/bartmoss/domain/CapturedPacket.java) | Line 15 declares `private ProtocolDetails protocolDetails`; line 32 assigns the constructor argument to it. The class provides getters for surrounding fields but no `getProtocolDetails()` method. |
| [`PcapMapper.java`](../bartmoss/src/main/java/io/github/catchingseven/bartmoss/processing/PcapMapper.java) | Lines 60–62 classify the protocol and extract details; line 92 passes those details to the packet constructor. `extractProtocolDetails` constructs the TCP, UDP and ICMP records. |
| [`PacketJsonConverter.java`](../bartmoss/src/main/java/io/github/catchingseven/bartmoss/processing/PacketJsonConverter.java) | Lines 13–17 create a default `ObjectMapper`, register `JavaTimeModule`, and call `writeValueAsString(capture)`. There is no custom field visibility or record filter. |
| [`Main.java`](../bartmoss/src/main/java/io/github/catchingseven/bartmoss/Main.java) | Reads `traffic_capture.pcapng`, converts the capture, and passes the resulting string to `requestResponse`. |
| [`OpenAIClient.java`](../bartmoss/src/main/java/io/github/catchingseven/bartmoss/integration/OpenAIClient.java) | Lines 20–22 build the request input by concatenating `"Analyse the json stuff: "` and the supplied JSON. No packet fields are removed in this method. |

The API receives text prepared by Bartmoss. It has no access to the original Java objects or the capture file through this code path. Changing the analysis prompt cannot recover fields that were never included in that text.

## 3. Why Jackson behaves this way

### Property discovery comes before nested serialization

Jackson serializes a Java object according to its discovered JSON properties. It does not automatically dump every private field in memory.

For the mapper used here, the probe printed:

```text
getter=PUBLIC_ONLY
isGetter=PUBLIC_ONLY
setter=ANY
creator=ANY
field=PUBLIC_ONLY
```

Public bean getters such as `getSourceIpAddress()` expose properties such as `sourceIpAddress`. A private, unannotated field with no readable accessor does not satisfy those visibility rules. Jackson's official [mapper feature documentation](https://fasterxml.github.io/jackson-databind/javadoc/2.14/com/fasterxml/jackson/databind/MapperFeature.html) describes public fields and public `get...` methods as the normal automatically detected properties. The runtime probe confirms the effective settings for the installed 2.22.4 mapper.

For `CapturedPacket`, Jackson discovered exactly these serializable properties:

```text
packetNumber, timestamp, capturedLength, originalLength,
ipVersion, sourceIpAddress, destinationIpAddress, ipProtocol,
ttlOrHopLimit, fragementIdentification, fragmentOffset, moreFragments
```

`protocolDetails` is absent. Consequently, Jackson does not obtain its value and does not select a serializer for the nested `TcpDetails` or `UdpDetails` object. Record handling is never reached for that branch of the capture.

Passing a value into a constructor and storing it privately does not expose it for JSON output. Constructor parameters concern object creation, while serialization needs a readable property on the existing object. A setter is also unnecessary for this output operation: the missing piece is a getter or another explicitly readable field/property.

### Records use a different accessor convention, and Jackson supports it

`TcpDetails` is a record. Its generated accessor is `sourcePort()`, rather than the JavaBean method `getSourcePort()`. Jackson has explicit record support and recognizes record component accessors. That support was introduced in Jackson 2.12, as documented in the official [2.12 release notes](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.12#java-14-record-type-support).

The project declares Jackson databind 2.22.4, and the probe confirmed that exact runtime version and jar path. Direct serialization of all three production record classes succeeded:

```json
{"sourcePort":54321,"destionationPort":443,"sequenceNumber":123,"acknowledgementNumber":456,"flags":["SYN"],"windowSize":64240}
```

```json
{"sourcePort":53000,"destionationPort":53,"length":40}
```

```json
{"version":4,"type":8,"code":0,"identifier":7,"sequenceNumber":1}
```

These are constructed probe values, not packets from the capture. They establish that the records themselves are serializable using the installed library. Adding JavaBean getters to the records or changing them into ordinary classes would leave the parent-property defect unresolved.

### The marker interface is not blocking serialization

The field has declared type `ProtocolDetails`, an empty interface. With the default configuration used here, Jackson can serialize a visible property's actual runtime value, such as `TcpDetails`.

The probe also used a temporary subclass of `CapturedPacket` with a public `ProtocolDetails getProtocolDetails()` method. All three record types serialized correctly through that interface-typed getter. This isolates the missing accessor from both record support and interface polymorphism.

Type discriminators such as `@JsonTypeInfo` may become relevant when reading JSON back into an interface-typed field and choosing which implementation to construct. That deserialization problem is separate from this outbound serialization failure.

### Why there is no exception

`CapturedPacket` has twelve other readable properties, so Jackson can create a valid object serializer and write valid JSON. It has no schema telling it that a thirteenth property is required. Omission is therefore successful serialization under the configured rules.

`FAIL_ON_EMPTY_BEANS` would not detect this condition: the packet bean is not empty. The hidden nested record is never visited. Likewise, `JavaTimeModule` handles `Instant` timestamps; registering it does not expose unrelated private fields or enable record support.

## 4. Before and after: the actual first packet

The current converter generates the following first packet. The timestamp is a numeric epoch-seconds value because the converter leaves Jackson's timestamp formatting defaults in place.

```json
{
  "packetNumber": 0,
  "timestamp": 1792483200.100000000,
  "capturedLength": 54,
  "originalLength": 54,
  "ipVersion": 4,
  "sourceIpAddress": "192.0.2.10",
  "destinationIpAddress": "203.0.113.80",
  "ipProtocol": "TCP",
  "ttlOrHopLimit": 64,
  "fragementIdentification": 701,
  "fragmentOffset": 0,
  "moreFragments": false
}
```

An experimental mapper used a mix-in to expose only `protocolDetails`, without editing the domain class. It added this data to the same packet:

```json
"protocolDetails": {
  "sourcePort": 51010,
  "destionationPort": 443,
  "sequenceNumber": 13000,
  "acknowledgementNumber": 0,
  "flags": ["SYN"],
  "windowSize": 64240
}
```

The probe compared every exposed detail value with the stored record, not just this example. All 231 non-null records matched. The remaining four detail values were explicitly `null` after exposure.

This establishes that the first packet's destination port and SYN flag were available locally. It does not by itself classify the whole burst or prove an attack; that would require analyzing the restored metadata across the capture.

## 5. Why four protocol values are null

This defect happens earlier, inside `PcapMapper`:

```java
String protocol = header.getProtocol().name();
ipProtocol = getProtocolType(protocol);
protocolDetails = extractProtocolDetails(ipProtocol, ipPacket);
```

The mapper lowercases the string and compares it with `"icmp"`. Pcap4J returns `"ICMPv4"` for IPv4 protocol number 1. Lowercasing produces `"icmpv4"`, which does not match. The mapper returns `null`, and `extractProtocolDetails(null, ...)` returns `null` without entering its ICMP branch.

A second, direct pass over the actual capture reported:

```text
IPv4 protocol 6 / TCP:     211
IPv4 protocol 17 / UDP:     20
IPv4 protocol 1 / ICMPv4:    4

Zero-based frame 8:   ICMP type 8, code 0  (echo request)
Zero-based frame 9:   ICMP type 0, code 0  (echo reply)
Zero-based frame 227: ICMP type 8, code 0  (echo request)
Zero-based frame 228: ICMP type 0, code 0  (echo reply)
```

For this capture, the four unknown values can therefore be identified from the original packets. A getter alone will not restore them, because their details were never constructed.

The existing test `currentlyDoesNotRecognizePcap4jIcmpv4ProtocolName` already asserts this current null behavior. Other ICMP tests substitute the artificial protocol name `"ICMP"` to exercise the extraction branch. Those tests characterize the limitation; passing them does not mean real IPv4 ICMP is mapped correctly.

## 6. Recommended repairs and their boundaries

### Expose the parent property

The smallest repair consistent with the domain class's existing accessor style is to add this method to `CapturedPacket`:

```java
public ProtocolDetails getProtocolDetails() {
    return protocolDetails;
}
```

No setter is required to serialize the value. The record declarations, marker interface and converter can remain as they are for this repair. The probe's getter-bearing subclass demonstrates this mechanism; a future production patch should also be checked through the real converter.

An alternative is to annotate the existing field:

```java
@JsonProperty("protocolDetails")
private ProtocolDetails protocolDetails;
```

Jackson documents this field/property annotation in its [annotation reference](https://github.com/FasterXML/jackson-annotations/wiki/Jackson-Annotations). A mix-in can apply an equivalent rule without placing Jackson annotations in the domain layer; the probe used that approach for its full-capture experiment.

Globally enabling all private fields would also expose this field, but it changes the JSON contract for every affected class. A single getter or field annotation is a narrower repair. Upgrading Jackson, enabling default typing, changing the prompt, or adding a record module is not needed to address the reproduced cause.

### Recognize IPv4 ICMP

A small correction is to accept `"icmpv4"` as well as `"icmp"` in the current classifier. A more stable mapping uses numeric protocol identifiers instead of library display names:

```java
int protocolNumber = Byte.toUnsignedInt(header.getProtocol().value());
IpProtocol ipProtocol = switch (protocolNumber) {
    case 6 -> IpProtocol.TCP;
    case 17 -> IpProtocol.UDP;
    case 1 -> IpProtocol.ICMP;
    case 58 -> IpProtocol.ICMPv6;
    default -> null;
};
```

This illustrates the protocol-name correction for the current mapping path. It does not address IPv6 extension-header traversal or malformed/incomplete transport headers, which would need separate treatment.

### Preserve the distinction between absent metadata and unsupported payload analysis

The model also reported absent payloads. Bartmoss's [requirements](Bartmoss-requirements.md) explicitly scope the application to Layer 3/4 and ICMP metadata and exclude application payload analysis. No payload field exists in `CapturedPacket` or these records. Exposing `protocolDetails` restores ports and flags, not payload content or application decoding.

Two spelling issues are independent of the missing records:

- `fragementIdentification` is the existing packet field/getter spelling, so Jackson emits that spelling.
- TCP and UDP records use `destionationPort`, so successful record serialization also emits that spelling.

Renaming either property would change the JSON field name and should be handled deliberately. Neither typo causes the parent property to disappear.

## 7. Why existing verification did not catch this

The current tests verify domain construction, record accessors/equality and packet mapping. In `PcapMapperTest`, the helper `details(...)` uses reflection to read the private field directly. It can prove that a `TcpDetails` object was stored while bypassing the missing getter that affects Jackson.

There is no `PacketJsonConverterTest` in the inspected test tree. The JaCoCo include list in `pom.xml` covers the domain package, `PcapReader` and `PcapMapper`, but excludes `PacketJsonConverter` and `OpenAIClient`. Even complete executable-line coverage in the included classes would not assert that the JSON contains ports or flags.

The checklist marks protocol-specific JSON representation and JSON output tests complete. The inspected code and reproduced output do not support treating those checks as satisfied. The checklist was left unchanged during this documentation task.

A future repair should add assertions at the actual serialization boundary:

| Test | Required assertion |
|---|---|
| TCP packet through `PacketJsonConverter` | `packets[0].protocolDetails` contains both ports, sequence/acknowledgement numbers, flags and window. |
| UDP packet through the converter | Both ports and UDP length are present with their exact recorded values. |
| ICMP packet through the converter | Version, type and code are present; echo identifier/sequence values survive. |
| Real Pcap4J IPv4 ICMP input | Protocol number 1 produces `IpProtocol.ICMP` and `IcmpDetails` without substituting a display name. |
| Unsupported protocol | The agreed representation distinguishes an unsupported/null value from an accidentally omitted property. |
| Local full-capture check | Before ICMP repair: 231 non-null details; after both repairs: 235, including the four ICMP packets. |

The probe is an investigation aid, not a permanent regression test. It intentionally asserts the current omission, so its baseline checks will need updating once the production getter or annotation is added.

## 8. Reproduction and validation

The accompanying [Java probe](investigations/record-serialization/RecordSerializationProbe.java) and [PowerShell runner](investigations/record-serialization/run-probe.ps1) compile the current domain and processing sources into an isolated directory. They do not compile or execute `Main` or `OpenAIClient`, retrieve API credentials, or contact a provider.

Run from the repository root:

```powershell
& .\docs\investigations\record-serialization\run-probe.ps1
```

The runner assumes Java/Javac 25 on `PATH`, the exact listed dependencies cached under `$env:USERPROFILE\.m2\repository`, and Npcap in `C:\Windows\System32\Npcap`. Its classpath intentionally includes only the dependencies needed for this local investigation. An SLF4J warning about having no logging provider is expected for this reduced classpath.

Generated evidence is written under `bartmoss/target/record-serialization-investigation/`:

- `probe-output.txt`: version, visibility, record examples, counts and assertions.
- `baseline.json`: output of the unchanged production converter.
- `field-exposed.json`: experimental output with only the missing field made visible.

The final probe run passed. It freshly compiled the inspected source and checked direct record serialization, interface-typed getter serialization, absence of the baseline parent property, and equality between stored records and every experimentally exposed detail value.

Maven `test-compile` also succeeded. A focused run of the domain tests and `PcapMapperTest` completed with **29 tests, zero failures, zero errors, zero skips**, using:

```powershell
Set-Location C:\mythweaver\bartmoss\bartmoss
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot'
& 'C:\Program Files\IntelliJ IDEA Community Edition 2023.2.4\plugins\maven\lib\maven3\bin\mvn.cmd' -o '-Dtest=io.github.catchingseven.bartmoss.domain.*Test,io.github.catchingseven.bartmoss.processing.PcapMapperTest' test
```

A separate full `mvn -o verify` attempt stopped making progress in `PcapReaderTest` and was interrupted. It produced no final suite or coverage-gate result; this report does not claim a full-suite pass. An initial attempt to use `dependency:build-classpath` also failed because Maven's dependency plugin was not cached offline; the standalone runner avoids that plugin.

The confirmed cause does not depend on either incomplete build attempt: the actual capture, current converter, installed Jackson version and isolated exposure experiments all reproduce the loss directly.
