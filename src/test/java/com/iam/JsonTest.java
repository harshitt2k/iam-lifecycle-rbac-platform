package com.iam;

import com.iam.api.Json;
import com.iam.model.Source;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {

    @Test
    void writesMapsListsEnumsAndDates() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("a", 1);
        m.put("b", "x\"y\n");
        m.put("c", List.of(true, Source.DIRECT));
        m.put("d", LocalDate.of(2026, 10, 25));
        m.put("e", null);
        assertEquals("{\"a\":1,\"b\":\"x\\\"y\\n\",\"c\":[true,\"DIRECT\"],\"d\":\"2026-10-25\",\"e\":null}",
                Json.write(m));
    }

    @Test
    void roundTripsNestedValues() {
        Map<String, Object> m = Json.parseObject(
                "{ \"name\": \"Zoë \\u0041\", \"n\": 42, \"f\": 1.5, \"list\": [1, \"two\", {\"k\": false}], \"z\": null }");
        assertEquals("Zoë A", m.get("name"));
        assertEquals(42L, m.get("n"));
        assertEquals(1.5, m.get("f"));
        assertNull(m.get("z"));
        assertEquals(3, ((List<?>) m.get("list")).size());
        assertEquals(m, Json.parseObject(Json.write(m)));
    }

    @Test
    void rejectsBadJson() {
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("{\"a\": }"));
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("{\"a\": 1"));
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("[1,2]"));
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("{\"a\": 1} extra"));
    }
}
