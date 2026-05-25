package io.wiretap.util;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.BinaryNode;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.POJONode;
import tools.jackson.databind.node.StringNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonBodyUtilsTest {

    private static final ObjectMapper MAPPER = tools.jackson.databind.json.JsonMapper.builder().build();

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
        assertThat(JsonBodyUtils.isJsonBody(StringNode.valueOf("hello"))).isFalse();
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
        StringNode text = StringNode.valueOf("plain");
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
        JsonNode roundTrip = MAPPER.readTree(pretty);
        assertThat(roundTrip.get("id").asInt()).isEqualTo(42);
        assertThat(roundTrip.get("name").asString()).isEqualTo("alice");
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
        ArrayNode array = MAPPER.createArrayNode().add(1).add(2).add(3);

        String pretty = JsonBodyUtils.getStringBody(array);

        JsonNode roundTrip = MAPPER.readTree(pretty);
        assertThat(roundTrip.isArray()).isTrue();
        assertThat(roundTrip).hasSize(3);
    }
}
