package io.algernon.vespera.corpus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.dataformat.yaml.YAMLFactory;

/**
 * What a text file opens with, read from its first 64 KB and no further (ADR-287 section 3.5): the first XML
 * element, or the root keys of a JSON or YAML document. Reading either is reading the file's bytes, and stops
 * when the answer is known.
 *
 * <p>The XML reader is told to read no DTD and resolve no entity, whatever the file declares: a corpus file is
 * untrusted, and nothing here may fetch from the network or the disk on its say-so.
 */
final class FirstElement {

    /** How many bytes are read. */
    static final int BYTES_READ = 65_536;

    private static final JsonFactory JSON = JsonFactory.builder().build();

    private static final YAMLFactory YAML = YAMLFactory.builder().build();

    private FirstElement() {}

    /** An XML element: its local name and its namespace, {@code null} where it has none. */
    record Element(String localName, String namespace) {}

    /** What {@code file} opens with. */
    sealed interface Opening {

        /** The file opens with an XML element, which is the root, or with none that could be read. */
        record Xml(Optional<Element> root) implements Opening {}

        /** The file opens as a JSON or a YAML document, with these keys at its root, in order, as far as they were read. */
        record Keys(List<String> keys) implements Opening {}
    }

    /** What {@code file} opens with, or throws where it cannot be read. */
    static Opening of(Path file) throws IOException {
        byte[] bytes;
        try (InputStream in = Files.newInputStream(file)) {
            bytes = in.readNBytes(BYTES_READ);
        }
        int skip = startsWithUtf8ByteOrderMark(bytes) ? 3 : 0;
        Charset charset = TimestampedLines.charsetOf(bytes);
        String text = new String(bytes, skip, bytes.length - skip, charset);
        // A UTF-16 or UTF-32 mark stays in the bytes the XML reader gets, since it names their encoding.
        String opening = text.stripLeading();
        if (opening.startsWith("<")) {
            return new Opening.Xml(firstElement(bytes, skip));
        }
        return new Opening.Keys(rootKeys(opening, opening.startsWith("{") ? JSON : YAML));
    }

    private static boolean startsWithUtf8ByteOrderMark(byte[] bytes) {
        return bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF;
    }

    private static Optional<Element> firstElement(byte[] bytes, int skip) {
        XMLStreamReader reader = null;
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
                throw new XMLStreamException("an external entity is never resolved");
            });
            reader = factory.createXMLStreamReader(new ByteArrayInputStream(bytes, skip, bytes.length - skip));
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                    String namespace = reader.getNamespaceURI();
                    return Optional.of(new Element(
                            reader.getLocalName(), namespace == null || namespace.isEmpty() ? null : namespace));
                }
            }
            return Optional.empty();
        } catch (XMLStreamException | RuntimeException e) {
            return Optional.empty();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException ignored) {
                    // nothing was opened that the answer rests on
                }
            }
        }
    }

    /** The root-level keys of the document {@code text} opens, each value skipped; those read before an error count. */
    private static List<String> rootKeys(String text, TokenStreamFactory factory) {
        List<String> keys = new ArrayList<>();
        try (JsonParser parser = factory.createParser(ObjectReadContext.empty(), text)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return keys;
            }
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                keys.add(parser.currentName());
                parser.nextToken();
                parser.skipChildren();
            }
        } catch (RuntimeException e) {
            return keys;
        }
        return keys;
    }
}
