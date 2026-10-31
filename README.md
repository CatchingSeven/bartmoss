# Bartmoss

Bartmoss is an experimental Java project exploring how large language models can be integrated into cybersecurity systems. The main goal is to test how well an LLM can identify malicious activity in network traffic.

I started Bartmoss while working on a university assignment, which prompted me to explore how LLMs could be integrated into cybersecurity systems. I was particularly interested in their ability to interpret structured and unstructured data and reason about the context within it. I wanted to investigate how well that could translate to identifying malicious activity in network traffic, and what limitations would emerge in practice. Bartmoss is my way of exploring that question through implementation and testing.

## Current status

**Bartmoss is in early development and is far from finished**

The current implementation works with existing PCAP and PCAPNG files. Testing so far has been exploratory, and I haven’t established how reliably it can identify malicious activity across different types of traffic. It should be treated as an experimental prototype.

## How it works

The current process is:

1. Read a saved PCAP or PCAPNG file using Pcap4J.
2. Extract packet metadata and map it into Java objects.
3. Convert that data to JSON using Jackson.
4. Send the JSON to the OpenAI API for analysis and retrieve the model’s response.

The integration currently uses the OpenAI Java SDK and is configured to use `gpt-6-luna`. Packet metadata is sent to the OpenAI API when analysis is requested.

Capture reading, packet mapping, JSON conversion and API integration are handled by seperate classes. This gives me room to experiment with individual parts of the program as the project develops.

The project also includes handling for authentication, rate-limit, connection and service errors, along with JUnit 5 and Mockito tests for parts of the Java implementation. These unit tests are separate from evaluating whether the model correctly identifies malicious traffic.

## Early testing

Some of the easier tests used AI-generated synthetic PCAPs containing simulated DDoS traffic. In those tests, the model identified the attack and provided an explanation that made sense when I reviewed it.

I’ve also tried more difficult cases, with less consistent results. One attempted test involved a deliberately obfuscated capture of WannaCry traffic containing roughly 40,000 packets. I couldn’t get an analysis of that capture and suspect the input was too large, although I haven’t confirmed the exact cause.

These are early observations. They don’t establish a detection rate or show how well Bartmoss would perform on a live network.

## Limitations and further work

### Large captures

The current approach sends the extracted capture data as JSON in a single request. This becomes a problem as the amount of data grows.

One area I want to explore is how to split a large capture into smaller inputs while preserving the context between them. Breaking the data up is only part of the problem; the model also needs enough context to recognise activity spread across different parts of the capture.

### Hallucinations and interpretation

As with any technology that utilises LLMs, there is a risk that the model will produce a convincing explanation that is incorrect, or miss malicious activity altogether. Its output needs to be checked against the traffic being analysed.

Further testing is needed to understand where the approach works, where it struggles and how much confidence can reasonably be placed in its conclusions.

### Live capture

Bartmoss currently reads saved captures. I haven’t tested how it would perform with live network traffic.

Because capture reading is handled separately, I can explore replacing the file reader with a live capture component. How the rest of the system would handle a continuous stream of traffic still need investigation.

## Technologies

- Java and Maven
- Pcap4J
- Jackson
- OpenAI Java SDK and Responses API
- JUnit 5 and Mockito

## License

See [LICENSE](https://github.com/CatchingSeven/bartmoss/blob/main/LICENSE)
