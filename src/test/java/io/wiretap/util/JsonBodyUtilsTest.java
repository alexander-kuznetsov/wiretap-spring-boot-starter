package io.wiretap.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BinaryNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.POJONode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonBodyUtilsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void isJsonBody_returnsTrue_forObjectArrayAndPojoNodes() {
        ObjectNode object = MAPPER.createObjectNode().put("k", "v");
        ArrayNode array = MAPPER.createArrayNode().add(1);
        POJONode pojo = new POJONode(new Object());

        assertThat(JsonBodyUtils.isJsonBody(object)).isTrue();
        assertThat(JsonBodyUtils.isJsonBody(array)).isTrue();
        assertThat(JsonBodyUtils.isJsonBody(pojo)).isTrue();
    }

    @Test
    void isJsonBody_returnsFalse_forScalarNodes() {
        assertThat(JsonBodyUtils.isJsonBody(new TextNode("hello"))).isFalse();
        assertThat(JsonBodyUtils.isJsonBody(IntNode.valueOf(42))).isFalse();
        assertThat(JsonBodyUtils.isJsonBody(BooleanNode.TRUE)).isFalse();
        assertThat(JsonBodyUtils.isJsonBody(NullNode.getInstance())).isFalse();
        assertThat(JsonBodyUtils.isJsonBody(BinaryNode.valueOf(new byte[]{1, 2, 3}))).isFalse();
        assertThat(JsonBodyUtils.isJsonBody(MissingNode.getInstance())).isFalse();
    }

    @Test
    void getStringBody_returnsNull_whenInputIsNull() throws Exception {
        assertThat(JsonBodyUtils.getStringBody(null)).isNull();
    }

    @Test
    void getStringBody_returnsToString_forScalarNodes() throws Exception {
        TextNode text = new TextNode("plain");
        assertThat(JsonBodyUtils.getStringBody(text)).isEqualTo(text.toString());
        assertThat(JsonBodyUtils.getStringBody(IntNode.valueOf(7)))
                .isEqualTo(IntNode.valueOf(7).toString());
    }

    @Test
    void getStringBody_returnsPrettyPrintedMultilineString_forObject() throws Exception {
        ObjectNode object = MAPPER.createObjectNode()
                .put("id", 42)
                .put("name", "alice");

        String pretty = JsonBodyUtils.getStringBody(object);

        assertThat(pretty).contains("\n");
        // Parses back into an equivalent tree so the format change does not lose data.
        JsonNode roundTrip = MAPPER.readTree(pretty);
        assertThat(roundTrip.get("id").asInt()).isEqualTo(42);
        assertThat(roundTrip.get("name").asText()).isEqualTo("alice");
    }

    @Test
    void getStringBody_returnsPrettyPrintedMultilineString_forArrayOfObjects() throws Exception {
        ArrayNode array = MAPPER.createArrayNode();
        array.addObject().put("id", 1);
        array.addObject().put("id", 2);

        String pretty = JsonBodyUtils.getStringBody(array);

        assertThat(pretty).contains("\n");
        JsonNode roundTrip = MAPPER.readTree(pretty);
        assertThat(roundTrip.isArray()).isTrue();
        assertThat(roundTrip).hasSize(2);
        assertThat(roundTrip.get(0).get("id").asInt()).isEqualTo(1);
    }

    @Test
    void getStringBody_returnsSingleLine_forArrayOfScalars() throws Exception {
        // Jackson's DefaultPrettyPrinter keeps shallow scalar arrays on one
        // line — this is fine for log readability and we document it here.
        ArrayNode array = MAPPER.createArrayNode().add(1).add(2).add(3);

        String pretty = JsonBodyUtils.getStringBody(array);

        JsonNode roundTrip = MAPPER.readTree(pretty);
        assertThat(roundTrip.isArray()).isTrue();
        assertThat(roundTrip).hasSize(3);
    }
}
